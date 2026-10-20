/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.repository.character.CharacterRaidPointRepository;
import app.l2nx.highfive.threads.ThreadPool;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author Kerberos, JIV
 * @version 8/24/10
 */
@Slf4j
@Component
public class RaidBossPointsManager {
    private static volatile RaidBossPointsManager instance;

    private final Map<Integer, Map<Integer, Integer>> list = new ConcurrentHashMap<>();

    private final CharacterRaidPointRepository raidPointRepository;

    public RaidBossPointsManager(CharacterRaidPointRepository raidPointRepository) {
        this.raidPointRepository = raidPointRepository;
        instance = this;
    }

    public void load() {
        init();

        // Start reset task at 00:10 and repeat every 24 hours.
        final ZonedDateTime now = ZonedDateTime.now(ClockBridge.wallClock());
        final ZonedDateTime todayResetTime = now.with(LocalTime.of(0, 10));
        final ZonedDateTime nextResetTime = todayResetTime.isBefore(now) ? todayResetTime.plusDays(1) : todayResetTime;
        ThreadPool.scheduleAtFixedRate(
                this::resetRaidPoints, nextResetTime.toInstant().toEpochMilli() - ClockBridge.wallMillis(), 86400000);
    }

    private void init() {
        try {
            list.putAll(raidPointRepository.findAll());
            log.info("Loaded {} Characters Raid Points.", list.size());
        } catch (DataAccessException e) {
            log.warn("Could not load raid points ", e);
        }
    }

    private void resetRaidPoints() {
        final Calendar calendar = ClockBridge.calendar();
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            return;
        }

        // Reward clan reputation points.
        final Map<Integer, Integer> rankList =
                RaidBossPointsManager.getInstance().getRankList();
        for (Clan clan : ClanTable.getInstance().getClans()) {
            for (Entry<Integer, Integer> entry : rankList.entrySet()) {
                if ((entry.getValue() <= 100) && clan.isMember(entry.getKey())) {
                    int reputation = 0;
                    switch (entry.getValue()) {
                        case 1: {
                            reputation = FeatureConfig.RAID_RANKING_1ST;
                            break;
                        }
                        case 2: {
                            reputation = FeatureConfig.RAID_RANKING_2ND;
                            break;
                        }
                        case 3: {
                            reputation = FeatureConfig.RAID_RANKING_3RD;
                            break;
                        }
                        case 4: {
                            reputation = FeatureConfig.RAID_RANKING_4TH;
                            break;
                        }
                        case 5: {
                            reputation = FeatureConfig.RAID_RANKING_5TH;
                            break;
                        }
                        case 6: {
                            reputation = FeatureConfig.RAID_RANKING_6TH;
                            break;
                        }
                        case 7: {
                            reputation = FeatureConfig.RAID_RANKING_7TH;
                            break;
                        }
                        case 8: {
                            reputation = FeatureConfig.RAID_RANKING_8TH;
                            break;
                        }
                        case 9: {
                            reputation = FeatureConfig.RAID_RANKING_9TH;
                            break;
                        }
                        case 10: {
                            reputation = FeatureConfig.RAID_RANKING_10TH;
                            break;
                        }
                        default: {
                            if (entry.getValue() <= 50) {
                                reputation = FeatureConfig.RAID_RANKING_UP_TO_50TH;
                            } else {
                                reputation = FeatureConfig.RAID_RANKING_UP_TO_100TH;
                            }
                            break;
                        }
                    }

                    clan.addReputationScore(reputation);
                }
            }
        }

        cleanUp();
        log.info("Reset task launched.");
    }

    public void updatePointsInDB(Player player, int raidId, int points) {
        try {
            raidPointRepository.save(player.getObjectId(), raidId, points);
        } catch (DataAccessException e) {
            log.warn("Couldn't update char raid points for player: {}", player, e);
        }
    }

    public void addPoints(Player player, int bossId, int points) {
        final Map<Integer, Integer> tmpPoint = list.computeIfAbsent(player.getObjectId(), _ -> new HashMap<>());
        updatePointsInDB(player, bossId, tmpPoint.merge(bossId, points, Integer::sum));
    }

    public int getPointsByOwnerId(int ownerId) {
        final Map<Integer, Integer> tmpPoint = list.get(ownerId);
        int totalPoints = 0;
        if ((tmpPoint == null) || tmpPoint.isEmpty()) {
            return 0;
        }

        for (int points : tmpPoint.values()) {
            totalPoints += points;
        }

        return totalPoints;
    }

    public Map<Integer, Integer> getList(Player player) {
        return list.get(player.getObjectId());
    }

    public void cleanUp() {
        try {
            raidPointRepository.deleteAll();
            list.clear();
        } catch (DataAccessException e) {
            log.warn("Couldn't clean raid points", e);
        }
    }

    public int calculateRanking(int playerObjId) {
        return getRankList().getOrDefault(playerObjId, 0);
    }

    public Map<Integer, Integer> getRankList() {
        final Map<Integer, Integer> tmpPoints = new HashMap<>();
        for (int ownerId : this.list.keySet()) {
            final int totalPoints = getPointsByOwnerId(ownerId);
            if (totalPoints != 0) {
                tmpPoints.put(ownerId, totalPoints);
            }
        }

        final List<Entry<Integer, Integer>> list = new ArrayList<>(tmpPoints.entrySet());
        list.sort(Comparator.comparingInt(Entry<Integer, Integer>::getValue).reversed());
        int ranking = 1;
        final Map<Integer, Integer> tmpRanking = new HashMap<>();
        for (Entry<Integer, Integer> entry : list) {
            tmpRanking.put(entry.getKey(), ranking++);
        }

        return tmpRanking;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static RaidBossPointsManager getInstance() {
        final RaidBossPointsManager current = instance;
        if (current == null) {
            throw new IllegalStateException("RaidBossPointsManager is used before the Spring context created it.");
        }
        return current;
    }
}
