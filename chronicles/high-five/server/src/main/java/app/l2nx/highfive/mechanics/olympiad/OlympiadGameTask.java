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

import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.entity.zone.type.OlympiadStadiumZone;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author DS
 */
@Slf4j
public class OlympiadGameTask implements Runnable {
    protected static final long BATTLE_PERIOD = OlympiadConfig.OLYMPIAD_BATTLE; // 6 mins

    private static final int[] TELEPORT_TO_ARENA_TIMES = {120, 60, 30, 15, 10, 5, 4, 3, 2, 1, 0};
    private static final int[] BATTLE_START_TIME_FIRST = {60, 50, 40, 30, 20, 10, 0};
    private static final int[] BATTLE_START_TIME_SECOND = {10, 5, 4, 3, 2, 1, 0};
    private static final int[] TELEPORT_TO_TOWN_TIMES = {40, 30, 20, 10, 5, 4, 3, 2, 1, 0};

    @Getter
    private final OlympiadStadiumZone zone;

    @Getter
    private AbstractOlympiadGame game;

    private OlympiadGameState state = OlympiadGameState.IDLE;
    private boolean needAnnounce = false;
    private int countDown = 0;

    public OlympiadGameTask(OlympiadStadiumZone zone, int instanceId) {
        this.zone = zone;
        this.zone.setInstanceId(instanceId);
        zone.registerTask(this);
    }

    public boolean isRunning() {
        return state != OlympiadGameState.IDLE;
    }

    public boolean isGameStarted() {
        return (state.ordinal() >= OlympiadGameState.GAME_STARTED.ordinal())
                && (state.ordinal() <= OlympiadGameState.CLEANUP.ordinal());
    }

    public boolean isBattleStarted() {
        return state == OlympiadGameState.BATTLE_IN_PROGRESS;
    }

    public boolean isBattleFinished() {
        return state == OlympiadGameState.TELEPORT_TO_TOWN;
    }

    public boolean needAnnounce() {
        if (needAnnounce) {
            needAnnounce = false;
            return true;
        }

        return false;
    }

    public void attachGame(AbstractOlympiadGame game) {
        if ((game != null) && (state != OlympiadGameState.IDLE)) {
            log.warn("Attempt to overwrite non-finished game in state {}", state);
            return;
        }

        this.game = game;
        state = OlympiadGameState.BEGIN;
        needAnnounce = false;
        ThreadPool.execute(this);
    }

    @Override
    public void run() {
        try {
            int delay = 1; // Schedule next call after 1s.
            switch (state) {
                // Game created
                case BEGIN: {
                    state = OlympiadGameState.TELEPORT_TO_ARENA;
                    countDown = OlympiadConfig.OLYMPIAD_WAIT_TIME;
                    break;
                }
                // Teleport to arena countdown.
                case TELEPORT_TO_ARENA: {
                    if (countDown > 0) {
                        final SystemMessage sm = new SystemMessage(
                                SystemMessageId.YOU_WILL_BE_MOVED_TO_THE_OLYMPIAD_STADIUM_IN_S1_SECOND_S);
                        sm.addInt(countDown);
                        game.broadcastPacket(sm);
                    }

                    delay = getDelay(TELEPORT_TO_ARENA_TIMES);
                    if (countDown <= 0) {
                        state = OlympiadGameState.GAME_STARTED;
                    }
                    break;
                }
                // Game start, port players to arena.
                case GAME_STARTED: {
                    if (!startGame()) {
                        state = OlympiadGameState.GAME_CANCELLED;
                        break;
                    }

                    state = OlympiadGameState.BATTLE_COUNTDOWN_FIRST;
                    countDown = BATTLE_START_TIME_FIRST[0];
                    delay = 5;
                    break;
                }
                // Battle start countdown, first part (60-10).
                case BATTLE_COUNTDOWN_FIRST: {
                    if (countDown > 0) {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.THE_MATCH_WILL_START_IN_S1_SECOND_S);
                        sm.addInt(countDown);
                        zone.broadcastPacket(sm);
                    }

                    delay = getDelay(BATTLE_START_TIME_FIRST);
                    if (countDown <= 0) {
                        game.makePlayersInvul();
                        openDoors();

                        state = OlympiadGameState.BATTLE_COUNTDOWN_SECOND;
                        countDown = BATTLE_START_TIME_SECOND[0];
                        delay = getDelay(BATTLE_START_TIME_SECOND);
                    }
                    break;
                }
                // Battle start countdown, second part (10-0).
                case BATTLE_COUNTDOWN_SECOND: {
                    if (countDown > 0) {
                        final SystemMessage sm = new SystemMessage(SystemMessageId.THE_MATCH_WILL_START_IN_S1_SECOND_S);
                        sm.addInt(countDown);
                        zone.broadcastPacket(sm);
                    }

                    delay = getDelay(BATTLE_START_TIME_SECOND);
                    if (countDown <= 0) {
                        state = OlympiadGameState.BATTLE_STARTED;
                        game.removePlayersInvul();
                    }
                    break;
                }
                // Beginning of the battle.
                case BATTLE_STARTED: {
                    countDown = 0;
                    state = OlympiadGameState.BATTLE_IN_PROGRESS; // Set state first, used in zone update.
                    if (!startBattle()) {
                        state = OlympiadGameState.GAME_STOPPED;
                    }
                    break;
                }
                // Checks during battle
                case BATTLE_IN_PROGRESS: {
                    countDown += 1000;
                    if (checkBattle() || (countDown > OlympiadConfig.OLYMPIAD_BATTLE)) {
                        state = OlympiadGameState.GAME_STOPPED;
                    }
                    break;
                }
                // Battle cancelled before teleport participants to the stadium
                case GAME_CANCELLED: {
                    stopGame();
                    state = OlympiadGameState.CLEANUP;
                    break;
                }
                // End of the battle.
                case GAME_STOPPED: {
                    state = OlympiadGameState.TELEPORT_TO_TOWN;
                    countDown = TELEPORT_TO_TOWN_TIMES[0];
                    stopGame();
                    delay = getDelay(TELEPORT_TO_TOWN_TIMES);
                    break;
                }
                // Teleport to town countdown.
                case TELEPORT_TO_TOWN: {
                    if (countDown > 0) {
                        final SystemMessage sm =
                                new SystemMessage(SystemMessageId.YOU_WILL_BE_MOVED_BACK_TO_TOWN_IN_S1_SECOND_S);
                        sm.addInt(countDown);
                        game.broadcastPacket(sm);
                    }

                    delay = getDelay(TELEPORT_TO_TOWN_TIMES);
                    if (countDown <= 0) {
                        state = OlympiadGameState.CLEANUP;
                    }
                    break;
                }
                // Removals
                case CLEANUP: {
                    cleanupGame();
                    state = OlympiadGameState.IDLE;
                    game = null;
                    return;
                }
            }

            ThreadPool.schedule(this, delay * 1000);
        } catch (Exception e) {
            switch (state) {
                case GAME_STOPPED:
                case TELEPORT_TO_TOWN:
                case CLEANUP:
                case IDLE: {
                    log.warn("Unable to return players back in town, exception: {}", e.getMessage());
                    state = OlympiadGameState.IDLE;
                    game = null;
                    return;
                }
            }

            log.warn("Exception in {}, trying to port players back: {}", state, e.getMessage(), e);
            state = OlympiadGameState.GAME_STOPPED;
            ThreadPool.schedule(this, 1000);
        }
    }

    private int getDelay(int[] times) {
        int time;
        for (int i = 0; i < (times.length - 1); i++) {
            time = times[i];
            if (time >= countDown) {
                continue;
            }

            final int delay = countDown - time;
            countDown = time;
            return delay;
        }

        // should not happens
        countDown = -1;
        return 1;
    }

    /**
     * Second stage: check for defaulted, port players to arena, announce game.
     * @return true if no participants defaulted.
     */
    private boolean startGame() {
        try {
            // Checking for opponents and teleporting to arena.
            if (game.checkDefaulted()) {
                return false;
            }

            zone.closeDoors();
            if (game.needBuffers()) {
                zone.spawnBuffers();
            }

            if (!game.portPlayersToArena(zone.getSpawns())) {
                return false;
            }

            game.removals();
            needAnnounce = true;
            OlympiadGameManager.getInstance().startBattle(); // inform manager
            return true;
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        return false;
    }

    /**
     * Third stage: open doors.
     */
    private void openDoors() {
        try {
            game.resetDamage();
            zone.openDoors();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    /**
     * Fourth stage: last checks, remove buffers, start competition itself.
     * @return true if all participants online and ready on the stadium.
     */
    private boolean startBattle() {
        try {
            if (game.needBuffers()) {
                zone.deleteBuffers();
            }

            if (game.checkBattleStatus() && game.makeCompetitionStart()) {
                // game successfully started
                game.broadcastOlympiadInfo(zone);
                zone.broadcastPacket(new SystemMessage(SystemMessageId.THE_MATCH_HAS_STARTED_FIGHT));
                zone.updateZoneStatusForCharactersInside();
                return true;
            }
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        return false;
    }

    /**
     * Fifth stage: battle is running, returns true if winner found.
     * @return
     */
    private boolean checkBattle() {
        try {
            return game.haveWinner();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        return true;
    }

    /**
     * Sixth stage: winner's validations
     */
    private void stopGame() {
        try {
            game.validateWinner(zone);
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            game.cleanEffects();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            game.makePlayersInvul();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            zone.updateZoneStatusForCharactersInside();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    /**
     * Seventh stage: game cleanup (port players back, closing doors, etc)
     */
    private void cleanupGame() {
        try {
            game.removePlayersInvul();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            game.playersStatusBack();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            game.portPlayersBack();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            game.clearPlayers();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }

        try {
            zone.closeDoors();
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
        }
    }
}
