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
package app.l2nx.highfive.scripts.custom.events.Race;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.mechanics.script.Event;
import app.l2nx.highfive.mechanics.script.QuestState;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.serverpackets.CreatureSay;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Gnacik
 */
@Slf4j
public class Race extends Event {
    // Event NPCs list
    private final Set<Npc> npcs = ConcurrentHashMap.newKeySet();

    // Npc
    private Npc npc;

    // Player list
    private final Set<Player> players = ConcurrentHashMap.newKeySet();

    // Event Task
    ScheduledFuture<?> eventTask = null;

    // Event state
    private static boolean isactive = false;

    // Race state
    private static boolean raceStarted = false;

    // 5 min for register
    private static final int REGISTER_TIME = 5;

    // 5 min for race
    private static final int RACE_TIME = 10;

    // NPCs
    private static final int START_NPC = 900103;
    private static final int STOP_NPC = 900104;

    // Skills (Frog by default)
    private static int skill = 6201;

    // We must keep second NPC spawn for radar.
    private static int[] randspawn = null;

    // Locations
    private static final String[] locations = {
        "Heretic catacomb enterance", "Dion castle bridge", "Floran village enterance", "Floran fort gate"
    };

    // @formatter:off
    private static final int[][] coords = {
        // x, y, z, heading
        {39177, 144345, -3650, 0},
        {22294, 155892, -2950, 0},
        {16537, 169937, -3500, 0},
        {7644, 150898, -2890, 0}
    };
    private static final int[][] rewards = {
        {6622, 2}, // Giant's Codex
        {9625, 2}, // Giant's Codex -
        {9626, 2}, // Giant's Codex -
        {9627, 2}, // Giant's Codex -
        {9546, 5}, // Attr stones
        {9547, 5},
        {9548, 5},
        {9549, 5},
        {9550, 5},
        {9551, 5},
        {9574, 3}, // Mid-Grade Life Stone: level 80
        {9575, 2}, // High-Grade Life Stone: level 80
        {9576, 1}, // Top-Grade Life Stone: level 80
        {20034, 1} // Revita pop
    };
    // @formatter:on

    private Race() {
        addStartNpc(START_NPC);
        addFirstTalkId(START_NPC);
        addTalkId(START_NPC);
        addStartNpc(STOP_NPC);
        addFirstTalkId(STOP_NPC);
        addTalkId(STOP_NPC);

        loadSchedules("data/scripts/custom/events/Race/config.xml");
    }

    @Override
    public void onTimerEvent(String event, StatSet params, Npc npc, Player player) {
        if (event.startsWith(SCHEDULE_TIMER)) {
            eventStart(null);
            scheduleStart(event, params);
        }
    }

    @Override
    public boolean eventStart(Player eventMaker) {
        // Don't start event if it is active.
        if (isactive) {
            return false;
        }

        // Check Custom Table - we use custom NPCs.
        if (!GeneralConfig.CUSTOM_NPC_DATA) {
            log.info("{}: Event can't be started, because custom npc table is disabled!", getName());
            eventMaker.sendMessage("Event " + getName() + " can't be started because custom NPC table is disabled!");
            return false;
        }

        // Set Event active
        isactive = true;

        // Spawn Manager
        npc = recordSpawn(START_NPC, 18429, 145861, -3090, 35000, false, 0);

        // Announce event start
        World.broadcastToAllOnlinePlayers("* Race Event started! *");
        World.broadcastToAllOnlinePlayers("Visit Event Manager in Dion village and signup, you have " + REGISTER_TIME
                + " min before Race Start...");

        // Schedule Event end
        eventTask = ThreadPool.schedule(this::StartRace, REGISTER_TIME * 60 * 1000);
        return true;
    }

    protected void StartRace() {
        // Abort race if no players signup.
        if (players.isEmpty()) {
            World.broadcastToAllOnlinePlayers("Race aborted, nobody signup.");
            eventStop();
            return;
        }

        // Set state
        raceStarted = true;

        // Announce
        World.broadcastToAllOnlinePlayers("Race started!");

        // Get random Finish
        final int location = getRandom(0, locations.length - 1);
        randspawn = coords[location];

        // And spawn NPC
        recordSpawn(STOP_NPC, randspawn[0], randspawn[1], randspawn[2], randspawn[3], false, 0);

        // Transform players and send message.
        for (Player player : players) {
            if (player.isOnline()) {
                if (player.isInsideRadius2D(npc, 500)) {
                    sendMessage(
                            player,
                            "Race started! Go find Finish NPC as fast as you can... He is located near "
                                    + locations[location]);
                    transformPlayer(player);
                    player.getRadar().addMarker(randspawn[0], randspawn[1], randspawn[2]);
                } else {
                    sendMessage(
                            player, "I told you stay near me right? Distance was too high, you are excluded from race");
                    players.remove(player);
                }
            }
        }

        // Schedule timeup for Race.
        eventTask = ThreadPool.schedule(this::timeUp, RACE_TIME * 60 * 1000);
    }

    @Override
    public boolean eventStop() {
        // Don't stop inactive event.
        if (!isactive) {
            return false;
        }

        // Set inactive
        isactive = false;
        raceStarted = false;

        // Cancel task if any.
        if (eventTask != null) {
            eventTask.cancel(true);
            eventTask = null;
        }

        // Untransform players
        // Teleport to event start point.
        for (Player player : players) {
            if (player.isOnline()) {
                player.untransform();
                player.teleToLocation(this.npc.getX(), this.npc.getY(), this.npc.getZ(), true);
            }
        }

        players.clear();

        // Despawn NPCs
        for (Npc npc : npcs) {
            npc.deleteMe();
        }

        npcs.clear();

        // Announce event end
        World.broadcastToAllOnlinePlayers("* Race Event finished *");
        return true;
    }

    @Override
    public boolean eventBypass(Player player, String bypass) {
        if (bypass.startsWith("skill")) {
            if (raceStarted) {
                player.sendMessage("Race already started, you cannot change transform skill now");
            } else {
                final int number = Integer.parseInt(bypass.substring(5));
                final Skill skill = SkillData.getInstance().getSkill(number, 1);
                if (skill != null) {
                    Race.skill = number;
                    player.sendMessage("Transform skill set to:");
                    player.sendMessage(skill.getName());
                } else {
                    player.sendMessage("Error while changing transform skill");
                }
            }
        } else if (bypass.startsWith("tele")) {
            if ((Integer.parseInt(bypass.substring(4)) > 0) && (randspawn != null)) {
                player.teleToLocation(randspawn[0], randspawn[1], randspawn[2]);
            } else {
                player.teleToLocation(18429, 145861, -3090);
            }
        }

        showMenu(player);
        return true;
    }

    @Override
    public String onEvent(String event, Npc npc, Player player) {
        final String htmltext = event;
        final QuestState qs = getQuestState(player, false);
        if (qs == null) {
            return null;
        }

        if (event.equalsIgnoreCase("transform")) {
            transformPlayer(player);
            return null;
        } else if (event.equalsIgnoreCase("untransform")) {
            player.untransform();
            return null;
        } else if (event.equalsIgnoreCase("showfinish")) {
            player.getRadar().addMarker(randspawn[0], randspawn[1], randspawn[2]);
            return null;
        } else if (event.equalsIgnoreCase("signup")) {
            if (players.contains(player)) {
                return "900103-onlist.htm";
            }

            players.add(player);
            return "900103-signup.htm";
        } else if (event.equalsIgnoreCase("quit")) {
            player.untransform();
            players.remove(player);
            return "900103-quit.htm";
        } else if (event.equalsIgnoreCase("finish")) {
            if (player.isAffectedBySkill(skill)) {
                winRace(player);
                return "900104-winner.htm";
            }

            return "900104-notrans.htm";
        }

        return htmltext;
    }

    @Override
    public String onFirstTalk(Npc npc, Player player) {
        getQuestState(player, true);
        if (npc.getId() == START_NPC) {
            if (raceStarted) {
                return START_NPC + "-started-" + isRacing(player) + ".htm";
            }

            return START_NPC + "-" + isRacing(player) + ".htm";
        } else if ((npc.getId() == STOP_NPC) && raceStarted) {
            return STOP_NPC + "-" + isRacing(player) + ".htm";
        }

        return npc.getId() + ".htm";
    }

    private int isRacing(Player player) {
        return players.contains(player) ? 1 : 0;
    }

    private Npc recordSpawn(int npcId, int x, int y, int z, int heading, boolean randomOffSet, long despawnDelay) {
        final Npc npc = addSpawn(npcId, x, y, z, heading, randomOffSet, despawnDelay);
        npcs.add(npc);
        return npc;
    }

    private void transformPlayer(Player player) {
        if (player.isTransformed() || player.isInStance()) {
            player.untransform();
        }

        if (player.isSitting()) {
            player.standUp();
        }

        player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, AbnormalType.SPEED_UP);
        player.stopSkillEffects(SkillFinishType.REMOVED, 268);
        player.stopSkillEffects(SkillFinishType.REMOVED, 298); // Rabbit Spirit Totem
        SkillData.getInstance().getSkill(skill, 1).applyEffects(player, player);
    }

    private void sendMessage(Player player, String text) {
        player.sendPacket(new CreatureSay(npc, ChatType.BATTLEFIELD, npc.getName(), text));
    }

    private void showMenu(Player player) {
        final NpcHtmlMessage html = new NpcHtmlMessage();
        final String content = getHtm(player, "admin_menu.htm");
        html.setHtml(content);
        player.sendPacket(html);
    }

    protected void timeUp() {
        World.broadcastToAllOnlinePlayers("Time up, nobody wins!");
        eventStop();
    }

    private void winRace(Player player) {
        final int[] reward = rewards[getRandom(rewards.length - 1)];
        player.addItem(ItemProcessType.REWARD, reward[0], reward[1], npc, true);
        World.broadcastToAllOnlinePlayers(player.getName() + " is a winner!");
        eventStop();
    }

    public static void main(String[] args) {
        new Race();
    }
}
