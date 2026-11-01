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
import app.l2nx.engine.mail.repository.CancelTransaction;
import app.l2nx.engine.mail.repository.MailAttachmentRepository;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class CancelMailHandler implements ContextCommandHandler<MailActor, CancelMail> {
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
    public void handle(MailActor actor, CancelMail command) {
        if (!settings.enabled()) {
            actor.port().emit(MailRefused.of(MailAction.CANCEL, MailRefusal.DISABLED));
            return;
        }
        queries.run(
                () -> mails.findSent(command.mailId(), actor.characterId())
                        .map(row -> new Loaded(row, attachments.findByMail(row.id()))),
                participants.loop(),
                loaded -> loaded.ifPresentOrElse(
                        mail -> cancel(actor, mail),
                        () -> log.debug(
                                "{} asked to cancel mail {} that is not in its box", actor.name(), command.mailId())));
    }

    private void cancel(MailActor actor, Loaded mail) {
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
        switch (items.incomingTargets(actor.characterHandle(), HolderType.INVENTORY, mail.snapshots(), 0)) {
            case ReserveResult.Refused<IncomingTargets> refused -> refuse(actor, refused.reason());
            case ReserveResult.Reserved<IncomingTargets> reserved -> submit(actor, row, reserved.value());
        }
    }

    private void submit(MailActor actor, MailRow row, IncomingTargets sender) {
        final CancelTransaction cancel = CancelTransaction.builder()
                .writer(writer)
                .idempotencyKey(EntityIds.next())
                .mailId(row.id())
                .sender(sender)
                .build();
        participants.beginTransaction(actor.characterId());
        submitter.submit(cancel, participants.loop(), new ValueOutcomeHandler<>() {
            @Override
            public void committed(List<CommittedItemRow> rows) {
                cancelled(actor, row, sender, rows);
            }

            // The rows are read back by the ids the ledger names, so memory follows the commit all the same.
            @Override
            public void replayed(List<ItemMovement> movements) {
                final List<UUID> itemIds = sender.changedItems(movements);
                queries.runUntilAnswered(
                        () -> store.rows(itemIds), participants.loop(), rows -> cancelled(actor, row, sender, rows));
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                items.release(sender);
                actor.port()
                        .emit(MailRefused.builder()
                                .action(MailAction.CANCEL)
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

    private void cancelled(MailActor actor, MailRow row, IncomingTargets sender, List<CommittedItemRow> rows) {
        items.applyIncoming(sender, rows);
        actor.port().emit(MailStateChanged.of(MailBox.SENT, row.id(), MailChangeType.ATTACHMENTS_TAKEN));
        actor.port().emit(MailDone.of(MailNotice.CANCELLED));
        if (row.receiverId() != null) {
            participants.online(row.receiverId()).ifPresent(receiver -> {
                receiver.port().emit(MailStateChanged.of(MailBox.RECEIVED, row.id(), MailChangeType.DELETED));
                receiver.port().emit(MailDone.of(MailNotice.CANCELLED_BY_SENDER, actor.name()));
            });
        }
        participants.endTransaction(actor.characterId());
    }

    private static void refuse(MailActor actor, MailRefusal reason) {
        actor.port().emit(MailRefused.of(MailAction.CANCEL, reason));
    }

    private record Loaded(MailRow row, List<AttachmentSnapshot> snapshots) {}
}
