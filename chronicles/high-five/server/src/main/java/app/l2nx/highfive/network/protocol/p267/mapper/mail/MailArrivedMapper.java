/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.mapper.OutboundMapper;
import app.l2nx.protocol.game.p267.serverpackets.ExNoticePostArrived;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;

public final class MailArrivedMapper implements OutboundMapper<MailArrived> {
    private static final ExNoticePostArrived FRESH = new ExNoticePostArrived(true);
    private static final ExNoticePostArrived WAITING = new ExNoticePostArrived(false);

    @Override
    public Class<MailArrived> eventType() {
        return MailArrived.class;
    }

    @Override
    public GameServerPacket map(MailArrived event, GameClient viewer) {
        return event.fresh() ? FRESH : WAITING;
    }
}
