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
import app.l2nx.engine.mail.repository.MailAttachmentRepository;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailStateChanged;
import app.l2nx.engine.mail.view.MailView;
import app.l2nx.engine.mail.view.ReceivedMailOpened;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Opening an unread mail marks it read, and the view already shows it read. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class OpenReceivedMailHandler implements ContextCommandHandler<MailActor, OpenReceivedMail> {
    private final MailSettings settings;
    private final MailParticipants participants;
    private final BackgroundQueries queries;
    private final MailRepository mails;
    private final MailAttachmentRepository attachments;
    private final GameClock clock;

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, OpenReceivedMail command) {
        if (!settings.enabled()) {
            actor.port().emit(MailRefused.of(MailAction.READ, MailRefusal.DISABLED));
            return;
        }
        queries.run(
                () -> open(command.mailId(), actor.characterId()),
                participants.loop(),
                opened -> opened.ifPresent(mail -> {
                    actor.port().emit(new ReceivedMailOpened(mail.view()));
                    if (mail.firstRead()) {
                        actor.port()
                                .emit(MailStateChanged.of(
                                        MailBox.RECEIVED, mail.view().mailId(), MailChangeType.READ));
                    }
                }));
    }

    private Optional<Opened> open(UUID mailId, UUID receiverId) {
        final Optional<MailRow> found = mails.findReceived(mailId, receiverId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        final MailRow row = found.get();
        final boolean firstRead = (row.readAt() == null)
                && mails.markRead(row.id(), receiverId, clock.wallClock().instant());
        final MailView view = row.view(attachments.findByMail(row.id()), false);
        return Optional.of(new Opened(view, firstRead));
    }

    private record Opened(MailView view, boolean firstRead) {}
}
