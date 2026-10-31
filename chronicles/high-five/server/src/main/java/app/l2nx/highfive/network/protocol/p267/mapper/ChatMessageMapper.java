/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.chat.view.ChatMessage;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267ChatTypes;
import app.l2nx.protocol.game.p267.serverpackets.CreatureSay;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.List;

public final class ChatMessageMapper implements OutboundMapper<ChatMessage> {
    private static final String ECHO_PREFIX = "->";

    @Override
    public Class<ChatMessage> eventType() {
        return ChatMessage.class;
    }

    @Override
    public GameServerPacket map(ChatMessage event, GameClient viewer) {
        return CreatureSay.builder()
                .senderId(event.senderHandle())
                .channel(P267ChatTypes.channel(event.type()))
                .senderName(speaker(event))
                .messageId(CreatureSay.NO_MESSAGE)
                .text(event.text())
                .parameters(List.of())
                .build();
    }

    /** The name the High Five client shows before the line: a whisper echo shows its addressee after an arrow. */
    public static String speaker(ChatMessage message) {
        final String recipient = message.recipientName();
        return (recipient == null) ? message.senderName() : (ECHO_PREFIX + recipient);
    }

    @Override
    public int sizeHint() {
        return 256;
    }
}
