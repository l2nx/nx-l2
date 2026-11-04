/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.view.MailSent;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.mapper.OutboundMapper;
import app.l2nx.protocol.game.p267.serverpackets.ExNoticePostSent;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class MailSentMapper implements OutboundMapper<MailSent> {
    private static final ExNoticePostSent SENT = new ExNoticePostSent(true);

    @Override
    public Class<MailSent> eventType() {
        return MailSent.class;
    }

    @Override
    public GameServerPacket map(MailSent event, GameClient viewer) {
        return SENT;
    }
}
