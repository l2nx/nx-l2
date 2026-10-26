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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.npc.RaidBossStatus;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.managers.RaidBossPointsManager;
import app.l2nx.highfive.managers.RaidBossSpawnManager;
import app.l2nx.highfive.mechanics.olympiad.Hero;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import lombok.Getter;
import lombok.Setter;

/**
 * This class manages all RaidBoss.<br>
 * In a group mob, there are one master called RaidBoss and several slaves called Minions.
 */
public class RaidBoss extends Monster {
    @Getter
    @Setter
    private RaidBossStatus raidStatus;

    @Setter
    private boolean useRaidCurse = true;

    /**
     * Creates a raid boss.
     * @param template the raid boss template
     */
    public RaidBoss(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.RaidBoss);
        setIsRaid(true);
        setLethalable(false);
    }

    @Override
    public void onSpawn() {
        setRandomWalking(false);
        super.onSpawn();
    }

    @Override
    public boolean doDie(Creature killer) {
        if (!super.doDie(killer)) {
            return false;
        }

        if ((killer != null) && killer.isPlayable()) {
            final SystemMessage msg = new SystemMessage(SystemMessageId.CONGRATULATIONS_YOUR_RAID_WAS_SUCCESSFUL);
            final Player player = killer.asPlayer();
            final Party party = player.getParty();
            if (party != null) {
                party.broadcastPacket(msg);
                for (Player member : party.getMembers()) {
                    RaidBossPointsManager.getInstance()
                            .addPoints(
                                    member,
                                    getId(),
                                    (getLevel() / 2) + ClockBridge.random().nextInt(-5, 6));
                    if (member.isNoble()) {
                        Hero.getInstance().setRBkilled(member.getObjectId(), getId());
                    }
                }
            } else {
                player.sendPacket(msg);
                RaidBossPointsManager.getInstance()
                        .addPoints(
                                player,
                                getId(),
                                (getLevel() / 2) + ClockBridge.random().nextInt(-5, 6));
                if (player.isNoble()) {
                    Hero.getInstance().setRBkilled(player.getObjectId(), getId());
                }
            }
        }

        RaidBossSpawnManager.getInstance().updateStatus(this, true);
        return true;
    }

    @Override
    public float getVitalityPoints(int level, long damage, int maxHp) {
        return -super.getVitalityPoints(level, damage, maxHp) / 100;
    }

    @Override
    public boolean useVitalityRate() {
        return PlayerConfig.RAIDBOSS_USE_VITALITY;
    }

    @Override
    public boolean giveRaidCurse() {
        return useRaidCurse;
    }
}
