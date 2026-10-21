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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.clan.ClanMember;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.OnDailyReset;
import app.l2nx.highfive.mechanics.olympiad.Olympiad;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExNevitAdventTimeChange;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ExVoteSystemInfo;
import app.l2nx.highfive.repository.character.CharacterRecommendationRepository;
import app.l2nx.highfive.repository.character.CharacterSkillSaveRepository;
import app.l2nx.highfive.threads.ThreadPool;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author Mobius
 */
@Slf4j
@Component
public class DailyResetManager {
    private static volatile DailyResetManager instance;

    private static final int[] RESET_SKILLS = {
        2510, // Wondrous Cubic
        22180, // Wondrous Cubic - 1 time use
    };

    private static final int DAILY_RECOMMENDATIONS = 20;
    private static final long RECOMMENDATION_BONUS_TIME_MS = 3_600_000;

    private final CharacterSkillSaveRepository skillSaveRepository;
    private final CharacterRecommendationRepository recommendationRepository;

    public DailyResetManager(
            CharacterSkillSaveRepository skillSaveRepository,
            CharacterRecommendationRepository recommendationRepository) {
        this.skillSaveRepository = skillSaveRepository;
        this.recommendationRepository = recommendationRepository;
        instance = this;
    }

    public void load() {
        // Schedule reset everyday at 6:30.
        final ZonedDateTime now = ZonedDateTime.now(ClockBridge.wallClock());
        final ZonedDateTime todayResetTime = now.with(LocalTime.of(6, 30));
        final long nextResetTime = (todayResetTime.isBefore(now) ? todayResetTime.plusDays(1) : todayResetTime)
                .toInstant()
                .toEpochMilli();
        final long currentTime = ClockBridge.wallMillis();

        // Get today's 6:30 AM timestamp.
        final Calendar calendar = ClockBridge.calendar();
        calendar.set(Calendar.HOUR_OF_DAY, 6);
        calendar.set(Calendar.MINUTE, 30);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        final long currentResetTime = calendar.getTimeInMillis();

        // Check if 24 hours have passed since the last daily reset.
        if ((currentTime < currentResetTime)
                || (GlobalVariablesManager.getInstance().getLong(GlobalVariablesManager.DAILY_TASK_RESET, 0)
                        > currentResetTime)) {
            log.info("Next schedule at {}.", DateFormatUtils.format(nextResetTime, "dd/MM/yyyy HH:mm:ss"));
        } else {
            log.info("Daily task will run now.");
            onReset();
        }

        // Daily reset task.
        final long startDelay = Math.max(0, nextResetTime - currentTime);
        ThreadPool.scheduleAtFixedRate(this::onReset, startDelay, 86400000); // 86400000 = 1 day

        // Global save task.
        ThreadPool.scheduleAtFixedRate(this::onSave, 1800000, 1800000); // 1800000 = 30 minutes
    }

    private void onReset() {
        log.info("Starting reset of daily tasks...");

        // Store last reset time.
        GlobalVariablesManager.getInstance().set(GlobalVariablesManager.DAILY_TASK_RESET, ClockBridge.wallMillis());

        // Wednesday weekly tasks.
        final Calendar calendar = ClockBridge.calendar();
        if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.WEDNESDAY) {
            clanLeaderApply();
        }

        // Daily tasks.
        resetDailySkills();
        resetNevit();
        resetRecommends();

        // Trigger daily reset event.
        if (EventDispatcher.getInstance().hasListener(EventType.ON_DAILY_RESET)) {
            EventDispatcher.getInstance().notifyEvent(new OnDailyReset());
        }

        // Store player variables.
        for (Player player : World.getPlayers()) {
            player.getVariables().storeMe();
            player.getAccountVariables().storeMe();
        }

        log.info("Daily tasks reset completed.");
    }

    private void onSave() {
        GlobalVariablesManager.getInstance().storeMe();

        if (Olympiad.getInstance().inCompPeriod()) {
            Olympiad.getInstance().saveOlympiadStatus();
            log.info("Olympiad System: Data updated.");
        }

        SevenSigns.getInstance().saveSevenSignsStatus();
        if (!SevenSigns.getInstance().isSealValidationPeriod()) {
            SevenSignsFestival.getInstance().saveFestivalData(false);
        }
        log.info("SevenSigns: Data updated.");
    }

    private void clanLeaderApply() {
        for (Clan clan : ClanTable.getInstance().getClans()) {
            if (clan.getNewLeaderId() != 0) {
                final ClanMember member = clan.getClanMember(clan.getNewLeaderId());
                if (member == null) {
                    continue;
                }

                clan.setNewLeader(member);
            }
        }

        log.info("Clan leaders have been updated.");
    }

    private void resetDailySkills() {
        try {
            for (int skillId : RESET_SKILLS) {
                skillSaveRepository.deleteSkillOfOfflineCharacters(skillId);
            }
        } catch (DataAccessException e) {
            log.error("Could not reset daily skill reuse", e);
        }

        // Update data for online players.
        for (int skillId : RESET_SKILLS) {
            final Skill skill = SkillData.getInstance().getSkill(skillId, 1 /* No known need for more levels */);
            if (skill != null) {
                for (Player player : World.getPlayers()) {
                    if (player.hasSkillReuse(skill.getReuseHashCode())) {
                        player.removeTimeStamp(skill);
                    }
                }
            }
        }

        log.info("Daily skill reuse cleaned.");
    }

    private void resetNevit() {
        if (!PlayerConfig.NEVIT_ENABLED) {
            return;
        }

        for (Player player : World.getPlayers()) {
            if ((player == null) || !player.isOnline()) {
                continue;
            }

            player.getVariables().set("hunting_time", 0);
            player.sendPacket(new ExNevitAdventTimeChange(0, true));
        }
    }

    private void resetRecommends() {
        try {
            recommendationRepository.resetDaily(
                    DAILY_RECOMMENDATIONS, RECOMMENDATION_BONUS_TIME_MS, DAILY_RECOMMENDATIONS);
        } catch (DataAccessException e) {
            log.error("Could not reset Recommendations System", e);
        }

        for (Player player : World.getPlayers()) {
            player.setRecomLeft(DAILY_RECOMMENDATIONS);
            player.setRecomHave(player.getRecomHave() - DAILY_RECOMMENDATIONS);
            player.sendPacket(new ExVoteSystemInfo(player));
            player.broadcastUserInfo();
        }

        // Refresh reco bonus for online players.
        if (PlayerConfig.NEVIT_ENABLED) {
            for (Player player : World.getPlayers()) {
                if (player != null) {
                    player.stopNevitHourglassTask();
                    player.startNevitHourglassTask();
                }
            }
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static DailyResetManager getInstance() {
        final DailyResetManager current = instance;
        if (current == null) {
            throw new IllegalStateException("DailyResetManager is used before the Spring context created it.");
        }
        return current;
    }
}
