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
package app.l2nx.highfive.entity.actor.status;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@SingleWriter
public class CreatureStatus {

    private final Creature activeChar;

    private double currentHp = 0;

    private double currentMp = 0;

    // Created on the first listener: most creatures are never targeted. Linked for a stable send order.
    private ObjectLinkedOpenHashSet<Creature> statusListener;

    private ScheduledFuture<?> regTask;

    protected byte flagsRegenActive = 0;

    protected static final byte REGEN_FLAG_CP = 4;
    private static final byte REGEN_FLAG_HP = 1;
    private static final byte REGEN_FLAG_MP = 2;

    public CreatureStatus(Creature creature) {
        activeChar = creature;
    }

    public Creature getActiveChar() {
        WorldPartition.checkOwner();
        return activeChar;
    }

    /** Unchecked, for a subclass method that has already checked the owner. */
    protected final Creature creature() {
        return activeChar;
    }

    public double getCurrentHp() {
        WorldPartition.checkOwner();
        return currentHp;
    }

    public double getCurrentMp() {
        WorldPartition.checkOwner();
        return currentMp;
    }

    public void addStatusListener(Creature object) {
        WorldPartition.checkOwner();
        Objects.requireNonNull(object, "object");
        if (object == activeChar) {
            return;
        }

        if (statusListener == null) {
            statusListener = new ObjectLinkedOpenHashSet<>();
        }

        statusListener.add(object);
    }

    public void removeStatusListener(Creature object) {
        WorldPartition.checkOwner();
        if (statusListener != null) {
            statusListener.remove(object);
        }
    }

    public boolean hasStatusListeners() {
        WorldPartition.checkOwner();
        return (statusListener != null) && !statusListener.isEmpty();
    }

    /** Walks a copy, so the action may add or remove listeners. */
    public void forEachStatusListener(Consumer<Creature> action) {
        WorldPartition.checkOwner();
        if (statusListener == null) {
            return;
        }

        try (ScratchBuffers.Snapshot<Creature> listeners = ScratchBuffers.snapshot(statusListener)) {
            for (int i = 0; i < listeners.size(); i++) {
                action.accept(listeners.get(i));
            }
        }
    }

    // No-op: only PcStatus has CP.
    public void reduceCp(int value) {
        WorldPartition.checkOwner();
    }

    public final void reduceHp(double value, Creature attacker) {
        WorldPartition.checkOwner();
        doReduceHp(value, attacker, true, false, false);
    }

    public final void reduceHp(double value, Creature attacker, boolean isHpConsumption) {
        WorldPartition.checkOwner();
        doReduceHp(value, attacker, true, false, isHpConsumption);
    }

    public final void reduceHp(double value, Creature attacker, boolean awake, boolean isDOT, boolean isHPConsumption) {
        WorldPartition.checkOwner();
        doReduceHp(value, attacker, awake, isDOT, isHPConsumption);
    }

    /** Unchecked {@link #reduceHp(double, Creature, boolean, boolean, boolean)}, the one subclasses override. */
    protected void doReduceHp(double value, Creature attacker, boolean awake, boolean isDOT, boolean isHPConsumption) {
        if (activeChar.isDead()) {
            return;
        }

        if (activeChar.isInvul() && !(isDOT || isHPConsumption)) {
            return;
        }

        if (attacker != null) {
            final Player attackerPlayer = attacker.asPlayer();
            if ((attackerPlayer != null)
                    && attackerPlayer.isGM()
                    && !attackerPlayer.getAccessLevel().canGiveDamage()) {
                return;
            }
        }

        if (!isDOT && !isHPConsumption) {
            activeChar.stopEffectsOnDamage(awake);
            if (activeChar.isStunned() && (ClockBridge.random().nextInt(10) == 0)) {
                activeChar.stopStunning(true);
            }
        }

        if (value > 0) {
            doSetCurrentHp(Math.max(currentHp - value, 0), true);
        }

        if ((activeChar.getCurrentHp() < 0.5) && activeChar.isMortal()) {
            activeChar.abortAttack();
            activeChar.abortCast();

            activeChar.doDie(attacker);
        }
    }

    public final void reduceMp(double value) {
        WorldPartition.checkOwner();
        doReduceMp(value);
    }

    /** Unchecked {@link #reduceMp(double)}, the one subclasses override. */
    protected void doReduceMp(double value) {
        doSetCurrentMp(Math.max(currentMp - value, 0), true);
    }

    public void startHpMpRegeneration() {
        WorldPartition.checkOwner();
        startRegeneration();
    }

    /** Unchecked {@link #startHpMpRegeneration()}, for methods that have already checked the owner. */
    protected final void startRegeneration() {
        if ((regTask == null) && !activeChar.isDead()) {
            final int period = Formulas.getRegeneratePeriod(activeChar);

            regTask = WorldPartition.loop()
                    .scheduleAtFixedRate(this::doRegeneration, period, period, TimeUnit.MILLISECONDS);
        }
    }

    public void stopHpMpRegeneration() {
        WorldPartition.checkOwner();
        stopRegeneration();
    }

    /** Unchecked {@link #stopHpMpRegeneration()}, for methods that have already checked the owner. */
    protected final void stopRegeneration() {
        if (regTask != null) {
            regTask.cancel(false);
            regTask = null;
            flagsRegenActive = 0;
        }
    }

    // No-op: only PcStatus has CP.
    public double getCurrentCp() {
        WorldPartition.checkOwner();
        return 0;
    }

    // No-op: only PcStatus has CP.
    public void setCurrentCp(double newCp) {
        WorldPartition.checkOwner();
    }

    public final void setCurrentHp(double newHp) {
        WorldPartition.checkOwner();
        doSetCurrentHp(newHp, true);
    }

    public final boolean setCurrentHp(double newHp, boolean broadcastPacket) {
        WorldPartition.checkOwner();
        return doSetCurrentHp(newHp, broadcastPacket);
    }

    /** Unchecked {@link #setCurrentHp(double, boolean)}, the one subclasses override. */
    protected boolean doSetCurrentHp(double newHp, boolean broadcastPacket) {
        final int currentHp = (int) this.currentHp;
        final double maxHp = activeChar.getMaxHp();

        // Crossing 30% HP refreshes UserInfo: Final Frenzy and Final Fortress activate there.
        final double thirtyPercent = maxHp * 0.3;
        final boolean updateUserInfo = activeChar.isPlayer()
                && (((currentHp < thirtyPercent) && (newHp > thirtyPercent))
                        || ((currentHp > thirtyPercent) && (newHp < thirtyPercent)));

        if (activeChar.isDead()) {
            return false;
        }

        if (newHp >= maxHp) {
            this.currentHp = maxHp;
            flagsRegenActive &= ~REGEN_FLAG_HP;

            if (flagsRegenActive == 0) {
                stopRegeneration();
            }
        } else {
            this.currentHp = newHp;
            flagsRegenActive |= REGEN_FLAG_HP;

            startRegeneration();
        }

        final boolean hpWasChanged = currentHp != this.currentHp;

        if (hpWasChanged && broadcastPacket) {
            activeChar.broadcastStatusUpdate();
        }

        if (updateUserInfo) {
            activeChar.asPlayer().updateUserInfo();
        }

        return hpWasChanged;
    }

    public void setCurrentHpMp(double newHp, double newMp) {
        WorldPartition.checkOwner();
        final boolean hpChanged = doSetCurrentHp(newHp, false);
        final boolean mpChanged = doSetCurrentMp(newMp, false);
        if (hpChanged || mpChanged) {
            activeChar.broadcastStatusUpdate();
        }
    }

    public final void setCurrentMp(double newMp) {
        WorldPartition.checkOwner();
        doSetCurrentMp(newMp, true);
    }

    public final boolean setCurrentMp(double newMp, boolean broadcastPacket) {
        WorldPartition.checkOwner();
        return doSetCurrentMp(newMp, broadcastPacket);
    }

    private boolean doSetCurrentMp(double newMp, boolean broadcastPacket) {
        final int currentMp = (int) this.currentMp;
        final int maxMp = activeChar.getMaxMp();

        if (activeChar.isDead()) {
            return false;
        }

        if (newMp >= maxMp) {
            this.currentMp = maxMp;
            flagsRegenActive &= ~REGEN_FLAG_MP;

            if (flagsRegenActive == 0) {
                stopRegeneration();
            }
        } else {
            this.currentMp = newMp;
            flagsRegenActive |= REGEN_FLAG_MP;

            startRegeneration();
        }

        final boolean mpWasChanged = currentMp != this.currentMp;

        if (mpWasChanged && broadcastPacket) {
            activeChar.broadcastStatusUpdate();
        }

        return mpWasChanged;
    }

    protected void doRegeneration() {
        if (!activeChar.isDead()
                && ((currentHp < activeChar.getMaxRecoverableHp()) || (currentMp < activeChar.getMaxRecoverableMp()))) {
            final double newHp = currentHp + Formulas.calcHpRegen(activeChar);
            final double newMp = currentMp + Formulas.calcMpRegen(activeChar);
            setCurrentHpMp(newHp, newMp);
        } else {
            stopRegeneration();
        }
    }
}
