/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailSummary;
import app.l2nx.engine.mail.view.ReceivedMailList;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.protocol.game.p267.serverpackets.ExShowReceivedPostList;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.ArrayList;
import java.util.List;

public final class ReceivedMailListMapper implements OutboundMapper<ReceivedMailList> {
    @Override
    public Class<ReceivedMailList> eventType() {
        return ReceivedMailList.class;
    }

    @Override
    public GameServerPacket map(ReceivedMailList event, GameClient viewer) {
        final List<MailSummary> summaries = event.mails();
        final int[] numbers = P267MailNumbers.of(viewer)
                .show(
                        MailBox.RECEIVED,
                        summaries.stream().map(MailSummary::mailId).toList());
        final List<ExShowReceivedPostList.Mail> mails = new ArrayList<>(summaries.size());
        for (int i = 0; i < numbers.length; i++) {
            final MailSummary mail = summaries.get(i);
            mails.add(ExShowReceivedPostList.Mail.builder()
                    .mailId(numbers[i])
                    .subject(P267MailWire.listSubject(mail.subject()))
                    .senderName(mail.counterpartName())
                    .codPending(mail.codPending())
                    .expiresAt(P267MailWire.unixSeconds(mail.expiresAt()))
                    .unread(mail.unread())
                    .returnable(mail.returnable())
                    .withItems(mail.attachmentsTakeable())
                    .returned(mail.returned())
                    .sentBySystem(P267MailWire.sender(mail.type()))
                    .build());
        }
        return new ExShowReceivedPostList(P267MailWire.unixSeconds(ClockBridge.wallMillis()), mails);
    }

    @Override
    public int sizeHint() {
        return 4_096;
    }
}
