/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.chat;

import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.entity.actor.Player;
import java.util.Collection;
import org.springframework.stereotype.Component;

/**
 * Delivers player chat. A snooped receiver forwards what it receives to its snoopers.
 */
@Component
public class ChatService {
    private static volatile ChatService instance;

    ChatService() {
        instance = this;
    }

    // TODO (#41): callers receive the bean through their constructor.
    public static ChatService getInstance() {
        final ChatService bound = instance;
        if (bound == null) {
            throw new IllegalStateException("ChatService is not bound: the Spring context has not created it");
        }
        return bound;
    }

    public static ChatMessage message(Player sender, ChatType type, String text) {
        return message(sender, sender.getName(), type, text);
    }

    /** @param senderName the name receivers see when it is not the sender's own: general chat shows the visible one */
    public static ChatMessage message(Player sender, String senderName, ChatType type, String text) {
        return ChatMessage.builder()
                .senderHandle(sender.getObjectId())
                .type(type)
                .senderName(senderName)
                .text(text)
                .build();
    }

    public void deliver(Player receiver, ChatMessage message) {
        receiver.getPort().emit(message);
        receiver.broadcastSnoop(message);
    }

    public void deliverAll(Collection<Player> receivers, ChatMessage message) {
        for (Player receiver : receivers) {
            deliver(receiver, message);
        }
    }
}
