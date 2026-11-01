/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcFirstTalk;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * This class manages all Guards in the world. It inherits all methods from Attackable and adds some more such as tracking PK and aggressive Monster.
 */
@SingleWriter
public class Guard extends Attackable {
    public Guard(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.Guard);
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        if (attacker.isMonster() && !attacker.isFakePlayer()) {
            return true;
        }

        if (FactionSystemConfig.FACTION_SYSTEM_ENABLED
                && FactionSystemConfig.FACTION_GUARDS_ENABLED
                && attacker.isPlayable()) {
            final Player player = attacker.asPlayer();
            if ((player.isGood() && getTemplate().isClan(FactionSystemConfig.FACTION_EVIL_TEAM_NAME))
                    || (player.isEvil() && getTemplate().isClan(FactionSystemConfig.FACTION_GOOD_TEAM_NAME))) {
                return true;
            }
        }

        return super.isAutoAttackable(attacker);
    }

    @Override
    public void addDamage(Creature attacker, int damage, Skill skill) {
        WorldPartition.checkOwner();
        super.addDamage(attacker, damage, skill);
        getAI().startFollow(attacker);
        addDamageHate(attacker, 0, 10);
        World.forEachVisibleObjectInRange(this, Guard.class, 500, guard -> {
            guard.getAI().startFollow(attacker);
            guard.addDamageHate(attacker, 0, 10);
        });
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        setRandomWalking(false);
        super.onSpawn();

        getAI().setIntentionActive();
    }

    @Override
    public String getHtmlPath(int npcId, int value) {
        WorldPartition.checkOwner();
        String pom = "";
        if (value == 0) {
            pom = Integer.toString(npcId);
        } else {
            pom = npcId + "-" + value;
        }

        return "data/html/guard/" + pom + ".htm";
    }

    @Override
    public void onAction(Player player, boolean interactValue) {
        WorldPartition.checkOwner();
        if (!canTarget(player)) {
            return;
        }

        boolean interact = interactValue;
        if (FactionSystemConfig.FACTION_SYSTEM_ENABLED
                && FactionSystemConfig.FACTION_GUARDS_ENABLED
                && ((player.isGood() && getTemplate().isClan(FactionSystemConfig.FACTION_EVIL_TEAM_NAME))
                        || (player.isEvil() && getTemplate().isClan(FactionSystemConfig.FACTION_GOOD_TEAM_NAME)))) {
            interact = false;

            // TODO: Fix normal targeting
            player.getAI().setIntentionAttack(this);
        }

        if (isFakePlayer() && isInCombat()) {
            interact = false;

            // TODO: Fix normal targeting
            player.getAI().setIntentionAttack(this);
        }

        if (getObjectId() != player.getTargetId()) {
            player.setTarget(this);
        } else if (interact) {
            if (isInAggroList(player)) {
                player.getAI().setIntentionAttack(this);
            } else {
                if (!canInteract(player)) {
                    player.getAI().setIntentionInteract(this);
                } else {
                    player.setLastFolkNPC(this);

                    if (hasListener(EventType.ON_NPC_QUEST_START)) {
                        player.setLastQuestNpcObject(getObjectId());
                    }

                    if (hasListener(EventType.ON_NPC_FIRST_TALK)) {
                        if (EventDispatcher.getInstance().hasListener(EventType.ON_NPC_FIRST_TALK, this)) {
                            EventDispatcher.getInstance().notifyEventAsync(new OnNpcFirstTalk(this, player), this);
                        }
                    } else {
                        showChatWindow(player, 0);
                    }
                }
            }
        }

        player.sendPacket(ActionFailed.STATIC_PACKET);
    }
}
