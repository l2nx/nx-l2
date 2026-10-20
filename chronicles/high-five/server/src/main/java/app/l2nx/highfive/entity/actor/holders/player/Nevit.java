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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.annotations.RegisterEvent;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogin;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogout;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.mechanics.skill.AbnormalVisualEffect;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ExNevitAdventEffect;
import app.l2nx.highfive.network.serverpackets.ExNevitAdventPointInfoPacket;
import app.l2nx.highfive.network.serverpackets.ExNevitAdventTimeChange;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.Getter;

/**
 * Nevit's Blessing handler.
 * @author Janiko
 */
public class Nevit {
    @Getter
    public Player player;

    private ScheduledFuture<?> adventTask;
    private ScheduledFuture<?> nevitEffectTask;

    public Nevit(Player player) {
        this.player = player;
        if (PlayerConfig.NEVIT_ENABLED) {
            player.addListener(new ConsumerEventListener(
                    player, EventType.ON_PLAYER_LOGIN, (OnPlayerLogin event) -> onPlayerLogin(event), this));
            player.addListener(new ConsumerEventListener(
                    player, EventType.ON_PLAYER_LOGOUT, (OnPlayerLogout event) -> onPlayerLogout(event), this));
        }
    }

    @RegisterEvent(EventType.ON_PLAYER_LOGIN)
    private void onPlayerLogin(OnPlayerLogin event) {
        final Calendar cal = ClockBridge.calendar();
        cal.set(Calendar.HOUR_OF_DAY, 6);
        cal.set(Calendar.MINUTE, 30);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // Reset Nevit's Blessing
        if ((player.getLastAccess() < cal.getTimeInMillis()) && (ClockBridge.wallMillis() > cal.getTimeInMillis())) {
            player.getVariables().set("hunting_time", 0);
        }

        // Send Packets
        player.sendPacket(new ExNevitAdventPointInfoPacket(getAdventPoints()));
        player.sendPacket(new ExNevitAdventTimeChange(getAdventTime(), true));
        startNevitEffect(player.getVariables().getInt("nevit_b", 0));

        // Set percent
        final int percent = calcPercent(player.getVariables().getInt("hunting_points", 0));
        if ((percent >= 45) && (percent < 50)) {
            player.sendPacket(SystemMessageId.YOU_ARE_STARTING_TO_FEEL_THE_EFFECTS_OF_NEVIT_S_ADVENT_BLESSING);
        } else if ((percent >= 50) && (percent < 75)) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_ARE_FURTHER_INFUSED_WITH_THE_BLESSINGS_OF_NEVIT_CONTINUE_TO_BATTLE_EVIL_WHEREVER_IT_MAY_LURK);
        } else if (percent >= 75) {
            player.sendPacket(
                    SystemMessageId
                            .NEVIT_S_ADVENT_BLESSING_SHINES_STRONGLY_FROM_ABOVE_YOU_CAN_ALMOST_SEE_HIS_DIVINE_AURA);
        }
    }

    @RegisterEvent(EventType.ON_PLAYER_LOGOUT)
    private void onPlayerLogout(OnPlayerLogout event) {
        stopNevitEffectTask(true);
        stopAdventTask(false);
    }

    public void addPoints(int value) {
        if (getEffectTime() > 0) {
            setAdventPoints(0);
        } else {
            setAdventPoints(getAdventPoints() + value);
        }

        if (getAdventPoints() > PlayerConfig.NEVIT_MAX_POINTS) {
            setAdventPoints(0);
            startNevitEffect(PlayerConfig.NEVIT_BONUS_EFFECT_TIME);
        }

        final int percent = calcPercent(getAdventPoints());
        switch (percent) {
            case 45: {
                player.sendPacket(SystemMessageId.YOU_ARE_STARTING_TO_FEEL_THE_EFFECTS_OF_NEVIT_S_ADVENT_BLESSING);
                break;
            }
            case 50: {
                player.sendPacket(
                        SystemMessageId
                                .YOU_ARE_FURTHER_INFUSED_WITH_THE_BLESSINGS_OF_NEVIT_CONTINUE_TO_BATTLE_EVIL_WHEREVER_IT_MAY_LURK);
                break;
            }
            case 75: {
                player.sendPacket(
                        SystemMessageId
                                .NEVIT_S_ADVENT_BLESSING_SHINES_STRONGLY_FROM_ABOVE_YOU_CAN_ALMOST_SEE_HIS_DIVINE_AURA);
                break;
            }
        }

        player.sendPacket(new ExNevitAdventPointInfoPacket(getAdventPoints()));
    }

    public void startAdventTask() {
        if (adventTask == null) {
            synchronized (this) {
                if ((adventTask == null) && (getAdventTime() < PlayerConfig.NEVIT_ADVENT_TIME)) {
                    adventTask = ThreadPool.schedule(new AdventTask(), 30000);
                    player.sendPacket(new ExNevitAdventTimeChange(getAdventTime(), false));
                }
            }
        }
    }

    public class AdventTask implements Runnable {
        @Override
        public void run() {
            setAdventTime(getAdventTime() + 30);
            if (getAdventTime() >= PlayerConfig.NEVIT_ADVENT_TIME) {
                setAdventTime(PlayerConfig.NEVIT_ADVENT_TIME);
                stopAdventTask(true);
            } else {
                addPoints(72);
                if ((getAdventTime() % 60) == 0) {
                    player.sendPacket(new ExNevitAdventTimeChange(getAdventTime(), false));
                }
            }

            stopAdventTask(false);
        }
    }

    public synchronized void stopAdventTask(boolean sendPacket) {
        if (adventTask != null) {
            adventTask.cancel(true);
            adventTask = null;
        }

        if (sendPacket) {
            player.sendPacket(new ExNevitAdventTimeChange(getAdventTime(), true));
        }
    }

    public synchronized void startNevitEffect(int timeValue) {
        int time = timeValue;
        if (getEffectTime() > 0) {
            stopNevitEffectTask(false);
            time += getEffectTime();
        }

        if ((PlayerConfig.NEVIT_IGNORE_ADVENT_TIME || (getAdventTime() < PlayerConfig.NEVIT_ADVENT_TIME))
                && (time > 0)) {
            player.getVariables().set("nevit_b", time);
            player.sendPacket(new ExNevitAdventEffect(time));
            player.sendPacket(
                    SystemMessageId
                            .THE_ANGEL_NEVIT_HAS_BLESSED_YOU_FROM_ABOVE_YOU_ARE_IMBUED_WITH_FULL_VITALITY_AS_WELL_AS_A_VITALITY_REPLENISHING_EFFECT_AND_SHOULD_YOU_DIE_YOU_WILL_NOT_LOSE_EXP);
            player.startAbnormalVisualEffect(true, AbnormalVisualEffect.NAVIT_ADVENT);
            nevitEffectTask = ThreadPool.schedule(new NevitEffectEnd(), time * 1000);
        }
    }

    public class NevitEffectEnd implements Runnable {
        @Override
        public void run() {
            if (PlayerConfig.NEVIT_IGNORE_ADVENT_TIME) {
                setAdventTime(0);
            }

            player.getVariables().remove("nevit_b");
            player.sendPacket(new ExNevitAdventEffect(0));
            player.sendPacket(new ExNevitAdventPointInfoPacket(getAdventPoints()));
            player.sendPacket(
                    SystemMessageId
                            .NEVIT_S_ADVENT_BLESSING_HAS_ENDED_CONTINUE_YOUR_JOURNEY_AND_YOU_WILL_SURELY_MEET_HIS_FAVOR_AGAIN_SOMETIME_SOON);
            player.stopAbnormalVisualEffect(true, AbnormalVisualEffect.NAVIT_ADVENT);
            stopNevitEffectTask(false);
        }
    }

    public synchronized void stopNevitEffectTask(boolean saveTime) {
        if (nevitEffectTask != null) {
            if (saveTime) {
                final int time = getEffectTime();
                if (time > 0) {
                    player.getVariables().set("nevit_b", time);
                } else {
                    player.getVariables().remove("nevit_b");
                }
            }

            nevitEffectTask.cancel(true);
            nevitEffectTask = null;
        }
    }

    public int getObjectId() {
        return player.getObjectId();
    }

    private int getEffectTime() {
        if (nevitEffectTask == null) {
            return 0;
        }

        return (int) Math.max(0, nevitEffectTask.getDelay(TimeUnit.SECONDS));
    }

    public boolean isAdventBlessingActive() {
        return ((nevitEffectTask != null) && (nevitEffectTask.getDelay(TimeUnit.MILLISECONDS) > 0));
    }

    public static int calcPercent(int points) {
        return (int) ((100.0D / PlayerConfig.NEVIT_MAX_POINTS) * points);
    }

    public void setAdventPoints(int points) {
        player.getVariables().set("hunting_points", points);
    }

    public void setAdventTime(int time) {
        player.getVariables().set("hunting_time", time);
    }

    public int getAdventPoints() {
        return PlayerConfig.NEVIT_ENABLED ? player.getVariables().getInt("hunting_points", 0) : 0;
    }

    public int getAdventTime() {
        return PlayerConfig.NEVIT_ENABLED ? player.getVariables().getInt("hunting_time", 0) : 0;
    }
}
