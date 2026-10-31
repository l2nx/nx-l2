/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.chat.ChatType;
import lombok.Builder;

/** @param recipientName set only on the whisper copy echoed to its sender, naming the addressee; else {@code null} */
@Builder(toBuilder = true)
public record ChatMessage(int senderHandle, ChatType type, String senderName, String recipientName, String text)
        implements ViewEvent {
    public ChatMessage echoTo(String recipientName) {
        return toBuilder().recipientName(recipientName).build();
    }
}
