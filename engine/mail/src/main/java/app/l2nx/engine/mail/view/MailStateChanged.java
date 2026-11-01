/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder(toBuilder = true)
public record MailStateChanged(MailBox box, List<MailChange> changes) implements ViewEvent {
    public MailStateChanged {
        changes = List.copyOf(changes);
    }

    public static MailStateChanged of(MailBox box, UUID mailId, MailChangeType type) {
        return new MailStateChanged(box, List.of(new MailChange(mailId, type)));
    }
}
