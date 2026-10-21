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
package app.l2nx.highfive.mechanics.olympiad;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.character.CharacterVariableRepository;
import app.l2nx.highfive.repository.olympiad.OlympiadFightRepository;
import app.l2nx.highfive.repository.olympiad.OlympiadNobleRepository;
import app.l2nx.highfive.repository.olympiad.OlympiadStatusRepository;
import app.l2nx.highfive.repository.olympiad.model.OlympiadFight;
import app.l2nx.highfive.repository.olympiad.model.OlympiadNoble;
import app.l2nx.highfive.repository.olympiad.model.OlympiadStatus;
import app.l2nx.highfive.repository.olympiad.model.RankedNoble;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author godson
 */
@Slf4j
@Component
public class Olympiad extends ListenersContainer {
    private static volatile Olympiad instance;

    private static final Logger OLYMPIAD_LOG = LoggerFactory.getLogger("olympiad");

    private static final Map<Integer, StatSet> NOBLES = new ConcurrentHashMap<>();
    private static final List<StatSet> HEROS_TO_BE = new ArrayList<>();
    private static final Map<Integer, Integer> NOBLES_RANK = new HashMap<>();

    public static final String OLYMPIAD_HTML_PATH = "data/html/olympiad/";
    public static final String UNCLAIMED_OLYMPIAD_PASSES_VAR = "UNCLAIMED_OLYMPIAD_POINTS";

    private static final int[] HERO_IDS = {
        88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112,
        113, 114, 115, 116, 117, 118, 131, 132, 133, 134
    };

    public static final String CHAR_ID = "charId";
    public static final String CLASS_ID = "class_id";
    public static final String CHAR_NAME = "char_name";
    public static final String POINTS = "olympiad_points";
    public static final String COMP_DONE = "competitions_done";
    public static final String COMP_WON = "competitions_won";
    public static final String COMP_LOST = "competitions_lost";
    public static final String COMP_DRAWN = "competitions_drawn";
    public static final String COMP_DONE_WEEK = "competitions_done_week";
    public static final String COMP_DONE_WEEK_CLASSED = "competitions_done_week_classed";
    public static final String COMP_DONE_WEEK_NON_CLASSED = "competitions_done_week_non_classed";
    public static final String COMP_DONE_WEEK_TEAM = "competitions_done_week_team";

    protected long olympiadEnd;
    protected long validationEnd;

    /** 0 = competition period, 1 = validation period. */
    @Getter
    protected int period;

    protected long nextWeeklyChange;

    @Getter
    protected int currentCycle;

    private long compEnd;
    private Calendar compStart;
    protected static boolean inCompPeriod;
    protected static boolean compStarted = false;
    protected ScheduledFuture<?> scheduledCompStart;
    protected ScheduledFuture<?> scheduledCompEnd;
    protected ScheduledFuture<?> scheduledOlympiadEnd;
    protected ScheduledFuture<?> scheduledWeeklyTask;
    protected ScheduledFuture<?> scheduledValdationTask;
    protected ScheduledFuture<?> gameManager = null;
    protected ScheduledFuture<?> gameAnnouncer = null;

    private final OlympiadStatusRepository statusRepository;
    private final OlympiadNobleRepository nobleRepository;
    private final OlympiadFightRepository fightRepository;
    private final CharacterVariableRepository characterVariableRepository;

    public Olympiad(
            OlympiadStatusRepository statusRepository,
            OlympiadNobleRepository nobleRepository,
            OlympiadFightRepository fightRepository,
            CharacterVariableRepository characterVariableRepository) {
        this.statusRepository = statusRepository;
        this.nobleRepository = nobleRepository;
        this.fightRepository = fightRepository;
        this.characterVariableRepository = characterVariableRepository;
        instance = this;
    }

    public void load() {
        if (OlympiadConfig.OLYMPIAD_ENABLED) {
            loadData();
            AntiFeedManager.getInstance().registerEvent(AntiFeedManager.OLYMPIAD_ID);

            if (period == 0) {
                init();
            }
        } else {
            log.info("Disabled.");
        }
    }

    private void loadData() {
        NOBLES.clear();
        boolean loaded = false;
        try {
            final OlympiadStatus status = statusRepository.find().orElse(null);
            if (status != null) {
                currentCycle = status.currentCycle();
                period = status.period();
                olympiadEnd = epochMillisOrZero(status.olympiadEnd());
                validationEnd = epochMillisOrZero(status.validationEnd());
                nextWeeklyChange = epochMillisOrZero(status.nextWeeklyChange());
                loaded = true;
            }
        } catch (DataAccessException e) {
            log.warn("Olympiad System: Error loading olympiad data from database: ", e);
        }

        if (!loaded) {
            currentCycle = 1;
            period = 0;
            olympiadEnd = 0;
            validationEnd = 0;
            nextWeeklyChange = 0;
        }

        final long currentTime = ClockBridge.wallMillis();
        switch (period) {
            case 0: {
                if ((olympiadEnd == 0) || (olympiadEnd < currentTime)) {
                    setNewOlympiadEnd();
                } else {
                    scheduleWeeklyChange();
                }
                break;
            }
            case 1: {
                if (validationEnd > currentTime) {
                    loadNoblesRank();
                    scheduledValdationTask = ThreadPool.schedule(new ValidationEndTask(), getMillisToValidationEnd());
                } else {
                    currentCycle++;
                    period = 0;
                    deleteNobles();
                    setNewOlympiadEnd();
                }
                break;
            }
            default: {
                log.warn("Olympiad System: Omg something went wrong in loading!! Period = {}", period);
                return;
            }
        }

        try {
            for (OlympiadNoble noble : nobleRepository.findAll()) {
                final StatSet statData = new StatSet();
                statData.set(CLASS_ID, noble.classId());
                statData.set(CHAR_NAME, noble.characterName());
                final int compDone = noble.competitionsDone();
                statData.set(
                        POINTS,
                        Math.clamp(
                                noble.olympiadPoints(),
                                0,
                                (OlympiadConfig.OLYMPIAD_MAX_POINTS * compDone)
                                        + (OlympiadConfig.OLYMPIAD_WEEKLY_POINTS * 4)));
                statData.set(COMP_DONE, compDone);
                statData.set(COMP_WON, noble.competitionsWon());
                statData.set(COMP_LOST, noble.competitionsLost());
                statData.set(COMP_DRAWN, noble.competitionsDrawn());
                statData.set(COMP_DONE_WEEK, noble.competitionsDoneWeek());
                statData.set(COMP_DONE_WEEK_CLASSED, noble.competitionsDoneWeekClassed());
                statData.set(COMP_DONE_WEEK_NON_CLASSED, noble.competitionsDoneWeekNonClassed());
                statData.set(COMP_DONE_WEEK_TEAM, noble.competitionsDoneWeekTeam());

                addNobleStats(noble.objectId(), statData);
            }
        } catch (DataAccessException e) {
            log.warn("Olympiad System: Error loading noblesse data from database: ", e);
        }

        log.info("Olympiad System: Loading....");
        if (period == 0) {
            log.info("Olympiad System: Currently in Olympiad Period");
        } else {
            log.info("Olympiad System: Currently in Validation Period");
        }

        long milliToEnd;
        if (period == 0) {
            milliToEnd = getMillisToOlympiadEnd();
        } else {
            milliToEnd = getMillisToValidationEnd();
        }

        final double numSecs = (milliToEnd / 1000) % 60;
        double countDown = ((milliToEnd / 1000.) - numSecs) / 60;
        final int numMins = (int) (countDown % 60);
        countDown = (countDown - numMins) / 60;
        final int numHours = (int) (countDown % 24);
        final int numDays = (int) ((countDown - numHours) / 24);

        log.info("Olympiad System: {} days, {} hours and {} mins until period ends.", numDays, numHours, numMins);

        if (period == 0) {
            milliToEnd = getMillisToWeekChange();
            final double numSecs2 = (milliToEnd / 1000) % 60;
            double countDown2 = ((milliToEnd / 1000.) - numSecs2) / 60;
            final int numMins2 = (int) (countDown % 60);
            countDown2 = (countDown2 - numMins) / 60;
            final int numHours2 = (int) (countDown2 % 24);
            final int numDays2 = (int) ((countDown2 - numHours) / 24);

            log.info(
                    "Olympiad System: Next weekly change is in {} days, {} hours and {} mins.",
                    numDays2,
                    numHours2,
                    numMins2);
        }

        log.info("Olympiad System: Loaded {} Nobles", NOBLES.size());
    }

    public void loadNoblesRank() {
        NOBLES_RANK.clear();
        final Map<Integer, Integer> tmpPlace = new HashMap<>();
        try {
            int place = 1;
            for (int objectId : nobleRepository.findSnapshotRanking(OlympiadConfig.OLYMPIAD_MIN_MATCHES)) {
                tmpPlace.put(objectId, place++);
            }
        } catch (DataAccessException e) {
            log.warn("Olympiad System: Error loading noblesse data from database for Ranking: ", e);
        }

        int rank1 = (int) Math.round(tmpPlace.size() * 0.01);
        int rank2 = (int) Math.round(tmpPlace.size() * 0.10);
        int rank3 = (int) Math.round(tmpPlace.size() * 0.25);
        int rank4 = (int) Math.round(tmpPlace.size() * 0.50);
        if (rank1 == 0) {
            rank1 = 1;
            rank2++;
            rank3++;
            rank4++;
        }

        for (Entry<Integer, Integer> chr : tmpPlace.entrySet()) {
            if (chr.getValue() <= rank1) {
                NOBLES_RANK.put(chr.getKey(), 1);
            } else if (tmpPlace.get(chr.getKey()) <= rank2) {
                NOBLES_RANK.put(chr.getKey(), 2);
            } else if (tmpPlace.get(chr.getKey()) <= rank3) {
                NOBLES_RANK.put(chr.getKey(), 3);
            } else if (tmpPlace.get(chr.getKey()) <= rank4) {
                NOBLES_RANK.put(chr.getKey(), 4);
            } else {
                NOBLES_RANK.put(chr.getKey(), 5);
            }
        }

        for (int noblesId : NOBLES.keySet()) {
            final int points = getNoblessePasses(noblesId);
            if (points > 0) {
                final Player player = World.getPlayer(noblesId);
                if (player != null) {
                    player.getVariables().set(UNCLAIMED_OLYMPIAD_PASSES_VAR, points);
                } else {
                    try {
                        characterVariableRepository.save(
                                noblesId, UNCLAIMED_OLYMPIAD_PASSES_VAR, String.valueOf(points));
                    } catch (DataAccessException e) {
                        log.warn("Olympiad System: Couldn't store unclaimed olympiad points to DB!");
                    }
                }
            }
        }
    }

    protected void init() {
        if (period == 1) {
            return;
        }

        compStart = ClockBridge.calendar();
        if (OlympiadConfig.OLYMPIAD_USE_CUSTOM_PERIOD_SETTINGS) {
            final int currentDay = compStart.get(Calendar.DAY_OF_WEEK);
            boolean dayFound = false;
            int dayCounter = 0;
            for (int i = currentDay; i < 8; i++) {
                if (OlympiadConfig.OLYMPIAD_COMPETITION_DAYS.contains(i)) {
                    dayFound = true;
                    break;
                }

                dayCounter++;
            }

            if (!dayFound) {
                for (int i = 1; i < 8; i++) {
                    if (OlympiadConfig.OLYMPIAD_COMPETITION_DAYS.contains(i)) {
                        break;
                    }

                    dayCounter++;
                }
            }

            if (dayCounter > 0) {
                compStart.add(Calendar.DAY_OF_MONTH, dayCounter);
            }
        }

        compStart.set(Calendar.HOUR_OF_DAY, OlympiadConfig.OLYMPIAD_START_TIME);
        compStart.set(Calendar.MINUTE, OlympiadConfig.OLYMPIAD_MIN);
        compEnd = compStart.getTimeInMillis() + OlympiadConfig.OLYMPIAD_CPERIOD;

        if (scheduledOlympiadEnd != null) {
            scheduledOlympiadEnd.cancel(true);
        }

        scheduledOlympiadEnd = ThreadPool.schedule(new OlympiadEndTask(HEROS_TO_BE), getMillisToOlympiadEnd());

        updateCompStatus();
    }

    protected class OlympiadEndTask implements Runnable {
        private final List<StatSet> herosToBe;

        public OlympiadEndTask(List<StatSet> herosToBe) {
            this.herosToBe = herosToBe;
        }

        @Override
        public void run() {
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.ROUND_S1_OF_THE_GRAND_OLYMPIAD_GAMES_HAS_NOW_ENDED);
            sm.addInt(currentCycle);

            World.broadcastToAllOnlinePlayers(sm);

            if (scheduledWeeklyTask != null) {
                scheduledWeeklyTask.cancel(true);
            }

            saveNobleData();

            period = 1;
            sortHerosToBe();
            Hero.getInstance().resetData();
            Hero.getInstance().computeNewHeroes(herosToBe);

            saveOlympiadStatus();
            updateMonthlyData();

            final Calendar validationEnd = ClockBridge.calendar();
            Olympiad.this.validationEnd = validationEnd.getTimeInMillis() + OlympiadConfig.OLYMPIAD_VPERIOD;

            loadNoblesRank();
            scheduledValdationTask = ThreadPool.schedule(new ValidationEndTask(), getMillisToValidationEnd());
        }
    }

    protected class ValidationEndTask implements Runnable {
        @Override
        public void run() {
            World.broadcastToAllOnlinePlayers("Olympiad Validation Period has ended");
            period = 0;
            currentCycle++;
            deleteNobles();
            setNewOlympiadEnd();
            init();
        }
    }

    protected static int getNobleCount() {
        return NOBLES.size();
    }

    protected static StatSet getNobleStats(int playerId) {
        return NOBLES.get(playerId);
    }

    private void updateCompStatus() {
        final long milliToStart = getMillisToCompBegin();
        final double numSecs = (milliToStart / 1000) % 60;
        double countDown = ((milliToStart / 1000.) - numSecs) / 60;
        final int numMins = (int) (countDown % 60);
        countDown = (countDown - numMins) / 60;
        final int numHours = (int) (countDown % 24);
        final int numDays = (int) ((countDown - numHours) / 24);
        log.info(
                "Olympiad System: Competition Period Starts in {} days, {} hours and {} mins.",
                numDays,
                numHours,
                numMins);
        log.info("Olympiad System: Event starts/started: {}", compStart.getTime());

        scheduledCompStart = ThreadPool.schedule(
                () -> {
                    if (isOlympiadEnd()) {
                        return;
                    }

                    inCompPeriod = true;

                    World.broadcastToAllOnlinePlayers(
                            new SystemMessage(
                                    SystemMessageId
                                            .SHARPEN_YOUR_SWORDS_TIGHTEN_THE_STITCHING_IN_YOUR_ARMOR_AND_MAKE_HASTE_TO_A_GRAND_OLYMPIAD_MANAGER_BATTLES_IN_THE_GRAND_OLYMPIAD_GAMES_ARE_NOW_TAKING_PLACE));
                    log.info("Olympiad System: Olympiad Games have started.");
                    OLYMPIAD_LOG.info("Result,Player1,Player2,Player1 HP,Player2 HP,Player1 Damage,Player2"
                            + " Damage,Points,Classed");

                    gameManager = ThreadPool.scheduleAtFixedRate(OlympiadGameManager.getInstance(), 30000, 30000);
                    if (OlympiadConfig.OLYMPIAD_ANNOUNCE_GAMES) {
                        gameAnnouncer = ThreadPool.scheduleAtFixedRate(new OlympiadAnnouncer(), 30000, 500);
                    }

                    final long regEnd = getMillisToCompEnd() - 600000;
                    if (regEnd > 0) {
                        ThreadPool.schedule(
                                () -> World.broadcastToAllOnlinePlayers(new SystemMessage(
                                        SystemMessageId.THE_GRAND_OLYMPIAD_REGISTRATION_PERIOD_HAS_ENDED)),
                                regEnd);
                    }

                    scheduledCompEnd = ThreadPool.schedule(
                            () -> {
                                if (isOlympiadEnd()) {
                                    return;
                                }

                                inCompPeriod = false;
                                World.broadcastToAllOnlinePlayers(
                                        new SystemMessage(
                                                SystemMessageId
                                                        .MUCH_CARNAGE_HAS_BEEN_LEFT_FOR_THE_CLEANUP_CREW_OF_THE_OLYMPIAD_STADIUM_BATTLES_IN_THE_GRAND_OLYMPIAD_GAMES_ARE_NOW_OVER));
                                log.info("Olympiad System: Olympiad games have ended.");

                                while (OlympiadGameManager.getInstance().isBattleStarted()) {
                                    try {
                                        Thread.sleep(60000);
                                    } catch (Exception e) {
                                    }
                                }

                                if (gameManager != null) {
                                    gameManager.cancel(false);
                                    gameManager = null;
                                }

                                if (gameAnnouncer != null) {
                                    gameAnnouncer.cancel(false);
                                    gameAnnouncer = null;
                                }

                                saveOlympiadStatus();

                                init();
                            },
                            getMillisToCompEnd());
                },
                getMillisToCompBegin());
    }

    public long getMillisToOlympiadEnd() {
        return olympiadEnd - ClockBridge.wallMillis();
    }

    public void manualSelectHeroes() {
        if (scheduledOlympiadEnd != null) {
            scheduledOlympiadEnd.cancel(true);
        }

        scheduledOlympiadEnd = ThreadPool.schedule(new OlympiadEndTask(HEROS_TO_BE), 0);
    }

    protected long getMillisToValidationEnd() {
        final long currentTime = ClockBridge.wallMillis();
        if (validationEnd > currentTime) {
            return validationEnd - currentTime;
        }

        return 10;
    }

    public boolean isOlympiadEnd() {
        return period != 0;
    }

    protected void setNewOlympiadEnd() {
        final SystemMessage sm = new SystemMessage(SystemMessageId.ROUND_S1_OF_THE_GRAND_OLYMPIAD_GAMES_HAS_STARTED);
        sm.addInt(currentCycle);
        World.broadcastToAllOnlinePlayers(sm);

        if (!OlympiadConfig.OLYMPIAD_USE_CUSTOM_PERIOD_SETTINGS) {
            final Calendar currentTime = ClockBridge.calendar();
            currentTime.add(Calendar.MONTH, 1);
            currentTime.set(Calendar.DAY_OF_MONTH, 1);
            currentTime.set(Calendar.AM_PM, Calendar.AM);
            currentTime.set(Calendar.HOUR_OF_DAY, 12);
            currentTime.set(Calendar.MINUTE, 0);
            currentTime.set(Calendar.SECOND, 0);
            olympiadEnd = currentTime.getTimeInMillis();

            final Calendar nextChange = ClockBridge.calendar();
            nextWeeklyChange = nextChange.getTimeInMillis() + OlympiadConfig.OLYMPIAD_WPERIOD;
        } else {
            final Calendar currentTime = ClockBridge.calendar();
            currentTime.set(Calendar.AM_PM, Calendar.AM);
            currentTime.set(Calendar.HOUR_OF_DAY, 12);
            currentTime.set(Calendar.MINUTE, 0);
            currentTime.set(Calendar.SECOND, 0);

            final Calendar nextChange = ClockBridge.calendar();

            switch (OlympiadConfig.OLYMPIAD_PERIOD) {
                case "DAY": {
                    currentTime.add(Calendar.DAY_OF_MONTH, OlympiadConfig.OLYMPIAD_PERIOD_MULTIPLIER);
                    currentTime.add(Calendar.DAY_OF_MONTH, -1); // last day is for validation

                    if (OlympiadConfig.OLYMPIAD_PERIOD_MULTIPLIER >= 14) {
                        nextWeeklyChange = nextChange.getTimeInMillis() + OlympiadConfig.OLYMPIAD_WPERIOD;
                    } else if (OlympiadConfig.OLYMPIAD_PERIOD_MULTIPLIER >= 7) {
                        nextWeeklyChange = nextChange.getTimeInMillis() + (OlympiadConfig.OLYMPIAD_WPERIOD / 2);
                    } else {
                        log.warn("Invalid config value for Config.OLYMPIAD_PERIOD_MULTIPLIER, must be >= 7");
                    }
                    break;
                }
                case "WEEK": {
                    currentTime.add(Calendar.WEEK_OF_MONTH, OlympiadConfig.OLYMPIAD_PERIOD_MULTIPLIER);
                    currentTime.add(Calendar.DAY_OF_MONTH, -1);

                    if (OlympiadConfig.OLYMPIAD_PERIOD_MULTIPLIER > 1) {
                        nextWeeklyChange = nextChange.getTimeInMillis() + OlympiadConfig.OLYMPIAD_WPERIOD;
                    } else {
                        nextWeeklyChange = nextChange.getTimeInMillis() + (OlympiadConfig.OLYMPIAD_WPERIOD / 2);
                    }
                    break;
                }
                case "MONTH": {
                    currentTime.add(Calendar.MONTH, OlympiadConfig.OLYMPIAD_PERIOD_MULTIPLIER);
                    currentTime.add(Calendar.DAY_OF_MONTH, -1);

                    nextWeeklyChange = nextChange.getTimeInMillis() + OlympiadConfig.OLYMPIAD_WPERIOD;
                    break;
                }
            }

            olympiadEnd = currentTime.getTimeInMillis();
        }

        scheduleWeeklyChange();
    }

    public boolean inCompPeriod() {
        return inCompPeriod;
    }

    private long getMillisToCompBegin() {
        final long currentTime = ClockBridge.wallMillis();
        if ((compStart.getTimeInMillis() < currentTime) && (compEnd > currentTime)) {
            return 10;
        }

        if (compStart.getTimeInMillis() > currentTime) {
            return compStart.getTimeInMillis() - currentTime;
        }

        return setNewCompBegin();
    }

    private long setNewCompBegin() {
        compStart = ClockBridge.calendar();

        int currentDay = compStart.get(Calendar.DAY_OF_WEEK);
        compStart.set(Calendar.HOUR_OF_DAY, OlympiadConfig.OLYMPIAD_START_TIME);
        compStart.set(Calendar.MINUTE, OlympiadConfig.OLYMPIAD_MIN);

        if (currentDay == compStart.get(Calendar.DAY_OF_WEEK)) {
            if (currentDay == Calendar.SATURDAY) {
                currentDay = Calendar.SUNDAY;
            } else {
                currentDay++;
            }
        }

        if (OlympiadConfig.OLYMPIAD_USE_CUSTOM_PERIOD_SETTINGS) {
            boolean dayFound = false;
            int dayCounter = 0;
            for (int i = currentDay; i < 8; i++) {
                if (OlympiadConfig.OLYMPIAD_COMPETITION_DAYS.contains(i)) {
                    dayFound = true;
                    break;
                }

                dayCounter++;
            }

            if (!dayFound) {
                for (int i = 1; i < 8; i++) {
                    if (OlympiadConfig.OLYMPIAD_COMPETITION_DAYS.contains(i)) {
                        break;
                    }

                    dayCounter++;
                }
            }

            if (dayCounter > 0) {
                compStart.add(Calendar.DAY_OF_MONTH, dayCounter);
            }
        }

        compStart.add(Calendar.HOUR_OF_DAY, 24);
        compEnd = compStart.getTimeInMillis() + OlympiadConfig.OLYMPIAD_CPERIOD;

        log.info("Olympiad System: New Schedule @ {}", compStart.getTime());

        return compStart.getTimeInMillis() - ClockBridge.wallMillis();
    }

    protected long getMillisToCompEnd() {
        return compEnd - ClockBridge.wallMillis();
    }

    public long getMillisToWeekChange() {
        final long currentTime = ClockBridge.wallMillis();
        if (nextWeeklyChange > currentTime) {
            return nextWeeklyChange - currentTime;
        }

        return 10;
    }

    private void scheduleWeeklyChange() {
        scheduledWeeklyTask = ThreadPool.scheduleAtFixedRate(
                () -> {
                    addWeeklyPoints();
                    log.info("Olympiad System: Added weekly points to nobles");
                    resetWeeklyMatches();
                    log.info("Olympiad System: Reset weekly matches to nobles");

                    nextWeeklyChange = ClockBridge.wallMillis() + OlympiadConfig.OLYMPIAD_WPERIOD;
                },
                getMillisToWeekChange(),
                OlympiadConfig.OLYMPIAD_WPERIOD);
    }

    protected void addWeeklyPoints() {
        if (period == 1) {
            return;
        }

        for (StatSet nobleInfo : NOBLES.values()) {
            nobleInfo.set(
                    POINTS,
                    Math.clamp(
                            nobleInfo.getInt(POINTS, 0) + OlympiadConfig.OLYMPIAD_WEEKLY_POINTS,
                            0,
                            (nobleInfo.getInt(COMP_DONE, 0) * OlympiadConfig.OLYMPIAD_MAX_POINTS)
                                    + (OlympiadConfig.OLYMPIAD_WEEKLY_POINTS * 4)));
        }
    }

    protected void resetWeeklyMatches() {
        if (period == 1) {
            return;
        }

        for (StatSet nobleInfo : NOBLES.values()) {
            nobleInfo.set(COMP_DONE_WEEK, 0);
            nobleInfo.set(COMP_DONE_WEEK_CLASSED, 0);
            nobleInfo.set(COMP_DONE_WEEK_NON_CLASSED, 0);
            nobleInfo.set(COMP_DONE_WEEK_TEAM, 0);
        }
    }

    public boolean playerInStadia(Player player) {
        return ZoneManager.getInstance().getOlympiadStadium(player) != null;
    }

    protected synchronized void saveNobleData() {
        if (NOBLES.isEmpty()) {
            return;
        }

        final List<OlympiadNoble> nobles = new ArrayList<>();
        for (Entry<Integer, StatSet> entry : NOBLES.entrySet()) {
            final StatSet nobleInfo = entry.getValue();
            if (nobleInfo == null) {
                continue;
            }

            nobles.add(OlympiadNoble.builder()
                    .objectId(entry.getKey())
                    .classId(nobleInfo.getInt(CLASS_ID))
                    .olympiadPoints(nobleInfo.getInt(POINTS))
                    .competitionsDone(nobleInfo.getInt(COMP_DONE))
                    .competitionsWon(nobleInfo.getInt(COMP_WON))
                    .competitionsLost(nobleInfo.getInt(COMP_LOST))
                    .competitionsDrawn(nobleInfo.getInt(COMP_DRAWN))
                    .competitionsDoneWeek(nobleInfo.getInt(COMP_DONE_WEEK))
                    .competitionsDoneWeekClassed(nobleInfo.getInt(COMP_DONE_WEEK_CLASSED))
                    .competitionsDoneWeekNonClassed(nobleInfo.getInt(COMP_DONE_WEEK_NON_CLASSED))
                    .competitionsDoneWeekTeam(nobleInfo.getInt(COMP_DONE_WEEK_TEAM))
                    .build());
        }

        try {
            nobleRepository.saveAll(nobles);
        } catch (DataAccessException e) {
            log.error("Olympiad System: Failed to save noblesse data to database: ", e);
        }
    }

    public void saveOlympiadStatus() {
        saveNobleData();

        try {
            statusRepository.save(OlympiadStatus.builder()
                    .currentCycle(currentCycle)
                    .period(period)
                    .olympiadEnd(instantOrNull(olympiadEnd))
                    .validationEnd(instantOrNull(validationEnd))
                    .nextWeeklyChange(instantOrNull(nextWeeklyChange))
                    .build());
        } catch (DataAccessException e) {
            log.error("Olympiad System: Failed to save olympiad data to database: ", e);
        }
    }

    public void saveFight(OlympiadFight fight) {
        try {
            fightRepository.insert(fight);
        } catch (DataAccessException e) {
            log.error("SQL exception while saving olympiad fight.", e);
        }
    }

    protected void updateMonthlyData() {
        try {
            nobleRepository.replaceSnapshot();
        } catch (DataAccessException e) {
            log.error("Olympiad System: Failed to update monthly noblese data: ", e);
        }
    }

    protected void sortHerosToBe() {
        if (period != 1) {
            return;
        }

        if (NOBLES != null) {
            OLYMPIAD_LOG.info("Noble,charid,classid,compDone,points");
            StatSet nobleInfo;
            for (Entry<Integer, StatSet> entry : NOBLES.entrySet()) {
                nobleInfo = entry.getValue();
                if (nobleInfo == null) {
                    continue;
                }

                final int charId = entry.getKey();
                final int classId = nobleInfo.getInt(CLASS_ID);
                final String charName = nobleInfo.getString(CHAR_NAME);
                final int points = nobleInfo.getInt(POINTS);
                final int compDone = nobleInfo.getInt(COMP_DONE);

                OLYMPIAD_LOG.info("{},{},{},{},{}", charName, charId, classId, compDone, points);
            }
        }

        try {
            StatSet hero;
            final List<StatSet> soulHounds = new ArrayList<>();
            for (int element : HERO_IDS) {
                final RankedNoble candidate = nobleRepository
                        .findHeroCandidate(element, OlympiadConfig.OLYMPIAD_MIN_MATCHES)
                        .orElse(null);
                if (candidate != null) {
                    hero = new StatSet();
                    hero.set(CLASS_ID, element);
                    hero.set(CHAR_ID, candidate.objectId());
                    hero.set(CHAR_NAME, candidate.characterName());

                    OLYMPIAD_LOG.info(
                            "Hero {},{},{}", hero.getString(CHAR_NAME), hero.getInt(CHAR_ID), hero.getInt(CLASS_ID));
                    if ((element == 132) || (element == 133)) // Male & Female Soulhounds rank as one hero class
                    {
                        hero = NOBLES.get(hero.getInt(CHAR_ID));
                        hero.set(CHAR_ID, candidate.objectId());
                        soulHounds.add(hero);
                    } else {
                        HEROS_TO_BE.add(hero);
                    }
                }
            }

            switch (soulHounds.size()) {
                case 0: {
                    break;
                }
                case 1: {
                    hero = new StatSet();
                    final StatSet winner = soulHounds.get(0);
                    hero.set(CLASS_ID, winner.getInt(CLASS_ID));
                    hero.set(CHAR_ID, winner.getInt(CHAR_ID));
                    hero.set(CHAR_NAME, winner.getString(CHAR_NAME));

                    HEROS_TO_BE.add(hero);
                    break;
                }
                case 2: {
                    hero = new StatSet();
                    StatSet winner;
                    final StatSet hero1 = soulHounds.get(0);
                    final StatSet hero2 = soulHounds.get(1);
                    final int hero1Points = hero1.getInt(POINTS);
                    final int hero2Points = hero2.getInt(POINTS);
                    final int hero1Comps = hero1.getInt(COMP_DONE);
                    final int hero2Comps = hero2.getInt(COMP_DONE);
                    final int hero1Wins = hero1.getInt(COMP_WON);
                    final int hero2Wins = hero2.getInt(COMP_WON);

                    if (hero1Points > hero2Points) {
                        winner = hero1;
                    } else if (hero2Points > hero1Points) {
                        winner = hero2;
                    } else if (hero1Comps > hero2Comps) {
                        winner = hero1;
                    } else if (hero2Comps > hero1Comps) {
                        winner = hero2;
                    } else if (hero1Wins > hero2Wins) {
                        winner = hero1;
                    } else {
                        winner = hero2;
                    }

                    hero.set(CLASS_ID, winner.getInt(CLASS_ID));
                    hero.set(CHAR_ID, winner.getInt(CHAR_ID));
                    hero.set(CHAR_NAME, winner.getString(CHAR_NAME));

                    HEROS_TO_BE.add(hero);
                    break;
                }
            }
        } catch (DataAccessException e) {
            log.warn("Olympiad System: Could not load heros from DB");
        }
    }

    public List<String> getClassLeaderBoard(int classId) {
        final List<Integer> classIds = classId == PlayerClass.MALE_SOUL_HOUND.getId()
                ? List.of(classId, PlayerClass.FEMALE_SOUL_HOUND.getId())
                : List.of(classId);
        try {
            return OlympiadConfig.OLYMPIAD_SHOW_MONTHLY_WINNERS
                    ? nobleRepository.findSnapshotLeaderNames(classIds, OlympiadConfig.OLYMPIAD_MIN_MATCHES, 10)
                    : nobleRepository.findLeaderNames(classIds, OlympiadConfig.OLYMPIAD_MIN_MATCHES, 10);
        } catch (DataAccessException e) {
            log.warn("Olympiad System: Couldn't load olympiad leaders from DB!");
            return new ArrayList<>();
        }
    }

    private int getNoblessePasses(int objectId) {
        if ((period != 1) || NOBLES_RANK.isEmpty()) {
            return 0;
        }

        if (!NOBLES_RANK.containsKey(objectId)) {
            return 0;
        }

        final StatSet noble = NOBLES.get(objectId);
        if ((noble == null) || (noble.getInt(POINTS) == 0)) {
            return 0;
        }

        int points = Hero.getInstance().isHero(objectId) || Hero.getInstance().isUnclaimedHero(objectId)
                ? OlympiadConfig.OLYMPIAD_HERO_POINTS
                : 0;

        switch (NOBLES_RANK.get(objectId)) {
            case 1: {
                points += OlympiadConfig.OLYMPIAD_RANK1_POINTS;
                break;
            }
            case 2: {
                points += OlympiadConfig.OLYMPIAD_RANK2_POINTS;
                break;
            }
            case 3: {
                points += OlympiadConfig.OLYMPIAD_RANK3_POINTS;
                break;
            }
            case 4: {
                points += OlympiadConfig.OLYMPIAD_RANK4_POINTS;
                break;
            }
            default: {
                points += OlympiadConfig.OLYMPIAD_RANK5_POINTS;
            }
        }

        points += getCompetitionWon(objectId) > 0 ? 10 : 5;

        // This is a one time calculation.
        noble.set(POINTS, 0);

        return points;
    }

    public int getNoblePoints(int objId) {
        return !NOBLES.containsKey(objId) ? 0 : NOBLES.get(objId).getInt(POINTS);
    }

    public int getLastNobleOlympiadPoints(int objId) {
        try {
            return nobleRepository.findSnapshotPoints(objId).orElse(0);
        } catch (DataAccessException e) {
            log.warn("Could not load last olympiad points:", e);
            return 0;
        }
    }

    public int getCompetitionDone(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_DONE) : 0;
    }

    public int getCompetitionWon(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_WON) : 0;
    }

    public int getCompetitionLost(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_LOST) : 0;
    }

    public int getCompetitionDoneWeek(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_DONE_WEEK) : 0;
    }

    public int getCompetitionDoneWeekClassed(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_DONE_WEEK_CLASSED) : 0;
    }

    public int getCompetitionDoneWeekNonClassed(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_DONE_WEEK_NON_CLASSED) : 0;
    }

    public int getCompetitionDoneWeekTeam(int objId) {
        return NOBLES.containsKey(objId) ? NOBLES.get(objId).getInt(COMP_DONE_WEEK_TEAM) : 0;
    }

    public int getRemainingWeeklyMatches(int objId) {
        return Math.max(OlympiadConfig.OLYMPIAD_MAX_WEEKLY_MATCHES - getCompetitionDoneWeek(objId), 0);
    }

    public int getRemainingWeeklyMatchesClassed(int objId) {
        return Math.max(OlympiadConfig.OLYMPIAD_MAX_WEEKLY_MATCHES_CLASSED - getCompetitionDoneWeekClassed(objId), 0);
    }

    public int getRemainingWeeklyMatchesNonClassed(int objId) {
        return Math.max(
                OlympiadConfig.OLYMPIAD_MAX_WEEKLY_MATCHES_NON_CLASSED - getCompetitionDoneWeekNonClassed(objId), 0);
    }

    public int getRemainingWeeklyMatchesTeam(int objId) {
        return Math.max(OlympiadConfig.OLYMPIAD_MAX_WEEKLY_MATCHES_TEAM - getCompetitionDoneWeekTeam(objId), 0);
    }

    protected void deleteNobles() {
        try {
            nobleRepository.deleteAll();
        } catch (DataAccessException e) {
            log.warn("Olympiad System: Couldn't delete nobles from DB!");
        }

        NOBLES.clear();
    }

    /** @return the previous stats, or null if the noble was absent. */
    protected static StatSet addNobleStats(int charId, StatSet data) {
        return NOBLES.put(charId, data);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static Olympiad getInstance() {
        final Olympiad current = instance;
        if (current == null) {
            throw new IllegalStateException("Olympiad is used before the Spring context created it.");
        }
        return current;
    }
}
