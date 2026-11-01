/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailView;
import app.l2nx.engine.mail.view.ReceivedMailOpened;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.protocol.game.p267.serverpackets.ExReplyReceivedPost;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class ReceivedMailOpenedMapper implements OutboundMapper<ReceivedMailOpened> {
    @Override
    public Class<ReceivedMailOpened> eventType() {
        return ReceivedMailOpened.class;
    }

    @Override
    public GameServerPacket map(ReceivedMailOpened event, GameClient viewer) {
        final MailView mail = event.mail();
        final Long cod = mail.codAdenaPending();
        return ExReplyReceivedPost.builder()
                .mailId(P267MailNumbers.of(viewer).number(MailBox.RECEIVED, mail.mailId()))
                .codPending(cod != null)
                .returned(mail.returned())
                .senderName(mail.senderName())
                .subject(mail.subject())
                .body(mail.body())
                .attachments(P267MailWire.attachments(mail.snapshots(), ClockBridge.wallMillis()))
                .codAdena((cod == null) ? 0 : cod)
                .returnable(mail.returnable())
                .sentBySystem(P267MailWire.sender(mail.type()))
                .build();
    }

    @Override
    public int sizeHint() {
        return 1_536;
    }
}
