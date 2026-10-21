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
import app.l2nx.highfive.threads.ThreadPool;
import java.time.Clock;
import java.time.LocalTime;
import lombok.Getter;

/**
 * GameTime task manager class.
 * @author Forsaiken, Mobius
 */
public class GameTimeTaskManager extends Thread {
    public static final int TICKS_PER_SECOND = 10; // Not able to change this without checking through code.
    public static final int MILLIS_IN_TICK = 1000 / TICKS_PER_SECOND;
    public static final int IG_DAYS_PER_DAY = 6;
    public static final int MILLIS_PER_IG_DAY = (3600000 * 24) / IG_DAYS_PER_DAY;
    public static final int SECONDS_PER_IG_DAY = MILLIS_PER_IG_DAY / 1000;
    public static final int TICKS_PER_IG_DAY = SECONDS_PER_IG_DAY * TICKS_PER_SECOND;

    @Getter
    private boolean night;

    @Getter
    private int gameTime;

    @Getter
    private int gameHour;

    protected GameTimeTaskManager() {
        super("GameTimeTaskManager");
        super.setDaemon(true);
        super.setPriority(MAX_PRIORITY);
    }

    @Override
    public void run() {
        while (true) {
            gameTime = inGameMinutesAt(ClockBridge.wallClock());
            gameHour = gameTime / 60;

            if ((gameHour < 6) != night) {
                night = !night;

                for (Player player : World.getPlayers()) {
                    if (player.getRace() == Race.DARK_ELF) {
                        final Skill shadowSense = player.getKnownSkill(CommonSkill.SHADOW_SENSE.getId());
                        if (shadowSense != null) {
                            if (night) {
                                player.sendPacket(new SystemMessage(
                                                SystemMessageId.IT_IS_NOW_MIDNIGHT_AND_THE_EFFECT_OF_S1_CAN_BE_FELT)
                                        .addSkillName(shadowSense));
                                player.addSkill(shadowSense, false);
                            } else {
                                player.sendPacket(new SystemMessage(
                                                SystemMessageId.IT_IS_DAWN_AND_THE_EFFECT_OF_S1_WILL_NOW_DISAPPEAR)
                                        .addSkillName(shadowSense));
                                player.getEffectList().stopSkillEffects(SkillFinishType.NORMAL, shadowSense);
                            }
                        }
                    }
                }

                ThreadPool.execute(() -> DayNightSpawnManager.getInstance().notifyChangeMode());

                if (EventDispatcher.getInstance().hasListener(EventType.ON_DAY_NIGHT_CHANGE)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnDayNightChange(night));
                }
            }

            try {
                Thread.sleep(MILLIS_IN_TICK);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** Read from the clock on every call: a value cached by the thread would lag the clock by up to a tick. */
    public int getGameTicks() {
        return ticksAt(ClockBridge.gameMillis());
    }

    static int ticksAt(long gameMillis) {
        return (int) (gameMillis / MILLIS_IN_TICK);
    }

    /**
     * Counts from the local time of day, not from the time elapsed since midnight: on a DST change day the two differ
     * by an hour, which is six in-game hours.
     */
    static int inGameMinutesAt(Clock wallClock) {
        final long realTicks = (LocalTime.now(wallClock).toNanoOfDay() / 1_000_000) / MILLIS_IN_TICK;
        return (int) ((realTicks % TICKS_PER_IG_DAY) / MILLIS_IN_TICK);
    }

    public int getGameMinute() {
        return gameTime % 60;
    }

    public static GameTimeTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final GameTimeTaskManager INSTANCE = started(new GameTimeTaskManager());

        private static GameTimeTaskManager started(GameTimeTaskManager manager) {
            manager.start();
            return manager;
        }
    }
}
