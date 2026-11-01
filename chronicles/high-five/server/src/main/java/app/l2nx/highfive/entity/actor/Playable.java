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
package app.l2nx.highfive.entity.actor;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.stat.PlayableStat;
import app.l2nx.highfive.entity.actor.status.PlayableStatus;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.effects.EffectFlag;
import app.l2nx.highfive.mechanics.effects.EffectType;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureDeath;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EtcStatusUpdate;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * This class represents all Playable characters in the world.<br>
 * Playable:
 * <ul>
 * <li>Player</li>
 * <li>Summon</li>
 * </ul>
 */
@SingleWriter
public abstract class Playable extends Creature {
    private Creature lockedTarget = null;

    private Player transferDmgTo = null;

    public Playable(int objectId, CreatureTemplate template) {
        super(objectId, template);
        setInstanceType(InstanceType.Playable);
        setInvul(false);
    }

    public Playable(CreatureTemplate template) {
        super(template);
        setInstanceType(InstanceType.Playable);
        setInvul(false);
    }

    public void setLockedTarget(Creature lockedTarget) {
        WorldPartition.checkOwner();
        this.lockedTarget = lockedTarget;
    }

    @Override
    public PlayableStat getStat() {
        WorldPartition.checkOwner();
        return (PlayableStat) uncheckedStat();
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new PlayableStat(this, statAccess()));
    }

    @Override
    public PlayableStatus getStatus() {
        WorldPartition.checkOwner();
        return (PlayableStatus) uncheckedStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new PlayableStatus(this));
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_DEATH, this)) {
            final TerminateReturn returnBack = EventDispatcher.getInstance()
                    .notifyEvent(
                            OnCreatureDeath.builder()
                                    .attacker(killer)
                                    .target(this)
                                    .build(),
                            this,
                            TerminateReturn.class);
            if ((returnBack != null) && returnBack.terminate()) {
                return false;
            }
        }

        if (isDead()) {
            return false;
        }

        setCurrentHp(0);
        setDead(true);

        setTarget(null);

        stopMove(null);

        getStatus().stopHpMpRegeneration();

        boolean deleteBuffs = true;
        if (isNoblesseBlessedAffected()) {
            stopEffects(EffectType.NOBLESSE_BLESSING);
            deleteBuffs = false;
        }

        if (isResurrectSpecialAffected()) {
            stopEffects(EffectType.RESURRECTION_SPECIAL);
            deleteBuffs = false;
        }

        final Player player = asPlayer();
        if (isPlayer() && player.hasCharmOfCourage()) {
            if (player.isInSiege()) {
                player.reviveRequest(player, false, 0);
            }

            player.setCharmOfCourage(false);
            player.sendPacket(new EtcStatusUpdate(player));
        }

        if (deleteBuffs) {
            stopAllEffectsExceptThoseThatLastThroughDeath();
        }

        broadcastStatusUpdate();

        ZoneManager.getInstance().getRegion(this).onDeath(this);

        if (!player.isNotifyQuestOfDeathEmpty()) {
            for (QuestState qs : player.getNotifyQuestOfDeath()) {
                qs.getQuest().onDeath(killer == null ? this : killer, this, qs);
            }
        }

        if (isPlayer() && isInInstance()) {
            final Instance instance = InstanceManager.getInstance().getInstance(getInstanceId());
            if (instance != null) {
                instance.notifyDeath(player);
            }
        }

        if (killer != null) {
            final Player killerPlayer = killer.asPlayer();
            if (killerPlayer != null) {
                killerPlayer.onKillUpdatePvPKarma(this);
            }
        }

        getAI().notifyActionDeath();
        updateEffectIcons();
        return true;
    }

    public boolean checkIfPvP(Creature target) {
        WorldPartition.checkOwner();
        if (target == null) {
            return false;
        }

        if (target == this) {
            return false;
        }

        if (!target.isPlayable()) {
            return false;
        }

        final Player player = asPlayer();
        if (player == null) {
            return false;
        }

        if (player.getKarma() != 0) {
            return false;
        }

        final Player targetPlayer = target.asPlayer();
        if (targetPlayer == null) {
            return false;
        }

        if (targetPlayer == this) {
            return false;
        }

        if (targetPlayer.getKarma() != 0) {
            return false;
        }

        if (targetPlayer.getPvpFlag() == 0) {
            return false;
        }

        return true;
    }

    @Override
    public boolean canBeAttacked() {
        WorldPartition.checkOwner();
        return true;
    }

    public boolean isNoblesseBlessedAffected() {
        WorldPartition.checkOwner();
        return isAffected(EffectFlag.NOBLESS_BLESSING);
    }

    public boolean isResurrectSpecialAffected() {
        WorldPartition.checkOwner();
        return isAffected(EffectFlag.RESURRECTION_SPECIAL);
    }

    public boolean isSilentMovingAffected() {
        WorldPartition.checkOwner();
        return isAffected(EffectFlag.SILENT_MOVE);
    }

    /** Newbie Protection Blessing: safe from chaotic characters 10 or more levels above. */
    public boolean isProtectionBlessingAffected() {
        WorldPartition.checkOwner();
        return isAffected(EffectFlag.PROTECTION_BLESSING);
    }

    @Override
    public void updateEffectIcons(boolean partyOnly) {
        WorldPartition.checkOwner();
        getEffectList().updateEffectIcons(partyOnly);
    }

    public boolean isLockedTarget() {
        WorldPartition.checkOwner();
        return lockedTarget != null;
    }

    public Creature getLockedTarget() {
        WorldPartition.checkOwner();
        return lockedTarget;
    }

    public void setTransferDamageTo(Player val) {
        WorldPartition.checkOwner();
        transferDmgTo = val;
    }

    public Player getTransferingDamageTo() {
        WorldPartition.checkOwner();
        return transferDmgTo;
    }

    public abstract void doPickupItem(WorldObject object);

    public abstract byte getPvpFlag();

    public abstract boolean useMagic(Skill skill, boolean forceUse, boolean dontMove);

    public abstract void storeMe();

    public abstract void storeEffect(boolean storeEffects);

    public abstract void restoreEffects();

    public boolean isOnEvent() {
        WorldPartition.checkOwner();
        return false;
    }

    @Override
    public boolean isPlayable() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Playable asPlayable() {
        WorldPartition.checkOwner();
        return this;
    }
}
