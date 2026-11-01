/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.SentMailList;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ListSentMailHandler implements ContextCommandHandler<MailActor, ListSentMail> {
    private final MailSettings settings;
    private final MailParticipants participants;
    private final BackgroundQueries queries;
    private final MailRepository mails;

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, ListSentMail command) {
        if (!settings.enabled()) {
            actor.port().emit(MailRefused.of(MailAction.READ, MailRefusal.DISABLED));
            return;
        }
        queries.run(
                () -> mails.listSent(actor.characterId(), settings.mailboxLimit()).stream()
                        .map(MailRow::sentSummary)
                        .toList(),
                participants.loop(),
                list -> actor.port().emit(new SentMailList(list)));
    }
}
