/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.message.MessageParam;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.protocol.game.p267.serverpackets.GameServerPacket;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the NPC and skill a parameter names and fills the legacy system message builder with them, so its High Five
 * parameter rules (NPC id offset, server-side NPC, item and skill names, localisation) apply; then maps its wire
 * record. The localized text makes the bytes depend on the viewer's language.
 */
@Slf4j
public final class MessageShownMapper implements OutboundMapper<MessageShown> {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

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
                case MessageParam.NpcTemplate npc -> addNpcName(message, npc.templateId());
                case MessageParam.ItemTemplate item -> message.addItemName(item.templateId());
                case MessageParam.Skill skill -> addSkillName(message, skill);
            }
        }
        return message.toWire(viewer);
    }

    private static void addNpcName(SystemMessage message, int templateId) {
        final NpcTemplate template = NpcData.getInstance().getTemplate(templateId);
        if (template != null) {
            message.addNpcName(template);
            return;
        }
        if (RATE.tryAcquire("unknown-npc")) {
            log.warn(
                    "No NPC template {} for SystemMessageId {}, sent as a bare NPC id",
                    templateId,
                    message.getSystemMessageId());
        }
        message.addNpcName(templateId);
    }

    // SkillData logs an unknown skill itself; the client still names it from its own data.
    private static void addSkillName(SystemMessage message, MessageParam.Skill param) {
        final Skill skill = SkillData.getInstance().getSkill(param.id(), param.level());
        if (skill != null) {
            message.addSkillName(skill);
        } else {
            message.addSkillName(param.id(), param.level());
        }
    }
}
