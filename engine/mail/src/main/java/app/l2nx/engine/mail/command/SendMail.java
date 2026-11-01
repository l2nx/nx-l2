/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.command;

import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.mail.ItemCount;
import java.util.List;
import lombok.Builder;

/**
 * @param body {@code null} when the mail has none
 * @param codAdena {@code null} without cash on delivery
 */
@Builder(toBuilder = true)
public record SendMail(String receiverName, String subject, String body, List<ItemCount> items, Long codAdena)
        implements Command {
    public SendMail {
        items = List.copyOf(items);
    }
}
