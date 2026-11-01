/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;

/**
 * @param fresh a mail arrived just now; {@code false} for the notice of unread mail at world entry
 */
public record MailArrived(boolean fresh) implements ViewEvent {
    public static final MailArrived FRESH = new MailArrived(true);
    public static final MailArrived WAITING = new MailArrived(false);
}
