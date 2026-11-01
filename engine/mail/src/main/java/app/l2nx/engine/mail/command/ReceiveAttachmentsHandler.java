/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueOutcomeHandler;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailItemStore;
import app.l2nx.engine.mail.MailItems;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailRules;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.ReserveResult;
import app.l2nx.engine.mail.repository.MailAttachmentRepository;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.repository.ReceiveTransaction;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import app.l2nx.engine.mail.view.ReceivedMailList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The receiver takes the attachments and pays the cash on delivery. A sender in the world has its adena row reserved
 * too; an offline one is paid by delta. The cash on delivery is not charged when the sender was deleted.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ReceiveAttachmentsHandler implements ContextCommandHandler<MailActor, ReceiveAttachments> {
    private final MailSettings settings;
    private final MailParticipants participants;
    private final MailItems items;
    private final BackgroundQueries queries;
    private final MailRepository mails;
    private final MailAttachmentRepository attachments;
    private final MailItemStore store;
    private final ValueTransactionSubmitter submitter;
    private final MailWriter writer;

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, ReceiveAttachments command) {
        if (!settings.enabled()) {
            actor.port().emit(MailRefused.of(MailAction.RECEIVE, MailRefusal.DISABLED));
            return;
        }
        queries.run(
                () -> mails.findReceived(command.mailId(), actor.characterId())
                        .map(row -> new Loaded(row, attachments.findByMail(row.id()))),
                participants.loop(),
                loaded -> loaded.ifPresentOrElse(
                        mail -> receive(actor, mail),
                        () -> log.debug(
                                "{} asked for the attachments of mail {} that is not in its box",
                                actor.name(),
                                command.mailId())));
    }

    private void receive(MailActor actor, Loaded mail) {
        final MailRow row = mail.row();
        final MailRefusal ruled = MailRules.refusal(settings, participants.state(actor.characterHandle()), true, true);
        if (ruled != null) {
            refuse(actor, ruled);
            return;
        }
        if (!row.attached()) {
            refuse(actor, MailRefusal.ALREADY_RECEIVED);
            return;
        }
        final boolean charge = (row.codAdena() != null) && (row.senderId() != null);
        final long cod = charge ? row.codAdena() : 0;
        final IncomingTargets receiver;
        switch (items.incomingTargets(actor.characterHandle(), HolderType.INVENTORY, mail.snapshots(), -cod)) {
            case ReserveResult.Refused<IncomingTargets> refused -> {
                refuse(actor, refused.reason());
                return;
            }
            case ReserveResult.Reserved<IncomingTargets> reserved -> receiver = reserved.value();
        }
        final Optional<MailActor> sender = charge ? participants.online(row.senderId()) : Optional.empty();
        IncomingTargets senderTargets = null;
        if (sender.isPresent()) {
            switch (items.incomingTargets(sender.get().characterHandle(), HolderType.INVENTORY, List.of(), cod)) {
                case ReserveResult.Refused<IncomingTargets> refused -> {
                    items.release(receiver);
                    refuse(
                            actor,
                            refused.reason() == MailRefusal.ADENA_LIMIT
                                    ? MailRefusal.SENDER_ADENA_LIMIT
                                    : MailRefusal.BUSY);
                    return;
                }
                case ReserveResult.Reserved<IncomingTargets> reserved -> senderTargets = reserved.value();
            }
        }
        submit(actor, row, charge, receiver, sender, senderTargets);
    }

    private void submit(
            MailActor actor,
            MailRow row,
            boolean charge,
            IncomingTargets receiver,
            Optional<MailActor> sender,
            IncomingTargets senderTargets) {
        participants.beginTransaction(actor.characterId());
        if (charge) {
            participants.beginTransaction(row.senderId());
        }
        final ReceiveTransaction receive = ReceiveTransaction.builder()
                .writer(writer)
                .idempotencyKey(EntityIds.next())
                .mailId(row.id())
                .receiver(receiver)
                .senderId(row.senderId())
                .codAdena(row.codAdena())
                .charge(charge)
                .senderAdena(senderTargets == null ? null : senderTargets.adena())
                .build();
        final IncomingTargets senderSide = senderTargets;
        submitter.submit(receive, participants.loop(), new ValueOutcomeHandler<>() {
            @Override
            public void committed(ReceiveTransaction.Received result) {
                received(actor, row, charge, receiver, sender, senderSide, result.receiverRows(), result.senderRows());
            }

            // The rows are read back by the ids the ledger names, so memory follows the commit all the same.
            @Override
            public void replayed(List<ItemMovement> movements) {
                final List<UUID> receiverItems = receiver.changedItems(movements);
                final List<UUID> senderItems = senderSide == null ? List.of() : senderSide.changedItems(movements);
                queries.runUntilAnswered(
                        () -> List.of(store.rows(receiverItems), store.rows(senderItems)),
                        participants.loop(),
                        rows -> received(actor, row, charge, receiver, sender, senderSide, rows.get(0), rows.get(1)));
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                items.release(receiver);
                if (senderSide != null) {
                    items.release(senderSide);
                }
                actor.port()
                        .emit(MailRefused.builder()
                                .action(MailAction.RECEIVE)
                                .reason(MailRefusal.of(reason))
                                .param(param)
                                .build());
                end(actor, row, charge);
            }

            @Override
            public void failed() {
                refused(MailRefusal.FAILED, null);
            }
        });
    }

    private void received(
            MailActor actor,
            MailRow row,
            boolean charge,
            IncomingTargets receiver,
            Optional<MailActor> sender,
            IncomingTargets senderTargets,
            List<CommittedItemRow> receiverRows,
            List<CommittedItemRow> senderRows) {
        items.applyIncoming(receiver, receiverRows);
        if (senderTargets != null) {
            items.applyIncoming(senderTargets, senderRows);
        }
        actor.port().emit(MailStateChanged.of(MailBox.RECEIVED, row.id(), MailChangeType.ATTACHMENTS_TAKEN));
        actor.port().emit(MailDone.of(MailNotice.RECEIVED));
        if (charge) {
            // The client keeps the payment flag of a list it already shows and would ask for the payment again.
            queries.run(
                    () -> mails.listReceived(actor.characterId(), settings.mailboxLimit()).stream()
                            .map(MailRow::receivedSummary)
                            .toList(),
                    participants.loop(),
                    list -> actor.port().emit(new ReceivedMailList(list)));
        }
        // A paid mail tells its sender of the payment, any other of the taking (the legacy client flow).
        final Optional<MailActor> told =
                charge ? sender : Optional.ofNullable(row.senderId()).flatMap(participants::online);
        told.ifPresent(online -> {
            online.port().emit(MailStateChanged.of(MailBox.SENT, row.id(), MailChangeType.ATTACHMENTS_TAKEN));
            online.port()
                    .emit(
                            charge
                                    ? MailDone.builder()
                                            .notice(MailNotice.COD_PAID)
                                            .name(actor.name())
                                            .adena(row.codAdena())
                                            .build()
                                    : MailDone.of(MailNotice.ATTACHMENTS_TAKEN, actor.name()));
        });
        end(actor, row, charge);
    }

    private void end(MailActor actor, MailRow row, boolean charge) {
        participants.endTransaction(actor.characterId());
        if (charge) {
            participants.endTransaction(row.senderId());
        }
    }

    private static void refuse(MailActor actor, MailRefusal reason) {
        actor.port().emit(MailRefused.of(MailAction.RECEIVE, reason));
    }

    private record Loaded(MailRow row, List<AttachmentSnapshot> snapshots) {}
}
