/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.session.command.ContextCommandHandler;
import app.l2nx.engine.core.session.command.ContextResolver;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailItems;
import app.l2nx.engine.mail.MailParticipants;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailRules;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailableItems;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ListMailableItemsHandler implements ContextCommandHandler<MailActor, ListMailableItems> {
    private final MailSettings settings;
    private final MailParticipants participants;
    private final MailItems items;

    @Override
    public ContextResolver<MailActor> context() {
        return participants.context();
    }

    @Override
    public void handle(MailActor actor, ListMailableItems command) {
        final MailRefusal refusal =
                MailRules.refusal(settings, participants.state(actor.characterHandle()), true, false);
        if (refusal != null) {
            actor.port().emit(MailRefused.of(MailAction.SEND, refusal));
            return;
        }
        actor.port().emit(new MailableItems(items.mailable(actor.characterHandle())));
    }
}
