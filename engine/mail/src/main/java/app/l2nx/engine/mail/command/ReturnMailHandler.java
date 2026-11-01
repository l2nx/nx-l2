/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueOutcomeHandler;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailRules;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.repository.ReturnTransaction;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The receiver sends the attachments back in a new mail to the sender. Only mail rows move, no character's items, so
 * nobody is reserved or held in flight.
 */
@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ReturnMailHandler implements ContextCommandHandler<MailActor, ReturnMail> {
    private final MailSettings settings;
    private final MailParticipants participants;
    private final BackgroundQueries queries;
    private final MailRepository mails;
    private final ValueTransactionSubmitter submitter;
    private final MailWriter writer;

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, ReturnMail command) {
        if (!settings.enabled()) {
            actor.port().emit(MailRefused.of(MailAction.RETURN, MailRefusal.DISABLED));
            return;
        }
        queries.run(
                () -> mails.findReceived(command.mailId(), actor.characterId()),
                participants.loop(),
                found -> found.ifPresentOrElse(
                        row -> returnMail(actor, row),
                        () -> log.debug(
                                "{} asked to return mail {} that is not in its box", actor.name(), command.mailId())));
    }

    private void returnMail(MailActor actor, MailRow row) {
        final MailRefusal ruled = MailRules.refusal(settings, participants.state(actor.characterHandle()), true, false);
        if (ruled != null) {
            refuse(actor, ruled);
            return;
        }
        if (!row.returnable()) {
            refuse(actor, MailRefusal.NOT_RETURNABLE);
            return;
        }
        final ReturnTransaction returnMail = ReturnTransaction.builder()
                .writer(writer)
                .idempotencyKey(EntityIds.next())
                .mailId(row.id())
                .receiverId(actor.characterId())
                .build();
        submitter.submit(returnMail, participants.loop(), new ValueOutcomeHandler<>() {
            @Override
            public void committed(ReturnTransaction.Returned result) {
                returned(actor, row);
            }

            @Override
            public void replayed(List<ItemMovement> movements) {
                returned(actor, row);
            }

            @Override
            public void refused(Enum<?> reason, String param) {
                actor.port()
                        .emit(MailRefused.builder()
                                .action(MailAction.RETURN)
                                .reason(MailRefusal.of(reason))
                                .param(param)
                                .build());
            }

            @Override
            public void failed() {
                refused(MailRefusal.FAILED, null);
            }
        });
    }

    private void returned(MailActor actor, MailRow row) {
        actor.port().emit(MailStateChanged.of(MailBox.RECEIVED, row.id(), MailChangeType.ATTACHMENTS_TAKEN));
        actor.port().emit(MailDone.of(MailNotice.RETURNED));
        participants.online(row.senderId()).ifPresent(sender -> {
            sender.port().emit(MailArrived.FRESH);
            sender.port().emit(MailDone.of(MailNotice.RETURNED_TO_YOU, actor.name()));
        });
    }

    private static void refuse(MailActor actor, MailRefusal reason) {
        actor.port().emit(MailRefused.of(MailAction.RETURN, reason));
    }
}
