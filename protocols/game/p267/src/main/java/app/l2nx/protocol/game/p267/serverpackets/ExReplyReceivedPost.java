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
 * @param senderName {@code null} reaches the client as an empty string
 * @param subject {@code null} reaches the client as an empty string
 * @param body {@code null} reaches the client as an empty string
 * @param codAdena the amount the client shows, 0 without cash on delivery
 * @param returnable drives the client's Return button only
 */
@Builder(toBuilder = true)
public record ExReplyReceivedPost(
        int mailId,
        boolean codPending,
        boolean returned,
        String senderName,
        String subject,
        String body,
        List<ItemBlock> attachments,
        long codAdena,
        boolean returnable,
        PostSender sentBySystem)
        implements GameServerPacket {
    public ExReplyReceivedPost {
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
        Objects.requireNonNull(sentBySystem, "sentBySystem");
    }

    public static ExReplyReceivedPost read(ByteBuf body) {
        return Bodies.parse(
                "ExReplyReceivedPost",
                body,
                in -> ExReplyReceivedPost.builder()
                        .mailId(in.readIntLE())
                        .codPending(in.readIntLE() != 0)
                        .returned(in.readIntLE() != 0)
                        .senderName(Wire.readString(in))
                        .subject(Wire.readString(in))
                        .body(Wire.readString(in))
                        .attachments(ItemBlock.readAttachments(in))
                        .codAdena(in.readLongLE())
                        .returnable(in.readIntLE() != 0)
                        .sentBySystem(PostSender.read(in))
                        .build());
    }

    @Override
    public void write(ByteBuf out) {
        GameServerExOpcode.EX_REPLY_RECEIVED_POST.write(out);
        out.writeIntLE(mailId);
        out.writeIntLE(codPending ? 1 : 0);
        out.writeIntLE(returned ? 1 : 0);
        Wire.writeString(out, senderName);
        Wire.writeString(out, subject);
        Wire.writeString(out, body);
        ItemBlock.writeAttachments(out, attachments);
        out.writeLongLE(codAdena);
        out.writeIntLE(returnable ? 1 : 0);
        out.writeIntLE(sentBySystem.code());
    }
}
