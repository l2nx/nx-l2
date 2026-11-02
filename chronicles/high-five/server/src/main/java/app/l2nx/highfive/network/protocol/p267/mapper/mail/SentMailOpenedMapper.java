/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailView;
import app.l2nx.engine.mail.view.SentMailOpened;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.protocol.game.p267.serverpackets.ExReplySentPost;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class SentMailOpenedMapper implements OutboundMapper<SentMailOpened> {
    @Override
    public Class<SentMailOpened> eventType() {
        return SentMailOpened.class;
    }

    @Override
    public GameServerPacket map(SentMailOpened event, GameClient viewer) {
        final MailView mail = event.mail();
        final Long cod = mail.codAdenaPending();
        return ExReplySentPost.builder()
                .mailId(P267MailNumbers.of(viewer).number(MailBox.SENT, mail.mailId()))
                .codPending(cod != null)
                .receiverName(mail.receiverName())
                .subject(mail.subject())
                .body(mail.body())
                .attachments(P267MailWire.attachments(mail.snapshots(), ClockBridge.wallMillis()))
                .codAdena((cod == null) ? 0 : cod)
                .unread(mail.unread())
                .build();
    }

    @Override
    public int sizeHint() {
        return 1_536;
    }
}
