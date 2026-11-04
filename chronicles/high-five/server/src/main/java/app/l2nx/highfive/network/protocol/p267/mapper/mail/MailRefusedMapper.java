/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.highfive.message.MessageParam;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.mapper.MessageShownMapper;
import app.l2nx.highfive.network.protocol.p267.mapper.OutboundMapper;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.List;

public final class MailRefusedMapper implements OutboundMapper<MailRefused> {
    @Override
    public Class<MailRefused> eventType() {
        return MailRefused.class;
    }

    @Override
    public GameServerPacket map(MailRefused event, GameClient viewer) {
        final List<MessageParam> params = (P267MailRefusals.namesReceiver(event.reason()) && (event.param() != null))
                ? List.of(new MessageParam.PlayerName(event.param()))
                : List.of();
        return MessageShownMapper.map(P267MailRefusals.id(event.action(), event.reason()), params, viewer);
    }
}
