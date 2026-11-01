/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailRefusal;
import lombok.Builder;

/**
 * @param param the value the client message names, such as the receiver; {@code null} when it names none
 */
@Builder(toBuilder = true)
public record MailRefused(MailAction action, MailRefusal reason, String param) implements ViewEvent {
    public static MailRefused of(MailAction action, MailRefusal reason) {
        return MailRefused.builder().action(action).reason(reason).build();
    }
}
