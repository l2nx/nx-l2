/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import app.l2nx.network.codec.Wire;
import app.l2nx.protocol.game.p267.Bodies;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * @param serverTime the server's clock in Unix seconds: the client counts each mail's time left from it, not from its
 *     own clock
 */
@Builder(toBuilder = true)
public record ExShowReceivedPostList(int serverTime, List<Mail> mails) implements GameServerPacket {
    private static final int MIN_MAIL_BYTES = 4 + 2 + 2 + (4 * 8);

    public ExShowReceivedPostList {
        mails = List.copyOf(Objects.requireNonNull(mails, "mails"));
    }

    /**
     * @param subject {@code null} reaches the client as an empty string
     * @param senderName {@code null} reaches the client as an empty string
     * @param expiresAt Unix seconds
     * @param returnable drives the client's Return button only
     * @param withItems the attachment icon
     */
    @Builder(toBuilder = true)
    public record Mail(
            int mailId,
            String subject,
            String senderName,
            boolean codPending,
            int expiresAt,
            boolean unread,
            boolean returnable,
            boolean withItems,
            boolean returned,
            PostSender sentBySystem) {
        public Mail {
            Objects.requireNonNull(sentBySystem, "sentBySystem");
        }
    }

    public static ExShowReceivedPostList read(ByteBuf body) {
        return Bodies.parse("ExShowReceivedPostList", body, in -> {
            final int serverTime = in.readIntLE();
            final int count = Bodies.count(in.readIntLE(), in, MIN_MAIL_BYTES);
            final List<Mail> mails = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                mails.add(readMail(in));
            }
            return new ExShowReceivedPostList(serverTime, mails);
        });
    }

    @Override
    public void write(ByteBuf out) {
        GameServerExOpcode.EX_SHOW_RECEIVED_POST_LIST.write(out);
        out.writeIntLE(serverTime);
        out.writeIntLE(mails.size());
        for (Mail mail : mails) {
            out.writeIntLE(mail.mailId());
            Wire.writeString(out, mail.subject());
            Wire.writeString(out, mail.senderName());
            out.writeIntLE(mail.codPending() ? 1 : 0);
            out.writeIntLE(mail.expiresAt());
            out.writeIntLE(mail.unread() ? 1 : 0);
            out.writeIntLE(mail.returnable() ? 1 : 0);
            out.writeIntLE(mail.withItems() ? 1 : 0);
            out.writeIntLE(mail.returned() ? 1 : 0);
            out.writeIntLE(mail.sentBySystem().code());
            out.writeIntLE(0); // unknown, the client reads it
        }
    }

    private static Mail readMail(ByteBuf in) {
        final Mail.MailBuilder mail = Mail.builder()
                .mailId(in.readIntLE())
                .subject(Wire.readString(in))
                .senderName(Wire.readString(in))
                .codPending(in.readIntLE() != 0)
                .expiresAt(in.readIntLE())
                .unread(in.readIntLE() != 0)
                .returnable(in.readIntLE() != 0)
                .withItems(in.readIntLE() != 0)
                .returned(in.readIntLE() != 0);
        final PostSender sender = PostSender.read(in);
        in.skipBytes(4);
        return mail.sentBySystem(sender).build();
    }
}
