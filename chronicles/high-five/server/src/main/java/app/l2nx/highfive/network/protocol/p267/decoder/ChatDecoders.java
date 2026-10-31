/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.chat.command.SendChat;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.p267.P267ChatTypes;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.protocol.game.p267.clientpackets.Say2;
import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ChatDecoders {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private ChatDecoders() {}

    /**
     * An unknown channel code becomes a command without a type, which the handler answers by closing the connection;
     * the code itself exists only here, so it is logged here.
     */
    public static Object say2(ByteBuf frame, GameConnection connection) {
        final Say2 packet = Say2.decode(frame);
        final ChatType type = P267ChatTypes.type(packet.channel());
        if ((type == null) && RATE.tryAcquire("say2-unknown-channel")) {
            log.warn("Unknown chat channel code {} from {}", packet.channelCode(), connection.client());
        }
        final String target = packet.target();
        return SendChat.builder()
                .text(packet.text())
                .type(type)
                .target(((target == null) || target.isBlank()) ? null : target)
                .build();
    }
}
