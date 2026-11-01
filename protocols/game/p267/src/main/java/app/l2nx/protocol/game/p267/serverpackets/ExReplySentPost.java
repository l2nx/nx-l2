/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * @param receiverName {@code null} reaches the client as an empty string
 * @param subject {@code null} reaches the client as an empty string
 * @param body {@code null} reaches the client as an empty string
 * @param codAdena the amount the client shows, 0 without cash on delivery
 */
@Builder(toBuilder = true)
public record ExReplySentPost(
        int mailId,
        boolean codPending,
        String receiverName,
        String subject,
        String body,
        List<ItemBlock> attachments,
        long codAdena,
        boolean unread)
        implements GameServerPacket {
    public ExReplySentPost {
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
    }

    public static ExReplySentPost read(ByteBuf body) {
        return Bodies.parse(
                "ExReplySentPost",
                body,
                in -> ExReplySentPost.builder()
                        .mailId(in.readIntLE())
                        .codPending(in.readIntLE() != 0)
                        .receiverName(Wire.readString(in))
                        .subject(Wire.readString(in))
                        .body(Wire.readString(in))
                        .attachments(ItemBlock.readAttachments(in))
                        .codAdena(in.readLongLE())
                        .unread(in.readIntLE() != 0)
                        .build());
    }

    @Override
    public void write(ByteBuf out) {
        GameServerExOpcode.EX_REPLY_SENT_POST.write(out);
        out.writeIntLE(mailId);
        out.writeIntLE(codPending ? 1 : 0);
        Wire.writeString(out, receiverName);
        Wire.writeString(out, subject);
        Wire.writeString(out, body);
        ItemBlock.writeAttachments(out, attachments);
        out.writeLongLE(codAdena);
        out.writeIntLE(unread ? 1 : 0);
    }
}
