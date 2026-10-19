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
import app.l2nx.highfive.network.serverpackets.AbstractNpcInfo.TrapInfo;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.taskmanagers.DecayTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;

/**
 * Trap instance.
 * @author nBd
 */
public class Trap extends Npc {
    private static final int TICK = 1000; // 1s

    @Setter
    private boolean hasLifeTime;

    private boolean inArena = false;

    @Getter
    private boolean triggered;

    private final int lifeTime;

    @Getter
    private Player owner;

    private final Set<Integer> playersWhoDetectedMe = new HashSet<>();
    private final SkillHolder skill;

    @Getter
    @Setter
    private int remainingTime;

    // Tasks
    private ScheduledFuture<?> trapTask = null;

    /**
     * Creates a trap.
     * @param template the trap NPC template
     * @param instanceId the instance ID
     * @param lifeTime the life time
     */
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
            trapTask = ThreadPool.scheduleAtFixedRate(new TrapTask(this), TICK, TICK);
        }
    }

    /**
     * Creates a trap.
     * @param template the trap NPC template
     * @param owner the owner
     * @param lifeTime the life time
     */
    public Trap(NpcTemplate template, Player owner, int lifeTime) {
        this(template, owner.getInstanceId(), lifeTime);
        this.owner = owner;
    }

    @Override
    public void broadcastPacket(ServerPacket packet) {
        World.forEachVisibleObject(this, Player.class, player -> {
            if (triggered || canBeSeen(player)) {
                player.sendPacket(packet);
            }
        });
    }

    /**
     * Verify if the character can see the trap.
     * @param creature The creature to verify
     * @return {@code true} if the character can see the trap, {@code false} otherwise
     */
    public boolean canBeSeen(Creature creature) {
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
            // observers can't see trap
            if (creature.asPlayer().inObserverMode()) {
                return false;
            }

            // olympiad competitors can't see trap
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
        // Range seems to be reduced from Freya(300) to H5(150).
        if (!target.isInsideRadius2D(this, 150)) {
            return false;
        }

        if (!Skill.checkForAreaOffensiveSkills(this, target, skill.getSkill(), inArena)) {
            return false;
        }

        // observers
        final Player player = target.asPlayer();
        if (target.isPlayer() && player.inObserverMode()) {
            return false;
        }

        // olympiad own team and their summons not attacked
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

        // trap owned by players not attack non-flagged players
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
        if (owner != null) {
            owner.setTrap(null);
            owner = null;
        }

        return super.deleteMe();
    }

    @Override
    public Player asPlayer() {
        return owner;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        return null;
    }

    @Override
    public int getKarma() {
        return owner != null ? owner.getKarma() : 0;
    }

    public byte getPvpFlag() {
        return owner != null ? owner.getPvpFlag() : 0;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        return null;
    }

    public Skill getSkill() {
        return skill.getSkill();
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return !canBeSeen(attacker);
    }

    @Override
    public boolean isTrap() {
        return true;
    }

    @Override
    public void onSpawn() {
        super.onSpawn();
        inArena = isInsideZone(ZoneId.PVP) && !isInsideZone(ZoneId.SIEGE);
        playersWhoDetectedMe.clear();
    }

    @Override
    public void sendDamageMessage(Creature target, int damage, boolean mcrit, boolean pcrit, boolean miss) {
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
        if (triggered || canBeSeen(player)) {
            player.sendPacket(new TrapInfo(this, player));
        }
    }

    public void setDetected(Creature detector) {
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

        // Notify to scripts.
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
        DecayTaskManager.getInstance().cancel(this);
    }

    /**
     * Trigger the trap.
     * @param target the target
     */
    public void triggerTrap(Creature target) {
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

        ThreadPool.schedule(new TrapTriggerTask(this), 500);
    }

    public void unSummon() {
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
    public void updateAbnormalEffect() {}

    public boolean hasLifeTime() {
        return hasLifeTime;
    }

    public int getLifeTime() {
        return lifeTime;
    }
}
