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
package app.l2nx.highfive.managers.games;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.holders.player.ArenaParticipantsHolder;
import app.l2nx.highfive.entity.actor.instance.Block;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.HandysBlockCheckerManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExBasicActionList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCubeGameChangePoints;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCubeGameCloseUI;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCubeGameEnd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCubeGameExtendedChangePoints;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RelationChanged;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author BiggBoss
 */
@Slf4j
public class BlockCheckerManager {

    // The object which holds all basic members info.
    @Getter
    protected ArenaParticipantsHolder holder;

    // Maps to hold player of each team and his points.
    protected Map<Player, Integer> redTeamPoints = new ConcurrentHashMap<>();
    protected Map<Player, Integer> blueTeamPoints = new ConcurrentHashMap<>();

    // The initial points of the event.
    protected int redPoints = 15;
    protected int bluePoints = 15;

    // Current used arena
    @Getter
    protected int arena = -1;

    // All blocks
    protected Collection<Spawn> spawns = ConcurrentHashMap.newKeySet();

    // Sets if the red team won the event at the end of this (used for packets).
    protected boolean redWinner;

    // Time when the event starts. Used on packet sending.
    @Getter
    protected long starterTime;

    // The needed arena coordinates
    // Arena X: team1X, team1Y, team2X, team2Y, ArenaCenterX, ArenaCenterY
    protected static final int[][] arenaCoordinates = {
        // Arena 0 - Team 1 XY, Team 2 XY - CENTER XY
        {-58368, -62745, -57751, -62131, -58053, -62417},
        // Arena 1 - Team 1 XY, Team 2 XY - CENTER XY
        {-58350, -63853, -57756, -63266, -58053, -63551},
        // Arena 2 - Team 1 XY, Team 2 XY - CENTER XY
        {-57194, -63861, -56580, -63249, -56886, -63551},
        // Arena 3 - Team 1 XY, Team 2 XY - CENTER XY
        {-57200, -62727, -56584, -62115, -56850, -62391}
    };

    // Common z coordinate
    private static final int Z_COORD = -2405;

    // List of dropped items in event (for later deletion).
    protected Collection<Item> drops = ConcurrentHashMap.newKeySet();

    // Default arena
    private static final byte DEFAULT_ARENA = -1;

    // Event is started
    @Getter
    protected boolean started = false;

    // Event end
    protected ScheduledFuture<?> task;

    // Preserve from exploit reward by logging out.
    protected boolean abnormalEnd = false;

    public BlockCheckerManager(ArenaParticipantsHolder holder, int arena) {
        this.holder = holder;
        if ((arena > -1) && (arena < 4)) {
            this.arena = arena;
        }

        for (Player player : holder.getRedPlayers()) {
            redTeamPoints.put(player, 0);
        }

        for (Player player : holder.getBluePlayers()) {
            blueTeamPoints.put(player, 0);
        }
    }

    /**
     * Updates the player holder before the event starts to synchronize all info
     * @param holder
     */
    public void updatePlayersOnStart(ArenaParticipantsHolder holder) {
        this.holder = holder;
    }

    /**
     * Returns the current red team points
     * @return int
     */
    public int getRedPoints() {
        synchronized (this) {
            return redPoints;
        }
    }

    /**
     * Returns the current blue team points
     * @return int
     */
    public int getBluePoints() {
        synchronized (this) {
            return bluePoints;
        }
    }

    /**
     * Returns the player points
     * @param player
     * @param isRed
     * @return int
     */
    public int getPlayerPoints(Player player, boolean isRed) {
        if (!redTeamPoints.containsKey(player) && !blueTeamPoints.containsKey(player)) {
            return 0;
        }

        if (isRed) {
            return redTeamPoints.get(player);
        }

        return blueTeamPoints.get(player);
    }

    /**
     * Increases player points for his teams
     * @param player
     * @param team
     */
    public synchronized void increasePlayerPoints(Player player, int team) {
        if (player == null) {
            return;
        }

        if (team == 0) {
            final int points = redTeamPoints.get(player) + 1;
            redTeamPoints.put(player, points);
            redPoints++;
            bluePoints--;
        } else {
            final int points = blueTeamPoints.get(player) + 1;
            blueTeamPoints.put(player, points);
            bluePoints++;
            redPoints--;
        }
    }

    /**
     * Will add a new drop into the list of dropped items
     * @param item
     */
    public void addNewDrop(Item item) {
        if (item != null) {
            drops.add(item);
        }
    }

    /**
     * Will send all packets for the event members with the relation info
     * @param plr
     */
    protected void broadcastRelationChanged(Player plr) {
        for (Player p : holder.getAllPlayers()) {
            p.sendPacket(new RelationChanged(plr, plr.getRelation(p), plr.isAutoAttackable(p)));
        }
    }

    /**
     * Called when a there is an empty team. The event will end.
     */
    public void endEventAbnormally() {
        try {
            synchronized (this) {
                started = false;
                if (task != null) {
                    task.cancel(true);
                }

                abnormalEnd = true;
                ThreadPool.execute(new EndEvent());
            }
        } catch (Exception e) {
            log.error("Could not end Block Checker event at {}", arena, e);
        }
    }

    /**
     * This inner class set ups all player and arena parameters to start the event
     */
    public class StartEvent implements Runnable {
        // In event used skills
        private final Skill freeze;
        private final Skill transformationRed;
        private final Skill transformationBlue;

        // Common and unparametizer packet
        private final ExCubeGameCloseUI closeUserInterface = new ExCubeGameCloseUI();

        public StartEvent() {
            // Initialize all used skills.
            freeze = SkillData.getInstance().getSkill(6034, 1);
            transformationRed = SkillData.getInstance().getSkill(6035, 1);
            transformationBlue = SkillData.getInstance().getSkill(6036, 1);
        }

        /**
         * Will set up all player parameters and port them to their respective location based on their teams
         */
        private void setUpPlayers() {
            // Set current arena as being used.
            HandysBlockCheckerManager.getInstance().setArenaBeingUsed(arena);

            // Initialize packets avoiding create a new one per player.
            redPoints = spawns.size() / 2;
            bluePoints = spawns.size() / 2;
            final ExCubeGameChangePoints initialPoints = new ExCubeGameChangePoints(300, bluePoints, redPoints);
            ExCubeGameExtendedChangePoints clientSetUp;
            for (Player player : holder.getAllPlayers()) {
                if (player == null) {
                    continue;
                }

                // Send the secret client packet set up.
                final boolean isRed = holder.getRedPlayers().contains(player);
                clientSetUp = new ExCubeGameExtendedChangePoints(300, bluePoints, redPoints, isRed, player, 0);
                player.sendPacket(clientSetUp);

                player.sendPacket(ActionFailed.STATIC_PACKET);

                // Teleport Player - Array access.
                // Team 0 * 2 = 0; 0 = 0, 0 + 1 = 1.
                // Team 1 * 2 = 2; 2 = 2, 2 + 1 = 3
                final int tc = holder.getPlayerTeam(player) * 2;

                // Get x and y coordinates.
                final int x = arenaCoordinates[arena][tc];
                final int y = arenaCoordinates[arena][tc + 1];
                player.teleToLocation(x, y, Z_COORD);

                // Set the player team.
                if (isRed) {
                    redTeamPoints.put(player, 0);
                    player.setTeam(Team.RED);
                } else {
                    blueTeamPoints.put(player, 0);
                    player.setTeam(Team.BLUE);
                }

                player.stopAllEffects();

                if (player.hasSummon()) {
                    player.getSummon().unSummon(player);
                }

                // Give the player start up effects.
                // Freeze
                freeze.applyEffects(player, player);

                // Transformation
                if (holder.getPlayerTeam(player) == 0) {
                    transformationRed.applyEffects(player, player);
                } else {
                    transformationBlue.applyEffects(player, player);
                }

                // Set the current player arena.
                player.setBlockCheckerArena((byte) arena);
                player.setInsideZone(ZoneId.PVP, true);

                // Send needed packets
                player.sendPacket(initialPoints);
                player.sendPacket(closeUserInterface);

                // ExBasicActionList
                player.sendPacket(ExBasicActionList.STATIC_PACKET);
                broadcastRelationChanged(player);
            }
        }

        @Override
        public void run() {
            // Wrong arena passed, stop event.
            if (arena == -1) {
                log.error("Could not set up the arena Id for the Block Checker event, cancelling event...");
                return;
            }

            started = true;

            // Spawn the blocks
            ThreadPool.execute(new SpawnRound(16, 1));

            // Start up player parameters.
            setUpPlayers();

            // Set the started time.
            starterTime = ClockBridge.gameMillis() + 300000;
        }
    }

    /**
     * This class spawns the second round of boxes and schedules the event end
     */
    private class SpawnRound implements Runnable {
        int numOfBoxes;
        int round;

        SpawnRound(int numberOfBoxes, int round) {
            numOfBoxes = numberOfBoxes;
            this.round = round;
        }

        @Override
        public void run() {
            if (!started) {
                return;
            }

            switch (round) {
                case 1: // Schedule second spawn round
                {
                    task = ThreadPool.schedule(new SpawnRound(20, 2), 60000);
                    break;
                }
                case 2: // Schedule third spawn round
                {
                    task = ThreadPool.schedule(new SpawnRound(14, 3), 60000);
                    break;
                }
                case 3: // Schedule Event End Count Down
                {
                    task = ThreadPool.schedule(new EndEvent(), 180000);
                    break;
                }
            }

            // random % 2, if == 0 will spawn a red block
            // if != 0, will spawn a blue block
            byte random = 2;

            // Spawn blocks
            try {
                // Creates 50 new blocks
                for (int i = 0; i < numOfBoxes; i++) {
                    final Spawn spawn = new Spawn(18672);
                    spawn.setXYZ(
                            arenaCoordinates[arena][4] + ClockBridge.random().nextInt(-400, 401),
                            arenaCoordinates[arena][5] + ClockBridge.random().nextInt(-400, 401),
                            Z_COORD);
                    spawn.setAmount(1);
                    spawn.setHeading(1);
                    spawn.setRespawnDelay(1);
                    SpawnTable.getInstance().addSpawn(spawn);
                    spawn.init();
                    final Block block = (Block) spawn.getLastSpawn();

                    // switch color
                    block.setRed((random % 2) == 0);
                    block.disableCoreAI(true);
                    spawns.add(spawn);
                    random++;
                }
            } catch (Exception e) {
                log.warn("{}", e.getMessage());
            }

            // Spawn the block carrying girl.
            if ((round == 1) || (round == 2)) {
                try {
                    final Spawn girlSpawn = new Spawn(18676);
                    girlSpawn.setXYZ(
                            arenaCoordinates[arena][4] + ClockBridge.random().nextInt(-400, 401),
                            arenaCoordinates[arena][5] + ClockBridge.random().nextInt(-400, 401),
                            Z_COORD);
                    girlSpawn.setAmount(1);
                    girlSpawn.setHeading(1);
                    girlSpawn.setRespawnDelay(1);
                    SpawnTable.getInstance().addSpawn(girlSpawn);
                    girlSpawn.init();

                    // Schedule his deletion after 9 secs of spawn.
                    ThreadPool.schedule(new CarryingGirlUnspawn(girlSpawn), 9000);
                } catch (Exception e) {
                    log.warn("Could not Spawn Block Checker NPCs! Wrong instance type at npc table?");
                    log.warn("{}", e.getMessage());
                }
            }

            redPoints += numOfBoxes / 2;
            bluePoints += numOfBoxes / 2;
            holder.broadCastPacketToTeam(new ExCubeGameChangePoints(
                    (int) ((starterTime - ClockBridge.gameMillis()) / 1000), getBluePoints(), getRedPoints()));
        }
    }

    private class CarryingGirlUnspawn implements Runnable {
        private final Spawn spawn;

        protected CarryingGirlUnspawn(Spawn spawn) {
            this.spawn = spawn;
        }

        @Override
        public void run() {
            if (spawn == null) {
                log.warn("HBCE: Block Carrying Girl is null");
                return;
            }

            SpawnTable.getInstance().removeSpawn(spawn);
            spawn.stopRespawn();
            spawn.getLastSpawn().deleteMe();
        }
    }

    /*
     * private class CountDown implements Runnable {
     * @Override public void run() { holder.broadCastPacketToTeam(new SystemMessage(SystemMessageId.BLOCK_CHECKER_ENDS_5)); ThreadPoolManager.schedule(new EndEvent(), 5000); } }
     */

    /**
     * This class erase all event parameters on player and port them back near Handy. Also, unspawn blocks, runs a garbage collector and set as free the used arena
     */
    protected class EndEvent implements Runnable {
        // Garbage collector and arena free setter.
        private void clearMe() {
            HandysBlockCheckerManager.getInstance().clearPaticipantQueueByArenaId(arena);
            holder.clearPlayers();
            blueTeamPoints.clear();
            redTeamPoints.clear();
            HandysBlockCheckerManager.getInstance().setArenaFree(arena);

            for (Spawn spawn : spawns) {
                spawn.stopRespawn();
                spawn.getLastSpawn().deleteMe();
                SpawnTable.getInstance().removeSpawn(spawn);
            }

            spawns.clear();

            for (Item item : drops) {
                // npe
                if (item == null) {
                    continue;
                }

                // a player has it, it will be deleted later
                if (!item.isSpawned() || (item.getOwnerId() != 0)) {
                    continue;
                }

                item.decayMe();
                // World.removeObject(item); // redundant - decayMe() already calls World.removeObject
            }

            drops.clear();
        }

        /**
         * Reward players after event. Tie - No Reward
         */
        private void rewardPlayers() {
            if (redPoints == bluePoints) {
                return;
            }

            redWinner = redPoints > bluePoints;
            if (redWinner) {
                rewardAsWinner(true);
                rewardAsLooser(false);
                final SystemMessage msg = new SystemMessage(SystemMessageId.THE_C1_TEAM_HAS_WON);
                msg.addString("Red Team");
                holder.broadCastPacketToTeam(msg);
            } else if (bluePoints > redPoints) {
                rewardAsWinner(false);
                rewardAsLooser(true);
                final SystemMessage msg = new SystemMessage(SystemMessageId.THE_C1_TEAM_HAS_WON);
                msg.addString("Blue Team");
                holder.broadCastPacketToTeam(msg);
            } else {
                rewardAsLooser(true);
                rewardAsLooser(false);
            }
        }

        /**
         * Reward the specified team as a winner team 1) Higher score - 8 extra 2) Higher score - 5 extra
         * @param isRed
         */
        private void rewardAsWinner(boolean isRed) {
            final Map<Player, Integer> tempPoints = isRed ? redTeamPoints : blueTeamPoints;

            // Main give
            for (Entry<Player, Integer> points : tempPoints.entrySet()) {
                if (points.getKey() == null) {
                    continue;
                }

                if (points.getValue() >= 10) {
                    points.getKey().addItem(ItemProcessType.QUEST, 13067, 2, points.getKey(), true);
                } else {
                    tempPoints.remove(points.getKey());
                }
            }

            int first = 0;
            int second = 0;
            Player winner1 = null;
            Player winner2 = null;
            for (Entry<Player, Integer> entry : tempPoints.entrySet()) {
                final Player pc = entry.getKey();
                final int pcPoints = entry.getValue();
                if (pcPoints > first) {
                    // Move old data
                    second = first;
                    winner2 = winner1;

                    // Set new data
                    first = pcPoints;
                    winner1 = pc;
                } else if (pcPoints > second) {
                    second = pcPoints;
                    winner2 = pc;
                }
            }

            if (winner1 != null) {
                winner1.addItem(ItemProcessType.QUEST, 13067, 8, winner1, true);
            }

            if (winner2 != null) {
                winner2.addItem(ItemProcessType.QUEST, 13067, 5, winner2, true);
            }
        }

        /**
         * Will reward the looser team with the predefined rewards Player got >= 10 points: 2 coins Player got < 10 points: 0 coins
         * @param isRed
         */
        private void rewardAsLooser(boolean isRed) {
            for (Entry<Player, Integer> entry : (isRed ? redTeamPoints : blueTeamPoints).entrySet()) {
                final Player player = entry.getKey();
                if ((player != null) && (entry.getValue() >= 10)) {
                    player.addItem(ItemProcessType.QUEST, 13067, 2, player, true);
                }
            }
        }

        /**
         * Teleport players back, give status back and send final packet
         */
        private void setPlayersBack() {
            final ExCubeGameEnd end = new ExCubeGameEnd(redWinner);
            for (Player player : holder.getAllPlayers()) {
                if (player == null) {
                    continue;
                }

                player.stopAllEffects();

                // Remove team aura
                player.setTeam(Team.NONE);

                // Set default arena
                player.setBlockCheckerArena(DEFAULT_ARENA);

                // Untransform.
                player.untransform();

                // Remove the event items.
                final PlayerInventory inv = player.getInventory();
                if (inv.getItemByItemId(13787) != null) {
                    inv.destroyItemByItemId(
                            ItemProcessType.DESTROY, 13787, inv.getInventoryItemCount(13787, 0), player, player);
                }

                if (inv.getItemByItemId(13788) != null) {
                    inv.destroyItemByItemId(
                            ItemProcessType.DESTROY, 13788, inv.getInventoryItemCount(13788, 0), player, player);
                }

                broadcastRelationChanged(player);

                // Teleport Back
                player.teleToLocation(-57478, -60367, -2370);
                player.setInsideZone(ZoneId.PVP, false);

                // Send end packet
                player.sendPacket(end);
                player.broadcastUserInfo();
            }
        }

        @Override
        public void run() {
            if (!abnormalEnd) {
                rewardPlayers();
            }

            setPlayersBack();
            clearMe();
            started = false;
            abnormalEnd = false;
        }
    }
}
