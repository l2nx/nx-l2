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
package app.l2nx.highfive.mechanics.sevensigns;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.config.NpcConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.spawns.AutoSpawnHandler;
import app.l2nx.highfive.entity.spawns.AutoSpawnHandler.AutoSpawnInstance;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.SSQInfo;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.sevensigns.SevenSignsParticipantRepository;
import app.l2nx.highfive.repository.sevensigns.SevenSignsStatusRepository;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsParticipant;
import app.l2nx.highfive.repository.sevensigns.model.SevenSignsStatus;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Seven Signs engine.
 * @author Tempy
 */
@Slf4j
@Component
public class SevenSigns {
    private static volatile SevenSigns instance;

    // Basic Seven Signs Constants
    public static final String SEVEN_SIGNS_HTML_PATH = "data/html/seven_signs/";

    public static final int CABAL_NULL = 0;
    public static final int CABAL_DUSK = 1;
    public static final int CABAL_DAWN = 2;

    public static final int SEAL_NULL = 0;
    public static final int SEAL_AVARICE = 1;
    public static final int SEAL_GNOSIS = 2;
    public static final int SEAL_STRIFE = 3;

    public static final int PERIOD_COMP_RECRUITING = 0;
    public static final int PERIOD_COMPETITION = 1;
    public static final int PERIOD_COMP_RESULTS = 2;
    public static final int PERIOD_SEAL_VALIDATION = 3;

    public static final int PERIOD_START_HOUR = 18;
    public static final int PERIOD_START_MINS = 00;
    public static final int PERIOD_START_DAY = Calendar.MONDAY;

    // The quest event and seal validation periods last for approximately one week
    // with a 15 minutes "interval" period sandwiched between them.
    public static final int PERIOD_MINOR_LENGTH = 900000;
    public static final int PERIOD_MAJOR_LENGTH = 604800000 - PERIOD_MINOR_LENGTH;

    public static final int RECORD_SEVEN_SIGNS_ID = 5707;
    public static final int RECORD_SEVEN_SIGNS_COST = 500;

    // NPC Related Constants
    public static final int ORATOR_NPC_ID = 31094;
    public static final int PREACHER_NPC_ID = 31093;
    public static final int MAMMON_MERCHANT_ID = 31113;
    public static final int MAMMON_BLACKSMITH_ID = 31126;
    public static final int MAMMON_MARKETEER_ID = 31092;
    public static final int LILITH_NPC_ID = 25283;
    public static final int ANAKIM_NPC_ID = 25286;
    public static final int CREST_OF_DAWN_ID = 31170;
    public static final int CREST_OF_DUSK_ID = 31171;

    // Seal Stone Related Constants
    public static final int SEAL_STONE_BLUE_ID = 6360;
    public static final int SEAL_STONE_GREEN_ID = 6361;
    public static final int SEAL_STONE_RED_ID = 6362;

    protected static final int[] SEAL_STONE_IDS = {SEAL_STONE_BLUE_ID, SEAL_STONE_GREEN_ID, SEAL_STONE_RED_ID};

    public static final int SEAL_STONE_BLUE_VALUE = 3;
    public static final int SEAL_STONE_GREEN_VALUE = 5;
    public static final int SEAL_STONE_RED_VALUE = 10;

    public static final int BLUE_CONTRIB_POINTS = 3;
    public static final int GREEN_CONTRIB_POINTS = 5;
    public static final int RED_CONTRIB_POINTS = 10;

    private final Calendar nextPeriodChange = ClockBridge.calendar();

    @Getter
    protected int currentPeriod;

    @Getter
    protected int currentCycle;

    protected double dawnStoneScore;
    protected double duskStoneScore;
    protected int dawnFestivalScore;
    protected int duskFestivalScore;
    protected int compWinner;
    protected int previousWinner;
    protected Calendar lastSave = ClockBridge.calendar();

    protected Map<Integer, StatSet> signsPlayerData = new LinkedHashMap<>();
    private final Map<Integer, Integer> signsSealOwners = new LinkedHashMap<>();
    private final Map<Integer, Integer> signsDuskSealTotals = new LinkedHashMap<>();
    private final Map<Integer, Integer> signsDawnSealTotals = new LinkedHashMap<>();

    private final SevenSignsParticipantRepository participantRepository;
    private final SevenSignsStatusRepository statusRepository;

    public SevenSigns(
            SevenSignsParticipantRepository participantRepository, SevenSignsStatusRepository statusRepository) {
        this.participantRepository = participantRepository;
        this.statusRepository = statusRepository;
        instance = this;
    }

    public void load() {
        try {
            restoreSevenSignsData();
        } catch (Exception e) {
            log.error("Failed to load configuration: {}", e.getMessage(), e);
        }

        SevenSignsFestival.getInstance().restoreFestivalData();

        log.info("Currently in the {} period!", getCurrentPeriodName());
        initializeSeals();

        if (isSealValidationPeriod()) {
            if (getCabalHighestScore() == CABAL_NULL) {
                log.info("The competition ended with a tie last week.");
            } else {
                log.info("The {} were victorious last week.", getCabalName(getCabalHighestScore()));
            }
        } else if (getCabalHighestScore() == CABAL_NULL) {
            log.info("The competition, if the current trend continues, will end in a tie this week.");
        } else {
            log.info("The {} are in the lead this week.", getCabalName(getCabalHighestScore()));
        }

        long milliToChange = 0;
        if (isNextPeriodChangeInPast()) {
            log.info("Next period change was in the past (server was offline), changing periods now!");
        } else {
            setCalendarForNextPeriodChange();
            milliToChange = getMilliToPeriodChange();
        }

        // Schedule a time for the next period change.
        ThreadPool.schedule(new SevenSignsPeriodChange(), milliToChange);
        log.info("Next period begins in {}", DurationFormatUtils.formatDurationWords(milliToChange, true, true));
    }

    private boolean isNextPeriodChangeInPast() {
        final Calendar lastPeriodChange = ClockBridge.calendar();
        switch (currentPeriod) {
            case PERIOD_SEAL_VALIDATION:
            case PERIOD_COMPETITION: {
                lastPeriodChange.set(Calendar.DAY_OF_WEEK, PERIOD_START_DAY);
                lastPeriodChange.set(Calendar.HOUR_OF_DAY, PERIOD_START_HOUR);
                lastPeriodChange.set(Calendar.MINUTE, PERIOD_START_MINS);
                lastPeriodChange.set(Calendar.SECOND, 0);

                // if we hit next week, just turn back 1 week
                if (ClockBridge.calendar().before(lastPeriodChange)) {
                    lastPeriodChange.add(Calendar.HOUR, -24 * 7);
                }
                break;
            }
            case PERIOD_COMP_RECRUITING:
            case PERIOD_COMP_RESULTS: {
                // because of the short duration of this period, just check it from last save
                lastPeriodChange.setTimeInMillis(lastSave.getTimeInMillis() + PERIOD_MINOR_LENGTH);
                break;
            }
        }

        // because of previous "date" column usage, check only if it already contains usable data for us
        return (lastSave.getTimeInMillis() > 7) && lastSave.before(lastPeriodChange);
    }

    /**
     * Registers all random spawns and auto-chats for Seven Signs NPCs, along with spawns for the Preachers of Doom and Orators of Revelations at the beginning of the Seal Validation period.
     */
    public void spawnSevenSignsNPC() {
        final AutoSpawnInstance merchantSpawn =
                AutoSpawnHandler.getInstance().getAutoSpawnInstance(MAMMON_MERCHANT_ID, false);
        final AutoSpawnInstance blacksmithSpawn =
                AutoSpawnHandler.getInstance().getAutoSpawnInstance(MAMMON_BLACKSMITH_ID, false);
        final AutoSpawnInstance lilithSpawn = AutoSpawnHandler.getInstance().getAutoSpawnInstance(LILITH_NPC_ID, false);
        final AutoSpawnInstance anakimSpawn = AutoSpawnHandler.getInstance().getAutoSpawnInstance(ANAKIM_NPC_ID, false);
        final List<AutoSpawnInstance> crestOfDawnSpawns =
                AutoSpawnHandler.getInstance().getAutoSpawnInstances(CREST_OF_DAWN_ID);
        final List<AutoSpawnInstance> crestOfDuskSpawns =
                AutoSpawnHandler.getInstance().getAutoSpawnInstances(CREST_OF_DUSK_ID);
        final List<AutoSpawnInstance> oratorSpawns =
                AutoSpawnHandler.getInstance().getAutoSpawnInstances(ORATOR_NPC_ID);
        final List<AutoSpawnInstance> preacherSpawns =
                AutoSpawnHandler.getInstance().getAutoSpawnInstances(PREACHER_NPC_ID);
        final List<AutoSpawnInstance> marketeerSpawns =
                AutoSpawnHandler.getInstance().getAutoSpawnInstances(MAMMON_MARKETEER_ID);
        if (isSealValidationPeriod() || isCompResultsPeriod()) {
            for (AutoSpawnInstance spawnInst : marketeerSpawns) {
                AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, true);
            }

            if ((getSealOwner(SEAL_GNOSIS) == getCabalHighestScore()) && (getSealOwner(SEAL_GNOSIS) != CABAL_NULL)) {
                if (!NpcConfig.ANNOUNCE_MAMMON_SPAWN) {
                    blacksmithSpawn.setBroadcast(false);
                }

                if (!AutoSpawnHandler.getInstance()
                        .getAutoSpawnInstance(blacksmithSpawn.getObjectId(), true)
                        .isSpawnActive()) {
                    AutoSpawnHandler.getInstance().setSpawnActive(blacksmithSpawn, true);
                }

                for (AutoSpawnInstance spawnInst : oratorSpawns) {
                    if (!AutoSpawnHandler.getInstance()
                            .getAutoSpawnInstance(spawnInst.getObjectId(), true)
                            .isSpawnActive()) {
                        AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, true);
                    }
                }

                for (AutoSpawnInstance spawnInst : preacherSpawns) {
                    if (!AutoSpawnHandler.getInstance()
                            .getAutoSpawnInstance(spawnInst.getObjectId(), true)
                            .isSpawnActive()) {
                        AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, true);
                    }
                }
            } else {
                AutoSpawnHandler.getInstance().setSpawnActive(blacksmithSpawn, false);
                for (AutoSpawnInstance spawnInst : oratorSpawns) {
                    AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
                }

                for (AutoSpawnInstance spawnInst : preacherSpawns) {
                    AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
                }
            }

            if ((getSealOwner(SEAL_AVARICE) == getCabalHighestScore()) && (getSealOwner(SEAL_AVARICE) != CABAL_NULL)) {
                if (!NpcConfig.ANNOUNCE_MAMMON_SPAWN) {
                    merchantSpawn.setBroadcast(false);
                }

                if (!AutoSpawnHandler.getInstance()
                        .getAutoSpawnInstance(merchantSpawn.getObjectId(), true)
                        .isSpawnActive()) {
                    AutoSpawnHandler.getInstance().setSpawnActive(merchantSpawn, true);
                }

                switch (getCabalHighestScore()) {
                    case CABAL_DAWN: {
                        if (!AutoSpawnHandler.getInstance()
                                .getAutoSpawnInstance(lilithSpawn.getObjectId(), true)
                                .isSpawnActive()) {
                            AutoSpawnHandler.getInstance().setSpawnActive(lilithSpawn, true);
                        }

                        AutoSpawnHandler.getInstance().setSpawnActive(anakimSpawn, false);
                        for (AutoSpawnInstance dawnCrest : crestOfDawnSpawns) {
                            if (!AutoSpawnHandler.getInstance()
                                    .getAutoSpawnInstance(dawnCrest.getObjectId(), true)
                                    .isSpawnActive()) {
                                AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, true);
                            }
                        }

                        for (AutoSpawnInstance duskCrest : crestOfDuskSpawns) {
                            AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, false);
                        }
                        break;
                    }
                    case CABAL_DUSK: {
                        if (!AutoSpawnHandler.getInstance()
                                .getAutoSpawnInstance(anakimSpawn.getObjectId(), true)
                                .isSpawnActive()) {
                            AutoSpawnHandler.getInstance().setSpawnActive(anakimSpawn, true);
                        }

                        AutoSpawnHandler.getInstance().setSpawnActive(lilithSpawn, false);
                        for (AutoSpawnInstance duskCrest : crestOfDuskSpawns) {
                            if (!AutoSpawnHandler.getInstance()
                                    .getAutoSpawnInstance(duskCrest.getObjectId(), true)
                                    .isSpawnActive()) {
                                AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, true);
                            }
                        }

                        for (AutoSpawnInstance dawnCrest : crestOfDawnSpawns) {
                            AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, false);
                        }
                        break;
                    }
                }
            } else {
                AutoSpawnHandler.getInstance().setSpawnActive(merchantSpawn, false);
                AutoSpawnHandler.getInstance().setSpawnActive(lilithSpawn, false);
                AutoSpawnHandler.getInstance().setSpawnActive(anakimSpawn, false);
                for (AutoSpawnInstance dawnCrest : crestOfDawnSpawns) {
                    AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, false);
                }

                for (AutoSpawnInstance duskCrest : crestOfDuskSpawns) {
                    AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, false);
                }
            }
        } else {
            AutoSpawnHandler.getInstance().setSpawnActive(merchantSpawn, false);
            AutoSpawnHandler.getInstance().setSpawnActive(blacksmithSpawn, false);
            AutoSpawnHandler.getInstance().setSpawnActive(lilithSpawn, false);
            AutoSpawnHandler.getInstance().setSpawnActive(anakimSpawn, false);
            for (AutoSpawnInstance dawnCrest : crestOfDawnSpawns) {
                AutoSpawnHandler.getInstance().setSpawnActive(dawnCrest, false);
            }

            for (AutoSpawnInstance duskCrest : crestOfDuskSpawns) {
                AutoSpawnHandler.getInstance().setSpawnActive(duskCrest, false);
            }

            for (AutoSpawnInstance spawnInst : oratorSpawns) {
                AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
            }

            for (AutoSpawnInstance spawnInst : preacherSpawns) {
                AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
            }

            for (AutoSpawnInstance spawnInst : marketeerSpawns) {
                AutoSpawnHandler.getInstance().setSpawnActive(spawnInst, false);
            }
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static SevenSigns getInstance() {
        final SevenSigns current = instance;
        if (current == null) {
            throw new IllegalStateException("SevenSigns is used before the Spring context created it.");
        }
        return current;
    }

    public static long calcContributionScore(long blueCount, long greenCount, long redCount) {
        long contrib = blueCount * BLUE_CONTRIB_POINTS;
        contrib += greenCount * GREEN_CONTRIB_POINTS;
        contrib += redCount * RED_CONTRIB_POINTS;
        return contrib;
    }

    public static long calcAncientAdenaReward(long blueCount, long greenCount, long redCount) {
        long reward = blueCount * SEAL_STONE_BLUE_VALUE;
        reward += greenCount * SEAL_STONE_GREEN_VALUE;
        reward += redCount * SEAL_STONE_RED_VALUE;
        return reward;
    }

    public static String getCabalShortName(int cabal) {
        switch (cabal) {
            case CABAL_DAWN: {
                return "dawn";
            }
            case CABAL_DUSK: {
                return "dusk";
            }
        }

        return "No Cabal";
    }

    public static String getCabalName(int cabal) {
        switch (cabal) {
            case CABAL_DAWN: {
                return "Lords of Dawn";
            }
            case CABAL_DUSK: {
                return "Revolutionaries of Dusk";
            }
        }

        return "No Cabal";
    }

    public static String getSealName(int seal, boolean shortName) {
        String sealName = (!shortName) ? "Seal of " : "";

        switch (seal) {
            case SEAL_AVARICE: {
                sealName += "Avarice";
                break;
            }
            case SEAL_GNOSIS: {
                sealName += "Gnosis";
                break;
            }
            case SEAL_STRIFE: {
                sealName += "Strife";
                break;
            }
        }

        return sealName;
    }

    private int getDaysToPeriodChange() {
        final int numDays = nextPeriodChange.get(Calendar.DAY_OF_WEEK) - PERIOD_START_DAY;
        if (numDays < 0) {
            return 0 - numDays;
        }

        return 7 - numDays;
    }

    public long getMilliToPeriodChange() {
        return (nextPeriodChange.getTimeInMillis() - ClockBridge.wallMillis());
    }

    protected void setCalendarForNextPeriodChange() {
        // Calculate the number of days until the next period
        // A period starts at 18:00 pm (local time), like on official servers.
        switch (currentPeriod) {
            case PERIOD_SEAL_VALIDATION:
            case PERIOD_COMPETITION: {
                int daysToChange = getDaysToPeriodChange();
                if (daysToChange == 7) {
                    if (nextPeriodChange.get(Calendar.HOUR_OF_DAY) < PERIOD_START_HOUR) {
                        daysToChange = 0;
                    } else if ((nextPeriodChange.get(Calendar.HOUR_OF_DAY) == PERIOD_START_HOUR)
                            && (nextPeriodChange.get(Calendar.MINUTE) < PERIOD_START_MINS)) {
                        daysToChange = 0;
                    }
                }

                // Otherwise...
                if (daysToChange > 0) {
                    nextPeriodChange.add(Calendar.DATE, daysToChange);
                }

                nextPeriodChange.set(Calendar.HOUR_OF_DAY, PERIOD_START_HOUR);
                nextPeriodChange.set(Calendar.MINUTE, PERIOD_START_MINS);
                break;
            }
            case PERIOD_COMP_RECRUITING:
            case PERIOD_COMP_RESULTS: {
                nextPeriodChange.add(Calendar.MILLISECOND, PERIOD_MINOR_LENGTH);
                break;
            }
        }

        log.info("Next period change set to {}", nextPeriodChange.getTime());
    }

    public String getCurrentPeriodName() {
        String periodName = null;

        switch (currentPeriod) {
            case PERIOD_COMP_RECRUITING: {
                periodName = "Quest Event Initialization";
                break;
            }
            case PERIOD_COMPETITION: {
                periodName = "Competition (Quest Event)";
                break;
            }
            case PERIOD_COMP_RESULTS: {
                periodName = "Quest Event Results";
                break;
            }
            case PERIOD_SEAL_VALIDATION: {
                periodName = "Seal Validation";
                break;
            }
        }

        return periodName;
    }

    /**
     * @return {@code true} if it's competition period, {@code false} otherwise
     */
    public boolean isCompetitionPeriod() {
        return (currentPeriod == PERIOD_COMPETITION);
    }

    public boolean isSealValidationPeriod() {
        return (currentPeriod == PERIOD_SEAL_VALIDATION);
    }

    public boolean isCompResultsPeriod() {
        return (currentPeriod == PERIOD_COMP_RESULTS);
    }

    /**
     * returns true if the given date is in Seal Validation or in Quest Event Results period
     * @param date
     * @return
     */
    public boolean isDateInSealValidPeriod(Calendar date) {
        final long nextPeriodChange = getMilliToPeriodChange();
        long nextQuestStart = 0;
        long nextValidStart = 0;
        long tillDate = date.getTimeInMillis() - ClockBridge.wallMillis();
        while (((2 * PERIOD_MAJOR_LENGTH) + (2 * PERIOD_MINOR_LENGTH)) < tillDate) {
            tillDate -= ((2 * PERIOD_MAJOR_LENGTH) + (2 * PERIOD_MINOR_LENGTH));
        }
        while (tillDate < 0) {
            tillDate += ((2 * PERIOD_MAJOR_LENGTH) + (2 * PERIOD_MINOR_LENGTH));
        }

        switch (currentPeriod) {
            case PERIOD_COMP_RECRUITING: {
                nextValidStart = nextPeriodChange + PERIOD_MAJOR_LENGTH;
                nextQuestStart = nextValidStart + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
                break;
            }
            case PERIOD_COMPETITION: {
                nextValidStart = nextPeriodChange;
                nextQuestStart = nextPeriodChange + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
                break;
            }
            case PERIOD_COMP_RESULTS: {
                nextQuestStart = nextPeriodChange + PERIOD_MAJOR_LENGTH;
                nextValidStart = nextQuestStart + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
                break;
            }
            case PERIOD_SEAL_VALIDATION: {
                nextQuestStart = nextPeriodChange;
                nextValidStart = nextPeriodChange + PERIOD_MAJOR_LENGTH + PERIOD_MINOR_LENGTH;
                break;
            }
        }

        if (((nextQuestStart < tillDate) && (tillDate < nextValidStart))
                || ((nextValidStart < nextQuestStart)
                        && ((tillDate < nextValidStart) || (nextQuestStart < tillDate)))) {
            return false;
        }

        return true;
    }

    public int getCurrentScore(int cabal) {
        final double totalStoneScore = dawnStoneScore + duskStoneScore;

        switch (cabal) {
            case CABAL_NULL: {
                return 0;
            }
            case CABAL_DAWN: {
                return Math.round((float) (dawnStoneScore / ((float) totalStoneScore == 0 ? 1 : totalStoneScore)) * 500)
                        + dawnFestivalScore;
            }
            case CABAL_DUSK: {
                return Math.round((float) (duskStoneScore / ((float) totalStoneScore == 0 ? 1 : totalStoneScore)) * 500)
                        + duskFestivalScore;
            }
        }

        return 0;
    }

    public double getCurrentStoneScore(int cabal) {
        switch (cabal) {
            case CABAL_NULL: {
                return 0;
            }
            case CABAL_DAWN: {
                return dawnStoneScore;
            }
            case CABAL_DUSK: {
                return duskStoneScore;
            }
        }

        return 0;
    }

    public int getCurrentFestivalScore(int cabal) {
        switch (cabal) {
            case CABAL_NULL: {
                return 0;
            }
            case CABAL_DAWN: {
                return dawnFestivalScore;
            }
            case CABAL_DUSK: {
                return duskFestivalScore;
            }
        }

        return 0;
    }

    public int getCabalHighestScore() {
        if (getCurrentScore(CABAL_DUSK) == getCurrentScore(CABAL_DAWN)) {
            return CABAL_NULL;
        } else if (getCurrentScore(CABAL_DUSK) > getCurrentScore(CABAL_DAWN)) {
            return CABAL_DUSK;
        } else {
            return CABAL_DAWN;
        }
    }

    public int getSealOwner(int seal) {
        return signsSealOwners.get(seal);
    }

    public int getSealProportion(int seal, int cabal) {
        if (cabal == CABAL_NULL) {
            return 0;
        } else if (cabal == CABAL_DUSK) {
            return signsDuskSealTotals.get(seal);
        } else {
            return signsDawnSealTotals.get(seal);
        }
    }

    public int getTotalMembers(int cabal) {
        int cabalMembers = 0;
        final String cabalName = getCabalShortName(cabal);
        for (StatSet sevenDat : signsPlayerData.values()) {
            if (cabalName.equals(sevenDat.getString("cabal", null))) {
                cabalMembers++;
            }
        }

        return cabalMembers;
    }

    public int getPlayerStoneContrib(int objectId) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        if (currPlayer == null) {
            return 0;
        }

        int stoneCount = 0;
        stoneCount += currPlayer.getInt("red_stones");
        stoneCount += currPlayer.getInt("green_stones");
        stoneCount += currPlayer.getInt("blue_stones");
        return stoneCount;
    }

    public int getPlayerContribScore(int objectId) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        if (currPlayer == null) {
            return 0;
        }

        return currPlayer.getInt("contribution_score");
    }

    public int getPlayerAdenaCollect(int objectId) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        if (currPlayer == null) {
            return 0;
        }

        return currPlayer.getInt("ancient_adena_amount");
    }

    public int getPlayerSeal(int objectId) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        if (currPlayer == null) {
            return SEAL_NULL;
        }

        return currPlayer.getInt("seal");
    }

    public int getPlayerCabal(int objectId) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        if (currPlayer == null) {
            return CABAL_NULL;
        }

        final String playerCabal = currPlayer.getString("cabal", null);
        if ("dawn".equalsIgnoreCase(playerCabal)) {
            return CABAL_DAWN;
        } else if ("dusk".equalsIgnoreCase(playerCabal)) {
            return CABAL_DUSK;
        } else {
            return CABAL_NULL;
        }
    }

    /**
     * Restores all Seven Signs data and settings, usually called at server startup.
     */
    protected void restoreSevenSignsData() {
        try {
            for (SevenSignsParticipant participant : participantRepository.findAll()) {
                final StatSet sevenDat = new StatSet();
                sevenDat.set("charId", participant.objectId());
                sevenDat.set("cabal", participant.cabal());
                sevenDat.set("seal", participant.seal());
                sevenDat.set("red_stones", participant.redStones());
                sevenDat.set("green_stones", participant.greenStones());
                sevenDat.set("blue_stones", participant.blueStones());
                sevenDat.set("ancient_adena_amount", participant.ancientAdenaAmount());
                sevenDat.set("contribution_score", participant.contributionScore());
                signsPlayerData.put(participant.objectId(), sevenDat);
            }

            final SevenSignsStatus status = statusRepository.find();
            currentCycle = status.currentCycle();
            currentPeriod = status.activePeriod();
            previousWinner = status.previousWinner();
            dawnStoneScore = status.dawnStoneScore();
            dawnFestivalScore = status.dawnFestivalScore();
            duskStoneScore = status.duskStoneScore();
            duskFestivalScore = status.duskFestivalScore();
            signsSealOwners.put(SEAL_AVARICE, status.avariceOwner());
            signsSealOwners.put(SEAL_GNOSIS, status.gnosisOwner());
            signsSealOwners.put(SEAL_STRIFE, status.strifeOwner());

            signsDawnSealTotals.put(SEAL_AVARICE, status.avariceDawnScore());
            signsDawnSealTotals.put(SEAL_GNOSIS, status.gnosisDawnScore());
            signsDawnSealTotals.put(SEAL_STRIFE, status.strifeDawnScore());
            signsDuskSealTotals.put(SEAL_AVARICE, status.avariceDuskScore());
            signsDuskSealTotals.put(SEAL_GNOSIS, status.gnosisDuskScore());
            signsDuskSealTotals.put(SEAL_STRIFE, status.strifeDuskScore());

            lastSave.setTimeInMillis(epochMillisOrZero(status.savedAt()));
        } catch (DataAccessException e) {
            log.error("Unable to load Seven Signs data from database: {}", e.getMessage(), e);
        }

        // Festival data is loaded now after the Seven Signs engine data.
    }

    private static SevenSignsParticipant toParticipant(StatSet sevenDat) {
        return SevenSignsParticipant.builder()
                .objectId(sevenDat.getInt("charId"))
                .cabal(sevenDat.getString("cabal", null))
                .seal(sevenDat.getInt("seal"))
                .redStones(sevenDat.getInt("red_stones"))
                .greenStones(sevenDat.getInt("green_stones"))
                .blueStones(sevenDat.getInt("blue_stones"))
                .ancientAdenaAmount(sevenDat.getDouble("ancient_adena_amount"))
                .contributionScore(sevenDat.getDouble("contribution_score"))
                .build();
    }

    /**
     * Saves all Seven Signs player data.<br>
     * Should be called on period change and shutdown only.
     */
    public void saveSevenSignsData() {
        try {
            participantRepository.saveAll(signsPlayerData.values().stream()
                    .map(SevenSigns::toParticipant)
                    .toList());
        } catch (DataAccessException e) {
            log.error("Unable to save data to database: {}", e.getMessage(), e);
        }
    }

    public void saveSevenSignsData(int objectId) {
        final StatSet sevenDat = signsPlayerData.get(objectId);
        if (sevenDat == null) {
            return;
        }

        try {
            participantRepository.save(toParticipant(sevenDat));
        } catch (DataAccessException e) {
            log.error("Unable to save data to database: {}", e.getMessage(), e);
        }
    }

    public void saveSevenSignsStatus() {
        final List<Integer> accumulatedBonuses = new ArrayList<>();
        for (int i = 0; i < SevenSignsFestival.FESTIVAL_COUNT; i++) {
            accumulatedBonuses.add(SevenSignsFestival.getInstance().getAccumulatedBonus(i));
        }

        lastSave = ClockBridge.calendar();
        try {
            statusRepository.update(SevenSignsStatus.builder()
                    .currentCycle(currentCycle)
                    .festivalCycle(SevenSignsFestival.getInstance().getCurrentFestivalCycle())
                    .activePeriod(currentPeriod)
                    .savedAt(instantOrNull(lastSave.getTimeInMillis()))
                    .previousWinner(previousWinner)
                    .dawnStoneScore(dawnStoneScore)
                    .dawnFestivalScore(dawnFestivalScore)
                    .duskStoneScore(duskStoneScore)
                    .duskFestivalScore(duskFestivalScore)
                    .avariceOwner(signsSealOwners.get(SEAL_AVARICE))
                    .gnosisOwner(signsSealOwners.get(SEAL_GNOSIS))
                    .strifeOwner(signsSealOwners.get(SEAL_STRIFE))
                    .avariceDawnScore(signsDawnSealTotals.get(SEAL_AVARICE))
                    .gnosisDawnScore(signsDawnSealTotals.get(SEAL_GNOSIS))
                    .strifeDawnScore(signsDawnSealTotals.get(SEAL_STRIFE))
                    .avariceDuskScore(signsDuskSealTotals.get(SEAL_AVARICE))
                    .gnosisDuskScore(signsDuskSealTotals.get(SEAL_GNOSIS))
                    .strifeDuskScore(signsDuskSealTotals.get(SEAL_STRIFE))
                    .accumulatedBonuses(accumulatedBonuses)
                    .build());
        } catch (DataAccessException e) {
            log.error("Unable to save data to database: {}", e.getMessage(), e);
        }
    }

    /**
     * Used to reset the cabal details of all players, and update the database.<br>
     * Primarily used when beginning a new cycle, and should otherwise never be called.
     */
    protected void resetPlayerData() {
        // Reset each player's contribution data as well as seal and cabal.
        for (StatSet sevenDat : signsPlayerData.values()) {
            // Reset the player's cabal and seal information
            sevenDat.remove("cabal");
            sevenDat.set("seal", SEAL_NULL);
            sevenDat.set("contribution_score", 0);
        }
    }

    /**
     * Used to specify cabal-related details for the specified player.<br>
     * This method checks to see if the player has registered before and will update the database if necessary.
     * @param objectId
     * @param chosenCabal
     * @param chosenSeal
     * @return the cabal ID the player has joined.
     */
    public int setPlayerInfo(int objectId, int chosenCabal, int chosenSeal) {
        StatSet currPlayerData = signsPlayerData.get(objectId);
        if (currPlayerData != null) {
            // If the seal validation period has passed,
            // cabal information was removed and so "re-register" player
            currPlayerData.set("cabal", getCabalShortName(chosenCabal));
            currPlayerData.set("seal", chosenSeal);
            signsPlayerData.put(objectId, currPlayerData);
        } else {
            currPlayerData = new StatSet();
            currPlayerData.set("charId", objectId);
            currPlayerData.set("cabal", getCabalShortName(chosenCabal));
            currPlayerData.set("seal", chosenSeal);
            currPlayerData.set("red_stones", 0);
            currPlayerData.set("green_stones", 0);
            currPlayerData.set("blue_stones", 0);
            currPlayerData.set("ancient_adena_amount", 0);
            currPlayerData.set("contribution_score", 0);
            signsPlayerData.put(objectId, currPlayerData);

            // Update data in database, as we have a new player signing up.
            try {
                participantRepository.save(toParticipant(currPlayerData));
            } catch (DataAccessException e) {
                log.error("Failed to save data: {}", e.getMessage(), e);
            }
        }

        // Increasing Seal total score for the player chosen Seal.
        if ("dawn".equals(currPlayerData.getString("cabal"))) {
            signsDawnSealTotals.put(chosenSeal, signsDawnSealTotals.get(chosenSeal) + 1);
        } else {
            signsDuskSealTotals.put(chosenSeal, signsDuskSealTotals.get(chosenSeal) + 1);
        }

        if (!FeatureConfig.ALT_SEVENSIGNS_LAZY_UPDATE) {
            saveSevenSignsStatus();
        }

        return chosenCabal;
    }

    /**
     * Returns the amount of ancient adena the specified player can claim, if any.<br>
     * If removeReward = True, all the ancient adena owed to them is removed, then DB is updated.
     * @param objectId
     * @param removeReward
     * @return
     */
    public int getAncientAdenaReward(int objectId, boolean removeReward) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        final int rewardAmount = currPlayer.getInt("ancient_adena_amount");
        currPlayer.set("red_stones", 0);
        currPlayer.set("green_stones", 0);
        currPlayer.set("blue_stones", 0);
        currPlayer.set("ancient_adena_amount", 0);
        if (removeReward) {
            signsPlayerData.put(objectId, currPlayer);
            if (!FeatureConfig.ALT_SEVENSIGNS_LAZY_UPDATE) {
                saveSevenSignsData(objectId);
                saveSevenSignsStatus();
            }
        }

        return rewardAmount;
    }

    /**
     * Used to add the specified player's seal stone contribution points to the current total for their cabal.<br>
     * Returns the point score the contribution was worth.<br>
     * Each stone count <b>must be</b> broken down and specified by the stone's color.
     * @param objectId
     * @param blueCount
     * @param greenCount
     * @param redCount
     * @return
     */
    public long addPlayerStoneContrib(int objectId, long blueCount, long greenCount, long redCount) {
        final StatSet currPlayer = signsPlayerData.get(objectId);
        final long contribScore = calcContributionScore(blueCount, greenCount, redCount);
        final long totalContribScore = currPlayer.getLong("contribution_score") + contribScore;
        if (totalContribScore > FeatureConfig.ALT_MAXIMUM_PLAYER_CONTRIB) {
            return -1;
        }

        final long totalAncientAdena =
                currPlayer.getLong("ancient_adena_amount") + calcAncientAdenaReward(blueCount, greenCount, redCount);

        currPlayer.set("red_stones", currPlayer.getInt("red_stones") + redCount);
        currPlayer.set("green_stones", currPlayer.getInt("green_stones") + greenCount);
        currPlayer.set("blue_stones", currPlayer.getInt("blue_stones") + blueCount);
        currPlayer.set("ancient_adena_amount", totalAncientAdena);
        currPlayer.set("contribution_score", totalContribScore);
        signsPlayerData.put(objectId, currPlayer);

        switch (getPlayerCabal(objectId)) {
            case CABAL_DAWN: {
                dawnStoneScore += contribScore;
                break;
            }
            case CABAL_DUSK: {
                duskStoneScore += contribScore;
                break;
            }
        }

        if (!FeatureConfig.ALT_SEVENSIGNS_LAZY_UPDATE) {
            saveSevenSignsData(objectId);
            saveSevenSignsStatus();
        }

        return contribScore;
    }

    /**
     * Adds the specified number of festival points to the specified cabal.<br>
     * Remember, the same number of points are <b>deducted from the rival cabal</b> to maintain proportionality.
     * @param cabal
     * @param amount
     */
    public void addFestivalScore(int cabal, int amount) {
        if (cabal == CABAL_DUSK) {
            duskFestivalScore += amount;

            // To prevent negative scores!
            if (dawnFestivalScore >= amount) {
                dawnFestivalScore -= amount;
            }
        } else {
            dawnFestivalScore += amount;
            if (duskFestivalScore >= amount) {
                duskFestivalScore -= amount;
            }
        }
    }

    /**
     * Send info on the current Seven Signs period to the specified player.
     * @param player
     */
    public void sendCurrentPeriodMsg(Player player) {
        switch (currentPeriod) {
            case PERIOD_COMP_RECRUITING: {
                player.sendPacket(SystemMessageId.SEVEN_SIGNS_PREPARATIONS_HAVE_BEGUN_FOR_THE_NEXT_QUEST_EVENT);
                break;
            }
            case PERIOD_COMPETITION: {
                player.sendPacket(
                        SystemMessageId
                                .SEVEN_SIGNS_THE_QUEST_EVENT_PERIOD_HAS_BEGUN_SPEAK_WITH_A_PRIEST_OF_DAWN_OR_DUSK_PRIESTESS_IF_YOU_WISH_TO_PARTICIPATE_IN_THE_EVENT);
                break;
            }
            case PERIOD_COMP_RESULTS: {
                player.sendPacket(SystemMessageId.SEVEN_SIGNS_QUEST_EVENT_HAS_ENDED_RESULTS_ARE_BEING_TALLIED);
                break;
            }
            case PERIOD_SEAL_VALIDATION: {
                player.sendPacket(
                        SystemMessageId
                                .SEVEN_SIGNS_THIS_IS_THE_SEAL_VALIDATION_PERIOD_A_NEW_QUEST_EVENT_PERIOD_BEGINS_NEXT_MONDAY);
                break;
            }
        }
    }

    /**
     * Sends the built-in system message specified by sysMsgId to all online players.
     * @param sysMsgId
     */
    public void sendMessageToAll(SystemMessageId sysMsgId) {
        World.broadcastToAllOnlinePlayers(new SystemMessage(sysMsgId));
    }

    /**
     * Used to initialize the seals for each cabal.<br>
     * (Used at startup or at beginning of a new cycle).<br>
     * This method should be called after <b>resetSeals()</b> and <b>calcNewSealOwners()</b> on a new cycle.
     */
    protected void initializeSeals() {
        for (Entry<Integer, Integer> e : signsSealOwners.entrySet()) {
            if (e.getValue() != CABAL_NULL) {
                if (isSealValidationPeriod()) {
                    log.info("The {} have won the {}.", getCabalName(e.getValue()), getSealName(e.getKey(), false));
                } else {
                    log.info(
                            "The {} is currently owned by {}.",
                            getSealName(e.getKey(), false),
                            getCabalName(e.getValue()));
                }
            } else {
                log.info("The {} remains unclaimed.", getSealName(e.getKey(), false));
            }
        }
    }

    /**
     * Only really used at the beginning of a new cycle, this method resets all seal-related data.
     */
    protected void resetSeals() {
        signsDawnSealTotals.put(SEAL_AVARICE, 0);
        signsDawnSealTotals.put(SEAL_GNOSIS, 0);
        signsDawnSealTotals.put(SEAL_STRIFE, 0);
        signsDuskSealTotals.put(SEAL_AVARICE, 0);
        signsDuskSealTotals.put(SEAL_GNOSIS, 0);
        signsDuskSealTotals.put(SEAL_STRIFE, 0);
    }

    /**
     * Calculates the ownership of the three Seals of the Seven Signs, based on various criterion.<br>
     * Should only ever called at the beginning of a new cycle.
     */
    protected void calcNewSealOwners() {
        for (Integer currSeal : signsDawnSealTotals.keySet()) {
            final int prevSealOwner = signsSealOwners.get(currSeal);
            int newSealOwner = CABAL_NULL;
            final int dawnProportion = getSealProportion(currSeal, CABAL_DAWN);
            final int totalDawnMembers = getTotalMembers(CABAL_DAWN) == 0 ? 1 : getTotalMembers(CABAL_DAWN);
            final int dawnPercent = Math.round(((float) dawnProportion / totalDawnMembers) * 100);
            final int duskProportion = getSealProportion(currSeal, CABAL_DUSK);
            final int totalDuskMembers = getTotalMembers(CABAL_DUSK) == 0 ? 1 : getTotalMembers(CABAL_DUSK);
            final int duskPercent = Math.round(((float) duskProportion / totalDuskMembers) * 100);

            /**
             * If a Seal was already closed or owned by the opponent and the new winner wants to assume ownership of the Seal, 35% or more of the members of the Cabal must have chosen the Seal.<br>
             * If they chose less than 35%, they cannot own the Seal.<br>
             * If the Seal was owned by the winner in the previous Seven Signs, they can retain that seal if 10% or more members have chosen it.<br>
             * If they want to possess a new Seal, at least 35% of the members of the Cabal must have chosen the new Seal.
             */
            switch (prevSealOwner) {
                case CABAL_NULL: {
                    switch (getCabalHighestScore()) {
                        case CABAL_NULL: {
                            newSealOwner = CABAL_NULL;
                            break;
                        }
                        case CABAL_DAWN: {
                            if (dawnPercent >= 35) {
                                newSealOwner = CABAL_DAWN;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                        case CABAL_DUSK: {
                            if (duskPercent >= 35) {
                                newSealOwner = CABAL_DUSK;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                    }
                    break;
                }
                case CABAL_DAWN: {
                    switch (getCabalHighestScore()) {
                        case CABAL_NULL: {
                            if (dawnPercent >= 10) {
                                newSealOwner = CABAL_DAWN;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                        case CABAL_DAWN: {
                            if (dawnPercent >= 10) {
                                newSealOwner = CABAL_DAWN;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                        case CABAL_DUSK: {
                            if (duskPercent >= 35) {
                                newSealOwner = CABAL_DUSK;
                            } else if (dawnPercent >= 10) {
                                newSealOwner = CABAL_DAWN;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                    }
                    break;
                }
                case CABAL_DUSK: {
                    switch (getCabalHighestScore()) {
                        case CABAL_NULL: {
                            if (duskPercent >= 10) {
                                newSealOwner = CABAL_DUSK;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                        case CABAL_DAWN: {
                            if (dawnPercent >= 35) {
                                newSealOwner = CABAL_DAWN;
                            } else if (duskPercent >= 10) {
                                newSealOwner = CABAL_DUSK;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                        case CABAL_DUSK: {
                            if (duskPercent >= 10) {
                                newSealOwner = CABAL_DUSK;
                            } else {
                                newSealOwner = CABAL_NULL;
                            }
                            break;
                        }
                    }
                    break;
                }
            }

            signsSealOwners.put(currSeal, newSealOwner);

            // Alert all online players to new seal status.
            switch (currSeal) {
                case SEAL_AVARICE: {
                    if (newSealOwner == CABAL_DAWN) {
                        sendMessageToAll(
                                SystemMessageId.SEVEN_SIGNS_THE_LORDS_OF_DAWN_HAVE_OBTAINED_THE_SEAL_OF_AVARICE);
                    } else if (newSealOwner == CABAL_DUSK) {
                        sendMessageToAll(
                                SystemMessageId
                                        .SEVEN_SIGNS_THE_REVOLUTIONARIES_OF_DUSK_HAVE_OBTAINED_THE_SEAL_OF_AVARICE);
                    }
                    break;
                }
                case SEAL_GNOSIS: {
                    if (newSealOwner == CABAL_DAWN) {
                        sendMessageToAll(
                                SystemMessageId.SEVEN_SIGNS_THE_LORDS_OF_DAWN_HAVE_OBTAINED_THE_SEAL_OF_GNOSIS);
                    } else if (newSealOwner == CABAL_DUSK) {
                        sendMessageToAll(
                                SystemMessageId
                                        .SEVEN_SIGNS_THE_REVOLUTIONARIES_OF_DUSK_HAVE_OBTAINED_THE_SEAL_OF_GNOSIS);
                    }
                    break;
                }
                case SEAL_STRIFE: {
                    if (newSealOwner == CABAL_DAWN) {
                        sendMessageToAll(
                                SystemMessageId.SEVEN_SIGNS_THE_LORDS_OF_DAWN_HAVE_OBTAINED_THE_SEAL_OF_STRIFE);
                    } else if (newSealOwner == CABAL_DUSK) {
                        sendMessageToAll(
                                SystemMessageId
                                        .SEVEN_SIGNS_THE_REVOLUTIONARIES_OF_DUSK_HAVE_OBTAINED_THE_SEAL_OF_STRIFE);
                    }

                    CastleManager.getInstance().validateTaxes(newSealOwner);
                    break;
                }
            }
        }
    }

    /**
     * This method is called to remove all players from catacombs and necropolises, who belong to the losing cabal.<br>
     * Should only ever called at the beginning of Seal Validation.
     * @param compWinner
     */
    protected void teleLosingCabalFromDungeons(String compWinner) {
        for (Player player : World.getPlayers()) {
            final StatSet currPlayer = signsPlayerData.get(player.getObjectId());
            if (isSealValidationPeriod() || isCompResultsPeriod()) {
                if (!player.isGM()
                        && player.isIn7sDungeon()
                        && ((currPlayer == null) || !compWinner.equals(currPlayer.getString("cabal", null)))) {
                    player.teleToLocation(TeleportWhereType.TOWN);
                    player.setIn7sDungeon(false);
                    player.sendMessage(
                            "You have been teleported to the nearest town due to the beginning of the Seal Validation"
                                    + " period.");
                }
            } else {
                if (!player.isGM()
                        && player.isIn7sDungeon()
                        && ((currPlayer == null) || (currPlayer.getString("cabal", null) == null))) {
                    player.teleToLocation(TeleportWhereType.TOWN);
                    player.setIn7sDungeon(false);
                    player.sendMessage(
                            "You have been teleported to the nearest town because you have not signed for any cabal.");
                }
            }
        }
    }

    /**
     * The primary controller of period change of the Seven Signs system.<br>
     * This runs all related tasks depending on the period that is about to begin.
     */
    protected class SevenSignsPeriodChange implements Runnable {
        @Override
        public void run() {
            // Remember the period check here refers to the period just ENDED!
            final int periodEnded = currentPeriod;
            currentPeriod++;

            switch (periodEnded) {
                case PERIOD_COMP_RECRUITING: // Initialization
                {
                    // Start the Festival of Darkness cycle.
                    SevenSignsFestival.getInstance().startFestivalManager();

                    // Send message that Competition has begun.
                    sendMessageToAll(
                            SystemMessageId
                                    .SEVEN_SIGNS_THE_COMPETITION_PERIOD_HAS_BEGUN_VISIT_A_PRIEST_OF_DAWN_OR_PRIESTESS_OF_DUSK_TO_PARTICIPATE_IN_THE_EVENT);
                    break;
                }
                case PERIOD_COMPETITION: // Results Calculation
                {
                    // Send message that Competition has ended.
                    sendMessageToAll(
                            SystemMessageId
                                    .SEVEN_SIGNS_THE_COMPETITION_PERIOD_HAS_ENDED_THE_NEXT_QUEST_EVENT_WILL_START_IN_ONE_WEEK);
                    final int compWinner = getCabalHighestScore();

                    // Schedule a stop of the festival engine and reward highest ranking members from cycle
                    SevenSignsFestival.getInstance()
                            .getFestivalManagerSchedule()
                            .cancel(false);
                    SevenSignsFestival.getInstance().rewardHighestRanked();
                    calcNewSealOwners();
                    switch (compWinner) {
                        case CABAL_DAWN: {
                            sendMessageToAll(SystemMessageId.SEVEN_SIGNS_THE_LORDS_OF_DAWN_HAVE_WON);
                            break;
                        }
                        case CABAL_DUSK: {
                            sendMessageToAll(SystemMessageId.SEVEN_SIGNS_THE_REVOLUTIONARIES_OF_DUSK_HAVE_WON);
                            break;
                        }
                    }

                    previousWinner = compWinner;

                    // Reset Castle ticket buy count
                    final List<Castle> castles = CastleManager.getInstance().getCastles();
                    for (Castle castle : castles) {
                        castle.setTicketBuyCount(0);
                    }
                    break;
                }
                case PERIOD_COMP_RESULTS: // Seal Validation
                {
                    // Perform initial Seal Validation set up.
                    initializeSeals();

                    // Buff/Debuff members of the event when Seal of Strife captured.
                    giveCPMult(getSealOwner(SEAL_STRIFE));

                    // Send message that Seal Validation has begun.
                    sendMessageToAll(SystemMessageId.SEVEN_SIGNS_THE_SEAL_VALIDATION_PERIOD_HAS_BEGUN);
                    log.info(
                            "The {} have won the competition with {} points!",
                            getCabalName(previousWinner),
                            getCurrentScore(previousWinner));
                    break;
                }
                case PERIOD_SEAL_VALIDATION: // Reset for New Cycle
                {
                    // Ensure a cycle restart when this period ends.
                    currentPeriod = PERIOD_COMP_RECRUITING;

                    // Send message that Seal Validation has ended.
                    sendMessageToAll(SystemMessageId.SEVEN_SIGNS_THE_SEAL_VALIDATION_PERIOD_HAS_ENDED);

                    // Clear Seal of Strife influence.
                    removeCPMult();

                    // Reset all data
                    resetPlayerData();
                    resetSeals();
                    currentCycle++;

                    // Reset all Festival-related data and remove any unused blood offerings.
                    // NOTE: A full update of Festival data in the database is also performed.
                    SevenSignsFestival.getInstance().resetFestivalData(false);
                    dawnStoneScore = 0;
                    duskStoneScore = 0;
                    dawnFestivalScore = 0;
                    duskFestivalScore = 0;
                    break;
                }
            }

            // Make sure all Seven Signs data is saved for future use.
            saveSevenSignsData();
            saveSevenSignsStatus();

            teleLosingCabalFromDungeons(getCabalShortName(getCabalHighestScore()));

            final SSQInfo ss = new SSQInfo();
            World.broadcastToAllOnlinePlayers(ss);
            spawnSevenSignsNPC();

            log.info("The {} period has begun!", getCurrentPeriodName());
            setCalendarForNextPeriodChange();

            final SevenSignsPeriodChange sspc = new SevenSignsPeriodChange();
            ThreadPool.schedule(sspc, getMilliToPeriodChange());
        }
    }

    public boolean checkIsDawnPostingTicket(int itemId) {
        // TODO: I think it should be some kind of a list in the datapack for compare.
        if ((itemId > 6114) && (itemId < 6175)) {
            return true;
        }

        if ((itemId > 6801) && (itemId < 6812)) {
            return true;
        }

        if ((itemId > 7997) && (itemId < 8008)) {
            return true;
        }

        if ((itemId > 7940) && (itemId < 7951)) {
            return true;
        }

        if ((itemId > 6294) && (itemId < 6307)) {
            return true;
        }

        if ((itemId > 6831) && (itemId < 6834)) {
            return true;
        }

        if ((itemId > 8027) && (itemId < 8030)) {
            return true;
        }

        if ((itemId > 7970) && (itemId < 7973)) {
            return true;
        }

        return false;
    }

    public boolean checkIsRookiePostingTicket(int itemId) {
        // TODO: I think it should be some kind of a list in the datapack for compare.
        if ((itemId > 6174) && (itemId < 6295)) {
            return true;
        }

        if ((itemId > 6811) && (itemId < 6832)) {
            return true;
        }

        if ((itemId > 7950) && (itemId < 7971)) {
            return true;
        }

        if ((itemId > 8007) && (itemId < 8028)) {
            return true;
        }

        return false;
    }

    public void giveCPMult(int strifeOwner) {
        for (Player player : World.getPlayers()) {
            // Gives "Victor of War" passive skill to all online characters with Cabal, which controls Seal of Strife
            final int cabal = getPlayerCabal(player.getObjectId());
            if (cabal != CABAL_NULL) {
                if (cabal == strifeOwner) {
                    player.addSkill(CommonSkill.THE_VICTOR_OF_WAR.getSkill());
                } else {
                    // Gives "The Vanquished of War" passive skill to all online characters with Cabal, which does not
                    // control Seal of Strife
                    player.addSkill(CommonSkill.THE_VANQUISHED_OF_WAR.getSkill());
                }
            }
        }
    }

    public void removeCPMult() {
        // Remove SevenSigns' buffs/debuffs.
        for (Player player : World.getPlayers()) {
            player.removeSkill(CommonSkill.THE_VICTOR_OF_WAR.getSkill());
            player.removeSkill(CommonSkill.THE_VANQUISHED_OF_WAR.getSkill());
        }
    }

    public boolean checkSummonConditions(Player player) {
        if (player == null) {
            return true;
        }

        // Golems cannot be summoned by Dusk when the Seal of Strife is controlled by the Dawn
        if (isSealValidationPeriod()
                && (getSealOwner(SEAL_STRIFE) == CABAL_DAWN)
                && (getPlayerCabal(player.getObjectId()) == CABAL_DUSK)) {
            player.sendPacket(
                    SystemMessageId.DUE_TO_THE_AFFECTS_OF_THE_SEAL_OF_STRIFE_IT_IS_NOT_POSSIBLE_TO_SUMMON_AT_THIS_TIME);
            return true;
        }

        return false;
    }
}
