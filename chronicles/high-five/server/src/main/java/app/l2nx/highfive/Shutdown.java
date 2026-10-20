/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
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
package app.l2nx.highfive;

import app.l2nx.boot.ServerExit;
import app.l2nx.boot.ServerExitCode;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.AutoPlayConfig;
import app.l2nx.highfive.config.custom.OfflinePlayConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.BotReportTable;
import app.l2nx.highfive.data.SchemeBufferTable;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.sql.OfflinePlayTable;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.FishingChampionshipManager;
import app.l2nx.highfive.managers.GlobalVariablesManager;
import app.l2nx.highfive.managers.GrandBossManager;
import app.l2nx.highfive.managers.ItemAuctionManager;
import app.l2nx.highfive.managers.ItemsOnGroundManager;
import app.l2nx.highfive.managers.PrecautionaryRestartManager;
import app.l2nx.highfive.managers.RaidBossSpawnManager;
import app.l2nx.highfive.managers.ScriptManager;
import app.l2nx.highfive.mechanics.olympiad.Hero;
import app.l2nx.highfive.mechanics.olympiad.Olympiad;
import app.l2nx.highfive.mechanics.sevensigns.SevenSigns;
import app.l2nx.highfive.mechanics.sevensigns.SevenSignsFestival;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.gslink.LoginServerLink;
import app.l2nx.highfive.network.loginserverpackets.send.ServerStatus;
import app.l2nx.highfive.network.serverpackets.ServerClose;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import lombok.extern.slf4j.Slf4j;

/**
 * This class provides the functions for shutting down and restarting the server.<br>
 * It closes all open client connections and saves all data.
 * @version $Revision: 1.2.4.5 $ $Date: 2005/03/27 15:29:09 $
 */
@Slf4j
public class Shutdown extends Thread {

    private static final int SIGTERM = 0;
    private static final int GM_SHUTDOWN = 1;
    private static final int GM_RESTART = 2;
    private static final int ABORT = 3;
    private static final String[] MODE_TEXT = {"SIGTERM", "shutting down", "restarting", "aborting"};

    private static Shutdown counterInstance;
    private static volatile boolean countdownFinished;
    private static volatile ServerExit serverExit;

    private static volatile int secondsShut;
    private static volatile int shutdownMode;

    /**
     * This function starts a shutdown count down (Copied from Function startShutdown())
     * @param seconds seconds until shutdown
     */
    private void sendServerQuit(int seconds) {
        final SystemMessage sysm = new SystemMessage(
                SystemMessageId.THE_SERVER_WILL_BE_COMING_DOWN_IN_S1_SECOND_S_PLEASE_FIND_A_SAFE_PLACE_TO_LOG_OUT);
        sysm.addInt(seconds);
        World.broadcastToAllOnlinePlayers(sysm);
    }

    /**
     * Default constructor is only used internal to create the singleton instance
     */
    protected Shutdown() {
        secondsShut = -1;
        shutdownMode = SIGTERM;
    }

    /**
     * This creates a countdown instance of Shutdown.
     * @param seconds how many seconds until shutdown
     * @param restart true is the server shall restart after shutdown
     */
    public Shutdown(int seconds, boolean restart) {
        secondsShut = Math.max(0, seconds);
        shutdownMode = restart ? GM_RESTART : GM_SHUTDOWN;
    }

    public static void bind(ServerExit exit) {
        serverExit = exit;
    }

    /**
     * A supervisor restarts the process on {@link ServerExitCode#RESTART}.
     */
    @Override
    public void run() {
        if (countdownFinished) {
            return;
        }

        // Send warnings and then call exit to start shutdown sequence.
        countdown();

        // The context may have been closed during the countdown.
        if (countdownFinished) {
            return;
        }

        log.warn("GM shutdown countdown is over. {} NOW!", MODE_TEXT[shutdownMode]);

        switch (shutdownMode) {
            case GM_SHUTDOWN: {
                getInstance().setMode(GM_SHUTDOWN);
                serverExit.exit(ServerExitCode.SHUTDOWN);
                break;
            }
            case GM_RESTART: {
                getInstance().setMode(GM_RESTART);
                serverExit.exit(ServerExitCode.RESTART);
                break;
            }
            case ABORT: {
                LoginServerLink.getInstance().setServerStatus(ServerStatus.STATUS_AUTO);
                break;
            }
        }
    }

    /**
     * This functions starts a shutdown countdown.
     * @param player GM who issued the shutdown command
     * @param seconds seconds until shutdown
     * @param restart true if the server will restart after shutdown
     */
    public void startShutdown(Player player, int seconds, boolean restart) {
        shutdownMode = restart ? GM_RESTART : GM_SHUTDOWN;

        if (player != null) {
            log.warn(
                    "GM: {}({}) issued shutdown command. {} in {} seconds!",
                    player.getName(),
                    player.getObjectId(),
                    MODE_TEXT[shutdownMode],
                    seconds);
        } else {
            log.warn(
                    "Server scheduled restart issued shutdown command. {} in {} seconds!",
                    (restart ? "Restart" : "Shutdown"),
                    seconds);
        }

        if (shutdownMode > 0) {
            switch (seconds) {
                case 540:
                case 480:
                case 420:
                case 360:
                case 300:
                case 240:
                case 180:
                case 120:
                case 60:
                case 30:
                case 10:
                case 5:
                case 4:
                case 3:
                case 2:
                case 1: {
                    break;
                }
                default: {
                    sendServerQuit(seconds);
                }
            }
        }

        if (counterInstance != null) {
            counterInstance.abort();
        }

        if (ServerConfig.PRECAUTIONARY_RESTART_ENABLED) {
            PrecautionaryRestartManager.getInstance().restartEnabled();
        }

        // The main instance never runs, so we start a new instance.
        counterInstance = new Shutdown(seconds, restart);
        counterInstance.start();
    }

    /**
     * This function aborts a running countdown.
     * @param player GM who issued the abort command
     */
    public void abort(Player player) {
        if (countdownFinished) {
            log.warn(
                    "GM: {}shutdown ABORT failed because countdown has finished.",
                    (player != null ? player.getName() + "(" + player.getObjectId() + ") " : ""));
            return;
        }

        log.warn(
                "GM: {}issued shutdown ABORT. {} has been stopped!",
                (player != null ? player.getName() + "(" + player.getObjectId() + ") " : ""),
                MODE_TEXT[shutdownMode]);
        if (counterInstance != null) {
            counterInstance.abort();

            if (ServerConfig.PRECAUTIONARY_RESTART_ENABLED) {
                PrecautionaryRestartManager.getInstance().restartAborted();
            }

            World.broadcastToAllOnlinePlayers(
                    "Server aborts " + MODE_TEXT[shutdownMode] + " and continues normal operation!", false);
        }
    }

    /**
     * Set the shutdown mode.
     * @param mode what mode shall be set
     */
    private void setMode(int mode) {
        shutdownMode = mode;
    }

    /**
     * Set shutdown mode to ABORT.
     */
    private void abort() {
        shutdownMode = ABORT;
    }

    /**
     * This counts the countdown and reports it to all players countdown is aborted if mode changes to ABORT.
     */
    private void countdown() {
        try {
            while (secondsShut > 0) {
                // Rehabilitate previous server status if shutdown is aborted.
                if (shutdownMode == ABORT) {
                    if (LoginServerLink.getInstance().getServerStatus() == ServerStatus.STATUS_DOWN) {
                        LoginServerLink.getInstance()
                                .setServerStatus(
                                        (GeneralConfig.SERVER_GMONLY)
                                                ? ServerStatus.STATUS_GM_ONLY
                                                : ServerStatus.STATUS_AUTO);
                    }
                    break;
                }

                switch (secondsShut) {
                    case 540:
                    case 480:
                    case 420:
                    case 360:
                    case 300:
                    case 240:
                    case 180:
                    case 120:
                    case 60:
                    case 30:
                    case 10:
                    case 5:
                    case 4:
                    case 3:
                    case 2:
                    case 1: {
                        sendServerQuit(secondsShut);
                    }
                }

                // Prevent players from logging in.
                if ((secondsShut <= 60)
                        && (LoginServerLink.getInstance().getServerStatus() != ServerStatus.STATUS_DOWN)) {
                    LoginServerLink.getInstance().setServerStatus(ServerStatus.STATUS_DOWN);
                }

                secondsShut--;

                Thread.sleep(1000);
            }
        } catch (Exception e) {
            // This will never happen.
        }
    }

    /**
     * Actions performed when the server stops.
     */
    void startShutdownActions() {
        if (countdownFinished) {
            return;
        }

        countdownFinished = true;

        final TimeCounter tc = new TimeCounter();
        final TimeCounter tc1 = new TimeCounter();

        try {
            if ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE)
                    && OfflineTradeConfig.RESTORE_OFFLINERS
                    && !OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME) {
                OfflineTraderTable.getInstance().storeOffliners();
                log.info("Offline Traders Table: Offline shops stored({}ms).", tc.getEstimatedTimeAndRestartCounter());
            }
        } catch (Throwable t) {
            log.warn("Error saving offline shops.", t);
        }

        try {
            if (OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS && AutoPlayConfig.ENABLE_AUTO_ASSIST) {
                OfflinePlayTable.getInstance().storeOfflineGroups();
                log.info(
                        "Offline Play Table: Offline play groups stored({}ms).",
                        tc.getEstimatedTimeAndRestartCounter());
            }
        } catch (Throwable t) {
            log.warn("Error saving offline play groups.", t);
        }

        try {
            disconnectAllCharacters();
            log.info("All players disconnected and saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        } catch (Throwable t) {
            // ignore
        }

        // ensure all services are stopped

        try {
            GameTimeTaskManager.getInstance().interrupt();
            log.info("Game Time Task Manager: Thread interruped({}ms).", tc.getEstimatedTimeAndRestartCounter());
        } catch (Throwable t) {
            // ignore
        }

        // stop all thread pools
        try {
            ThreadPool.shutdown();
            log.info("Thread Pool Manager: Manager has been shut down({}ms).", tc.getEstimatedTimeAndRestartCounter());
        } catch (Throwable t) {
            // ignore
        }

        try {
            LoginServerLink.getInstance().interrupt();
            log.info("Login Server Thread: Thread interruped({}ms).", tc.getEstimatedTimeAndRestartCounter());
        } catch (Throwable t) {
            // ignore
        }

        // last byebye, save all data and quit this server
        saveData();

        log.info("The server has been successfully shut down in {}seconds.", (tc1.getEstimatedTime() / 1000));
    }

    /**
     * This sends a last byebye, disconnects all players and saves data.
     */
    private void saveData() {
        switch (shutdownMode) {
            case SIGTERM: {
                log.info("SIGTERM received. Shutting down NOW!");
                break;
            }
            case GM_SHUTDOWN: {
                log.info("GM shutdown received. Shutting down NOW!");
                break;
            }
            case GM_RESTART: {
                log.info("GM restart received. Restarting NOW!");
                break;
            }
        }

        final TimeCounter tc = new TimeCounter();

        // Seven Signs data is now saved along with Festival data.
        if (!SevenSigns.getInstance().isSealValidationPeriod()) {
            SevenSignsFestival.getInstance().saveFestivalData(false);
            log.info("SevenSignsFestival: Festival data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        }

        // Save Seven Signs data before closing. :)
        SevenSigns.getInstance().saveSevenSignsData();
        log.info("SevenSigns: Seven Signs data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        SevenSigns.getInstance().saveSevenSignsStatus();
        log.info("SevenSigns: Seven Signs status saved({}ms).", tc.getEstimatedTimeAndRestartCounter());

        // Save all raidboss and GrandBoss status ^_^
        RaidBossSpawnManager.getInstance().cleanUp();
        log.info("RaidBossSpawnManager: All raidboss info saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        GrandBossManager.getInstance().cleanUp();
        log.info("GrandBossManager: All Grand Boss info saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        ItemAuctionManager.getInstance().shutdown();
        log.info("Item Auction Manager: All tasks stopped({}ms).", tc.getEstimatedTimeAndRestartCounter());
        Olympiad.getInstance().saveOlympiadStatus();
        log.info("Olympiad System: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        Hero.getInstance().shutdown();
        log.info("Hero System: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        ClanTable.getInstance().shutdown();
        log.info("Clan System: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());

        // Save Cursed Weapons data before closing.
        CursedWeaponsManager.getInstance().saveData();
        log.info("Cursed Weapons Manager: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());

        // Save all manor data.
        if (!GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
            CastleManorManager.getInstance().storeMe();
            log.info("Castle Manor Manager: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        }

        CHSiegeManager.getInstance().onServerShutDown();
        log.info("CHSiegeManager: Siegable hall attacker lists saved!");

        // Save all global (non-player specific) Quest data that needs to persist after reboot.
        ScriptManager.getInstance().save();
        log.info("Script Manager: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());

        // Save all global variables data.
        GlobalVariablesManager.getInstance().storeMe();
        log.info("Global Variables Manager: Variables saved({}ms).", tc.getEstimatedTimeAndRestartCounter());

        // Save Fishing tournament data.
        if (GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED) {
            FishingChampionshipManager.getInstance().shutdown();
            log.info("Fishing Championship Manager: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
        }

        // Schemes save.
        SchemeBufferTable.getInstance().saveSchemes();
        log.info("SchemeBufferTable: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());

        // Save items on ground before closing.
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().saveInDb();
            log.info("Items On Ground Manager: Data saved({}ms).", tc.getEstimatedTimeAndRestartCounter());
            ItemsOnGroundManager.getInstance().cleanUp();
            log.info("Items On Ground Manager: Cleaned up({}ms).", tc.getEstimatedTimeAndRestartCounter());
        }

        // Save bot reports to database.
        if (GeneralConfig.BOTREPORT_ENABLE) {
            BotReportTable.getInstance().saveReportedCharData();
            log.info("Bot Report Table: Successfully saved reports to database!");
        }

        try {
            Thread.sleep(5000);
        } catch (Exception e) {
            // This will never happen.
        }
    }

    /**
     * This disconnects all clients from the server.
     */
    private void disconnectAllCharacters() {
        for (Player player : World.getPlayers()) {
            Disconnection.of(player).storeAndDeleteWith(ServerClose.STATIC_PACKET);
        }
    }

    /**
     * A simple class used to track down the estimated time of method executions.<br>
     * Once this class is created, it saves the start time, and when you want to get the estimated time, use the getEstimatedTime() method.
     */
    private static class TimeCounter {
        private long startTime;

        protected TimeCounter() {
            restartCounter();
        }

        public void restartCounter() {
            startTime = ClockBridge.gameMillis();
        }

        public long getEstimatedTimeAndRestartCounter() {
            final long toReturn = ClockBridge.gameMillis() - startTime;
            restartCounter();
            return toReturn;
        }

        public long getEstimatedTime() {
            return ClockBridge.gameMillis() - startTime;
        }
    }

    /**
     * Get the singleton instance, created by the first call of this function.
     * @return instance of Shutdown
     */
    public static Shutdown getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final Shutdown INSTANCE = new Shutdown();
    }
}
