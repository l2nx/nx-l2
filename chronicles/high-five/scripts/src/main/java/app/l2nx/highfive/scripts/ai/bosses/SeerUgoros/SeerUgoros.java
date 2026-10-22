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
package app.l2nx.highfive.scripts.ai.bosses.SeerUgoros;

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcSay;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.scripts.quests.Q00288_HandleWithCare.Q00288_HandleWithCare;
import app.l2nx.highfive.scripts.quests.Q00423_TakeYourBestShot.Q00423_TakeYourBestShot;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author RobikBobik
 */
public class SeerUgoros extends Script {
    // NPCs
    private static final int SEER_UGOROS = 18863;
    private static final int BATRACOS = 32740;
    private static final int WEED_ID = 18867;

    // Items
    private static final int SEER_UGOROS_PASS = 15496;
    private static final int HIGH_GRADE_LIZARD_SCALE = 15497;
    private static final int MIDDLE_GRADE_LIZARD_SCALE = 15498;

    // Skill
    private static final Skill UGOROS_SKILL = SkillData.getInstance().getSkill(6426, 1);

    // Locations
    private static final Location UGOROS_SPAWN_LOCATION = new Location(96804, 85604, -3720, 34360);
    private static final Location BATRACOS_SPAWN_LOCATION = new Location(96782, 85918, -3720, 34360);
    private static final Location ENTER_LOCATION = new Location(95984, 85692, -3720);
    private static final Location EXIT_LOCATION = new Location(94763, 83562, -3425);

    // State
    private static final byte ALIVE = 0;
    private static final byte FIGHTING = 1;
    private static final byte DEAD = 2;

    // Misc
    protected static byte state = DEAD;
    protected static boolean weedAttack = false;
    private static boolean weedKilledByPlayer = false;
    private static boolean killedOneWeed = false;
    protected static Attackable weed = null;
    protected static Attackable ugoros = null;
    protected static Player attacker = null;
    protected static ScheduledFuture<?> thinkTask = null;

    private SeerUgoros() {
        addStartNpc(BATRACOS);
        addTalkId(BATRACOS);
        addKillId(SEER_UGOROS);
        addAttackId(WEED_ID);

        startQuestTimer("ugoros_respawn", 60000, null, null);
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        switch (event) {
            case "ugoros_respawn": {
                if (ugoros == null) {
                    ugoros = addSpawn(SEER_UGOROS, UGOROS_SPAWN_LOCATION, false, 0)
                            .asAttackable();
                    state = ALIVE;
                    broadcastInRegion(
                            ugoros,
                            NpcStringId
                                    .LISTEN_OH_TANTAS_I_HAVE_RETURNED_THE_PROPHET_YUGOROS_OF_THE_BLACK_ABYSS_IS_WITH_ME_SO_DO_NOT_BE_AFRAID);
                    startQuestTimer("ugoros_shout", 120000, null, null);
                }
                break;
            }
            case "ugoros_shout": {
                if (state == FIGHTING) {
                    if (attacker == null) {
                        state = ALIVE;
                    } else if ((ugoros == null) || (attacker.calculateDistance2D(ugoros) > 2000)) {
                        state = ALIVE;
                        attacker = null;
                    }
                } else if (state == ALIVE) {
                    broadcastInRegion(
                            ugoros, NpcStringId.LISTEN_OH_TANTAS_THE_BLACK_ABYSS_IS_FAMISHED_FIND_SOME_FRESH_OFFERINGS);
                }

                startQuestTimer("ugoros_shout", 120000, null, null);
                break;
            }
            case "ugoros_attack": {
                if (attacker != null) {
                    changeAttackTarget(attacker);
                    final NpcSay packet = new NpcSay(
                            ugoros.getObjectId(),
                            ChatType.NPC_GENERAL,
                            ugoros.getId(),
                            NpcStringId
                                    .WELCOME_S1_LET_US_SEE_IF_YOU_HAVE_BROUGHT_A_WORTHY_OFFERING_FOR_THE_BLACK_ABYSS);
                    packet.addStringParameter(attacker.getName());
                    ugoros.broadcastPacket(packet);
                    if (thinkTask != null) {
                        thinkTask.cancel(true);
                    }

                    thinkTask = WorldPartition.loop()
                            .scheduleAtFixedRate(new ThinkTask(), 1000, 3000, TimeUnit.MILLISECONDS);
                }
                break;
            }
            case "weed_check": {
                if (weedAttack && (ugoros != null) && (weed != null)) {
                    if (weed.isDead() && !weedKilledByPlayer) {
                        killedOneWeed = true;
                        weed = null;
                        weedAttack = false;
                        ugoros.getStatus().setCurrentHp(ugoros.getStatus().getCurrentHp() + (ugoros.getMaxHp() * 0.2));
                        ugoros.broadcastPacket(
                                new NpcSay(
                                        ugoros.getObjectId(),
                                        ChatType.NPC_GENERAL,
                                        ugoros.getId(),
                                        NpcStringId
                                                .WHAT_A_FORMIDABLE_FOE_BUT_I_HAVE_THE_ABYSS_WEED_GIVEN_TO_ME_BY_THE_BLACK_ABYSS_LET_ME_SEE));
                    } else {
                        startQuestTimer("weed_check", 2000, null, null);
                    }
                } else {
                    weed = null;
                    weedAttack = false;
                }
                break;
            }
            case "ugoros_expel": {
                if (attacker != null) {
                    attacker.teleToLocation(EXIT_LOCATION);
                    attacker = null;
                }
                break;
            }
            case "teleportInside": {
                if ((player != null) && (state == ALIVE)) {
                    if (hasAtLeastOneQuestItem(player, SEER_UGOROS_PASS)) {
                        state = FIGHTING;
                        attacker = player;
                        killedOneWeed = false;
                        takeItems(player, SEER_UGOROS_PASS, 1);
                        player.teleToLocation(ENTER_LOCATION);
                        startQuestTimer("ugoros_attack", 2000, null, null);

                        final QuestState qs = player.getQuestState(Q00288_HandleWithCare.class.getSimpleName());
                        if (qs != null) {
                            qs.set("drop", "1");
                        }
                    } else {
                        final QuestState qs = player.getQuestState(Q00423_TakeYourBestShot.class.getSimpleName());
                        if (qs == null) {
                            return "<html><body>Gatekeeper Batracos:<br>You look too inexperienced to make a journey to see Tanta Seer Ugoros. If you can convince Chief Investigator Johnny that you should go, then I will let you pass. Johnny has been everywhere and done everything. He may not be of my people but he has my respect, and anyone who has his will in turn have mine as well.<br></body></html>";
                        }

                        return "<html><body>Gatekeeper Batracos:<br>Tanta Seer Ugoros is hard to find. You'll just have to keep looking.<br></body></html>";
                    }
                } else {
                    return "<html><body>Gatekeeper Batracos:<br>Tanta Seer Ugoros is hard to find. You'll just have to keep looking.<br></body></html>";
                }
                break;
            }
            case "teleport_back": {
                if (player != null) {
                    player.teleToLocation(EXIT_LOCATION);
                    attacker = null;
                }
                break;
            }
        }

        return super.onEvent(event, npc, player);
    }

    @Override
    public void onAttack(Npc npc, Player attacker, int damage, boolean isPet) {
        if (npc.isDead()) {
            return;
        }

        if ((ugoros != null) && (weed != null) && npc.equals(weed)) {
            // Reset weed
            weed = null;

            // Reset attack state
            weedAttack = false;

            // Set it
            weedKilledByPlayer = true;

            // Complain
            ugoros.broadcastPacket(new NpcSay(
                    ugoros.getObjectId(),
                    ChatType.NPC_GENERAL,
                    ugoros.getId(),
                    NpcStringId.NO_HOW_DARE_YOU_STOP_ME_FROM_USING_THE_ABYSS_WEED_DO_YOU_KNOW_WHAT_YOU_HAVE_DONE));

            // Cancel current think-task
            if (thinkTask != null) {
                thinkTask.cancel(true);
            }

            // Re-setup task to re-think attack again
            thinkTask = WorldPartition.loop().scheduleAtFixedRate(new ThinkTask(), 500, 3000, TimeUnit.MILLISECONDS);
        }

        npc.doDie(attacker);
    }

    @Override
    public void onKill(Npc npc, Player killer, boolean isSummon) {
        if (thinkTask != null) {
            thinkTask.cancel(true);
            thinkTask = null;
        }

        state = DEAD;
        broadcastInRegion(ugoros, NpcStringId.AH_HOW_COULD_I_LOSE_OH_BLACK_ABYSS_RECEIVE_ME);
        ugoros = null;

        addSpawn(BATRACOS, BATRACOS_SPAWN_LOCATION, false, 60000);

        startQuestTimer("ugoros_expel", 50000, null, null);
        startQuestTimer("ugoros_respawn", 60000, null, null);

        final QuestState qs = killer.getQuestState(Q00288_HandleWithCare.class.getSimpleName());
        if ((qs != null) && qs.isCond(1) && (qs.getInt("drop") == 1)) {
            if (killedOneWeed) {
                giveItems(killer, MIDDLE_GRADE_LIZARD_SCALE, 1);
                qs.setCond(2, true);
            } else {
                giveItems(killer, HIGH_GRADE_LIZARD_SCALE, 1);
                qs.setCond(3, true);
            }

            qs.unset("drop");
        }
    }

    private void broadcastInRegion(Npc npc, NpcStringId npcString) {
        if (npc == null) {
            return;
        }

        final NpcSay npcSay = new NpcSay(npc.getObjectId(), ChatType.NPC_SHOUT, npc.getId(), npcString);
        World.forEachVisibleObjectInRange(npc, Player.class, 6000, player -> {
            player.sendPacket(npcSay);
        });
    }

    private class ThinkTask implements Runnable {
        public ThinkTask() {}

        @Override
        public void run() {
            if ((state == FIGHTING) && (attacker != null) && !attacker.isDead()) {
                if (weedAttack && (weed != null)) {
                    // Dummy, just wait.
                } else if (getRandom(10) < 6) {
                    weed = null;
                    World.forFirstVisibleObjectInRange(
                            ugoros,
                            Attackable.class,
                            2000,
                            attackable -> !attackable.isDead() && (attackable.getId() == WEED_ID),
                            attackable -> {
                                weedAttack = true;
                                weed = attackable;
                                changeAttackTarget(weed);
                                startQuestTimer("weed_check", 1000, null, null);
                            });

                    if (weed == null) {
                        changeAttackTarget(attacker);
                    }
                } else {
                    changeAttackTarget(attacker);
                }
            } else {
                state = ALIVE;
                attacker = null;
                if (thinkTask != null) {
                    thinkTask.cancel(true);
                    thinkTask = null;
                }
            }
        }
    }

    protected void changeAttackTarget(Creature attacker) {
        ugoros.getAI().setIntentionIdle();
        ugoros.clearAggroList();
        ugoros.setTarget(attacker);

        if (attacker instanceof Attackable) {
            weedKilledByPlayer = false;
            ugoros.disableSkill(UGOROS_SKILL, 100000);
            ugoros.setRunning();
            ugoros.addDamageHate(attacker, 0, Integer.MAX_VALUE);
        } else {
            ugoros.enableSkill(UGOROS_SKILL);
            ugoros.addDamageHate(attacker, 0, 99);
            ugoros.setWalking();
        }

        ugoros.getAI().setIntentionAttack(attacker);
    }

    public static void main(String[] args) {
        new SeerUgoros();
    }
}
