/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import lombok.Builder;

/**
 * @param name the other character the notice names; {@code null} when it names none
 * @param adena the adena the notice names, 0 when it names none
 */
@Builder(toBuilder = true)
public record MailDone(MailNotice notice, String name, long adena) implements ViewEvent {
    public static MailDone of(MailNotice notice) {
        return MailDone.builder().notice(notice).build();
    }

    public static MailDone of(MailNotice notice, String name) {
        return MailDone.builder().notice(notice).name(name).build();
    }
}
