/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChange;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Hides every mail whose attachments are no longer in it; one refusal stands for the rest. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class DeleteReceivedMailHandler implements ContextCommandHandler<MailActor, DeleteReceivedMail> {
    private final MailSettings settings;
    private final MailParticipants participants;
    private final BackgroundQueries queries;
    private final MailRepository mails;
    private final GameClock clock;

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, DeleteReceivedMail command) {
        if (!settings.enabled()) {
            actor.port().emit(MailRefused.of(MailAction.DELETE, MailRefusal.DISABLED));
            return;
        }
        final List<UUID> mailIds = command.mailIds();
        if (mailIds.isEmpty()) {
            return;
        }
        queries.run(
                () -> new Deleted(
                        mails.hideReceived(
                                mailIds, actor.characterId(), clock.wallClock().instant()),
                        mails.countAttachedReceived(mailIds, actor.characterId())),
                participants.loop(),
                deleted -> {
                    if (!deleted.hidden().isEmpty()) {
                        actor.port()
                                .emit(new MailStateChanged(
                                        MailBox.RECEIVED,
                                        deleted.hidden().stream()
                                                .map(id -> new MailChange(id, MailChangeType.DELETED))
                                                .toList()));
                    }
                    if (deleted.attached() > 0) {
                        actor.port().emit(MailRefused.of(MailAction.DELETE, MailRefusal.HAS_ATTACHMENTS));
                    }
                });
    }

    private record Deleted(List<UUID> hidden, int attached) {}
}
