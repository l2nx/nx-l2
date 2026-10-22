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
package app.l2nx.highfive.taskmanagers;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerHandler;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.managers.DayNightSpawnManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.OnDayNightChange;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * GameTime task manager class.
 * @author Forsaiken, Mobius
 */
public class GameTimeTaskManager {
    public static final int TICKS_PER_SECOND = 10; // Not able to change this without checking through code.
    public static final int MILLIS_IN_TICK = 1000 / TICKS_PER_SECOND;
    public static final int IG_DAYS_PER_DAY = 6;
    public static final int MILLIS_PER_IG_DAY = (3600000 * 24) / IG_DAYS_PER_DAY;
    public static final int SECONDS_PER_IG_DAY = MILLIS_PER_IG_DAY / 1000;
    public static final int TICKS_PER_IG_DAY = SECONDS_PER_IG_DAY * TICKS_PER_SECOND;

    private static final int NIGHT_END_HOUR = 6;
    private static final long IG_DAY_NANOS = TimeUnit.MILLISECONDS.toNanos(MILLIS_PER_IG_DAY);
    private static final long NIGHT_NANOS = IG_DAY_NANOS * NIGHT_END_HOUR / 24;

    private final TimerHandler dayChange = (loop, entry) -> follow(loop);

    @Getter
    private boolean night;

    protected GameTimeTaskManager() {}

    public void start(Loop world) {
        world.checkOwner();
        follow(world);
    }

    private void follow(Loop world) {
        final ZonedDateTime now = Instant.ofEpochMilli(world.wallNow())
                .atZone(world.getClock().wallClock().getZone());
        // Armed before the listeners run: one that throws must not stop day and night until a restart.
        world.timers(TimeBase.WALL)
                .schedule(
                        nextChangeAt(now).toEpochMilli(),
                        TimerSpec.builder()
                                .handler(dayChange)
                                .kind(TimerKind.ACTION)
                                .build());

        final boolean nightNow = isNightAt(now.toLocalTime());
        if (nightNow != night) {
            night = nightNow;
            onChange(night);
        }
    }

    void onChange(boolean night) {
        for (Player player : World.getPlayers()) {
            if (player.getRace() == Race.DARK_ELF) {
                final Skill shadowSense = player.getKnownSkill(CommonSkill.SHADOW_SENSE.getId());
                if (shadowSense != null) {
                    if (night) {
                        player.sendPacket(
                                new SystemMessage(SystemMessageId.IT_IS_NOW_MIDNIGHT_AND_THE_EFFECT_OF_S1_CAN_BE_FELT)
                                        .addSkillName(shadowSense));
                        player.addSkill(shadowSense, false);
                    } else {
                        player.sendPacket(
                                new SystemMessage(SystemMessageId.IT_IS_DAWN_AND_THE_EFFECT_OF_S1_WILL_NOW_DISAPPEAR)
                                        .addSkillName(shadowSense));
                        player.getEffectList().stopSkillEffects(SkillFinishType.NORMAL, shadowSense);
                    }
                }
            }
        }

        DayNightSpawnManager.getInstance().notifyChangeMode();

        if (EventDispatcher.getInstance().hasListener(EventType.ON_DAY_NIGHT_CHANGE)) {
            EventDispatcher.getInstance().notifyEventAsync(new OnDayNightChange(night));
        }
    }

    /** Read from the clock on every call: a cached value would lag the clock by up to a tick. */
    public int getGameTicks() {
        return ticksAt(ClockBridge.gameMillis());
    }

    static int ticksAt(long gameMillis) {
        return (int) (gameMillis / MILLIS_IN_TICK);
    }

    public int getGameTime() {
        return inGameMinutesAt(ClockBridge.wallClock());
    }

    public int getGameHour() {
        return getGameTime() / 60;
    }

    public int getGameMinute() {
        return getGameTime() % 60;
    }

    /**
     * Counts from the local time of day, not from the time elapsed since midnight: on a DST change day the two differ
     * by an hour, which is six in-game hours.
     */
    static int inGameMinutesAt(Clock wallClock) {
        return inGameMinutesAt(LocalTime.now(wallClock));
    }

    private static int inGameMinutesAt(LocalTime localTime) {
        final long realTicks = (localTime.toNanoOfDay() / 1_000_000) / MILLIS_IN_TICK;
        return (int) ((realTicks % TICKS_PER_IG_DAY) / MILLIS_IN_TICK);
    }

    static boolean isNightAt(LocalTime localTime) {
        return inGameMinutesAt(localTime) < NIGHT_END_HOUR * 60;
    }

    /**
     * Counted in local time of day, as {@link #inGameMinutesAt} counts. A change inside a DST gap moves to the gap's
     * end; in a repeated hour the current offset is kept, so the change is not placed before now.
     */
    static Instant nextChangeAt(ZonedDateTime now) {
        final long intoGameDay = now.toLocalTime().toNanoOfDay() % IG_DAY_NANOS;
        final long untilChange = intoGameDay < NIGHT_NANOS ? NIGHT_NANOS - intoGameDay : IG_DAY_NANOS - intoGameDay;
        return ZonedDateTime.ofLocal(now.toLocalDateTime().plusNanos(untilChange), now.getZone(), now.getOffset())
                .toInstant();
    }

    public static GameTimeTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final GameTimeTaskManager INSTANCE = new GameTimeTaskManager();
    }
}
