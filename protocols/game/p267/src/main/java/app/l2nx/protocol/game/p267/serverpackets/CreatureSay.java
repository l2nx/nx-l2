/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import app.l2nx.protocol.game.p267.ChatChannel;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * Chat, NPC speech and announcements.
 *
 * @param senderId the speaker's object id, 0 for none
 * @param senderName written when set; otherwise {@code senderNameId} names the speaker
 * @param messageId an NPC string id, {@link #NO_MESSAGE} for free text
 * @param text written when set; otherwise each of {@code parameters}, the NPC string's arguments
 * @param parameters a {@code null} argument reaches the client as an empty string
 */
@Builder(toBuilder = true)
public record CreatureSay(
        int senderId,
        ChatChannel channel,
        String senderName,
        int senderNameId,
        int messageId,
        String text,
        List<String> parameters)
        implements GameServerPacket {
    public static final int NO_MESSAGE = -1;

    public CreatureSay {
        Objects.requireNonNull(channel, "channel");
        parameters = (parameters == null) ? List.of() : Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    /**
     * Reads the variant the client parses on its own: a named speaker, free text for {@link #NO_MESSAGE}, else the
     * NPC string's arguments up to the end of the packet. A speaker sent as a name id reads as a garbled name.
     */
    public static CreatureSay read(ByteBuf body) {
        return Bodies.parse("CreatureSay", body, in -> {
            final int senderId = in.readIntLE();
            final int code = in.readIntLE();
            final ChatChannel channel = ChatChannel.of(code);
            if (channel == null) {
                throw new PacketDecodeException("Unknown CreatureSay channel " + code);
            }
            final String senderName = Wire.readString(in);
            final int messageId = in.readIntLE();
            final CreatureSayBuilder packet = CreatureSay.builder()
                    .senderId(senderId)
                    .channel(channel)
                    .senderName(senderName)
                    .messageId(messageId);
            if (messageId == NO_MESSAGE) {
                return packet.text(Wire.readString(in)).build();
            }
            final List<String> parameters = new ArrayList<>();
            while (in.isReadable()) {
                parameters.add(Wire.readString(in));
            }
            return packet.parameters(parameters).build();
        });
    }

    @Override
    public void write(ByteBuf out) {
        out.writeByte(GameServerOpcode.SAY2.opcode());
        out.writeIntLE(senderId);
        out.writeIntLE(channel.code());
        if (senderName != null) {
            Wire.writeString(out, senderName);
        } else {
            out.writeIntLE(senderNameId);
        }
        out.writeIntLE(messageId);
        if (text != null) {
            Wire.writeString(out, text);
        } else {
            for (String parameter : parameters) {
                Wire.writeString(out, parameter);
            }
        }
    }
}
