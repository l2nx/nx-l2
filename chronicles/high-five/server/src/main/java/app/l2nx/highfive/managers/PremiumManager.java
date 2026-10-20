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
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogin;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerLogout;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;
import app.l2nx.highfive.threads.ThreadPool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Mobius
 */
@Slf4j
public class PremiumManager {

    private static final String LOAD_SQL = "SELECT account_name,enddate FROM account_premium WHERE account_name = ?";
    private static final String UPDATE_SQL = "REPLACE INTO account_premium (account_name,enddate) VALUE (?,?)";
    private static final String DELETE_SQL = "DELETE FROM account_premium WHERE account_name = ?";

    class PremiumExpireTask implements Runnable {
        final Player player;

        PremiumExpireTask(Player player) {
            this.player = player;
        }

        @Override
        public void run() {
            player.setPremiumStatus(false);
        }
    }

    // Data Cache
    private final Map<String, Long> premiumData = new ConcurrentHashMap<>();

    // expireTasks
    private final Map<String, ScheduledFuture<?>> expiretasks = new ConcurrentHashMap<>();

    // Listeners
    private final ListenersContainer listenerContainer = Containers.Players();

    private final Consumer<OnPlayerLogin> playerLoginEvent = event -> {
        final Player player = event.player();
        final String accountName =
                PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM ? player.getAccountName() : player.getName();
        loadPremiumData(accountName);
        final long now = ClockBridge.wallMillis();
        final long premiumExpiration = getPremiumExpiration(accountName);
        player.setPremiumStatus(premiumExpiration > now);
        if (player.hasPremiumStatus()) {
            startExpireTask(player, premiumExpiration - now);
        } else if (premiumExpiration > 0) {
            removePremiumStatus(accountName, false);
        }
    };

    private final Consumer<OnPlayerLogout> playerLogoutEvent = event -> {
        stopExpireTask(event.player());
    };

    protected PremiumManager() {
        listenerContainer.addListener(
                new ConsumerEventListener(listenerContainer, EventType.ON_PLAYER_LOGIN, playerLoginEvent, this));
        listenerContainer.addListener(
                new ConsumerEventListener(listenerContainer, EventType.ON_PLAYER_LOGOUT, playerLogoutEvent, this));
    }

    /**
     * @param player
     * @param delay
     */
    private void startExpireTask(Player player, long delay) {
        expiretasks.put(
                PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM
                        ? player.getAccountName().toLowerCase()
                        : player.getName().toLowerCase(),
                ThreadPool.schedule(new PremiumExpireTask(player), delay));
    }

    /**
     * @param player
     */
    private void stopExpireTask(Player player) {
        final ScheduledFuture<?> task = expiretasks.remove(
                PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM
                        ? player.getAccountName().toLowerCase()
                        : player.getName().toLowerCase());
        if (task != null) {
            task.cancel(false);
        }
    }

    private void loadPremiumData(String accountName) {
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement stmt = con.prepareStatement(LOAD_SQL)) {
            stmt.setString(1, accountName.toLowerCase());
            try (ResultSet rset = stmt.executeQuery()) {
                while (rset.next()) {
                    premiumData.put(rset.getString(1).toLowerCase(), rset.getLong(2));
                }
            }
        } catch (SQLException e) {
            log.warn("Problem with PremiumManager: {}", e.getMessage());
        }
    }

    public long getPremiumExpiration(String accountName) {
        return premiumData.getOrDefault(accountName.toLowerCase(), 0L);
    }

    public void addPremiumTime(String accountName, int timeValue, TimeUnit timeUnit) {
        final long addTime = timeUnit.toMillis(timeValue);
        final long now = ClockBridge.wallMillis();

        // New premium task at least from now.
        final long oldPremiumExpiration = Math.max(now, getPremiumExpiration(accountName));
        final long newPremiumExpiration = oldPremiumExpiration + addTime;

        // UPDATE DATABASE
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement stmt = con.prepareStatement(UPDATE_SQL)) {
            stmt.setString(1, accountName.toLowerCase());
            stmt.setLong(2, newPremiumExpiration);
            stmt.execute();
        } catch (SQLException e) {
            log.warn("Problem with PremiumManager: {}", e.getMessage());
        }

        // UPDATE CACHE
        premiumData.put(accountName.toLowerCase(), newPremiumExpiration);

        // UPDATE PlAYER PREMIUMSTATUS
        for (Player player : World.getPlayers()) {
            if (accountName.equalsIgnoreCase(player.getAccountName())) {
                stopExpireTask(player);
                startExpireTask(player, newPremiumExpiration - now);
                if (!player.hasPremiumStatus()) {
                    player.setPremiumStatus(true);
                }
                break;
            }
        }
    }

    public void removePremiumStatus(String accountName, boolean checkOnline) {
        if (checkOnline) {
            for (Player player : World.getPlayers()) {
                if (accountName.equalsIgnoreCase(player.getAccountName()) && player.hasPremiumStatus()) {
                    player.setPremiumStatus(false);
                    stopExpireTask(player);
                    break;
                }
            }
        }

        // UPDATE CACHE
        premiumData.remove(accountName.toLowerCase());

        // UPDATE DATABASE
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement stmt = con.prepareStatement(DELETE_SQL)) {
            stmt.setString(1, accountName.toLowerCase());
            stmt.execute();
        } catch (SQLException e) {
            log.warn("Problem with PremiumManager: {}", e.getMessage());
        }
    }

    public static PremiumManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final PremiumManager INSTANCE = new PremiumManager();
    }
}
