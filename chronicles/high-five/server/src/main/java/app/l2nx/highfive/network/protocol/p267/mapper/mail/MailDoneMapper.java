/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.highfive.message.MessageParam;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.List;

public final class MailDoneMapper implements OutboundMapper<MailDone> {
    @Override
    public Class<MailDone> eventType() {
        return MailDone.class;
    }

    @Override
    public GameServerPacket map(MailDone event, GameClient viewer) {
        return switch (event.notice()) {
            case SENT -> MessageShownMapper.map(SystemMessageId.MAIL_SUCCESSFULLY_SENT, List.of(), viewer);
            case RECEIVED -> MessageShownMapper.map(SystemMessageId.MAIL_SUCCESSFULLY_RECEIVED, List.of(), viewer);
            case RETURNED -> MessageShownMapper.map(SystemMessageId.MAIL_SUCCESSFULLY_RETURNED, List.of(), viewer);
            case CANCELLED -> MessageShownMapper.map(SystemMessageId.MAIL_SUCCESSFULLY_CANCELLED, List.of(), viewer);
            // "$s2 has made a payment of $s1 Adena": the amount comes first.
            case COD_PAID ->
                MessageShownMapper.map(
                        SystemMessageId.S2_HAS_MADE_A_PAYMENT_OF_S1_ADENA_PER_YOUR_PAYMENT_REQUEST_MAIL,
                        List.of(new MessageParam.Numeric(event.adena()), name(event)),
                        viewer);
            case ATTACHMENTS_TAKEN ->
                MessageShownMapper.map(
                        SystemMessageId.S1_ACQUIRED_THE_ATTACHED_ITEM_TO_YOUR_MAIL, List.of(name(event)), viewer);
            case RETURNED_TO_YOU ->
                MessageShownMapper.map(SystemMessageId.S1_RETURNED_THE_MAIL, List.of(name(event)), viewer);
            case CANCELLED_BY_SENDER ->
                MessageShownMapper.map(SystemMessageId.S1_CANCELED_THE_SENT_MAIL, List.of(name(event)), viewer);
            case EXPIRED_RETURNED ->
                MessageShownMapper.map(
                        SystemMessageId.THE_MAIL_WAS_RETURNED_DUE_TO_THE_EXCEEDED_WAITING_TIME, List.of(), viewer);
        };
    }

    private static MessageParam name(MailDone event) {
        if (event.name() == null) {
            throw new IllegalArgumentException(event.notice() + " names a character: " + event);
        }
        return new MessageParam.Text(event.name());
    }
}
