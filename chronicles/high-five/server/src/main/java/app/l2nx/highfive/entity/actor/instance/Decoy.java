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
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.CharInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@SingleWriter
public class Decoy extends Creature {
    private final Player owner;

    private ScheduledFuture<?> decoyLifeTask;
    private ScheduledFuture<?> hateSpam;

    public Decoy(NpcTemplate template, Player owner, int totalLifeTime) {
        this(template, owner, totalLifeTime, true);
    }

    public Decoy(NpcTemplate template, Player owner, int totalLifeTime, boolean aggressive) {
        super(template);
        setInstanceType(InstanceType.Decoy);
        this.owner = owner;
        setXYZInvisible(owner.getX(), owner.getY(), owner.getZ());
        setInvul(false);
        decoyLifeTask = WorldPartition.loop().schedule(() -> unSummon(owner), totalLifeTime, TimeUnit.MILLISECONDS);
        if (aggressive) {
            final int hateSpamSkillId = 5272;
            final int skilllevel = Math.min(
                    getTemplate().getDisplayId() - 13070,
                    SkillData.getInstance().getMaxLevel(hateSpamSkillId));
            hateSpam = WorldPartition.loop()
                    .scheduleAtFixedRate(
                            new HateSpam(this, SkillData.getInstance().getSkill(hateSpamSkillId, skilllevel)),
                            2000,
                            5000,
                            TimeUnit.MILLISECONDS);
        }
    }

    public Player getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        if (hateSpam != null) {
            hateSpam.cancel(true);
            hateSpam = null;
        }

        unSummon(owner);
        DecayTaskManager.getInstance().add(this);
        return true;
    }

    @Slf4j
    private static class HateSpam implements Runnable {
        private final Decoy player;
        private final Skill skill;

        HateSpam(Decoy player, Skill hate) {
            this.player = player;
            skill = hate;
        }

        @Override
        public void run() {
            try {
                player.setTarget(player);
                player.doCast(skill);
            } catch (Throwable e) {
                log.error("Decoy Error: ", e);
            }
        }
    }

    public void unSummon(Player owner) {
        WorldPartition.checkOwner();
        if (hateSpam != null) {
            hateSpam.cancel(true);
            hateSpam = null;
        }

        if (isSpawned() && !isDead()) {
            ZoneManager.getInstance().getRegion(this).removeFromZones(this);
            owner.setDecoy(null);
            decayMe();
        }

        if (decoyLifeTask != null) {
            decoyLifeTask.cancel(false);
            decoyLifeTask = null;
        }
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        super.onSpawn();
        sendPacket(new CharInfo(this, false));
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
        World.forEachVisibleObject(this, Player.class, player -> {
            if (isVisibleFor(player)) {
                player.sendPacket(new CharInfo(this, false));
            }
        });
    }

    public void stopDecay() {
        WorldPartition.checkOwner();
        DecayTaskManager.getInstance().cancel(this);
    }

    @Override
    public void onDecay() {
        WorldPartition.checkOwner();
        deleteMe(owner);
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return owner.isAutoAttackable(attacker);
    }

    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return getTemplate().getId();
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return getTemplate().getLevel();
    }

    public void deleteMe(Player owner) {
        WorldPartition.checkOwner();
        decayMe();
        owner.setDecoy(null);
    }

    @Override
    public Player asPlayer() {
        WorldPartition.checkOwner();
        return owner;
    }

    @Override
    public NpcTemplate getTemplate() {
        WorldPartition.checkOwner();
        return (NpcTemplate) super.getTemplate();
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        player.sendPacket(new CharInfo(this, false));
    }

    @Override
    public void sendPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        if (owner != null) {
            owner.sendPacket(packet);
        }
    }

    @Override
    public void sendPacket(SystemMessageId id) {
        WorldPartition.checkOwner();
        if (owner != null) {
            owner.sendPacket(id);
        }
    }
}
