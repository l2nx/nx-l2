/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.TrapAction;
import app.l2nx.highfive.entity.actor.tasks.npc.trap.TrapTask;
import app.l2nx.highfive.entity.actor.tasks.npc.trap.TrapTriggerTask;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.trap.OnTrapAction;
import app.l2nx.highfive.mechanics.olympiad.OlympiadGameManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo.TrapInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author nBd
 */
@SingleWriter
public class Trap extends Npc {
    private static final int TICK = 1000; // 1s

    private boolean hasLifeTime;

    private boolean inArena = false;

    private boolean triggered;

    private final int lifeTime;

    private Player owner;

    private final IntOpenHashSet playersWhoDetectedMe = new IntOpenHashSet();
    private final SkillHolder skill;

    private int remainingTime;

    private ScheduledFuture<?> trapTask = null;

    public Trap(NpcTemplate template, int instanceId, int lifeTime) {
        super(template);
        setInstanceType(InstanceType.Trap);
        setInstanceId(instanceId);
        setName(template.getName());
        setInvul(false);

        owner = null;
        triggered = false;
        skill = getTemplate().getParameters().getObject("trap_skill", SkillHolder.class);
        hasLifeTime = lifeTime >= 0;
        this.lifeTime = lifeTime != 0 ? lifeTime : 30000;
        remainingTime = this.lifeTime;
        if (skill != null) {
            trapTask = WorldPartition.loop().scheduleAtFixedRate(new TrapTask(this), TICK, TICK, TimeUnit.MILLISECONDS);
        }
    }

    public Trap(NpcTemplate template, Player owner, int lifeTime) {
        this(template, owner.getInstanceId(), lifeTime);
        this.owner = owner;
    }

    public void setHasLifeTime(boolean hasLifeTime) {
        WorldPartition.checkOwner();
        this.hasLifeTime = hasLifeTime;
    }

    public boolean isTriggered() {
        WorldPartition.checkOwner();
        return triggered;
    }

    public Player getOwner() {
        WorldPartition.checkOwner();
        return owner;
    }

    public int getRemainingTime() {
        WorldPartition.checkOwner();
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        WorldPartition.checkOwner();
        this.remainingTime = remainingTime;
    }

    @Override
    public void broadcastPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        World.forEachVisibleObject(this, Player.class, player -> {
            if (triggered || canBeSeen(player)) {
                player.sendPacket(packet);
            }
        });
    }

    public boolean canBeSeen(Creature creature) {
        WorldPartition.checkOwner();
        if ((creature != null) && playersWhoDetectedMe.contains(creature.getObjectId())) {
            return true;
        }

        if ((owner == null) || (creature == null)) {
            return false;
        }

        if (creature == owner) {
            return true;
        }

        if (creature.isPlayer()) {
            if (creature.asPlayer().inObserverMode()) {
                return false;
            }

            if (owner.isInOlympiadMode()
                    && creature.asPlayer().isInOlympiadMode()
                    && (creature.asPlayer().getOlympiadSide() != owner.getOlympiadSide())) {
                return false;
            }
        }

        if (inArena) {
            return true;
        }

        if (owner.isInParty()
                && creature.isInParty()
                && (owner.getParty().getLeaderObjectId() == creature.getParty().getLeaderObjectId())) {
            return true;
        }

        return false;
    }

    public boolean checkTarget(Creature target) {
        WorldPartition.checkOwner();
        if (!target.isInsideRadius2D(this, 150)) {
            return false;
        }

        if (!Skill.checkForAreaOffensiveSkills(this, target, skill.getSkill(), inArena)) {
            return false;
        }

        final Player player = target.asPlayer();
        if (target.isPlayer() && player.inObserverMode()) {
            return false;
        }

        if ((owner != null)
                && owner.isInOlympiadMode()
                && (player != null)
                && player.isInOlympiadMode()
                && (player.getOlympiadSide() == owner.getOlympiadSide())) {
            return false;
        }

        if (inArena) {
            return true;
        }

        if (owner != null) {
            if (target.isAttackable()) {
                return true;
            }

            if ((player == null) || ((player.getPvpFlag() == 0) && (player.getKarma() == 0))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean deleteMe() {
        WorldPartition.checkOwner();
        if (owner != null) {
            owner.setTrap(null);
            owner = null;
        }

        return super.deleteMe();
    }

    @Override
    public Player asPlayer() {
        WorldPartition.checkOwner();
        return owner;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public int getKarma() {
        WorldPartition.checkOwner();
        return owner != null ? owner.getKarma() : 0;
    }

    public byte getPvpFlag() {
        WorldPartition.checkOwner();
        return owner != null ? owner.getPvpFlag() : 0;
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

    public Skill getSkill() {
        WorldPartition.checkOwner();
        return skill.getSkill();
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return !canBeSeen(attacker);
    }

    @Override
    public boolean isTrap() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        super.onSpawn();
        inArena = isInsideZone(ZoneId.PVP) && !isInsideZone(ZoneId.SIEGE);
        playersWhoDetectedMe.clear();
    }

    @Override
    public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
        WorldPartition.checkOwner();
        if (miss || (owner == null)) {
            return;
        }

        if (owner.isInOlympiadMode()
                && target.isPlayer()
                && target.asPlayer().isInOlympiadMode()
                && (target.asPlayer().getOlympiadGameId() == owner.getOlympiadGameId())) {
            OlympiadGameManager.getInstance().notifyCompetitorDamage(getOwner(), damage);
        }

        if (target.isInvul() && !target.isNpc()) {
            owner.sendPacket(SystemMessageId.THE_ATTACK_HAS_BEEN_BLOCKED);
        } else {
            final SystemMessage sm = new SystemMessage(SystemMessageId.C1_HAS_DONE_S3_POINTS_OF_DAMAGE_TO_C2);
            sm.addString(getName());
            sm.addString(target.getName());
            sm.addInt(damage);
            owner.sendPacket(sm);
        }
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        if (triggered || canBeSeen(player)) {
            player.sendPacket(new TrapInfo(this, player));
        }
    }

    public void setDetected(Creature detector) {
        WorldPartition.checkOwner();
        if (inArena) {
            if (detector.isPlayable()) {
                sendInfo(detector.asPlayer());
            }
            return;
        }

        if ((owner != null) && (owner.getPvpFlag() == 0) && (owner.getKarma() == 0)) {
            return;
        }

        playersWhoDetectedMe.add(detector.getObjectId());

        if (EventDispatcher.getInstance().hasListener(EventType.ON_TRAP_ACTION, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnTrapAction.builder()
                                    .trap(this)
                                    .trigger(detector)
                                    .action(TrapAction.TRAP_DETECTED)
                                    .build(),
                            this);
        }

        if (detector.isPlayable()) {
            sendInfo(detector.asPlayer());
        }
    }

    public void stopDecay() {
        WorldPartition.checkOwner();
        DecayTaskManager.getInstance().cancel(this);
    }

    public void triggerTrap(Creature target) {
        WorldPartition.checkOwner();
        if (trapTask != null) {
            trapTask.cancel(true);
            trapTask = null;
        }

        triggered = true;
        broadcastPacket(new TrapInfo(this, null));
        setTarget(target);

        if (EventDispatcher.getInstance().hasListener(EventType.ON_TRAP_ACTION, this)) {
            EventDispatcher.getInstance()
                    .notifyEventAsync(
                            OnTrapAction.builder()
                                    .trap(this)
                                    .trigger(target)
                                    .action(TrapAction.TRAP_TRIGGERED)
                                    .build(),
                            this);
        }

        WorldPartition.loop().schedule(new TrapTriggerTask(this), 500, TimeUnit.MILLISECONDS);
    }

    public void unSummon() {
        WorldPartition.checkOwner();
        if (trapTask != null) {
            trapTask.cancel(true);
            trapTask = null;
        }

        if (owner != null) {
            owner.setTrap(null);
            owner = null;
        }

        if (isSpawned() && !isDead()) {
            ZoneManager.getInstance().getRegion(this).removeFromZones(this);
            deleteMe();
        }
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
    }

    public boolean hasLifeTime() {
        WorldPartition.checkOwner();
        return hasLifeTime;
    }

    public int getLifeTime() {
        WorldPartition.checkOwner();
        return lifeTime;
    }
}
