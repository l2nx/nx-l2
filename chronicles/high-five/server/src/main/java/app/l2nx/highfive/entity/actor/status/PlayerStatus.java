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
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.xml.NpcNameLocalisationData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.actor.holders.player.Duel;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.managers.DuelManager;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.stats.Formulas;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.LocationUtil;

@SingleWriter
public class PlayerStatus extends PlayableStatus {
    private double currentCp = 0;

    public PlayerStatus(Player player) {
        super(player);
    }

    @Override
    public double getCurrentCp() {
        WorldPartition.checkOwner();
        return currentCp;
    }

    @Override
    public void reduceCp(int value) {
        WorldPartition.checkOwner();
        setCurrentCp(currentCp > value ? currentCp - value : 0);
    }

    @Override
    protected void doReduceHp(double value, Creature attacker, boolean awake, boolean isDOT, boolean isHPConsumption) {
        doReduceHp(value, attacker, awake, isDOT, isHPConsumption, false);
    }

    public void reduceHp(
            double value, Creature attacker, boolean awake, boolean isDOT, boolean isHPConsumption, boolean ignoreCP) {
        WorldPartition.checkOwner();
        doReduceHp(value, attacker, awake, isDOT, isHPConsumption, ignoreCP);
    }

    private void doReduceHp(
            double value, Creature attacker, boolean awake, boolean isDOT, boolean isHPConsumption, boolean ignoreCP) {
        final Player player = player();
        if (player.isDead()) {
            return;
        }

        if (OfflineTradeConfig.OFFLINE_MODE_NO_DAMAGE
                && (player.getClient() != null)
                && player.getClient().isDetached()
                && ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE
                                && ((player.getPrivateStoreType() == PrivateStoreType.SELL)
                                        || (player.getPrivateStoreType() == PrivateStoreType.BUY)))
                        || (OfflineTradeConfig.OFFLINE_CRAFT_ENABLE
                                && (player.isCrafting()
                                        || (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE))))) {
            return;
        }

        if (player.isInvul() && !(isDOT || isHPConsumption)) {
            return;
        }

        if (!isHPConsumption) {
            player.stopEffectsOnDamage(awake);

            if (player.isCrafting() || player.isInStoreMode()) {
                player.setPrivateStoreType(PrivateStoreType.NONE);
                player.standUp();
                player.broadcastUserInfo();
            } else if (player.isSitting()) {
                player.standUp();
            }

            if (!isDOT && player.isStunned() && (ClockBridge.random().nextInt(10) == 0)) {
                player.stopStunning(true);
            }
        }

        double amount = value;
        int fullValue = (int) amount;
        int tDmg = 0;
        int mpDam = 0;
        if ((attacker != null) && (attacker != player)) {
            final Player attackerPlayer = attacker.asPlayer();
            if (attackerPlayer != null) {
                if (attackerPlayer.isGM() && !attackerPlayer.getAccessLevel().canGiveDamage()) {
                    return;
                }

                if (player.isInDuel()) {
                    if (player.getDuelState() == Duel.DUELSTATE_DEAD) {
                        return;
                    } else if (player.getDuelState() == Duel.DUELSTATE_WINNER) {
                        return;
                    }

                    if (attackerPlayer.getDuelId() != player.getDuelId()) {
                        player.setDuelState(Duel.DUELSTATE_INTERRUPTED);
                    }
                }
            }

            final Summon summon = player.getSummon();
            if (player.hasServitor() && LocationUtil.checkIfInRange(1000, player, summon, true)) {
                tDmg = ((int) amount * (int) player.calcStat(Stat.TRANSFER_DAMAGE_PERCENT, 0, null, null)) / 100;

                // Only transfer dmg up to current HP, it should not be killed.
                tDmg = Math.min((int) summon.getCurrentHp() - 1, tDmg);
                if (tDmg > 0) {
                    summon.reduceCurrentHp(tDmg, attacker, null);
                    amount -= tDmg;
                    fullValue = (int)
                            amount; // Reduce the announced value here as player will get a message about summon damage.
                }
            }

            mpDam = ((int) amount * (int) player.calcStat(Stat.MANA_SHIELD_PERCENT, 0, null, null)) / 100;
            if (mpDam > 0) {
                mpDam = (int) (amount - mpDam);
                if (mpDam > player.getCurrentMp()) {
                    player.sendPacket(SystemMessageId.MP_BECAME_0_AND_THE_ARCANE_SHIELD_IS_DISAPPEARING);
                    player.stopSkillEffects(SkillFinishType.REMOVED, 1556);
                    amount = mpDam - player.getCurrentMp();
                    player.setCurrentMp(0);
                } else {
                    player.reduceCurrentMp(mpDam);
                    final SystemMessage smsg =
                            new SystemMessage(SystemMessageId.ARCANE_SHIELD_DECREASED_YOUR_MP_BY_S1_INSTEAD_OF_HP);
                    smsg.addInt(mpDam);
                    player.sendPacket(smsg);
                    return;
                }
            }

            final Player caster = player.getTransferingDamageTo();
            if ((caster != null)
                    && (player.getParty() != null)
                    && LocationUtil.checkIfInRange(1000, player, caster, true)
                    && !caster.isDead()
                    && (player != caster)
                    && player.getParty().getMembers().contains(caster)) {
                int transferDmg = Math.min(
                        (int) caster.getCurrentHp() - 1,
                        ((int) amount * (int) player.calcStat(Stat.TRANSFER_DAMAGE_TO_PLAYER, 0, null, null)) / 100);
                if (transferDmg > 0) {
                    int membersInRange = 0;
                    for (Player member : caster.getParty().getMembers()) {
                        if (LocationUtil.checkIfInRange(1000, member, caster, false) && (member != caster)) {
                            membersInRange++;
                        }
                    }

                    if ((attacker.isPlayable() || attacker.isFakePlayer()) && (caster.getCurrentCp() > 0)) {
                        if (caster.getCurrentCp() > transferDmg) {
                            caster.getStatus().reduceCp(transferDmg);
                        } else {
                            transferDmg = (int) (transferDmg - caster.getCurrentCp());
                            caster.getStatus().reduceCp((int) caster.getCurrentCp());
                        }
                    }

                    if (membersInRange > 0) {
                        caster.reduceCurrentHp(transferDmg / membersInRange, attacker, null);
                        amount -= transferDmg;
                        fullValue = (int) amount;
                    }
                }
            }

            if (!ignoreCP && (attacker.isPlayable() || attacker.isFakePlayer())) {
                if (currentCp >= amount) {
                    setCurrentCp(currentCp - amount);
                    amount = 0;
                } else {
                    amount -= currentCp;
                    setCurrentCp(0, false);
                }
            }

            if ((fullValue > 0) && !isDOT) {
                SystemMessage smsg = new SystemMessage(SystemMessageId.C1_HAS_RECEIVED_S3_DAMAGE_FROM_C2);
                smsg.addString(player.getName());

                String targetName = attacker.getName();
                if (MultilingualSupportConfig.MULTILANG_ENABLE && attacker.isNpc()) {
                    final String[] localisation =
                            NpcNameLocalisationData.getInstance().getLocalisation(player.getLang(), attacker.getId());
                    if (localisation != null) {
                        targetName = localisation[0];
                    }
                }

                smsg.addString(targetName);
                smsg.addInt(fullValue);
                player.sendPacket(smsg);

                if ((tDmg > 0) && (attackerPlayer != null)) {
                    smsg = new SystemMessage(
                            SystemMessageId.YOU_HAVE_DEALT_S1_DAMAGE_TO_YOUR_TARGET_AND_S2_DAMAGE_TO_THE_SERVITOR);
                    smsg.addInt(fullValue);
                    smsg.addInt(tDmg);
                    attackerPlayer.sendPacket(smsg);
                }
            }
        }

        if (amount > 0) {
            amount = getCurrentHp() - amount;
            if (amount <= 0) {
                if (player.isInDuel()) {
                    player.disableAllSkills();
                    stopRegeneration();
                    if (attacker != null) {
                        attacker.getAI().setIntentionActive();
                        attacker.sendPacket(ActionFailed.STATIC_PACKET);
                        attacker.setTarget(null);
                        attacker.abortAttack();
                    }

                    DuelManager.getInstance().onPlayerDefeat(player);
                    amount = 1;
                } else {
                    amount = 0;
                }
            }

            doSetCurrentHp(amount, true);
        }

        if ((player.getCurrentHp() < 0.5) && !isHPConsumption) {
            player.abortAttack();
            player.abortCast();

            if (player.isInOlympiadMode()) {
                stopRegeneration();
                player.setDead(true);
                player.setIsPendingRevive(true);
                if (player.hasSummon()) {
                    player.getSummon().getAI().setIntentionIdle();
                }
                return;
            }

            player.doDie(attacker);
            if (!PlayerConfig.DISABLE_TUTORIAL) {
                final QuestState qs = player.getQuestState("Q00255_Tutorial");
                if (qs != null) {
                    qs.getQuest().notifyEvent("CE30", null, player);
                }
            }
        }
    }

    @Override
    protected boolean doSetCurrentHp(double newHp, boolean broadcastPacket) {
        final boolean result = super.doSetCurrentHp(newHp, broadcastPacket);
        final Player player = player();
        if (!PlayerConfig.DISABLE_TUTORIAL
                && (getCurrentHp() <= (player.getStat().getMaxHp() * .3))) {
            final QuestState qs = player.getQuestState("Q00255_Tutorial");
            if (qs != null) {
                qs.getQuest().notifyEvent("CE45", null, player);
            }
        }

        return result;
    }

    @Override
    public void setCurrentCp(double newCp) {
        WorldPartition.checkOwner();
        setCurrentCp(newCp, true);
    }

    public void setCurrentCp(double value, boolean broadcastPacket) {
        WorldPartition.checkOwner();
        final int currentCp = (int) this.currentCp;
        final Player player = player();
        final int maxCp = player.getStat().getMaxCp();

        if (player.isDead()) {
            return;
        }

        final double newCp = Math.max(0, value);
        if (newCp >= maxCp) {
            this.currentCp = maxCp;
            flagsRegenActive &= ~REGEN_FLAG_CP;

            if (flagsRegenActive == 0) {
                stopRegeneration();
            }
        } else {
            this.currentCp = newCp;
            flagsRegenActive |= REGEN_FLAG_CP;

            startRegeneration();
        }

        if ((currentCp != this.currentCp) && broadcastPacket) {
            player.broadcastStatusUpdate();
        }
    }

    @Override
    protected void doRegeneration() {
        final Player player = player();
        final PlayerStat stat = player.getStat();

        if (currentCp < stat.getMaxRecoverableCp()) {
            setCurrentCp(currentCp + Formulas.calcCpRegen(player), false);
        }

        if (getCurrentHp() < stat.getMaxRecoverableHp()) {
            setCurrentHp(getCurrentHp() + Formulas.calcHpRegen(player), false);
        }

        if (getCurrentMp() < stat.getMaxRecoverableMp()) {
            setCurrentMp(getCurrentMp() + Formulas.calcMpRegen(player), false);
        }

        player.broadcastStatusUpdate();
    }

    @Override
    public Player getActiveChar() {
        WorldPartition.checkOwner();
        return player();
    }

    private Player player() {
        return (Player) creature();
    }
}
