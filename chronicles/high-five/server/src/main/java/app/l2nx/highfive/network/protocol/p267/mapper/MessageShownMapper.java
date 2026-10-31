/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.message.MessageParam;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.List;
import java.util.Objects;

/**
 * Fills the legacy system message builder, which owns the High Five parameter rules (NPC id offset, server-side item
 * and skill names, localisation), and maps its wire record; the localized text makes the bytes depend on the
 * viewer's language.
 */
public final class MessageShownMapper implements OutboundMapper<MessageShown> {
    // The language the legacy writer treats as the default: every viewer reading it shares one frame.
    private static final String DEFAULT_LANGUAGE = "en";

    @Override
    public Class<MessageShown> eventType() {
        return MessageShown.class;
    }

    @Override
    public GameServerPacket map(MessageShown event, GameClient viewer) {
        return map(P267Messages.id(event.key()), event.params(), viewer);
    }

    @Override
    public boolean viewerDependent() {
        return true;
    }

    @Override
    public Object variant(MessageShown event, GameClient viewer) {
        return Objects.requireNonNullElse(SystemMessage.language(viewer), DEFAULT_LANGUAGE);
    }

    static GameServerPacket map(SystemMessageId id, List<MessageParam> params, GameClient viewer) {
        final SystemMessage message = new SystemMessage(id);
        for (MessageParam param : params) {
            switch (param) {
                case MessageParam.Text text -> message.addString(text.value());
                case MessageParam.Numeric numeric -> message.addLong(numeric.value());
                case MessageParam.PlayerName name -> message.addPlayerName(name.name());
                case MessageParam.NpcTemplate npc -> message.addNpcName(npc.templateId());
                case MessageParam.ItemTemplate item -> message.addItemName(item.templateId());
                case MessageParam.Skill skill -> message.addSkillName(skill.id(), skill.level());
            }
        }
        return message.toWire(viewer);
    }
}
