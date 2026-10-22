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
package app.l2nx.highfive.util;

import app.l2nx.engine.core.session.SessionState;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.PunishmentManager;
import app.l2nx.highfive.mechanics.punishment.PunishmentAffect;
import app.l2nx.highfive.mechanics.punishment.PunishmentTask;
import app.l2nx.highfive.mechanics.punishment.PunishmentType;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

/**
 * Flood protector implementation for preventing client request spam and abuse.<br>
 * Provides configurable protection intervals and punishment systems for violating clients.
 * <ul>
 * <li>Configurable protection intervals and punishment limits.</li>
 * <li>Multiple punishment types: kick, ban, and jail.</li>
 * <li>Automatic logging and monitoring of flood attempts.</li>
 * <li>GM immunity and per-client tracking.</li>
 * </ul>
 * @author fordfrog, Mobius
 */
@Slf4j
public class FloodProtectorAction {

    // Constants.
    private static final int LOG_BUILDER_INITIAL_CAPACITY = 128;
    private static final int MINUTES_TO_MILLISECONDS = 60000;
    private static final String PUNISHMENT_KICK = "kick";
    private static final String PUNISHMENT_BAN = "ban";
    private static final String PUNISHMENT_JAIL = "jail";
    private static final String DURATION_FOREVER = "forever";

    // Client Connection.
    private final GameClient client;
    private final FloodProtectorSettings settings;

    // Protection State.
    private final AtomicInteger requestCount = new AtomicInteger(0);
    // 0 lies before every game tick, so the first action passes. Not the current tick: the client and its protectors
    // are created on a network thread, which has no game time.
    private volatile int nextGameTick;
    private volatile boolean punishmentInProgress;
    private volatile boolean logged;

    /**
     * Creates a new flood protector action for the specified client and settings.
     * @param client
     * @param settings
     */
    public FloodProtectorAction(GameClient client, FloodProtectorSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    /**
     * Checks whether the request is flood protected or not.<br>
     * Applies punishment if violation limits are exceeded.
     * @return true if action is allowed, otherwise false.
     */
    public synchronized boolean canPerformAction() {
        final Player player = client.getPlayer();
        if ((player != null) && player.isGM()) {
            return true;
        }

        final int currentTick = GameTimeTaskManager.getInstance().getGameTicks();
        if ((currentTick < nextGameTick) || punishmentInProgress) {
            // Log flooding if enabled and not already logged.
            if (log.isWarnEnabled() && settings.isLogFlooding() && !logged) {
                final int timeUntilNext = (settings.getProtectionInterval() - (nextGameTick - currentTick))
                        * GameTimeTaskManager.MILLIS_IN_TICK;
                logFlooding(timeUntilNext);
                logged = true;
            }

            // Check if punishment should be applied.
            final int currentCount = requestCount.incrementAndGet();
            if (!punishmentInProgress) {
                final int punishmentLimit = settings.getPunishmentLimit();
                if ((punishmentLimit > 0) && (currentCount >= punishmentLimit)) {
                    final String punishmentType = settings.getPunishmentType();
                    if (punishmentType != null) {
                        punishmentInProgress = true;

                        try {
                            switch (punishmentType) {
                                case PUNISHMENT_KICK: {
                                    Disconnection.of(client).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);

                                    if (log.isWarnEnabled()) {
                                        logPunishment("kicked for flooding");
                                    }
                                    break;
                                }
                                case PUNISHMENT_BAN: {
                                    final long punishmentTime = settings.getPunishmentTime();

                                    // Before the login server accepts the session there is no account to ban yet.
                                    final boolean accountKnown = client.getAccountId() != null;
                                    PunishmentManager.getInstance()
                                            .startPunishment(new PunishmentTask(
                                                    accountKnown ? client.getAccountId() : client.getIp(),
                                                    accountKnown ? PunishmentAffect.ACCOUNT : PunishmentAffect.IP,
                                                    PunishmentType.BAN,
                                                    punishmentExpiration(punishmentTime),
                                                    null,
                                                    getClass().getSimpleName()));

                                    if (log.isWarnEnabled()) {
                                        final String duration = (punishmentTime <= 0)
                                                ? DURATION_FOREVER
                                                : "for " + (punishmentTime / MINUTES_TO_MILLISECONDS) + " mins.";
                                        logPunishment("banned for flooding " + duration);
                                    }
                                    break;
                                }
                                case PUNISHMENT_JAIL: {
                                    final long punishmentTime = settings.getPunishmentTime();

                                    if (player != null) {
                                        final int characterId = player.getObjectId();
                                        if (characterId > 0) {
                                            PunishmentManager.getInstance()
                                                    .startPunishment(new PunishmentTask(
                                                            characterId,
                                                            PunishmentAffect.CHARACTER,
                                                            PunishmentType.JAIL,
                                                            punishmentExpiration(punishmentTime),
                                                            null,
                                                            getClass().getSimpleName()));
                                        }
                                    }

                                    if (log.isWarnEnabled()) {
                                        final String duration = (punishmentTime <= 0)
                                                ? DURATION_FOREVER
                                                : "for " + (punishmentTime / MINUTES_TO_MILLISECONDS) + " mins.";
                                        logPunishment("jailed for flooding " + duration);
                                    }
                                    break;
                                }
                                default: {
                                    if (log.isWarnEnabled()) {
                                        log.warn(
                                                "FloodProtector: Unknown punishment type configured: {}",
                                                punishmentType);
                                    }
                                    break;
                                }
                            }
                        } finally {
                            punishmentInProgress = false;
                        }
                    }
                }
            }

            return false;
        }

        // Reset state for next interval.
        if (log.isWarnEnabled() && settings.isLogFlooding() && (requestCount.get() > 0)) {
            final StringBuilder sb = buildLogPrefix();
            sb.append(" issued ")
                    .append(requestCount.get())
                    .append(" extra requests within ~")
                    .append(settings.getProtectionInterval() * GameTimeTaskManager.MILLIS_IN_TICK)
                    .append(" ms.");
            log.warn(sb.toString());
        }

        nextGameTick = currentTick + settings.getProtectionInterval();
        logged = false;
        requestCount.set(0);
        return true;
    }

    /**
     * @return when a punishment of {@code punishmentTime} milliseconds expires, {@code 0} (never) for a non-positive
     *     time, as {@link PunishmentTask} reads it
     */
    static long punishmentExpiration(long punishmentTime) {
        return punishmentTime > 0 ? ClockBridge.wallMillis() + punishmentTime : 0;
    }

    /**
     * Logs flood protection violation with timing information.
     * @param timeUntilNext
     */
    private void logFlooding(int timeUntilNext) {
        final StringBuilder sb = buildLogPrefix();
        sb.append(" called command ")
                .append(settings.getFloodProtectorType())
                .append(" ~")
                .append(timeUntilNext)
                .append(" ms after previous command.");
        log.warn(sb.toString());
    }

    /**
     * Logs punishment action applied to violating client.
     * @param message
     */
    private void logPunishment(String message) {
        final StringBuilder sb = buildLogPrefix();
        sb.append(' ').append(message);
        log.warn(sb.toString());
    }

    /**
     * Builds log message prefix with client identification information.
     * @return StringBuilder containing client identification details
     */
    private StringBuilder buildLogPrefix() {
        final StringBuilder sb = new StringBuilder(LOG_BUILDER_INITIAL_CAPACITY);
        sb.append(settings.getFloodProtectorType()).append(": ");

        final SessionState connectionState = client.getConnectionState();
        switch (connectionState) {
            case AUTHED: {
                final Player player = client.getPlayer();
                if (player != null) {
                    sb.append(player.getName())
                            .append('(')
                            .append(player.getObjectId())
                            .append(") ");
                    break;
                }
                final String accountName = client.getAccountName();
                if (accountName != null) {
                    sb.append(accountName).append(' ');
                }
                break;
            }
            case CONNECTED: {
                try {
                    if (!client.isDetached()) {
                        final String clientAddress = client.getIp();
                        if (clientAddress != null) {
                            sb.append(clientAddress);
                        }
                    }
                } catch (Exception e) {
                    // Ignore - no IP information available.
                }
                break;
            }
            default: {
                throw new IllegalStateException(
                        "FloodProtector: Missing connection state in switch: " + connectionState);
            }
        }

        return sb;
    }
}
