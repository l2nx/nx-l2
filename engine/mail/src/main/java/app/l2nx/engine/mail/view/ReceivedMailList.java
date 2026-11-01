/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import java.util.List;

public record ReceivedMailList(List<MailSummary> mails) implements ViewEvent {
    public ReceivedMailList {
        mails = List.copyOf(mails);
    }
}
