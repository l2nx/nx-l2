/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.system;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueOutcomeHandler;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.core.persistence.value.ValueTransactionType;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailItemStore;
import app.l2nx.engine.mail.MailItems;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.ReserveResult;
import app.l2nx.engine.mail.repository.ExpireTransaction;
import app.l2nx.engine.mail.repository.MailAttachmentRepository;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailStateChanged;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Expires due mail every {@code expiry-poll}; attachments go to their owner's warehouse, destroyed when the owner is
 * gone. Runs whether the postbox is enabled or not, so no mail keeps its items forever.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailExpiry implements SmartLifecycle {
    static final int BATCH = 200;

    private final MailSettings settings;
    private final TaskScheduler scheduler;
    private final GameClock clock;
    private final MailRepository mails;
    private final MailAttachmentRepository attachments;
    private final MailParticipants participants;
    private final MailItems items;
    private final MailItemStore store;
    private final BackgroundQueries queries;
    private final ValueTransactionSubmitter submitter;
    private final MailWriter writer;
    // Mails with an expiry in flight, touched on the loop only: a later poll that selects one again skips it.
    private final Set<UUID> expiring = new HashSet<>();
    private volatile ScheduledFuture<?> task;

    @Override
    public void start() {
        task = scheduler.scheduleWithFixedDelay(this::poll, settings.expiryPoll());
    }

    @Override
    public void stop() {
        final ScheduledFuture<?> scheduled = task;
        if (scheduled != null) {
            scheduled.cancel(false);
            task = null;
        }
    }

    @Override
    public boolean isRunning() {
        return task != null;
    }

    /** One batch of each kind; runs on the scheduler, never on a loop. */
    public void poll() {
        try {
            final Instant now = clock.wallClock().instant();
            final List<MailRow> hidden = mails.expireWithoutAttachments(now, BATCH);
            final List<MailRow> due = mails.findDueAttached(now, BATCH);
            if (hidden.isEmpty() && due.isEmpty()) {
                return;
            }
            final Map<UUID, List<AttachmentSnapshot>> snapshots =
                    attachments.findByMails(due.stream().map(MailRow::id).toList());
            participants.loop().execute(() -> {
                hidden.forEach(this::notifyHidden);
                due.forEach(mail -> expire(mail, snapshots.getOrDefault(mail.id(), List.of())));
            });
        } catch (RuntimeException e) {
            log.error("Mail expiry poll failed", e);
        }
    }

    // On the loop: the owner is held in flight until the outcome, so its world entry cannot load the rows halfway.
    private void expire(MailRow mail, List<AttachmentSnapshot> snapshots) {
        final UUID owner = mail.expiryOwner();
        if ((owner != null) && participants.loading(owner)) {
            return;
        }
        if (!expiring.add(mail.id())) {
            return;
        }
        if (owner != null) {
            participants.beginTransaction(owner);
        }
        try {
            start(mail, owner, snapshots);
        } catch (RuntimeException e) {
            // Nothing was submitted, so nothing is held: the mail stays due and the next poll tries it again.
            log.error("Expiry of mail {} not started", mail.id(), e);
            end(mail.id(), owner);
        }
    }

    private void start(MailRow mail, UUID owner, List<AttachmentSnapshot> snapshots) {
        final Optional<MailActor> online = owner == null ? Optional.empty() : participants.online(owner);
        IncomingTargets reserved = null;
        if (online.isPresent()) {
            switch (items.incomingTargets(online.get().characterHandle(), HolderType.WAREHOUSE, snapshots, 0)) {
                case ReserveResult.Refused<IncomingTargets> refused -> {
                    // A stack another transaction holds: the mail stays due, and a later poll expires it.
                    end(mail.id(), owner);
                    return;
                }
                case ReserveResult.Reserved<IncomingTargets> targets -> reserved = targets.value();
            }
        }
        final IncomingTargets warehouse = reserved;
        try {
            submit(mail, owner, online, warehouse);
        } catch (RuntimeException e) {
            if (warehouse != null) {
                items.release(warehouse);
            }
            throw e;
        }
    }

    private void submit(MailRow mail, UUID owner, Optional<MailActor> online, IncomingTargets warehouse) {
        // A key per mail: a mail selected again after a lost outcome replays instead of expiring twice.
        final ExpireTransaction expire = ExpireTransaction.builder()
                .writer(writer)
                .idempotencyKey(UuidCreator.getNameBasedSha1(mail.id(), ValueTransactionType.MAIL_EXPIRE.name()))
                .mailId(mail.id())
                .ownerId(owner)
                .warehouse(warehouse)
                .build();
        submitter.submit(expire, participants.loop(), new ValueOutcomeHandler<>() {
            @Override
            public void committed(ExpireTransaction.Expired result) {
                expired(mail, owner, online, warehouse, result.rows());
            }

            @Override
            public void replayed(List<ItemMovement> movements) {
                if (warehouse == null) {
                    expired(mail, owner, online, null, List.of());
                    return;
                }
                final List<UUID> itemIds = warehouse.changedItems(movements);
                queries.runUntilAnswered(
                        () -> store.rows(itemIds),
                        participants.loop(),
                        rows -> expired(mail, owner, online, warehouse, rows));
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                if (warehouse != null) {
                    items.release(warehouse);
                }
                if (reason != MailRefusal.ALREADY_RECEIVED) {
                    log.warn("Expiry of mail {} refused: {}", mail.id(), reason);
                }
                end(mail.id(), owner);
            }

            @Override
            public void failed() {
                refused(MailRefusal.FAILED, null);
            }
        });
    }

    /** @param warehouse the online owner's reserved warehouse; {@code null} when the owner is not online */
    private void expired(
            MailRow mail,
            UUID owner,
            Optional<MailActor> online,
            IncomingTargets warehouse,
            List<CommittedItemRow> rows) {
        if (warehouse != null) {
            items.applyIncoming(warehouse, rows);
        }
        notifyHidden(mail);
        // The attachments went back to the one they came from, not on to the receiver of a system mail.
        if ((owner != null) && owner.equals(mail.originatorId())) {
            online.ifPresent(actor -> actor.port().emit(MailDone.of(MailNotice.EXPIRED_RETURNED)));
        }
        end(mail.id(), owner);
    }

    private void end(UUID mailId, UUID owner) {
        expiring.remove(mailId);
        if (owner != null) {
            participants.endTransaction(owner);
        }
    }

    private void notifyHidden(MailRow mail) {
        notify(mail.receiverId(), MailBox.RECEIVED, mail.id());
        notify(mail.senderId(), MailBox.SENT, mail.id());
    }

    private void notify(UUID characterId, MailBox box, UUID mailId) {
        if (characterId == null) {
            return;
        }
        participants
                .online(characterId)
                .ifPresent(actor -> actor.port().emit(MailStateChanged.of(box, mailId, MailChangeType.DELETED)));
    }
}
