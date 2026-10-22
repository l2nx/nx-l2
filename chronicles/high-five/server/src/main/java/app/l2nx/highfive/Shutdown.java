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
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.engine.core.loop.timer.TimerEntry;
import app.l2nx.engine.core.loop.timer.TimerHandler;
import app.l2nx.engine.core.loop.timer.TimerKind;
import app.l2nx.engine.core.loop.timer.TimerSpec;
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
import app.l2nx.highfive.login.ListingStatus;
import app.l2nx.highfive.login.LoginService;
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
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerClose;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Shutdown {

    private static final int SIGTERM = 0;
    private static final int GM_SHUTDOWN = 1;
    private static final int GM_RESTART = 2;
    private static final int ABORT = 3;
    private static final String[] MODE_TEXT = {"SIGTERM", "shutting down", "restarting", "aborting"};

    private static final int[] ANNOUNCED_SECONDS = {540, 480, 420, 360, 300, 240, 180, 120, 60, 30, 10, 5, 4, 3, 2, 1};
    private static final int LOGIN_CLOSED_SECONDS = 60;

    private static final int FINISH = 0;
    private static final int CLOSE_LOGIN = -1;

    private static volatile ServerExit serverExit;

    private final TimerHandler countdown = new Countdown();
    private volatile boolean countdownFinished;
    private volatile int shutdownMode = SIGTERM;

    // World loop only. Every start and abort bumps the generation, so the steps of an earlier countdown are dropped.
    private int generation;
    private boolean counting;
    // Set once the exit is requested: it runs on its own thread, so the countdown can no longer be stopped or replaced.
    private boolean finishing;

    protected Shutdown() {}

    public static void bind(ServerExit exit) {
        serverExit = exit;
    }

    /** Any thread; a supervisor restarts the process on {@link ServerExitCode#RESTART}. */
    public void startShutdown(Player player, int seconds, boolean restart) {
        onWorldLoop(() -> start(player, Math.max(0, seconds), restart));
    }

    public void abort(Player player) {
        onWorldLoop(() -> abortCountdown(player));
    }

    void announce(int seconds) {
        final SystemMessage sysm = new SystemMessage(
                SystemMessageId.THE_SERVER_WILL_BE_COMING_DOWN_IN_S1_SECOND_S_PLEASE_FIND_A_SAFE_PLACE_TO_LOG_OUT);
        sysm.addInt(seconds);
        World.broadcastToAllOnlinePlayers(sysm);
    }

    private static void onWorldLoop(Runnable work) {
        final Loop world = WorldPartition.loop();
        if (world.inLoop()) {
            work.run();
        } else {
            world.execute(work);
        }
    }

    private void start(Player player, int seconds, boolean restart) {
        if (finishing || countdownFinished) {
            log.warn("GM: {}shutdown command ignored because the server is stopping already.", issuer(player));
            tooLate(player);
            return;
        }

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

        if (ServerConfig.PRECAUTIONARY_RESTART_ENABLED) {
            PrecautionaryRestartManager.getInstance().restartEnabled();
        }

        generation++;
        counting = true;
        final Loop world = WorldPartition.loop();
        final long startedAt = world.gameNow();

        announce(seconds);
        if (seconds <= LOGIN_CLOSED_SECONDS) {
            closeLogin();
        } else {
            arm(world, startedAt, seconds - LOGIN_CLOSED_SECONDS, CLOSE_LOGIN);
        }

        for (int left : ANNOUNCED_SECONDS) {
            if (left < seconds) {
                arm(world, startedAt, seconds - left, left);
            }
        }

        arm(world, startedAt, seconds, FINISH);
    }

    // Game time: the steps are delays from the start, which a wall-clock step must not shift or collapse.
    private void arm(Loop world, long startedAt, int afterSeconds, int step) {
        world.timers(TimeBase.GAME)
                .schedule(
                        startedAt + TimeUnit.SECONDS.toMillis(afterSeconds),
                        TimerSpec.builder()
                                .handler(countdown)
                                .key(step)
                                .generation(generation)
                                .kind(TimerKind.ACTION)
                                .build());
    }

    private void closeLogin() {
        if (LoginService.getInstance().getStatus() != ListingStatus.DOWN) {
            LoginService.getInstance().setStatus(ListingStatus.DOWN);
        }
    }

    private void finish() {
        counting = false;
        finishing = true;

        // The context may be closing already, for a SIGTERM during the countdown.
        if (countdownFinished) {
            return;
        }

        log.warn("GM shutdown countdown is over. {} NOW!", MODE_TEXT[shutdownMode]);
        serverExit.exit(shutdownMode == GM_RESTART ? ServerExitCode.RESTART : ServerExitCode.SHUTDOWN);
    }

    private void abortCountdown(Player player) {
        if (finishing || countdownFinished) {
            log.warn("GM: {}shutdown ABORT failed because countdown has finished.", issuer(player));
            tooLate(player);
            return;
        }

        final int aborted = shutdownMode;
        log.warn("GM: {}issued shutdown ABORT. {} has been stopped!", issuer(player), MODE_TEXT[aborted]);
        if (!counting) {
            return;
        }

        generation++;
        counting = false;
        shutdownMode = ABORT;

        if (LoginService.getInstance().getStatus() == ListingStatus.DOWN) {
            LoginService.getInstance()
                    .setStatus((GeneralConfig.SERVER_GMONLY) ? ListingStatus.GM_ONLY : ListingStatus.AUTO);
        }

        if (ServerConfig.PRECAUTIONARY_RESTART_ENABLED) {
            PrecautionaryRestartManager.getInstance().restartAborted();
        }

        World.broadcastToAllOnlinePlayers(
                "Server aborts " + MODE_TEXT[aborted] + " and continues normal operation!", false);
    }

    private static String issuer(Player player) {
        return player != null ? player.getName() + "(" + player.getObjectId() + ") " : "";
    }

    private static void tooLate(Player player) {
        if (player != null) {
            player.sendMessage("Too late: the countdown has finished and the server is stopping.");
        }
    }

    void startShutdownActions() {
        if (countdownFinished) {
            return;
        }

        countdownFinished = true;

        try {
            if ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE)
                    && OfflineTradeConfig.RESTORE_OFFLINERS
                    && !OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME) {
                OfflineTraderTable.getInstance().storeOffliners();
                log.info("Offline Traders Table: Offline shops stored.");
            }
        } catch (Throwable t) {
            log.warn("Error saving offline shops.", t);
        }

        try {
            if (OfflinePlayConfig.RESTORE_AUTO_PLAY_OFFLINERS && AutoPlayConfig.ENABLE_AUTO_ASSIST) {
                OfflinePlayTable.getInstance().storeOfflineGroups();
                log.info("Offline Play Table: Offline play groups stored.");
            }
        } catch (Throwable t) {
            log.warn("Error saving offline play groups.", t);
        }

        try {
            disconnectAllCharacters();
            log.info("All players disconnected and saved.");
        } catch (Throwable t) {
            log.warn("Error disconnecting players.", t);
        }

        saveData();

        log.info("The server has been successfully shut down.");
    }

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

        if (!SevenSigns.getInstance().isSealValidationPeriod()) {
            SevenSignsFestival.getInstance().saveFestivalData(false);
            log.info("SevenSignsFestival: Festival data saved.");
        }

        SevenSigns.getInstance().saveSevenSignsData();
        log.info("SevenSigns: Seven Signs data saved.");
        SevenSigns.getInstance().saveSevenSignsStatus();
        log.info("SevenSigns: Seven Signs status saved.");

        RaidBossSpawnManager.getInstance().cleanUp();
        log.info("RaidBossSpawnManager: All raidboss info saved.");
        GrandBossManager.getInstance().cleanUp();
        log.info("GrandBossManager: All Grand Boss info saved.");
        ItemAuctionManager.getInstance().shutdown();
        log.info("Item Auction Manager: All tasks stopped.");
        Olympiad.getInstance().saveOlympiadStatus();
        log.info("Olympiad System: Data saved.");
        Hero.getInstance().shutdown();
        log.info("Hero System: Data saved.");
        ClanTable.getInstance().shutdown();
        log.info("Clan System: Data saved.");

        CursedWeaponsManager.getInstance().saveData();
        log.info("Cursed Weapons Manager: Data saved.");

        if (!GeneralConfig.ALT_MANOR_SAVE_ALL_ACTIONS) {
            CastleManorManager.getInstance().storeMe();
            log.info("Castle Manor Manager: Data saved.");
        }

        CHSiegeManager.getInstance().onServerShutDown();
        log.info("CHSiegeManager: Siegable hall attacker lists saved!");

        ScriptManager.getInstance().save();
        log.info("Script Manager: Data saved.");

        GlobalVariablesManager.getInstance().storeMe();
        log.info("Global Variables Manager: Variables saved.");

        if (GeneralConfig.ALT_FISH_CHAMPIONSHIP_ENABLED) {
            FishingChampionshipManager.getInstance().shutdown();
            log.info("Fishing Championship Manager: Data saved.");
        }

        SchemeBufferTable.getInstance().saveSchemes();
        log.info("SchemeBufferTable: Data saved.");

        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            ItemsOnGroundManager.getInstance().saveInDb();
            log.info("Items On Ground Manager: Data saved.");
            ItemsOnGroundManager.getInstance().cleanUp();
            log.info("Items On Ground Manager: Cleaned up.");
        }

        if (GeneralConfig.BOTREPORT_ENABLE) {
            BotReportTable.getInstance().saveReportedCharData();
            log.info("Bot Report Table: Successfully saved reports to database!");
        }
    }

    private void disconnectAllCharacters() {
        for (Player player : World.getPlayers()) {
            Disconnection.of(player).storeAndDeleteWith(ServerClose.STATIC_PACKET);
        }
    }

    private final class Countdown implements TimerHandler {
        @Override
        public boolean isCurrent(Loop loop, TimerEntry entry) {
            return entry.getSpec().generation() == generation;
        }

        @Override
        public void onTimer(Loop loop, TimerEntry entry) {
            switch (entry.getSpec().key()) {
                case FINISH -> finish();
                case CLOSE_LOGIN -> closeLogin();
                default -> announce(entry.getSpec().key());
            }
        }
    }

    public static Shutdown getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final Shutdown INSTANCE = new Shutdown();
    }
}
