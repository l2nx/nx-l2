/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * Names, texts, the number of attachments and the amount are bounded only by the frame: the server answers each
 * with the client's own message, which a decode error would never reach.
 *
 * @param cashOnDelivery the client's payment request box; {@code codAdena} is sent whether it is ticked or not
 * @param body {@code null} is sent as an empty string
 */
@Builder(toBuilder = true)
public record RequestSendPost(
        String receiverName,
        boolean cashOnDelivery,
        String subject,
        String body,
        List<Attachment> attachments,
        long codAdena) {
    static final int ATTACHMENT_BYTES = Integer.BYTES + Long.BYTES;

    public RequestSendPost {
        Objects.requireNonNull(receiverName, "receiverName");
        Objects.requireNonNull(subject, "subject");
        attachments = List.copyOf(Objects.requireNonNull(attachments, "attachments"));
    }

    @Builder(toBuilder = true)
    public record Attachment(int objectId, long count) {}

    public static RequestSendPost decode(ByteBuf body) {
        return Bodies.parse("RequestSendPost", body, in -> {
            final RequestSendPostBuilder packet = RequestSendPost.builder()
                    .receiverName(Wire.readString(in))
                    .cashOnDelivery(in.readIntLE() != 0)
                    .subject(Wire.readString(in))
                    .body(Wire.readString(in));
            final int count = Bodies.count(in.readIntLE(), in, ATTACHMENT_BYTES);
            final List<Attachment> attachments = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                attachments.add(new Attachment(in.readIntLE(), in.readLongLE()));
            }
            return packet.attachments(attachments).codAdena(in.readLongLE()).build();
        });
    }

    public void encode(ByteBuf out) {
        GameClientExOpcode.REQUEST_SEND_POST.write(out);
        Wire.writeString(out, receiverName);
        out.writeIntLE(cashOnDelivery ? 1 : 0);
        Wire.writeString(out, subject);
        Wire.writeString(out, body);
        out.writeIntLE(attachments.size());
        for (Attachment attachment : attachments) {
            out.writeIntLE(attachment.objectId());
            out.writeLongLE(attachment.count());
        }
        out.writeLongLE(codAdena);
    }
}
