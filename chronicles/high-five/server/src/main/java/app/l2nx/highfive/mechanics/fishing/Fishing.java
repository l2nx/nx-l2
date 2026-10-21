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
package app.l2nx.highfive.mechanics.fishing;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.FishingMonstersData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.managers.FishingChampionshipManager;
import app.l2nx.highfive.mechanics.script.Quest;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExFishingHpRegen;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExFishingStartCombat;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.concurrent.Future;

public class Fishing implements Runnable {
    private Player fisher;
    private int time;
    private int stop = 0;
    private int goodUse = 0;
    private int anim = 0;
    private int mode = 0;
    private int deceptiveMode = 0;
    private Future<?> fishAiTask;
    private boolean thinking;

    // Fish datas
    private final int fishId;
    private final int fishMaxHp;
    private int fishCurHp;
    private final double regenHp;
    private final boolean upperGrade;
    private final int lureId;

    @Override
    public void run() {
        if (fisher == null) {
            return;
        }

        if (fishCurHp >= (fishMaxHp * 2)) {
            // The fish got away
            fisher.sendPacket(SystemMessageId.YOUR_BAIT_WAS_STOLEN_BY_THAT_FISH);
            doDie(false);
        } else if (time <= 0) {
            // Time is up, so that fish got away
            fisher.sendPacket(SystemMessageId.THAT_FISH_IS_MORE_DETERMINED_THAN_YOU_ARE_IT_SPIT_THE_HOOK);
            doDie(false);
        } else {
            aiTask();
        }
    }

    public Fishing(Player fisher, Fish fish, boolean isNoob, boolean isUpperGrade, int lureId) {
        this.fisher = fisher;
        fishMaxHp = fish.getFishHp();
        fishCurHp = fishMaxHp;
        regenHp = fish.getHpRegen();
        fishId = fish.getItemId();
        time = fish.getCombatDuration();
        upperGrade = isUpperGrade;
        this.lureId = lureId;
        final int lureType;
        if (isUpperGrade) {
            deceptiveMode = (ClockBridge.random().nextInt(100) >= 90) ? 1 : 0;
            lureType = 2;
        } else {
            deceptiveMode = 0;
            lureType = isNoob ? 0 : 1;
        }

        mode = (ClockBridge.random().nextInt(100) >= 80) ? 1 : 0;
        this.fisher.broadcastPacket(
                new ExFishingStartCombat(this.fisher, time, fishMaxHp, mode, lureType, deceptiveMode));
        this.fisher.sendPacket(new PlaySound(1, "SF_S_01", 0, 0, 0, 0, 0));

        // Succeeded in getting a bite
        this.fisher.sendPacket(SystemMessageId.YOU_VE_GOT_A_BITE);

        if (fishAiTask == null) {
            fishAiTask = ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
        }
    }

    public void changeHp(int hp, int pen) {
        fishCurHp -= hp;
        if (fishCurHp < 0) {
            fishCurHp = 0;
        }

        fisher.broadcastPacket(new ExFishingHpRegen(fisher, time, fishCurHp, mode, goodUse, anim, pen, deceptiveMode));
        anim = 0;
        if (fishCurHp > (fishMaxHp * 2)) {
            fishCurHp = fishMaxHp * 2;
            doDie(false);
        } else if (fishCurHp == 0) {
            doDie(true);
        }
    }

    public synchronized void doDie(boolean win) {
        if (fishAiTask != null) {
            fishAiTask.cancel(false);
            fishAiTask = null;
        }

        if (fisher == null) {
            return;
        }

        if (win) {
            final FishingMonster fishingMonster =
                    FishingMonstersData.getInstance().getFishingMonster(fisher.getLevel());
            if (fishingMonster != null) {
                if (ClockBridge.random().nextInt(100) <= fishingMonster.getProbability()) {
                    fisher.sendPacket(
                            SystemMessageId.YOU_CAUGHT_SOMETHING_SMELLY_AND_SCARY_MAYBE_YOU_SHOULD_THROW_IT_BACK);
                    final Npc monster = Quest.addSpawn(fishingMonster.getFishingMonsterId(), fisher);
                    monster.setTarget(fisher);
                } else {
                    fisher.sendPacket(SystemMessageId.YOU_CAUGHT_SOMETHING);
                    fisher.addItem(ItemProcessType.PICKUP, fishId, 1, null, true);
                    FishingChampionshipManager.getInstance().newFish(fisher, lureId);
                }
            }
        }

        fisher.endFishing(win);
        fisher = null;
    }

    protected void aiTask() {
        if (thinking) {
            return;
        }

        thinking = true;
        time--;

        try {
            // The fish state shown to the player must follow the mode alone.
            // Including the deceptive mode here would match the reversed actions and hide the deception.
            if (mode == 1) {
                fishCurHp += (int) regenHp;
            }

            if (stop == 0) {
                stop = 1;
                int check = ClockBridge.random().nextInt(100);
                if (check >= 70) {
                    mode = mode == 0 ? 1 : 0;
                }

                if (upperGrade) {
                    check = ClockBridge.random().nextInt(100);
                    if (check >= 90) {
                        deceptiveMode = deceptiveMode == 0 ? 1 : 0;
                    }
                }
            } else {
                stop--;
            }
        } finally {
            thinking = false;
            final ExFishingHpRegen efhr =
                    new ExFishingHpRegen(fisher, time, fishCurHp, mode, 0, anim, 0, deceptiveMode);
            if (anim != 0) {
                fisher.broadcastPacket(efhr);
            } else {
                fisher.sendPacket(efhr);
            }
        }
    }

    public void useReeling(int dmg, int pen) {
        anim = 2;
        if (ClockBridge.random().nextInt(100) > 90) {
            fisher.sendPacket(SystemMessageId.THE_FISH_HAS_RESISTED_YOUR_ATTEMPT_TO_BRING_IT_IN);
            goodUse = 0;
            changeHp(0, pen);
            return;
        }

        if (fisher == null) {
            return;
        }

        if (mode == 1) {
            if (deceptiveMode == 0) {
                // Reeling is successful, Damage: $s1
                SystemMessage sm = new SystemMessage(SystemMessageId.YOU_REEL_THAT_FISH_IN_CLOSER_AND_CAUSE_S1_DAMAGE);
                sm.addInt(dmg);
                fisher.sendPacket(sm);
                if (pen > 0) {
                    sm = new SystemMessage(SystemMessageId.YOUR_REELING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
                    sm.addInt(pen);
                    fisher.sendPacket(sm);
                }

                goodUse = 1;
                changeHp(dmg, pen);
            } else {
                // Reeling failed, Damage: $s1
                final SystemMessage sm =
                        new SystemMessage(SystemMessageId.YOU_FAILED_TO_REEL_THAT_FISH_IN_FURTHER_AND_IT_REGAINS_S1_HP);
                sm.addInt(dmg);
                fisher.sendPacket(sm);
                goodUse = 2;
                changeHp(-dmg, pen);
            }
        } else if (deceptiveMode == 0) {
            // Reeling failed, Damage: $s1
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.YOU_FAILED_TO_REEL_THAT_FISH_IN_FURTHER_AND_IT_REGAINS_S1_HP);
            sm.addInt(dmg);
            fisher.sendPacket(sm);
            goodUse = 2;
            changeHp(-dmg, pen);
        } else {
            // Reeling is successful, Damage: $s1
            SystemMessage sm = new SystemMessage(SystemMessageId.YOU_REEL_THAT_FISH_IN_CLOSER_AND_CAUSE_S1_DAMAGE);
            sm.addInt(dmg);
            fisher.sendPacket(sm);
            if (pen > 0) {
                sm = new SystemMessage(SystemMessageId.YOUR_REELING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
                sm.addInt(pen);
                fisher.sendPacket(sm);
            }

            goodUse = 1;
            changeHp(dmg, pen);
        }
    }

    public void usePumping(int dmg, int pen) {
        anim = 1;
        if (ClockBridge.random().nextInt(100) > 90) {
            fisher.sendPacket(SystemMessageId.THE_FISH_HAS_RESISTED_YOUR_ATTEMPT_TO_BRING_IT_IN);
            goodUse = 0;
            changeHp(0, pen);
            return;
        }

        if (fisher == null) {
            return;
        }

        if (mode == 0) {
            if (deceptiveMode == 0) {
                // Pumping is successful. Damage: $s1
                SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_IS_SUCCESSFUL_CAUSING_S1_DAMAGE);
                sm.addInt(dmg);
                fisher.sendPacket(sm);
                if (pen > 0) {
                    sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
                    sm.addInt(pen);
                    fisher.sendPacket(sm);
                }

                goodUse = 1;
                changeHp(dmg, pen);
            } else {
                // Pumping failed, Regained: $s1
                final SystemMessage sm =
                        new SystemMessage(SystemMessageId.YOU_FAILED_TO_DO_ANYTHING_WITH_THE_FISH_AND_IT_REGAINS_S1_HP);
                sm.addInt(dmg);
                fisher.sendPacket(sm);
                goodUse = 2;
                changeHp(-dmg, pen);
            }
        } else if (deceptiveMode == 0) {
            // Pumping failed, Regained: $s1
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.YOU_FAILED_TO_DO_ANYTHING_WITH_THE_FISH_AND_IT_REGAINS_S1_HP);
            sm.addInt(dmg);
            fisher.sendPacket(sm);
            goodUse = 2;
            changeHp(-dmg, pen);
        } else {
            // Pumping is successful. Damage: $s1
            SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_IS_SUCCESSFUL_CAUSING_S1_DAMAGE);
            sm.addInt(dmg);
            fisher.sendPacket(sm);
            if (pen > 0) {
                sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
                sm.addInt(pen);
                fisher.sendPacket(sm);
            }

            goodUse = 1;
            changeHp(dmg, pen);
        }
    }
}
