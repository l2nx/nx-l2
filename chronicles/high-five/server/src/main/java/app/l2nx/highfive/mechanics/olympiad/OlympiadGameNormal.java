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
package app.l2nx.highfive.mechanics.olympiad;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.OlympiadConfig;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.type.OlympiadStadiumZone;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.olympiad.OnOlympiadMatchResult;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOlympiadMatchResult;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExOlympiadUserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.olympiad.model.OlympiadFight;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author GodKratos, Pere, DS
 */
@Slf4j
public abstract class OlympiadGameNormal extends AbstractOlympiadGame {
    private static final Logger OLYMPIAD_LOG = LoggerFactory.getLogger("olympiad");
    protected int damageP1 = 0;
    protected int damageP2 = 0;

    protected Participant playerOne;
    protected Participant playerTwo;

    protected OlympiadGameNormal(int id, Participant[] opponents) {
        super(id);

        playerOne = opponents[0];
        playerTwo = opponents[1];

        playerOne.getPlayer().setOlympiadGameId(id);
        playerTwo.getPlayer().setOlympiadGameId(id);
    }

    protected static Participant[] createListOfParticipants(List<Integer> list) {
        if ((list == null) || list.isEmpty() || (list.size() < 2)) {
            return null;
        }

        int playerOneObjectId = 0;
        Player playerOne = null;
        Player playerTwo = null;

        while (list.size() > 1) {
            playerOneObjectId = list.remove(ClockBridge.random().nextInt(list.size()));
            playerOne = World.getPlayer(playerOneObjectId);
            if ((playerOne == null) || !playerOne.isOnline()) {
                continue;
            }

            playerTwo = World.getPlayer(list.remove(ClockBridge.random().nextInt(list.size())));
            if ((playerTwo == null) || !playerTwo.isOnline()) {
                list.add(playerOneObjectId);
                continue;
            }

            final Participant[] result = new Participant[2];
            result[0] = new Participant(playerOne, 1);
            result[1] = new Participant(playerTwo, 2);

            return result;
        }

        return null;
    }

    @Override
    public boolean containsParticipant(int playerId) {
        return ((playerOne != null) && (playerOne.getObjectId() == playerId))
                || ((playerTwo != null) && (playerTwo.getObjectId() == playerId));
    }

    @Override
    public void sendOlympiadInfo(Creature creature) {
        creature.sendPacket(new ExOlympiadUserInfo(playerOne));
        creature.sendPacket(new ExOlympiadUserInfo(playerTwo));
    }

    @Override
    public void broadcastOlympiadInfo(OlympiadStadiumZone stadium) {
        stadium.broadcastPacket(new ExOlympiadUserInfo(playerOne));
        stadium.broadcastPacket(new ExOlympiadUserInfo(playerTwo));
    }

    @Override
    protected void broadcastPacket(ServerPacket packet) {
        if (playerOne.updatePlayer()) {
            playerOne.getPlayer().sendPacket(packet);
        }

        if (playerTwo.updatePlayer()) {
            playerTwo.getPlayer().sendPacket(packet);
        }
    }

    @Override
    protected boolean portPlayersToArena(List<Location> spawns) {
        boolean result = true;
        try {
            result &= portPlayerToArena(playerOne, spawns.get(0), stadiumId);
            result &= portPlayerToArena(playerTwo, spawns.get(spawns.size() / 2), stadiumId);
        } catch (Exception e) {
            log.warn("portPlayersToArena: ", e);
            return false;
        }

        return result;
    }

    @Override
    protected boolean needBuffers() {
        return true;
    }

    @Override
    protected void removals() {
        if (aborted) {
            return;
        }

        removals(playerOne.getPlayer(), true);
        removals(playerTwo.getPlayer(), true);
    }

    @Override
    protected boolean makeCompetitionStart() {
        if (!super.makeCompetitionStart() || (playerOne.getPlayer() == null) || (playerTwo.getPlayer() == null)) {
            return false;
        }

        playerOne.getPlayer().setOlympiadStart(true);
        playerOne.getPlayer().updateEffectIcons();
        playerTwo.getPlayer().setOlympiadStart(true);
        playerTwo.getPlayer().updateEffectIcons();
        return true;
    }

    @Override
    protected void cleanEffects() {
        if ((playerOne.getPlayer() != null)
                && !playerOne.isDefaulted()
                && !playerOne.isDisconnected()
                && (playerOne.getPlayer().getOlympiadGameId() == stadiumId)) {
            cleanEffects(playerOne.getPlayer());
        }

        if ((playerTwo.getPlayer() != null)
                && !playerTwo.isDefaulted()
                && !playerTwo.isDisconnected()
                && (playerTwo.getPlayer().getOlympiadGameId() == stadiumId)) {
            cleanEffects(playerTwo.getPlayer());
        }
    }

    @Override
    protected void portPlayersBack() {
        if ((playerOne.getPlayer() != null) && !playerOne.isDefaulted() && !playerOne.isDisconnected()) {
            portPlayerBack(playerOne.getPlayer());
        }

        if ((playerTwo.getPlayer() != null) && !playerTwo.isDefaulted() && !playerTwo.isDisconnected()) {
            portPlayerBack(playerTwo.getPlayer());
        }
    }

    @Override
    protected void playersStatusBack() {
        if ((playerOne.getPlayer() != null)
                && !playerOne.isDefaulted()
                && !playerOne.isDisconnected()
                && (playerOne.getPlayer().getOlympiadGameId() == stadiumId)) {
            playerStatusBack(playerOne.getPlayer());
        }

        if ((playerTwo.getPlayer() != null)
                && !playerTwo.isDefaulted()
                && !playerTwo.isDisconnected()
                && (playerTwo.getPlayer().getOlympiadGameId() == stadiumId)) {
            playerStatusBack(playerTwo.getPlayer());
        }
    }

    @Override
    protected void clearPlayers() {
        playerOne.setPlayer(null);
        playerOne = null;
        playerTwo.setPlayer(null);
        playerTwo = null;
    }

    @Override
    protected void handleDisconnect(Player player) {
        if (player.getObjectId() == playerOne.getObjectId()) {
            playerOne.setDisconnected(true);
        } else if (player.getObjectId() == playerTwo.getObjectId()) {
            playerTwo.setDisconnected(true);
        }
    }

    @Override
    protected boolean checkBattleStatus() {
        if (aborted) {
            return false;
        }

        if ((playerOne.getPlayer() == null) || playerOne.isDisconnected()) {
            return false;
        }

        if ((playerTwo.getPlayer() == null) || playerTwo.isDisconnected()) {
            return false;
        }

        return true;
    }

    @Override
    protected boolean haveWinner() {
        if (!checkBattleStatus()) {
            return true;
        }

        boolean playerOneLost = true;
        try {
            if (playerOne.getPlayer().getOlympiadGameId() == stadiumId) {
                playerOneLost = playerOne.getPlayer().isDead();
            }
        } catch (Exception e) {
            playerOneLost = true;
        }

        boolean playerTwoLost = true;
        try {
            if (playerTwo.getPlayer().getOlympiadGameId() == stadiumId) {
                playerTwoLost = playerTwo.getPlayer().isDead();
            }
        } catch (Exception e) {
            playerTwoLost = true;
        }

        return playerOneLost || playerTwoLost;
    }

    @Override
    protected void validateWinner(OlympiadStadiumZone stadium) {
        if (aborted) {
            return;
        }

        ExOlympiadMatchResult result = null;

        boolean tie = false;
        int winside = 0;

        final List<OlympiadInfo> list1 = new ArrayList<>(1);
        final List<OlympiadInfo> list2 = new ArrayList<>(1);

        final boolean pOneCrash = (playerOne.getPlayer() == null) || playerOne.isDisconnected();
        final boolean pTwoCrash = (playerTwo.getPlayer() == null) || playerTwo.isDisconnected();

        final int playerOnePoints = playerOne.getStats().getInt(POINTS);
        final int playerTwoPoints = playerTwo.getStats().getInt(POINTS);
        int pointDiff = Math.min(playerOnePoints, playerTwoPoints) / getDivider();
        if (pointDiff <= 0) {
            pointDiff = 1;
        } else if (pointDiff > OlympiadConfig.OLYMPIAD_MAX_POINTS) {
            pointDiff = OlympiadConfig.OLYMPIAD_MAX_POINTS;
        }

        int points;
        SystemMessage sm;

        // Check for if a player defaulted before battle started.
        if (playerOne.isDefaulted() || playerTwo.isDefaulted()) {
            try {
                if (playerOne.isDefaulted()) {
                    try {
                        points = Math.min(playerOnePoints / 3, OlympiadConfig.OLYMPIAD_MAX_POINTS);
                        removePointsFromParticipant(playerOne, points);
                        list1.add(OlympiadInfo.builder()
                                .name(playerOne.getName())
                                .clanName(playerOne.getClanName())
                                .clanId(playerOne.getClanId())
                                .classId(playerOne.getBaseClass())
                                .damage(damageP1)
                                .currentPoints(playerOnePoints - points)
                                .diffPoints(-points)
                                .build());

                        winside = 2;

                        if (OlympiadConfig.OLYMPIAD_LOG_FIGHTS) {
                            OLYMPIAD_LOG.info(
                                    "{} default,{},{},0,0,0,0,{},{}",
                                    playerOne.getName(),
                                    playerOne,
                                    playerTwo,
                                    points,
                                    getType());
                        }
                    } catch (Exception e) {
                        log.warn("Exception on validateWinner(): {}", e.getMessage(), e);
                    }
                }

                if (playerTwo.isDefaulted()) {
                    try {
                        points = Math.min(playerTwoPoints / 3, OlympiadConfig.OLYMPIAD_MAX_POINTS);
                        removePointsFromParticipant(playerTwo, points);
                        list2.add(OlympiadInfo.builder()
                                .name(playerTwo.getName())
                                .clanName(playerTwo.getClanName())
                                .clanId(playerTwo.getClanId())
                                .classId(playerTwo.getBaseClass())
                                .damage(damageP2)
                                .currentPoints(playerTwoPoints - points)
                                .diffPoints(-points)
                                .build());

                        if (winside == 2) {
                            tie = true;
                        } else {
                            winside = 1;
                        }

                        if (OlympiadConfig.OLYMPIAD_LOG_FIGHTS) {
                            OLYMPIAD_LOG.info(
                                    "{} default,{},{},0,0,0,0,{},{}",
                                    playerTwo.getName(),
                                    playerOne,
                                    playerTwo,
                                    points,
                                    getType());
                        }
                    } catch (Exception e) {
                        log.warn("Exception on validateWinner(): {}", e.getMessage(), e);
                    }
                }

                if (winside == 1) {
                    result = new ExOlympiadMatchResult(tie, winside, list1, list2);
                } else {
                    result = new ExOlympiadMatchResult(tie, winside, list2, list1);
                }

                stadium.broadcastPacket(result);
                return;
            } catch (Exception e) {
                log.warn("Exception on validateWinner(): {}", e.getMessage(), e);
                return;
            }
        }

        // Create results for players if a player crashed.
        if (pOneCrash || pTwoCrash) {
            try {
                if (pTwoCrash && !pOneCrash) {
                    sm = new SystemMessage(SystemMessageId.CONGRATULATIONS_C1_YOU_WIN_THE_MATCH);
                    sm.addString(playerOne.getName());
                    stadium.broadcastPacket(sm);

                    playerOne.updateStat(COMP_WON, 1);
                    addPointsToParticipant(playerOne, pointDiff);
                    list1.add(OlympiadInfo.builder()
                            .name(playerOne.getName())
                            .clanName(playerOne.getClanName())
                            .clanId(playerOne.getClanId())
                            .classId(playerOne.getBaseClass())
                            .damage(damageP1)
                            .currentPoints(playerOnePoints + pointDiff)
                            .diffPoints(pointDiff)
                            .build());

                    playerTwo.updateStat(COMP_LOST, 1);
                    removePointsFromParticipant(playerTwo, pointDiff);
                    list2.add(OlympiadInfo.builder()
                            .name(playerTwo.getName())
                            .clanName(playerTwo.getClanName())
                            .clanId(playerTwo.getClanId())
                            .classId(playerTwo.getBaseClass())
                            .damage(damageP2)
                            .currentPoints(playerTwoPoints - pointDiff)
                            .diffPoints(-pointDiff)
                            .build());

                    winside = 1;

                    rewardParticipant(playerOne.getPlayer(), getReward());

                    if (OlympiadConfig.OLYMPIAD_LOG_FIGHTS) {
                        OLYMPIAD_LOG.info(
                                "{} crash,{},{},0,0,0,0,{},{}",
                                playerTwo.getName(),
                                playerOne,
                                playerTwo,
                                pointDiff,
                                getType());
                    }

                    // Notify to scripts
                    if (EventDispatcher.getInstance()
                            .hasListener(EventType.ON_OLYMPIAD_MATCH_RESULT, Olympiad.getInstance())) {
                        EventDispatcher.getInstance()
                                .notifyEventAsync(
                                        OnOlympiadMatchResult.builder()
                                                .winner(playerOne)
                                                .loser(playerTwo)
                                                .competitionType(getType())
                                                .build(),
                                        Olympiad.getInstance());
                    }
                } else if (pOneCrash && !pTwoCrash) {
                    sm = new SystemMessage(SystemMessageId.CONGRATULATIONS_C1_YOU_WIN_THE_MATCH);
                    sm.addString(playerTwo.getName());
                    stadium.broadcastPacket(sm);

                    playerTwo.updateStat(COMP_WON, 1);
                    addPointsToParticipant(playerTwo, pointDiff);
                    list2.add(OlympiadInfo.builder()
                            .name(playerTwo.getName())
                            .clanName(playerTwo.getClanName())
                            .clanId(playerTwo.getClanId())
                            .classId(playerTwo.getBaseClass())
                            .damage(damageP2)
                            .currentPoints(playerTwoPoints + pointDiff)
                            .diffPoints(pointDiff)
                            .build());

                    playerOne.updateStat(COMP_LOST, 1);
                    removePointsFromParticipant(playerOne, pointDiff);
                    list1.add(OlympiadInfo.builder()
                            .name(playerOne.getName())
                            .clanName(playerOne.getClanName())
                            .clanId(playerOne.getClanId())
                            .classId(playerOne.getBaseClass())
                            .damage(damageP1)
                            .currentPoints(playerOnePoints - pointDiff)
                            .diffPoints(-pointDiff)
                            .build());

                    winside = 2;

                    rewardParticipant(playerTwo.getPlayer(), getReward());

                    if (OlympiadConfig.OLYMPIAD_LOG_FIGHTS) {
                        OLYMPIAD_LOG.info(
                                "{} crash,{},{},0,0,0,0,{},{}",
                                playerOne.getName(),
                                playerOne,
                                playerTwo,
                                pointDiff,
                                getType());
                    }

                    // Notify to scripts
                    if (EventDispatcher.getInstance()
                            .hasListener(EventType.ON_OLYMPIAD_MATCH_RESULT, Olympiad.getInstance())) {
                        EventDispatcher.getInstance()
                                .notifyEventAsync(
                                        OnOlympiadMatchResult.builder()
                                                .winner(playerTwo)
                                                .loser(playerOne)
                                                .competitionType(getType())
                                                .build(),
                                        Olympiad.getInstance());
                    }
                } else if (pOneCrash && pTwoCrash) {
                    stadium.broadcastPacket(
                            new SystemMessage(SystemMessageId.THERE_IS_NO_VICTOR_THE_MATCH_ENDS_IN_A_TIE));

                    playerOne.updateStat(COMP_LOST, 1);
                    removePointsFromParticipant(playerOne, pointDiff);
                    list1.add(OlympiadInfo.builder()
                            .name(playerOne.getName())
                            .clanName(playerOne.getClanName())
                            .clanId(playerOne.getClanId())
                            .classId(playerOne.getBaseClass())
                            .damage(damageP1)
                            .currentPoints(playerOnePoints - pointDiff)
                            .diffPoints(-pointDiff)
                            .build());

                    playerTwo.updateStat(COMP_LOST, 1);
                    removePointsFromParticipant(playerTwo, pointDiff);
                    list2.add(OlympiadInfo.builder()
                            .name(playerTwo.getName())
                            .clanName(playerTwo.getClanName())
                            .clanId(playerTwo.getClanId())
                            .classId(playerTwo.getBaseClass())
                            .damage(damageP2)
                            .currentPoints(playerTwoPoints - pointDiff)
                            .diffPoints(-pointDiff)
                            .build());

                    tie = true;

                    if (OlympiadConfig.OLYMPIAD_LOG_FIGHTS) {
                        OLYMPIAD_LOG.info(
                                "both crash,{},{},0,0,0,0,{},{},{}",
                                playerOne.getName(),
                                playerOne,
                                playerTwo,
                                pointDiff,
                                getType());
                    }
                }

                playerOne.updateStat(COMP_DONE, 1);
                playerTwo.updateStat(COMP_DONE, 1);
                playerOne.updateStat(COMP_DONE_WEEK, 1);
                playerTwo.updateStat(COMP_DONE_WEEK, 1);
                playerOne.updateStat(getWeeklyMatchType(), 1);
                playerTwo.updateStat(getWeeklyMatchType(), 1);

                if (winside == 1) {
                    result = new ExOlympiadMatchResult(tie, winside, list1, list2);
                } else {
                    result = new ExOlympiadMatchResult(tie, winside, list2, list1);
                }

                stadium.broadcastPacket(result);

                // Notify to scripts
                if (EventDispatcher.getInstance()
                        .hasListener(EventType.ON_OLYMPIAD_MATCH_RESULT, Olympiad.getInstance())) {
                    EventDispatcher.getInstance()
                            .notifyEventAsync(
                                    OnOlympiadMatchResult.builder()
                                            .winner(null)
                                            .loser(playerOne)
                                            .competitionType(getType())
                                            .build(),
                                    Olympiad.getInstance());
                    EventDispatcher.getInstance()
                            .notifyEventAsync(
                                    OnOlympiadMatchResult.builder()
                                            .winner(null)
                                            .loser(playerTwo)
                                            .competitionType(getType())
                                            .build(),
                                    Olympiad.getInstance());
                }
                return;
            } catch (Exception e) {
                log.warn("Exception on validateWinner(): {}", e.getMessage(), e);
                return;
            }
        }

        try {
            String winner = "draw";

            // Calculate Fight time
            final long fightTime = ClockBridge.wallMillis() - startTime;

            double playerOneHp = 0;
            if ((playerOne.getPlayer() != null) && !playerOne.getPlayer().isDead()) {
                playerOneHp = playerOne.getPlayer().getCurrentHp()
                        + playerOne.getPlayer().getCurrentCp();
                if (playerOneHp < 0.5) {
                    playerOneHp = 0;
                }
            }

            double playerTwoHp = 0;
            if ((playerTwo.getPlayer() != null) && !playerTwo.getPlayer().isDead()) {
                playerTwoHp = playerTwo.getPlayer().getCurrentHp()
                        + playerTwo.getPlayer().getCurrentCp();
                if (playerTwoHp < 0.5) {
                    playerTwoHp = 0;
                }
            }

            // If players crashed, search if they've relogged.
            playerOne.updatePlayer();
            playerTwo.updatePlayer();

            if (((playerOne.getPlayer() == null) || !playerOne.getPlayer().isOnline())
                    && ((playerTwo.getPlayer() == null)
                            || !playerTwo.getPlayer().isOnline())) {
                playerOne.updateStat(COMP_DRAWN, 1);
                playerTwo.updateStat(COMP_DRAWN, 1);
                sm = new SystemMessage(SystemMessageId.THERE_IS_NO_VICTOR_THE_MATCH_ENDS_IN_A_TIE);
                stadium.broadcastPacket(sm);
            } else if ((playerTwo.getPlayer() == null)
                    || !playerTwo.getPlayer().isOnline()
                    || ((playerTwoHp == 0) && (playerOneHp != 0))
                    || ((damageP1 > damageP2) && (playerTwoHp != 0) && (playerOneHp != 0))) {
                sm = new SystemMessage(SystemMessageId.CONGRATULATIONS_C1_YOU_WIN_THE_MATCH);
                sm.addString(playerOne.getName());
                stadium.broadcastPacket(sm);

                playerOne.updateStat(COMP_WON, 1);
                playerTwo.updateStat(COMP_LOST, 1);

                addPointsToParticipant(playerOne, pointDiff);
                list1.add(OlympiadInfo.builder()
                        .name(playerOne.getName())
                        .clanName(playerOne.getClanName())
                        .clanId(playerOne.getClanId())
                        .classId(playerOne.getBaseClass())
                        .damage(damageP1)
                        .currentPoints(playerOnePoints + pointDiff)
                        .diffPoints(pointDiff)
                        .build());

                removePointsFromParticipant(playerTwo, pointDiff);
                list2.add(OlympiadInfo.builder()
                        .name(playerTwo.getName())
                        .clanName(playerTwo.getClanName())
                        .clanId(playerTwo.getClanId())
                        .classId(playerTwo.getBaseClass())
                        .damage(damageP2)
                        .currentPoints(playerTwoPoints - pointDiff)
                        .diffPoints(-pointDiff)
                        .build());
                winner = playerOne.getName() + " won";

                winside = 1;

                // Save Fight Result
                saveResults(playerOne, playerTwo, 1, startTime, fightTime, getType());
                rewardParticipant(playerOne.getPlayer(), getReward());

                // Notify to scripts
                if (EventDispatcher.getInstance()
                        .hasListener(EventType.ON_OLYMPIAD_MATCH_RESULT, Olympiad.getInstance())) {
                    EventDispatcher.getInstance()
                            .notifyEventAsync(
                                    OnOlympiadMatchResult.builder()
                                            .winner(playerOne)
                                            .loser(playerTwo)
                                            .competitionType(getType())
                                            .build(),
                                    Olympiad.getInstance());
                }
            } else if ((playerOne.getPlayer() == null)
                    || !playerOne.getPlayer().isOnline()
                    || ((playerOneHp == 0) && (playerTwoHp != 0))
                    || ((damageP2 > damageP1) && (playerOneHp != 0) && (playerTwoHp != 0))) {
                sm = new SystemMessage(SystemMessageId.CONGRATULATIONS_C1_YOU_WIN_THE_MATCH);
                sm.addString(playerTwo.getName());
                stadium.broadcastPacket(sm);

                playerTwo.updateStat(COMP_WON, 1);
                playerOne.updateStat(COMP_LOST, 1);

                addPointsToParticipant(playerTwo, pointDiff);
                list2.add(OlympiadInfo.builder()
                        .name(playerTwo.getName())
                        .clanName(playerTwo.getClanName())
                        .clanId(playerTwo.getClanId())
                        .classId(playerTwo.getBaseClass())
                        .damage(damageP2)
                        .currentPoints(playerTwoPoints + pointDiff)
                        .diffPoints(pointDiff)
                        .build());

                removePointsFromParticipant(playerOne, pointDiff);
                list1.add(OlympiadInfo.builder()
                        .name(playerOne.getName())
                        .clanName(playerOne.getClanName())
                        .clanId(playerOne.getClanId())
                        .classId(playerOne.getBaseClass())
                        .damage(damageP1)
                        .currentPoints(playerOnePoints - pointDiff)
                        .diffPoints(-pointDiff)
                        .build());

                winner = playerTwo.getName() + " won";
                winside = 2;

                // Save Fight Result
                saveResults(playerOne, playerTwo, 2, startTime, fightTime, getType());
                rewardParticipant(playerTwo.getPlayer(), getReward());

                // Notify to scripts
                if (EventDispatcher.getInstance()
                        .hasListener(EventType.ON_OLYMPIAD_MATCH_RESULT, Olympiad.getInstance())) {
                    EventDispatcher.getInstance()
                            .notifyEventAsync(
                                    OnOlympiadMatchResult.builder()
                                            .winner(playerTwo)
                                            .loser(playerOne)
                                            .competitionType(getType())
                                            .build(),
                                    Olympiad.getInstance());
                }
            } else {
                // Save Fight Result
                saveResults(playerOne, playerTwo, 0, startTime, fightTime, getType());

                sm = new SystemMessage(SystemMessageId.THERE_IS_NO_VICTOR_THE_MATCH_ENDS_IN_A_TIE);
                stadium.broadcastPacket(sm);

                int value = Math.min(playerOnePoints / getDivider(), OlympiadConfig.OLYMPIAD_MAX_POINTS);

                removePointsFromParticipant(playerOne, value);
                list1.add(OlympiadInfo.builder()
                        .name(playerOne.getName())
                        .clanName(playerOne.getClanName())
                        .clanId(playerOne.getClanId())
                        .classId(playerOne.getBaseClass())
                        .damage(damageP1)
                        .currentPoints(playerOnePoints - value)
                        .diffPoints(-value)
                        .build());

                value = Math.min(playerTwoPoints / getDivider(), OlympiadConfig.OLYMPIAD_MAX_POINTS);
                removePointsFromParticipant(playerTwo, value);
                list2.add(OlympiadInfo.builder()
                        .name(playerTwo.getName())
                        .clanName(playerTwo.getClanName())
                        .clanId(playerTwo.getClanId())
                        .classId(playerTwo.getBaseClass())
                        .damage(damageP2)
                        .currentPoints(playerTwoPoints - value)
                        .diffPoints(-value)
                        .build());

                tie = true;
            }

            playerOne.updateStat(COMP_DONE, 1);
            playerTwo.updateStat(COMP_DONE, 1);
            playerOne.updateStat(COMP_DONE_WEEK, 1);
            playerTwo.updateStat(COMP_DONE_WEEK, 1);
            playerOne.updateStat(getWeeklyMatchType(), 1);
            playerTwo.updateStat(getWeeklyMatchType(), 1);

            if (winside == 1) {
                result = new ExOlympiadMatchResult(tie, winside, list1, list2);
            } else {
                result = new ExOlympiadMatchResult(tie, winside, list2, list1);
            }

            stadium.broadcastPacket(result);

            if (OlympiadConfig.OLYMPIAD_LOG_FIGHTS) {
                OLYMPIAD_LOG.info(
                        "{},{},{},{},{},{},{},{},{},{}",
                        winner,
                        playerOne.getName(),
                        playerOne,
                        playerTwo,
                        playerOneHp,
                        playerTwoHp,
                        damageP1,
                        damageP2,
                        pointDiff,
                        getType());
            }
        } catch (Exception e) {
            log.warn("Exception on validateWinner(): {}", e.getMessage(), e);
        }
    }

    @Override
    protected void addDamage(Player player, int damage) {
        final Player player1 = playerOne.getPlayer();
        final Player player2 = playerTwo.getPlayer();
        if ((player1 == null) || (player2 == null)) {
            return;
        }

        if (player == player1) {
            if (!player2.isInvul()) {
                damageP1 += damage;
            }
        } else if (player == player2) {
            if (!player1.isInvul()) {
                damageP2 += damage;
            }
        }
    }

    @Override
    public String[] getPlayerNames() {
        return new String[] {playerOne.getName(), playerTwo.getName()};
    }

    @Override
    public boolean checkDefaulted() {
        SystemMessage reason;
        playerOne.updatePlayer();
        playerTwo.updatePlayer();

        reason = checkDefaulted(playerOne.getPlayer());
        if (reason != null) {
            playerOne.setDefaulted(true);
            if (playerTwo.getPlayer() != null) {
                playerTwo.getPlayer().sendPacket(reason);
            }
        }

        reason = checkDefaulted(playerTwo.getPlayer());
        if (reason != null) {
            playerTwo.setDefaulted(true);
            if (playerOne.getPlayer() != null) {
                playerOne.getPlayer().sendPacket(reason);
            }
        }

        return playerOne.isDefaulted() || playerTwo.isDefaulted();
    }

    @Override
    public void resetDamage() {
        damageP1 = 0;
        damageP2 = 0;
    }

    protected void saveResults(
            Participant one, Participant two, int winner, long startTime, long fightTime, CompetitionType type) {
        Olympiad.getInstance()
                .saveFight(OlympiadFight.builder()
                        .firstObjectId(one.getObjectId())
                        .secondObjectId(two.getObjectId())
                        .firstClassId(one.getBaseClass())
                        .secondClassId(two.getBaseClass())
                        .winner(winner)
                        .startedAt(instantOrNull(startTime))
                        .durationMs(fightTime)
                        .classed(type == CompetitionType.CLASSED)
                        .build());
    }

    @Override
    public void makePlayersInvul() {
        if (playerOne.getPlayer() != null) {
            playerOne.getPlayer().setInvul(true);
        }

        if (playerTwo.getPlayer() != null) {
            playerTwo.getPlayer().setInvul(true);
        }
    }

    @Override
    public void removePlayersInvul() {
        if (playerOne.getPlayer() != null) {
            playerOne.getPlayer().setInvul(false);
        }

        if (playerTwo.getPlayer() != null) {
            playerTwo.getPlayer().setInvul(false);
        }
    }
}
