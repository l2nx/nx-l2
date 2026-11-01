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
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.status.SiegeFlagStatus;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.siege.Siegable;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;

@SingleWriter
public class SiegeFlag extends Npc {
    private Clan clan;
    private Siegable siege;

    private final boolean advancedHeadquarter;

    private boolean canTalk;

    public SiegeFlag(Player player, NpcTemplate template, boolean advanced, boolean outPost) {
        super(template);
        setInstanceType(InstanceType.SiegeFlag);

        if (TerritoryWarManager.getInstance().isTWInProgress()) {
            clan = player.getClan();
            canTalk = false;
            if (clan == null) {
                deleteMe();
            }

            if (outPost) {
                advancedHeadquarter = false;
                setInvul(true);
            } else {
                advancedHeadquarter = advanced;
                setInvul(false);
            }

            getStatus();
            return;
        }

        clan = player.getClan();
        canTalk = true;
        siege = SiegeManager.getInstance().getSiege(player.getX(), player.getY(), player.getZ());
        if (siege == null) {
            siege = FortSiegeManager.getInstance().getSiege(player.getX(), player.getY(), player.getZ());
        }

        if (siege == null) {
            siege = CHSiegeManager.getInstance().getSiege(player);
        }

        if ((clan == null) || (siege == null)) {
            throw new NullPointerException(getClass().getSimpleName() + ": Initialization failed.");
        }

        final SiegeClan sc = siege.getAttackerClan(clan);
        if (sc == null) {
            throw new NullPointerException(getClass().getSimpleName() + ": Cannot find siege clan.");
        }

        sc.addFlag(this);
        advancedHeadquarter = advanced;
        getStatus();
        setInvul(false);
    }

    public boolean isAdvancedHeadquarter() {
        WorldPartition.checkOwner();
        return advancedHeadquarter;
    }

    void setCanTalk(boolean canTalk) {
        WorldPartition.checkOwner();
        this.canTalk = canTalk;
    }

    @Override
    public boolean canBeAttacked() {
        WorldPartition.checkOwner();
        return !isInvul();
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return !isInvul();
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        if ((siege != null) && (clan != null)) {
            final SiegeClan sc = siege.getAttackerClan(clan);
            if (sc != null) {
                sc.removeFlag(this);
            }
        } else if (clan != null) {
            TerritoryWarManager.getInstance().removeClanFlag(clan);
        }

        return true;
    }

    @Override
    public void onForcedAttack(Player player) {
        WorldPartition.checkOwner();
        onAction(player);
    }

    @Override
    public void onAction(Player player, boolean interact) {
        WorldPartition.checkOwner();
        if ((player == null) || !canTarget(player)) {
            return;
        }

        if (this != player.getTarget()) {
            player.setTarget(this);
        } else if (interact) {
            if (isAutoAttackable(player) && (Math.abs(player.getZ() - getZ()) < 100)) {
                player.getAI().setIntentionAttack(this);
            } else {
                player.sendPacket(ActionFailed.STATIC_PACKET);
            }
        }
    }

    @Override
    public SiegeFlagStatus getStatus() {
        WorldPartition.checkOwner();
        return (SiegeFlagStatus) super.getStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new SiegeFlagStatus(this));
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, Skill skill) {
        WorldPartition.checkOwner();
        super.reduceCurrentHp(damage, attacker, skill);
        if (canTalk()
                && (((getCastle() != null) && getCastle().getSiege().isInProgress())
                        || ((getFort() != null) && getFort().getSiege().isInProgress())
                        || ((getConquerableHall() != null)
                                && getConquerableHall().isInSiege()))
                && (clan != null)) {
            clan.broadcastToOnlineMembers(new SystemMessage(SystemMessageId.YOUR_BASE_IS_BEING_ATTACKED));
            setCanTalk(false);
            WorldPartition.loop().schedule(new ScheduleTalkTask(), 20000, TimeUnit.MILLISECONDS);
        }
    }

    private class ScheduleTalkTask implements Runnable {
        public ScheduleTalkTask() {}

        @Override
        public void run() {
            setCanTalk(true);
        }
    }

    private boolean canTalk() {
        return canTalk;
    }
}
