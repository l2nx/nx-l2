/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.command.CancelMail;
import app.l2nx.engine.mail.command.DeleteReceivedMail;
import app.l2nx.engine.mail.command.DeleteSentMail;
import app.l2nx.engine.mail.command.ListMailableItems;
import app.l2nx.engine.mail.command.ListReceivedMail;
import app.l2nx.engine.mail.command.ListSentMail;
import app.l2nx.engine.mail.command.OpenReceivedMail;
import app.l2nx.engine.mail.command.OpenSentMail;
import app.l2nx.engine.mail.command.ReceiveAttachments;
import app.l2nx.engine.mail.command.ReturnMail;
import app.l2nx.engine.mail.command.SendMail;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.clientpackets.RequestCancelPostAttachment;
import app.l2nx.protocol.game.p267.clientpackets.RequestDeleteReceivedPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestDeleteSentPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestPostAttachment;
import app.l2nx.protocol.game.p267.clientpackets.RequestPostItemList;
import app.l2nx.protocol.game.p267.clientpackets.RequestReceivedPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestReceivedPostList;
import app.l2nx.protocol.game.p267.clientpackets.RequestRejectPostAttachment;
import app.l2nx.protocol.game.p267.clientpackets.RequestSendPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestSentPost;
import app.l2nx.protocol.game.p267.clientpackets.RequestSentPostList;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The client names a mail by the session-local number the mappers gave it; a number this session never gave is a
 * malformed request and is dropped as a decode error.
 */
public final class MailDecoders {
    private MailDecoders() {}

    public static Object postItemList(ByteBuf frame, GameConnection connection) {
        RequestPostItemList.decode(frame);
        return new ListMailableItems();
    }

    /**
     * Texts are kept as sent for the handler to answer; a blank body is no body. The amount counts only when the client
     * marked the mail cash on delivery, so a payment request of no adena reaches the handler as 0.
     */
    public static Object sendPost(ByteBuf frame, GameConnection connection) {
        final RequestSendPost packet = RequestSendPost.decode(frame);
        final List<ItemCount> items = new ArrayList<>(packet.attachments().size());
        for (RequestSendPost.Attachment attachment : packet.attachments()) {
            items.add(new ItemCount(attachment.objectId(), attachment.count()));
        }
        final String body = packet.body();
        return SendMail.builder()
                .receiverName(packet.receiverName())
                .subject(packet.subject())
                .body(((body == null) || body.isBlank()) ? null : body)
                .items(items)
                .codAdena(packet.cashOnDelivery() ? packet.codAdena() : null)
                .build();
    }

    public static Object receivedPostList(ByteBuf frame, GameConnection connection) {
        RequestReceivedPostList.decode(frame);
        return new ListReceivedMail();
    }

    public static Object deleteReceivedPost(ByteBuf frame, GameConnection connection) {
        return new DeleteReceivedMail(
                mails(connection, RequestDeleteReceivedPost.decode(frame).mailIds()));
    }

    public static Object receivedPost(ByteBuf frame, GameConnection connection) {
        return new OpenReceivedMail(
                mail(connection, RequestReceivedPost.decode(frame).mailId()));
    }

    public static Object postAttachment(ByteBuf frame, GameConnection connection) {
        return new ReceiveAttachments(
                mail(connection, RequestPostAttachment.decode(frame).mailId()));
    }

    public static Object rejectPostAttachment(ByteBuf frame, GameConnection connection) {
        return new ReturnMail(
                mail(connection, RequestRejectPostAttachment.decode(frame).mailId()));
    }

    public static Object sentPostList(ByteBuf frame, GameConnection connection) {
        RequestSentPostList.decode(frame);
        return new ListSentMail();
    }

    public static Object deleteSentPost(ByteBuf frame, GameConnection connection) {
        return new DeleteSentMail(
                mails(connection, RequestDeleteSentPost.decode(frame).mailIds()));
    }

    public static Object sentPost(ByteBuf frame, GameConnection connection) {
        return new OpenSentMail(mail(connection, RequestSentPost.decode(frame).mailId()));
    }

    public static Object cancelPostAttachment(ByteBuf frame, GameConnection connection) {
        return new CancelMail(
                mail(connection, RequestCancelPostAttachment.decode(frame).mailId()));
    }

    private static List<UUID> mails(GameConnection connection, List<Integer> numbers) {
        final P267MailNumbers table = P267MailNumbers.of(connection);
        final List<UUID> mails = new ArrayList<>(numbers.size());
        for (int number : numbers) {
            mails.add(resolve(table, number));
        }
        return mails;
    }

    private static UUID mail(GameConnection connection, int number) {
        return resolve(P267MailNumbers.of(connection), number);
    }

    private static UUID resolve(P267MailNumbers table, int number) {
        final UUID mail = table.mail(number);
        if (mail == null) {
            throw new PacketDecodeException("Unknown mail number " + number);
        }
        return mail;
    }
}
