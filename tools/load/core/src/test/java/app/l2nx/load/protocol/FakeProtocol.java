/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol;

import app.l2nx.load.bot.intent.Intent;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.bot.model.Position;
import app.l2nx.load.bot.observation.Entered;
import app.l2nx.load.bot.observation.Observation;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.nio.NioIoHandler;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;

/**
 * Core test double: scripted answers with fault injection (stalls, slow answers, rejections, decode failures, drops,
 * refused identities). {@code InMemoryProtocol} in {@code tools/load/app} is the runnable protocol; core cannot depend
 * on it.
 */
public final class FakeProtocol implements ClientProtocol {
    public static final Position SPAWN =
            Position.builder().x(100).y(200).z(-300).build();

    private final LongSupplier clock;
    private final List<Session> sessions = new CopyOnWriteArrayList<>();
    private Set<IntentKind> capabilities = EnumSet.allOf(IntentKind.class);
    private Set<IntentKind> unacknowledged = EnumSet.noneOf(IntentKind.class);
    private LoadBotState lastState = LoadBotState.IN_GAME;
    private long ackDelayNanos;
    private long connectDelayNanos;
    private long stallFrom;
    private long stallUntil;
    private boolean stalled;
    private final Map<Integer, Long> slowAnswers = new HashMap<>();
    private final Set<Integer> rejections = new HashSet<>();
    private IntPredicate failOnEnter = index -> false;
    private IntPredicate dropOnEnter = index -> false;
    private IntPredicate rejectIdentity = index -> false;

    public FakeProtocol(LongSupplier clock) {
        this.clock = clock;
    }

    public FakeProtocol capabilities(Set<IntentKind> capabilities) {
        this.capabilities = EnumSet.copyOf(capabilities);
        return this;
    }

    public FakeProtocol lastState(LoadBotState state) {
        lastState = state;
        return this;
    }

    public FakeProtocol ackDelay(Duration delay) {
        ackDelayNanos = delay.toNanos();
        return this;
    }

    public FakeProtocol connectDelay(Duration delay) {
        connectDelayNanos = delay.toNanos();
        return this;
    }

    public FakeProtocol neverAcknowledge(IntentKind kind) {
        unacknowledged.add(kind);
        return this;
    }

    /** Intents received in {@code [from, until)} of the clock are acknowledged no earlier than {@code until}. */
    public FakeProtocol stall(long from, long until) {
        stallFrom = from;
        stallUntil = until;
        stalled = true;
        return this;
    }

    public FakeProtocol slowAnswer(int ordinal, Duration delay) {
        slowAnswers.put(ordinal, delay.toNanos());
        return this;
    }

    public FakeProtocol reject(int ordinal) {
        rejections.add(ordinal);
        return this;
    }

    public FakeProtocol failOnEnter(IntPredicate indexes) {
        failOnEnter = indexes;
        return this;
    }

    public FakeProtocol dropOnEnter(IntPredicate indexes) {
        dropOnEnter = indexes;
        return this;
    }

    public FakeProtocol rejectIdentity(IntPredicate indexes) {
        rejectIdentity = indexes;
        return this;
    }

    public List<Session> sessions() {
        return sessions;
    }

    @Override
    public String id() {
        return "fake";
    }

    @Override
    public Set<IntentKind> capabilities() {
        return capabilities;
    }

    @Override
    public LoadBotState lastState() {
        return lastState;
    }

    @Override
    public boolean hasServer() {
        return false;
    }

    @Override
    public IoHandlerFactory ioHandlerFactory() {
        return NioIoHandler.newFactory();
    }

    @Override
    public void checkIdentity(LoadBotIdentity identity) {
        if (rejectIdentity.test(identity.index())) {
            throw new IllegalArgumentException("The account " + identity.account() + " is too long");
        }
    }

    @Override
    public ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener listener) {
        final Session session = new Session(identity, loop, listener);
        sessions.add(session);
        loop.execute(() -> {
            session.state(LoadBotState.LOGIN_CONNECT);
            if (connectDelayNanos == 0) {
                session.state(LoadBotState.LOGIN_AUTH);
            } else {
                loop.schedule(() -> session.state(LoadBotState.LOGIN_AUTH), connectDelayNanos, TimeUnit.NANOSECONDS);
            }
        });
        return session;
    }

    public final class Session implements ClientSession {
        private final LoadBotIdentity identity;
        private final EventLoop loop;
        private final SessionListener listener;
        private final List<Intent> sent = new CopyOnWriteArrayList<>();
        private volatile boolean open = true;
        private int scenarioIntents;
        private long lastAnswerAt = Long.MIN_VALUE;

        private Session(LoadBotIdentity identity, EventLoop loop, SessionListener listener) {
            this.identity = identity;
            this.loop = loop;
            this.listener = listener;
        }

        public List<Intent> sent() {
            return sent;
        }

        public boolean open() {
            return open;
        }

        public void emit(Observation observation) {
            respond(() -> listener.observed(observation));
        }

        public void fail() {
            respond(() -> listener.failed(new IllegalStateException("Fake frame did not decode")));
        }

        @Override
        public void send(Intent intent) {
            sent.add(intent);
            final IntentKind kind = intent.kind();
            switch (kind) {
                case LOGIN -> respond(() -> acknowledge(kind, LoadBotState.SERVER_LIST));
                case SELECT_SERVER ->
                    respond(() -> acknowledge(kind, LoadBotState.GAME_CONNECT, LoadBotState.CHARACTER_SELECT));
                case SELECT_CHARACTER -> respond(() -> acknowledge(kind, LoadBotState.ENTERING));
                case ENTER_WORLD -> respond(this::enter);
                case LOGOUT -> respond(() -> acknowledge(kind, LoadBotState.CLOSING, LoadBotState.STOPPED));
                default -> {
                    final int ordinal = scenarioIntents++;
                    if (!unacknowledged.contains(kind)) {
                        answerLater(kind, ordinal);
                    }
                }
            }
        }

        @Override
        public void close() {
            open = false;
        }

        private void enter() {
            if (failOnEnter.test(identity.index())) {
                listener.failed(new IllegalStateException("Fake frame did not decode"));
                return;
            }
            if (dropOnEnter.test(identity.index())) {
                state(LoadBotState.STOPPED);
                return;
            }
            listener.acknowledged(IntentKind.ENTER_WORLD);
            listener.observed(Entered.builder()
                    .selfObjectId(1_000 + identity.index())
                    .position(SPAWN)
                    .build());
            state(LoadBotState.IN_GAME);
        }

        private void answerLater(IntentKind kind, int ordinal) {
            final long now = clock.getAsLong();
            long answerAt = now + slowAnswers.getOrDefault(ordinal, ackDelayNanos);
            if (stalled && now - stallFrom >= 0 && now - stallUntil < 0) {
                answerAt = Math.max(answerAt, stallUntil);
            }
            answerAt = Math.max(answerAt, lastAnswerAt);
            lastAnswerAt = answerAt;
            final Runnable answer = rejections.contains(ordinal)
                    ? () -> {
                        if (open) {
                            listener.rejected(kind);
                        }
                    }
                    : () -> acknowledge(kind);
            loop.schedule(answer, answerAt - now, TimeUnit.NANOSECONDS);
        }

        private void acknowledge(IntentKind kind, LoadBotState... next) {
            if (!open) {
                return;
            }
            listener.acknowledged(kind);
            state(next);
        }

        private void state(LoadBotState... states) {
            for (LoadBotState state : states) {
                if (open) {
                    listener.stateChanged(state);
                }
            }
        }

        private void respond(Runnable response) {
            loop.execute(() -> {
                if (open) {
                    response.run();
                }
            });
        }
    }
}
