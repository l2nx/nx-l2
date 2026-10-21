/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.managers.games;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.entity.zone.type.DerbyTrackZone;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.DeleteObject;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MonRaceInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.minigame.MonsterRaceRepository;
import app.l2nx.highfive.repository.minigame.model.MonsterRaceResult;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MonsterRaceManager {
    private static final int LANES = 8;

    private static volatile MonsterRaceManager instance;

    private final MonsterRaceRepository monsterRaceRepository;

    public enum RaceState {
        ACCEPTING_BETS,
        WAITING,
        STARTING_RACE,
        RACE_END
    }

    protected static final PlaySound SOUND_1 = new PlaySound(1, "S_Race", 0, 0, 0, 0, 0);
    protected static final PlaySound SOUND_2 = new PlaySound("ItemSound2.race_start");

    protected static final int[][] CODES = {
        {-1, 0},
        {0, 15322},
        {13765, -1}
    };

    protected final List<Integer> npcTemplates =
            new ArrayList<>(); // List holding npc templates, shuffled on a new race.

    @Getter
    protected final List<HistoryInfo> history = new ArrayList<>(); // List holding old race records.

    protected final Map<Integer, Long> betsPerLane =
            new ConcurrentHashMap<>(); // Map holding all bets for each lane ; values setted to 0 after every race.

    @Getter
    protected final List<Double> odds =
            new ArrayList<>(); // List holding sorted odds per lane ; cleared at new odds calculation.

    @Getter
    protected int raceNumber = 1;

    protected int finalCountdown = 0;

    @Getter
    protected RaceState currentRaceState = RaceState.RACE_END;

    @Getter
    protected MonRaceInfo racePacket;

    @Getter
    private final Npc[] monsters = new Npc[8];

    @Getter
    private int[][] speeds = new int[8][20];

    @Getter
    private int firstPlace; // Index going from 0-7.

    @Getter
    private int secondPlace; // Index going from 0-7.

    public MonsterRaceManager(MonsterRaceRepository monsterRaceRepository) {
        this.monsterRaceRepository = monsterRaceRepository;
        instance = this;
    }

    public void load() {
        if (!GeneralConfig.ALLOW_RACE) {
            return;
        }

        // Feed history with previous race results.
        loadHistory();

        // Feed betsPerLane with stored informations on bets.
        loadBets();

        // Feed npcTemplates, we will only have to shuffle it when needed.
        for (int i = 31003; i < 31027; i++) {
            npcTemplates.add(i);
        }

        ThreadPool.scheduleAtFixedRate(new Announcement(), 0, 1000);
    }

    public static class HistoryInfo {
        @Getter
        private final int raceId;

        @Getter
        @Setter
        private int first;

        @Getter
        @Setter
        private int second;

        @Getter
        @Setter
        private double oddRate;

        public HistoryInfo(int raceId, int first, int second, double oddRate) {
            this.raceId = raceId;
            this.first = first;
            this.second = second;
            this.oddRate = oddRate;
        }
    }

    private class Announcement implements Runnable {
        public Announcement() {}

        @Override
        public void run() {
            if (finalCountdown > 1200) {
                finalCountdown = 0;
            }

            switch (finalCountdown) {
                case 0: {
                    newRace();
                    newSpeeds();

                    currentRaceState = RaceState.ACCEPTING_BETS;
                    racePacket = new MonRaceInfo(CODES[0][0], CODES[0][1], getMonsters(), getSpeeds());

                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.TICKETS_ARE_NOW_AVAILABLE_FOR_MONSTER_RACE_S1);
                    msg.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(racePacket, msg);
                    break;
                }
                case 30: // 30 sec
                case 60: // 1 min
                case 90: // 1 min 30 sec
                case 120: // 2 min
                case 150: // 2 min 30
                case 180: // 3 min
                case 210: // 3 min 30
                case 240: // 4 min
                case 270: // 4 min 30 sec
                case 330: // 5 min 30 sec
                case 360: // 6 min
                case 390: // 6 min 30 sec
                case 420: // 7 min
                case 450: // 7 min 30
                case 480: // 8 min
                case 510: // 8 min 30
                case 540: // 9 min
                case 570: // 9 min 30 sec
                case 630: // 10 min 30 sec
                case 660: // 11 min
                case 690: // 11 min 30 sec
                case 720: // 12 min
                case 750: // 12 min 30
                case 780: // 13 min
                case 810: // 13 min 30
                case 870: // 14 min 30 sec
                {
                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.TICKETS_ARE_NOW_AVAILABLE_FOR_MONSTER_RACE_S1);
                    msg.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(msg);
                    break;
                }
                case 300: // 5 min
                {
                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.NOW_SELLING_TICKETS_FOR_MONSTER_RACE_S1);
                    msg.addInt(raceNumber);
                    final SystemMessage msg2 = new SystemMessage(
                            SystemMessageId.TICKET_SALES_FOR_THE_MONSTER_RACE_WILL_END_IN_S1_MINUTE_S);
                    msg2.addInt(10);
                    broadcastToAllPlayersInDerbyZone(msg, msg2);
                    break;
                }
                case 600: // 10 min
                {
                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.NOW_SELLING_TICKETS_FOR_MONSTER_RACE_S1);
                    msg.addInt(raceNumber);
                    final SystemMessage msg2 = new SystemMessage(
                            SystemMessageId.TICKET_SALES_FOR_THE_MONSTER_RACE_WILL_END_IN_S1_MINUTE_S);
                    msg2.addInt(5);
                    broadcastToAllPlayersInDerbyZone(msg, msg2);
                    break;
                }
                case 840: // 14 min
                {
                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.NOW_SELLING_TICKETS_FOR_MONSTER_RACE_S1);
                    msg.addInt(raceNumber);
                    final SystemMessage msg2 = new SystemMessage(
                            SystemMessageId.TICKET_SALES_FOR_THE_MONSTER_RACE_WILL_END_IN_S1_MINUTE_S);
                    msg2.addInt(1);
                    broadcastToAllPlayersInDerbyZone(msg, msg2);
                    break;
                }
                case 900: // 15 min
                {
                    currentRaceState = RaceState.WAITING;

                    calculateOdds();

                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.TICKETS_ARE_NOW_AVAILABLE_FOR_MONSTER_RACE_S1);
                    msg.addInt(raceNumber);
                    final SystemMessage msg2 = new SystemMessage(
                            SystemMessageId.TICKETS_SALES_ARE_CLOSED_FOR_MONSTER_RACE_S1_ODDS_ARE_POSTED);
                    msg2.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(msg, msg2);
                    break;
                }
                case 960: // 16 min
                case 1020: // 17 min
                {
                    final int minutes = (finalCountdown == 960) ? 2 : 1;
                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.MONSTER_RACE_S2_WILL_BEGIN_IN_S1_MINUTE_S);
                    msg.addInt(minutes);
                    msg.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(msg);
                    break;
                }
                case 1050: // 17 min 30 sec
                {
                    final SystemMessage msg =
                            new SystemMessage(SystemMessageId.MONSTER_RACE_S1_WILL_BEGIN_IN_30_SECONDS);
                    msg.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(msg);
                    break;
                }
                case 1070: // 17 min 50 sec
                {
                    final SystemMessage msg = new SystemMessage(
                            SystemMessageId.MONSTER_RACE_S1_IS_ABOUT_TO_BEGIN_COUNTDOWN_IN_FIVE_SECONDS);
                    msg.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(msg);
                    break;
                }
                case 1075: // 17 min 55 sec
                case 1076: // 17 min 56 sec
                case 1077: // 17 min 57 sec
                case 1078: // 17 min 58 sec
                case 1079: // 17 min 59 sec
                {
                    final int seconds = 1080 - finalCountdown;
                    final SystemMessage msg = new SystemMessage(SystemMessageId.THE_RACE_WILL_BEGIN_IN_S1_SECOND_S);
                    msg.addInt(seconds);
                    broadcastToAllPlayersInDerbyZone(msg);
                    break;
                }
                case 1080: // 18 min
                {
                    currentRaceState = RaceState.STARTING_RACE;
                    racePacket = new MonRaceInfo(CODES[1][0], CODES[1][1], getMonsters(), getSpeeds());
                    broadcastToAllPlayersInDerbyZone(
                            new SystemMessage(SystemMessageId.THEY_RE_OFF), SOUND_1, SOUND_2, racePacket);
                    break;
                }
                case 1085: // 18 min 5 sec
                {
                    racePacket = new MonRaceInfo(CODES[2][0], CODES[2][1], getMonsters(), getSpeeds());

                    broadcastToAllPlayersInDerbyZone(racePacket);
                    break;
                }
                case 1115: // 18 min 35 sec
                {
                    currentRaceState = RaceState.RACE_END;

                    // Populate history info with data, stores it in database.
                    final HistoryInfo info = history.get(history.size() - 1);
                    info.setFirst(getFirstPlace());
                    info.setSecond(getSecondPlace());
                    info.setOddRate(odds.get(getFirstPlace()));

                    saveHistory(info);
                    clearBets();

                    final SystemMessage msg = new SystemMessage(
                            SystemMessageId
                                    .FIRST_PRIZE_GOES_TO_THE_PLAYER_IN_LANE_S1_SECOND_PRIZE_GOES_TO_THE_PLAYER_IN_LANE_S2);
                    msg.addInt(getFirstPlace() + 1);
                    msg.addInt(getSecondPlace() + 1);
                    final SystemMessage msg2 = new SystemMessage(SystemMessageId.MONSTER_RACE_S1_IS_FINISHED);
                    msg2.addInt(raceNumber);
                    broadcastToAllPlayersInDerbyZone(msg, msg2);
                    raceNumber++;
                    break;
                }
                case 1140: // 19 min
                {
                    broadcastToAllPlayersInDerbyZone(
                            new DeleteObject(getMonsters()[0]),
                            new DeleteObject(getMonsters()[1]),
                            new DeleteObject(getMonsters()[2]),
                            new DeleteObject(getMonsters()[3]),
                            new DeleteObject(getMonsters()[4]),
                            new DeleteObject(getMonsters()[5]),
                            new DeleteObject(getMonsters()[6]),
                            new DeleteObject(getMonsters()[7]));
                    break;
                }
            }

            finalCountdown += 1;
        }
    }

    public void newRace() {
        // Edit history.
        history.add(new HistoryInfo(raceNumber, 0, 0, 0));

        // Randomize npcTemplates.
        Collections.shuffle(npcTemplates, ClockBridge.random());

        // Setup 8 new creatures ; pickup the first 8 from npcTemplates.
        for (int i = 0; i < 8; i++) {
            try {
                final NpcTemplate template = NpcData.getInstance().getTemplate(npcTemplates.get(i));
                monsters[i] = (Npc) Class.forName("app.l2nx.highfive.entity.actor.instance." + template.getType())
                        .getConstructors()[0]
                        .newInstance(template);
            } catch (Exception e) {
                log.warn("newRace: ", e);
            }
        }
    }

    public void newSpeeds() {
        speeds = new int[8][20];

        int total = 0;
        int winnerDistance = 0;
        int secondDistance = 0;

        // For each lane.
        for (int i = 0; i < 8; i++) {
            // Reset value upon new lane.
            total = 0;

            // Test the 20 segments.
            for (int j = 0; j < 20; j++) {
                if (j == 19) {
                    speeds[i][j] = 100;
                } else {
                    speeds[i][j] = ClockBridge.random().nextInt(60) + 65;
                }

                // Feed actual total to current lane total.
                total += speeds[i][j];
            }

            // The current total for this line is superior or equals to previous winner, it means we got a new winner
            // and the old winner becomes second.
            if (total >= winnerDistance) {
                // Old winner becomes second.
                secondPlace = firstPlace;

                // Old winner distance is the second.
                secondDistance = winnerDistance;

                // Find the good index.
                firstPlace = i;

                // Set the new limit to bypass winner position.
                winnerDistance = total;
            }
            // The total wasn't enough to.
            else if (total >= secondDistance) {
                // Find the good index.
                secondPlace = i;

                // Set the new limit to bypass second position.
                secondDistance = total;
            }
        }
    }

    /**
     * Load past races informations, feeding history arrayList.<br>
     * Also sets raceNumber, based on latest HistoryInfo loaded.
     */
    protected void loadHistory() {
        try {
            for (MonsterRaceResult row : monsterRaceRepository.findResults()) {
                history.add(new HistoryInfo(row.raceId(), row.first(), row.second(), row.oddRate()));
                raceNumber++;
            }
        } catch (DataAccessException e) {
            log.warn("MonsterRace: Can't load history: {}", e.getMessage(), e);
        }

        log.info("MonsterRace: loaded {} records, currently on race #{}", history.size(), raceNumber);
    }

    /**
     * Save an history record into database.
     * @param history The infos to store.
     */
    protected void saveHistory(HistoryInfo history) {
        try {
            monsterRaceRepository.saveResult(MonsterRaceResult.builder()
                    .raceId(history.getRaceId())
                    .first(history.getFirst())
                    .second(history.getSecond())
                    .oddRate(history.getOddRate())
                    .build());
        } catch (DataAccessException e) {
            log.warn("MonsterRace: Can't save history: {}", e.getMessage(), e);
        }
    }

    /**
     * Load current bets per lane ; initialize the map keys.
     */
    protected void loadBets() {
        // Every lane needs a key for the odds, also one nobody has bet on yet.
        for (int lane = 1; lane <= LANES; lane++) {
            betsPerLane.put(lane, 0L);
        }

        try {
            monsterRaceRepository.findBets().forEach((lane, bet) -> setBetOnLane(lane, bet, false));
        } catch (DataAccessException e) {
            log.warn("MonsterRace: Can't load bets: {}", e.getMessage(), e);
        }
    }

    /**
     * Save the current lane bet into database.
     * @param lane : The lane to affect.
     * @param sum : The sum to set.
     */
    protected void saveBet(int lane, long sum) {
        try {
            monsterRaceRepository.saveBet(lane, sum);
        } catch (DataAccessException e) {
            log.warn("MonsterRace: Can't save bet: {}", e.getMessage(), e);
        }
    }

    /**
     * Clear all lanes bets, either on database or Map.
     */
    protected void clearBets() {
        for (int key : betsPerLane.keySet()) {
            betsPerLane.put(key, 0L);
        }

        try {
            monsterRaceRepository.clearBets();
        } catch (DataAccessException e) {
            log.warn("MonsterRace: Can't clear bets: {}", e.getMessage(), e);
        }
    }

    /**
     * Setup lane bet, based on previous value (if any).
     * @param lane : The lane to edit.
     * @param amount : The amount to add.
     * @param saveOnDb : Should it be saved on db or not.
     */
    public void setBetOnLane(int lane, long amount, boolean saveOnDb) {
        final long sum = (betsPerLane.containsKey(lane)) ? betsPerLane.get(lane) + amount : amount;

        betsPerLane.put(lane, sum);

        if (saveOnDb) {
            saveBet(lane, sum);
        }
    }

    /**
     * Calculate odds for every lane, based on others lanes.
     */
    protected void calculateOdds() {
        // Clear previous List holding old odds.
        odds.clear();

        // Sort bets lanes per lane.
        final Map<Integer, Long> sortedLanes = new TreeMap<>(betsPerLane);

        // Pass a first loop in order to calculate total sum of all lanes.
        long sumOfAllLanes = 0;
        for (long amount : sortedLanes.values()) {
            sumOfAllLanes += amount;
        }

        // As we get the sum, we can now calculate the odd rate of each lane.
        for (long amount : sortedLanes.values()) {
            odds.add((amount == 0) ? 0D : Math.max(1.25, (sumOfAllLanes * 0.7) / amount));
        }
    }

    private <T extends ZoneType> void broadcastToAllPlayersInDerbyZone(ServerPacket... packets) {
        for (ServerPacket packet : packets) {
            packet.sendInBroadcast();
        }

        try {
            for (ZoneType zone : ZoneManager.getInstance().getAllZones(DerbyTrackZone.class)) {
                for (Creature creature : zone.getCharactersInside()) {
                    if (creature == null) {
                        continue;
                    }

                    for (ServerPacket packet : packets) {
                        creature.sendPacket(packet);
                    }
                }
            }
        } finally {
            for (ServerPacket packet : packets) {
                packet.releaseBroadcast();
            }
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static MonsterRaceManager getInstance() {
        final MonsterRaceManager current = instance;
        if (current == null) {
            throw new IllegalStateException("MonsterRaceManager is used before the Spring context created it.");
        }
        return current;
    }
}
