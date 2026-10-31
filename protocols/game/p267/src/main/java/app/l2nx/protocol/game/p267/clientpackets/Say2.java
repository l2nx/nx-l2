/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import app.l2nx.protocol.game.p267.ChatChannel;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import lombok.Builder;

/**
 * Text and target are bounded only by the frame: the server's length caps depend on who speaks, and a whisper to any
 * name, however long, is answered by the server, so neither is a decode error.
 *
 * @param channelCode as sent: a code the client does not have is answered by the handler as legacy {@code Say2} did,
 *     not dropped as a decode error
 * @param target the whisper's addressee, {@code null} for every other channel
 */
@Builder(toBuilder = true)
public record Say2(String text, int channelCode, String target) {
    public Say2 {
        Objects.requireNonNull(text, "text");
    }

    public static Say2 decode(ByteBuf body) {
        return Bodies.parse("Say2", body, in -> {
            final String text = Wire.readString(in);
            final int channelCode = in.readIntLE();
            return Say2.builder()
                    .text(text)
                    .channelCode(channelCode)
                    .target((channelCode == ChatChannel.WHISPER.code()) ? Wire.readString(in) : null)
                    .build();
        });
    }

    public ChatChannel channel() {
        return ChatChannel.of(channelCode);
    }

    public void encode(ByteBuf out) {
        out.writeByte(GameClientOpcode.SAY2.opcode());
        Wire.writeString(out, text);
        out.writeIntLE(channelCode);
        if (channelCode == ChatChannel.WHISPER.code()) {
            Wire.writeString(out, target);
        }
    }
}
