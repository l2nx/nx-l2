/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueOutcomeHandler;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailItems;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailRules;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.ParticipantState;
import app.l2nx.engine.mail.Reservation;
import app.l2nx.engine.mail.ReserveResult;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.repository.SendTransaction;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailSent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.local.SynchronizationStrategy;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The send interval is one token per character; a bucket untouched for one interval is full again, so evicting it from
 * the cache forgets nothing.
 */
@Component
class SendMailHandler implements ContextCommandHandler<MailActor, SendMail> {
    static final int MAX_RECEIVER_NAME = 16;
    static final int MAX_SUBJECT = 128;
    static final int MAX_BODY = 512;

    private final MailSettings settings;
    private final MailParticipants participants;
    private final MailItems items;
    private final ValueTransactionSubmitter submitter;
    private final MailWriter writer;
    private final Duration sendInterval;
    private final TimeMeter time;
    private final Cache<UUID, Bucket> sendTokens;

    SendMailHandler(
            MailSettings settings,
            MailParticipants participants,
            MailItems items,
            ValueTransactionSubmitter submitter,
            MailWriter writer,
            GameClock clock) {
        this.settings = settings;
        this.participants = participants;
        this.items = items;
        this.submitter = submitter;
        this.writer = writer;
        this.sendInterval = settings.sendInterval();
        this.time = new GameTimeMeter(clock);
        // Maintenance runs on the caller, the loop, instead of the common pool.
        this.sendTokens = Caffeine.newBuilder()
                .expireAfterAccess(sendInterval)
                .ticker(time::currentTimeNanos)
                .executor(Runnable::run)
                .build();
    }

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, SendMail command) {
        final List<ItemCount> attached = command.items();
        final Long codAdena = command.codAdena();
        final ParticipantState state = participants.state(actor.characterHandle());
        final MailRefusal ruled = MailRules.refusal(settings, state, !attached.isEmpty() || (codAdena != null), true);
        if (ruled != null) {
            refuse(actor, ruled);
            return;
        }
        if (state.mailBlocked() || (state.transactionsBlocked() && (!attached.isEmpty() || (codAdena != null)))) {
            refuse(actor, MailRefusal.JAILED);
            return;
        }
        final String body = ((command.body() == null) || command.body().isBlank()) ? null : command.body();
        if (!within(command.receiverName(), MAX_RECEIVER_NAME)
                || !within(command.subject(), MAX_SUBJECT)
                || ((body != null) && (body.length() > MAX_BODY))) {
            refuse(actor, MailRefusal.INVALID_TEXT);
            return;
        }
        if (attached.size() > settings.maxAttachments()) {
            refuse(actor, MailRefusal.TOO_MANY_ATTACHMENTS);
            return;
        }
        if ((codAdena != null) && attached.isEmpty()) {
            refuse(actor, MailRefusal.COD_WITHOUT_ATTACHMENTS);
            return;
        }
        if ((codAdena != null) && ((codAdena < 1) || (codAdena > items.adenaLimit()))) {
            refuse(actor, MailRefusal.COD_AMOUNT_INVALID);
            return;
        }
        if (!mailable(actor, attached)) {
            refuse(actor, MailRefusal.ITEM_NOT_MAILABLE);
            return;
        }
        if (participants.refusesMailFrom(command.receiverName(), actor.characterId())) {
            actor.port()
                    .emit(MailRefused.builder()
                            .action(MailAction.SEND)
                            .reason(MailRefusal.RECEIVER_BLOCKED)
                            .param(command.receiverName())
                            .build());
            return;
        }
        if (!takeSendToken(actor.characterId())) {
            refuse(actor, MailRefusal.SEND_INTERVAL);
            return;
        }
        switch (items.reserve(actor.characterHandle(), attached, settings.fee(attached.size()))) {
            case ReserveResult.Refused<Reservation> refused -> {
                refundSendToken(actor.characterId());
                refuse(actor, refused.reason());
            }
            case ReserveResult.Reserved<Reservation> reserved ->
                submit(
                        actor,
                        SendTransaction.builder()
                                .writer(writer)
                                .idempotencyKey(EntityIds.next())
                                .senderId(actor.characterId())
                                .senderName(actor.name())
                                .senderGm(state.gm())
                                .receiverName(command.receiverName())
                                .subject(command.subject())
                                .body(body)
                                .codAdena(codAdena)
                                .reservation(reserved.value())
                                .build());
        }
    }

    private void submit(MailActor actor, SendTransaction send) {
        final Reservation reservation = send.reservation();
        participants.beginTransaction(actor.characterId());
        submitter.submit(send, participants.loop(), new ValueOutcomeHandler<>() {
            @Override
            public void committed(SendTransaction.Sent result) {
                sent(actor, reservation);
                participants
                        .online(result.receiverId())
                        .ifPresent(receiver -> receiver.port().emit(MailArrived.FRESH));
            }

            // The receiver is not known without the result; it sees the mail at its next list.
            @Override
            public void replayed(List<ItemMovement> movements) {
                sent(actor, reservation);
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                items.release(reservation);
                refundSendToken(actor.characterId());
                actor.port()
                        .emit(MailRefused.builder()
                                .action(MailAction.SEND)
                                .reason(MailRefusal.of(reason))
                                .param(param)
                                .build());
                participants.endTransaction(actor.characterId());
            }

            @Override
            public void failed() {
                refused(MailRefusal.FAILED, null);
            }
        });
    }

    private void sent(MailActor actor, Reservation reservation) {
        items.applyOutgoing(reservation);
        actor.port().emit(MailSent.INSTANCE);
        actor.port().emit(MailDone.of(MailNotice.SENT));
        participants.endTransaction(actor.characterId());
    }

    private boolean mailable(MailActor actor, List<ItemCount> attached) {
        if (attached.isEmpty()) {
            return true;
        }
        final IntSet allowed = new IntOpenHashSet(items.mailable(actor.characterHandle()));
        final IntSet seen = new IntOpenHashSet(attached.size());
        for (ItemCount item : attached) {
            if ((item.count() < 1) || !allowed.contains(item.itemHandle()) || !seen.add(item.itemHandle())) {
                return false;
            }
        }
        return true;
    }

    private static boolean within(String text, int max) {
        return (text != null) && !text.isBlank() && (text.length() <= max);
    }

    private static void refuse(MailActor actor, MailRefusal reason) {
        actor.port().emit(MailRefused.of(MailAction.SEND, reason));
    }

    private boolean takeSendToken(UUID characterId) {
        if (sendInterval.isZero()) {
            return true;
        }
        return sendTokens.get(characterId, id -> newBucket()).tryConsume(1);
    }

    private void refundSendToken(UUID characterId) {
        if (sendInterval.isZero()) {
            return;
        }
        final Bucket bucket = sendTokens.getIfPresent(characterId);
        if (bucket != null) {
            bucket.addTokens(1);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(1).refillGreedy(1, sendInterval))
                .withCustomTimePrecision(time)
                .withSynchronizationStrategy(SynchronizationStrategy.NONE)
                .build();
    }

    private record GameTimeMeter(GameClock clock) implements TimeMeter {
        @Override
        public long currentTimeNanos() {
            return Duration.ofMillis(clock.gameMillis()).toNanos();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
