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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import lombok.Getter;

public class CreatureStatus {

    @Getter
    private final Creature activeChar;

    @Getter
    private double currentHp = 0; // Current HP of the Creature

    @Getter
    private double currentMp = 0; // Current MP of the Creature

    /** Array containing all clients that need to be notified about hp/mp updates of the Creature */
    private Set<Creature> statusListener;

    private Future<?> regTask;

    protected byte flagsRegenActive = 0;

    protected static final byte REGEN_FLAG_CP = 4;
    private static final byte REGEN_FLAG_HP = 1;
    private static final byte REGEN_FLAG_MP = 2;

    public CreatureStatus(Creature creature) {
        activeChar = creature;
    }

    /**
     * Add the object to the list of Creature that must be informed of HP/MP updates of this Creature.<br>
     * <br>
     * <b><u>Concept</u>:</b><br>
     * <br>
     * Each Creature owns a list called <b>statusListener</b> that contains all Player to inform of HP/MP updates.<br>
     * Players who must be informed are players that target this Creature.<br>
     * When a RegenTask is in progress sever just need to go through this list to send Server->Client packet StatusUpdate.<br>
     * <br>
     * <b><u>Example of use</u>:</b>
     * <ul>
     * <li>Target a PC or NPC</li>
     * <ul>
     * @param object Creature to add to the listener
     */
    public void addStatusListener(Creature object) {
        if (object == activeChar) {
            return;
        }

        getStatusListener().add(object);
    }

    /**
     * Remove the object from the list of Creature that must be informed of HP/MP updates of this Creature.<br>
     * <br>
     * <b><u>Concept</u>:</b><br>
     * <br>
     * Each Creature owns a list called <b>statusListener</b> that contains all Player to inform of HP/MP updates.<br>
     * Players who must be informed are players that target this Creature.<br>
     * When a RegenTask is in progress sever just need to go through this list to send Server->Client packet StatusUpdate.<br>
     * <br>
     * <b><u>Example of use </u>:</b>
     * <ul>
     * <li>Untarget a PC or NPC</li>
     * </ul>
     * @param object Creature to add to the listener
     */
    public void removeStatusListener(Creature object) {
        getStatusListener().remove(object);
    }

    /**
     * Return the list of Creature that must be informed of HP/MP updates of this Creature.<br>
     * <br>
     * <b><u>Concept</u>:</b><br>
     * <br>
     * Each Creature owns a list called <b>statusListener</b> that contains all Player to inform of HP/MP updates.<br>
     * Players who must be informed are players that target this Creature.<br>
     * When a RegenTask is in progress sever just need to go through this list to send Server->Client packet StatusUpdate.
     * @return The list of Creature to inform or null if empty
     */
    public Set<Creature> getStatusListener() {
        if (statusListener == null) {
            statusListener = ConcurrentHashMap.newKeySet();
        }

        return statusListener;
    }

    // place holder, only PcStatus has CP
    public void reduceCp(int value) {}

    /**
     * Reduce the current HP of the Creature and launch the doDie Task if necessary.
     * @param value
     * @param attacker
     */
    public void reduceHp(double value, Creature attacker) {
        reduceHp(value, attacker, true, false, false);
    }

    public void reduceHp(double value, Creature attacker, boolean isHpConsumption) {
        reduceHp(value, attacker, true, false, isHpConsumption);
    }

    public void reduceHp(double value, Creature attacker, boolean awake, boolean isDOT, boolean isHPConsumption) {
        if (activeChar.isDead()) {
            return;
        }

        // invul handling
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
            setCurrentHp(Math.max(currentHp - value, 0));
        }

        if ((activeChar.getCurrentHp() < 0.5) && activeChar.isMortal()) // Die
        {
            activeChar.abortAttack();
            activeChar.abortCast();

            activeChar.doDie(attacker);
        }
    }

    public void reduceMp(double value) {
        setCurrentMp(Math.max(currentMp - value, 0));
    }

    /**
     * Start the HP/MP/CP Regeneration task.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Calculate the regen task period</li>
     * <li>Launch the HP/MP/CP Regeneration task with Medium priority</li>
     * </ul>
     */
    public synchronized void startHpMpRegeneration() {
        if ((regTask == null) && !activeChar.isDead()) {
            // Get the Regeneration period.
            final int period = Formulas.getRegeneratePeriod(activeChar);

            // Create the HP/MP/CP Regeneration task.
            regTask = ThreadPool.scheduleAtFixedRate(this::doRegeneration, period, period);
        }
    }

    /**
     * Stop the HP/MP/CP Regeneration task.<br>
     * <br>
     * <b><u>Actions</u>:</b>
     * <ul>
     * <li>Set the RegenActive flag to False</li>
     * <li>Stop the HP/MP/CP Regeneration task</li>
     * </ul>
     */
    public synchronized void stopHpMpRegeneration() {
        if (regTask != null) {
            // Stop the HP/MP/CP Regeneration task.
            regTask.cancel(false);
            regTask = null;

            // Set the RegenActive flag to false.
            flagsRegenActive = 0;
        }
    }

    // place holder, only PcStatus has CP
    public double getCurrentCp() {
        return 0;
    }

    // place holder, only PcStatus has CP
    public void setCurrentCp(double newCp) {}

    public void setCurrentHp(double newHp) {
        setCurrentHp(newHp, true);
    }

    /**
     * Sets the current hp of this character.
     * @param newHp the new hp
     * @param broadcastPacket if true StatusUpdate packet will be broadcasted.
     * @return @{code true} if hp was changed, @{code false} otherwise.
     */
    public boolean setCurrentHp(double newHp, boolean broadcastPacket) {
        // Get the Max HP of the Creature.
        final int currentHp = (int) this.currentHp;
        final double maxHp = activeChar.getMaxHp();

        // Tempfix for updating UserInfo when skills like Final Frenzy and Final Fortress conditions activate.
        final double thirtyPercent = maxHp * 0.3;
        final boolean updateUserInfo = activeChar.isPlayer()
                && (((currentHp < thirtyPercent) && (newHp > thirtyPercent))
                        || ((currentHp > thirtyPercent) && (newHp < thirtyPercent)));

        synchronized (this) {
            if (activeChar.isDead()) {
                return false;
            }

            if (newHp >= maxHp) {
                // Set the RegenActive flag to false.
                this.currentHp = maxHp;
                flagsRegenActive &= ~REGEN_FLAG_HP;

                // Stop the HP/MP/CP Regeneration task.
                if (flagsRegenActive == 0) {
                    stopHpMpRegeneration();
                }
            } else {
                // Set the RegenActive flag to true.
                this.currentHp = newHp;
                flagsRegenActive |= REGEN_FLAG_HP;

                // Start the HP/MP/CP Regeneration task with Medium priority.
                startHpMpRegeneration();
            }
        }

        final boolean hpWasChanged = currentHp != this.currentHp;

        // Send the Server->Client packet StatusUpdate with current HP and MP to all other Player to inform.
        if (hpWasChanged && broadcastPacket) {
            activeChar.broadcastStatusUpdate();
        }

        if (updateUserInfo) {
            activeChar.asPlayer().updateUserInfo();
        }

        return hpWasChanged;
    }

    public void setCurrentHpMp(double newHp, double newMp) {
        final boolean hpChanged = setCurrentHp(newHp, false);
        final boolean mpChanged = setCurrentMp(newMp, false);
        if (hpChanged || mpChanged) {
            activeChar.broadcastStatusUpdate();
        }
    }

    public void setCurrentMp(double newMp) {
        setCurrentMp(newMp, true);
    }

    /**
     * Sets the current mp of this character.
     * @param newMp the new mp
     * @param broadcastPacket if true StatusUpdate packet will be broadcasted.
     * @return @{code true} if mp was changed, @{code false} otherwise.
     */
    public boolean setCurrentMp(double newMp, boolean broadcastPacket) {
        // Get the Max MP of the Creature.
        final int currentMp = (int) this.currentMp;
        final int maxMp = activeChar.getMaxMp();

        synchronized (this) {
            if (activeChar.isDead()) {
                return false;
            }

            if (newMp >= maxMp) {
                // Set the RegenActive flag to false.
                this.currentMp = maxMp;
                flagsRegenActive &= ~REGEN_FLAG_MP;

                // Stop the HP/MP/CP Regeneration task.
                if (flagsRegenActive == 0) {
                    stopHpMpRegeneration();
                }
            } else {
                // Set the RegenActive flag to true.
                this.currentMp = newMp;
                flagsRegenActive |= REGEN_FLAG_MP;

                // Start the HP/MP/CP Regeneration task with Medium priority.
                startHpMpRegeneration();
            }
        }

        final boolean mpWasChanged = currentMp != this.currentMp;

        // Send the Server->Client packet StatusUpdate with current HP and MP to all other Player to inform.
        if (mpWasChanged && broadcastPacket) {
            activeChar.broadcastStatusUpdate();
        }

        return mpWasChanged;
    }

    protected void doRegeneration() {
        // Modify the current HP/MP of the Creature and broadcast Server->Client packet StatusUpdate.
        if (!activeChar.isDead()
                && ((currentHp < activeChar.getMaxRecoverableHp()) || (currentMp < activeChar.getMaxRecoverableMp()))) {
            final double newHp = currentHp + Formulas.calcHpRegen(activeChar);
            final double newMp = currentMp + Formulas.calcMpRegen(activeChar);
            setCurrentHpMp(newHp, newMp);
        } else {
            stopHpMpRegeneration();
        }
    }
}
