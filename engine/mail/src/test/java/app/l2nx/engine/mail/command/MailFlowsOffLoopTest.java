/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.fixture.FakeItems;
import app.l2nx.engine.mail.fixture.FakeParticipants;
import app.l2nx.engine.mail.fixture.MailWorld;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailSent;
import app.l2nx.engine.mail.view.ReceivedMailList;
import app.l2nx.engine.mail.view.ReceivedMailOpened;
import app.l2nx.engine.mail.view.SentMailList;
import app.l2nx.engine.mail.view.SentMailOpened;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Runs the handlers on a real loop; a flow that takes a database connection on the loop trips the chronicle's
 * {@code l2nx.engine.jdbc.on.loop} meter.
 */
class MailFlowsOffLoopTest {
    static final int ARROW = 17;
    static final int SWORD = 1000;
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final AtomicInteger connectionsOnLoop = new AtomicInteger();
    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final MailWorld world =
            new MailWorld(MailSettings.defaults(), runner, background, this::countingConnectionsOnLoop);
    private final Loop loop =
            new Loop("world", world.clock, 42L, EngineProperties.defaults(), new SimpleMeterRegistry());
    private final MailActor sender = world.online("Sender");
    private final MailActor receiver = world.online("Receiver");

    @BeforeEach
    void bindLoop() {
        world.participants.loop(loop::execute);
    }

    @AfterEach
    void stopThreads() {
        runner.shutdownNow();
        background.shutdownNow();
    }

    private DataSource countingConnectionsOnLoop(DataSource dataSource) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "getConnection" -> {
                            if (Loop.current() != null) {
                                connectionsOnLoop.incrementAndGet();
                            }
                        }
                        default -> {}
                    }
                    try {
                        return method.invoke(dataSource, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private void onLoopUntil(Runnable work, BooleanSupplier done) {
        loop.execute(work);
        final long deadline = System.nanoTime() + TIMEOUT.toNanos();
        do {
            assertThat(System.nanoTime()).as("the flow completes").isLessThan(deadline);
            loop.runPass(Math.max(loop.getPassTime(), world.clock.gameMillis()));
            Thread.onSpinWait();
        } while (!done.getAsBoolean());
    }

    private static boolean emitted(MailActor actor, Predicate<ViewEvent> event) {
        return FakeParticipants.port(actor).events().stream().anyMatch(event);
    }

    private UUID send() {
        world.give(sender, FakeItems.ADENA, 10_000);
        final FakeItems.Item sword = world.give(sender, SWORD, 1);
        final FakeItems.Item arrows = world.give(sender, ARROW, 50);
        onLoopUntil(
                () -> MailHandlers.send(world)
                        .handle(
                                sender,
                                SendMail.builder()
                                        .receiverName("Receiver")
                                        .subject("Hello")
                                        .items(List.of(
                                                new ItemCount(sword.handle(), 1), new ItemCount(arrows.handle(), 20)))
                                        .build()),
                () -> emitted(sender, MailSent.INSTANCE::equals));
        return world.receivedBy(receiver.characterId()).getFirst().id();
    }

    @Nested
    class Probe {
        @Test
        void countingConnectionsOnLoop_shouldCount_whenTheLoopQueriesTheDatabase() {
            final AtomicInteger counted = new AtomicInteger(-1);

            onLoopUntil(() -> counted.set(world.mails.countReceived(receiver.characterId())), () -> counted.get() >= 0);

            assertThat(connectionsOnLoop).hasValue(1);
        }
    }

    @Nested
    class Send {
        @Test
        void handle_shouldTakeNoConnectionOnTheLoop_whenAMailIsSent() {
            send();

            assertThat(emitted(receiver, MailArrived.FRESH::equals)).isTrue();
            assertThat(world.receivedBy(receiver.characterId())).hasSize(1);
            assertThat(connectionsOnLoop).hasValue(0);
        }
    }

    @Nested
    class AfterSend {
        enum Flow {
            RECEIVE,
            CANCEL,
            RETURN,
            OPEN_RECEIVED,
            OPEN_SENT,
            LIST_RECEIVED,
            LIST_SENT,
            DELETE_RECEIVED
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Flow.class)
        void handle_shouldTakeNoConnectionOnTheLoop_whenTheMailIsHandled(Flow flow) {
            final UUID mailId = send();

            switch (flow) {
                case RECEIVE ->
                    onLoopUntil(
                            () -> MailHandlers.receive(world).handle(receiver, new ReceiveAttachments(mailId)),
                            () -> emitted(receiver, MailDone.of(MailNotice.RECEIVED)::equals));
                case CANCEL ->
                    onLoopUntil(
                            () -> MailHandlers.cancel(world).handle(sender, new CancelMail(mailId)),
                            () -> emitted(sender, MailDone.of(MailNotice.CANCELLED)::equals));
                case RETURN ->
                    onLoopUntil(
                            () -> MailHandlers.returnMail(world).handle(receiver, new ReturnMail(mailId)),
                            () -> emitted(receiver, MailDone.of(MailNotice.RETURNED)::equals));
                case OPEN_RECEIVED ->
                    onLoopUntil(
                            () -> MailHandlers.openReceived(world).handle(receiver, new OpenReceivedMail(mailId)),
                            () -> emitted(receiver, ReceivedMailOpened.class::isInstance));
                case OPEN_SENT ->
                    onLoopUntil(
                            () -> MailHandlers.openSent(world).handle(sender, new OpenSentMail(mailId)),
                            () -> emitted(sender, SentMailOpened.class::isInstance));
                case LIST_RECEIVED ->
                    onLoopUntil(
                            () -> MailHandlers.listReceived(world).handle(receiver, new ListReceivedMail()),
                            () -> emitted(receiver, ReceivedMailList.class::isInstance));
                case LIST_SENT ->
                    onLoopUntil(
                            () -> MailHandlers.listSent(world).handle(sender, new ListSentMail()),
                            () -> emitted(sender, SentMailList.class::isInstance));
                case DELETE_RECEIVED ->
                    onLoopUntil(
                            () -> MailHandlers.deleteReceived(world)
                                    .handle(receiver, new DeleteReceivedMail(List.of(mailId))),
                            () -> emitted(
                                    receiver, MailRefused.of(MailAction.DELETE, MailRefusal.HAS_ATTACHMENTS)::equals));
            }

            assertThat(connectionsOnLoop).hasValue(0);
        }
    }
}
