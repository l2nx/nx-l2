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
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.player.DuelResult;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.actor.instance.OlympiadManager;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.type.OlympiadStadiumZone;
import app.l2nx.highfive.managers.DuelManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.ExDuelEnd;
import app.l2nx.highfive.network.serverpackets.ExDuelReady;
import app.l2nx.highfive.network.serverpackets.ExDuelStart;
import app.l2nx.highfive.network.serverpackets.ExDuelUpdateUserInfo;
import app.l2nx.highfive.network.serverpackets.PlaySound;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.network.serverpackets.SocialAction;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Duel {

    public static final int DUELSTATE_NODUEL = 0;
    public static final int DUELSTATE_DUELLING = 1;
    public static final int DUELSTATE_DEAD = 2;
    public static final int DUELSTATE_WINNER = 3;
    public static final int DUELSTATE_INTERRUPTED = 4;

    private static final PlaySound B04_S01 = new PlaySound(1, "B04_S01", 0, 0, 0, 0, 0);

    private static final int PARTY_DUEL_DURATION = 300;
    private static final int PLAYER_DUEL_DURATION = 120;

    @Getter
    private final int id;

    @Getter
    private Player playerA;

    @Getter
    private Player playerB;

    @Getter
    private final boolean partyDuel;

    private final long duelEndTime;
    private int surrenderRequest = 0;
    private int countdown = 5;

    @Setter
    private boolean finished = false;

    private final Map<Integer, PlayerCondition> playerConditions = new ConcurrentHashMap<>();

    @Getter
    private int dueldInstanceId;

    public Duel(Player playerA, Player playerB, int partyDuel, int duelId) {
        id = duelId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.partyDuel = partyDuel == 1;
        if (this.partyDuel) {
            for (Player member : this.playerA.getParty().getMembers()) {
                member.setStartingDuel();
            }

            for (Player member : this.playerB.getParty().getMembers()) {
                member.setStartingDuel();
            }
        } else {
            this.playerA.setStartingDuel();
            this.playerB.setStartingDuel();
        }

        duelEndTime =
                ClockBridge.gameMillis() + ((this.partyDuel ? PARTY_DUEL_DURATION : PLAYER_DUEL_DURATION) * 1000L);
        setFinished(false);

        if (this.partyDuel) {
            // Inform players that they will be ported shortly.
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId.IN_A_MOMENT_YOU_WILL_BE_TRANSPORTED_TO_THE_SITE_WHERE_THE_DUEL_WILL_TAKE_PLACE);
            broadcastToTeam1(sm);
            broadcastToTeam2(sm);
        }

        // Schedule duel start
        ThreadPool.schedule(new ScheduleStartDuelTask(this), 3000);
    }

    public static class PlayerCondition {
        @Getter
        private Player player;

        private double hp;
        private double mp;
        private double cp;
        private boolean paDuel;
        private int x;
        private int y;
        private int z;
        private Set<Skill> debuffs;

        public PlayerCondition(Player player, boolean partyDuel) {
            if (player == null) {
                return;
            }

            this.player = player;
            hp = this.player.getCurrentHp();
            mp = this.player.getCurrentMp();
            cp = this.player.getCurrentCp();
            paDuel = partyDuel;
            if (paDuel) {
                x = this.player.getX();
                y = this.player.getY();
                z = this.player.getZ();
            }
        }

        public void restoreCondition() {
            if (player == null) {
                return;
            }

            player.setCurrentHp(hp);
            player.setCurrentMp(mp);
            player.setCurrentCp(cp);

            if (paDuel) {
                teleportBack();
            }

            if (debuffs != null) // Debuff removal
            {
                for (Skill skill : debuffs) {
                    if (skill != null) {
                        player.stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
                    }
                }
            }
        }

        public void registerDebuff(Skill debuff) {
            if (debuffs == null) {
                debuffs = ConcurrentHashMap.newKeySet();
            }

            debuffs.add(debuff);
        }

        public void teleportBack() {
            if (paDuel) {
                player.teleToLocation(x, y, z);
            }
        }
    }

    public class ScheduleDuelTask implements Runnable {
        private final Duel duel;

        public ScheduleDuelTask(Duel duel) {
            this.duel = duel;
        }

        @Override
        public void run() {
            try {
                switch (duel.checkEndDuelCondition()) {
                    case CANCELED: {
                        // do not schedule duel end if it was interrupted
                        setFinished(true);
                        duel.endDuel(DuelResult.CANCELED);
                        break;
                    }
                    case CONTINUE: {
                        ThreadPool.schedule(this, 1000);
                        break;
                    }
                    default: {
                        setFinished(true);
                        playKneelAnimation();
                        ThreadPool.schedule(new ScheduleEndDuelTask(duel, duel.checkEndDuelCondition()), 5000);
                        InstanceManager.getInstance().destroyInstance(duel.getDueldInstanceId());
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("There has been a problem while runing a duel task!", e);
            }
        }
    }

    @Slf4j
    public static class ScheduleStartDuelTask implements Runnable {
        private final Duel duel;

        public ScheduleStartDuelTask(Duel duel) {
            this.duel = duel;
        }

        @Override
        public void run() {
            try {
                // start/continue countdown
                final int count = duel.countdown();
                if (count == 4) {
                    // Save player conditions before teleporting players.
                    duel.savePlayerConditions();

                    duel.teleportPlayers();

                    // give players 20 seconds to complete teleport and get ready (its ought to be 30 on official..)
                    ThreadPool.schedule(this, duel.isPartyDuel() ? 20000 : 1);
                } else if (count > 0) // Duel not started yet - continue countdown.
                {
                    ThreadPool.schedule(this, 1000);
                } else {
                    duel.startDuel();
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    @Slf4j
    public static class ScheduleEndDuelTask implements Runnable {
        private final Duel duel;
        private final DuelResult result;

        public ScheduleEndDuelTask(Duel duel, DuelResult result) {
            this.duel = duel;
            this.result = result;
        }

        @Override
        public void run() {
            try {
                duel.endDuel(result);
            } catch (Exception e) {
                log.error("There has been a problem while runing a duel end task!", e);
            }
        }
    }

    /**
     * Stops all players from attacking. Used for duel timeout / interrupt.
     */
    private void stopFighting() {
        final ActionFailed af = ActionFailed.STATIC_PACKET;
        if (partyDuel) {
            for (Player temp : playerA.getParty().getMembers()) {
                temp.abortCast();
                temp.getAI().setIntentionActive();
                temp.setTarget(null);
                temp.sendPacket(af);
                if (temp.hasSummon()
                        && (temp.getSummon() != null)
                        && !temp.getSummon().isDead()) {
                    temp.getSummon().abortCast();
                    temp.getSummon().getAI().setIntentionActive();
                    temp.getSummon().setTarget(null);
                    temp.getSummon().sendPacket(af);
                }
            }

            for (Player temp : playerB.getParty().getMembers()) {
                temp.abortCast();
                temp.getAI().setIntentionActive();
                temp.setTarget(null);
                temp.sendPacket(af);
                if (temp.hasSummon()
                        && (temp.getSummon() != null)
                        && !temp.getSummon().isDead()) {
                    temp.getSummon().abortCast();
                    temp.getSummon().getAI().setIntentionActive();
                    temp.getSummon().setTarget(null);
                    temp.getSummon().sendPacket(af);
                }
            }
        } else {
            playerA.abortCast();
            playerB.abortCast();
            playerA.getAI().setIntentionActive();
            playerA.setTarget(null);
            playerB.getAI().setIntentionActive();
            playerB.setTarget(null);
            playerA.sendPacket(af);
            playerB.sendPacket(af);
            if (playerA.hasSummon()
                    && (playerA.getSummon() != null)
                    && !playerA.getSummon().isDead()) {
                playerA.getSummon().abortCast();
                playerA.getSummon().getAI().setIntentionActive();
                playerA.getSummon().setTarget(null);
                playerA.getSummon().sendPacket(af);
            }

            if (playerB.hasSummon()
                    && (playerB.getSummon() != null)
                    && !playerB.getSummon().isDead()) {
                playerB.getSummon().abortCast();
                playerB.getSummon().getAI().setIntentionActive();
                playerB.getSummon().setTarget(null);
                playerB.getSummon().sendPacket(af);
            }
        }
    }

    /**
     * Check if a player engaged in pvp combat (only for 1on1 duels)
     * @param sendMessage
     * @return returns true if a duelist is engaged in Pvp combat
     */
    public boolean isDuelistInPvp(boolean sendMessage) {
        if (partyDuel) {
            // Party duels take place in arenas - should be no other players there.
            return false;
        } else if ((playerA.getPvpFlag() != 0) || (playerB.getPvpFlag() != 0)) {
            if (sendMessage) {
                final String engagedInPvP = "The duel was canceled because a duelist engaged in PvP combat.";
                playerA.sendMessage(engagedInPvP);
                playerB.sendMessage(engagedInPvP);
            }

            return true;
        }

        return false;
    }

    /**
     * Starts the duel
     */
    public void startDuel() {
        if ((playerA == null) || (playerB == null) || playerA.isInDuel() || playerB.isInDuel()) {
            playerConditions.clear();
            DuelManager.getInstance().removeDuel(this);
            return;
        }

        if (partyDuel) {
            // Set duel state and team.
            for (Player temp : playerA.getParty().getMembers()) {
                temp.cancelActiveTrade();
                temp.setInDuel(id);
                temp.setTeam(Team.BLUE);
                temp.broadcastUserInfo();
                broadcastToTeam2(new ExDuelUpdateUserInfo(temp));
            }

            for (Player temp : playerB.getParty().getMembers()) {
                temp.cancelActiveTrade();
                temp.setInDuel(id);
                temp.setTeam(Team.RED);
                temp.broadcastUserInfo();
                broadcastToTeam1(new ExDuelUpdateUserInfo(temp));
            }

            // Send duel packets
            broadcastToTeam1(ExDuelReady.PARTY_DUEL);
            broadcastToTeam2(ExDuelReady.PARTY_DUEL);
            broadcastToTeam1(ExDuelStart.PARTY_DUEL);
            broadcastToTeam2(ExDuelStart.PARTY_DUEL);

            // Open arena doors
            for (Door door : InstanceManager.getInstance()
                    .getInstance(getDueldInstanceId())
                    .getDoors()) {
                if ((door != null) && !door.isOpen()) {
                    door.openMe();
                }
            }
        } else {
            // set isInDuel() state
            playerA.setInDuel(id);
            playerA.setTeam(Team.BLUE);
            playerB.setInDuel(id);
            playerB.setTeam(Team.RED);

            // Send duel packets
            broadcastToTeam1(ExDuelReady.PLAYER_DUEL);
            broadcastToTeam2(ExDuelReady.PLAYER_DUEL);
            broadcastToTeam1(ExDuelStart.PLAYER_DUEL);
            broadcastToTeam2(ExDuelStart.PLAYER_DUEL);

            broadcastToTeam1(new ExDuelUpdateUserInfo(playerB));
            broadcastToTeam2(new ExDuelUpdateUserInfo(playerA));
            playerA.broadcastUserInfo();
            playerB.broadcastUserInfo();
        }

        // play sound
        broadcastToTeam1(B04_S01);
        broadcastToTeam2(B04_S01);

        // start duelling task
        ThreadPool.schedule(new ScheduleDuelTask(this), 1000);
    }

    /**
     * Save the current player condition: hp, mp, cp, location
     */
    public void savePlayerConditions() {
        if (partyDuel) {
            for (Player player : playerA.getParty().getMembers()) {
                playerConditions.put(player.getObjectId(), new PlayerCondition(player, partyDuel));
            }

            for (Player player : playerB.getParty().getMembers()) {
                playerConditions.put(player.getObjectId(), new PlayerCondition(player, partyDuel));
            }
        } else {
            playerConditions.put(playerA.getObjectId(), new PlayerCondition(playerA, partyDuel));
            playerConditions.put(playerB.getObjectId(), new PlayerCondition(playerB, partyDuel));
        }
    }

    /**
     * Restore player conditions
     * @param abnormalDuelEnd true if the duel was the duel canceled
     */
    public void restorePlayerConditions(boolean abnormalDuelEnd) {
        // update isInDuel() state for all players
        if (partyDuel) {
            for (Player temp : playerA.getParty().getMembers()) {
                temp.setInDuel(0);
                temp.setTeam(Team.NONE);
                temp.broadcastUserInfo();
            }

            for (Player temp : playerB.getParty().getMembers()) {
                temp.setInDuel(0);
                temp.setTeam(Team.NONE);
                temp.broadcastUserInfo();
            }
        } else {
            playerA.setInDuel(0);
            playerA.setTeam(Team.NONE);
            playerA.broadcastUserInfo();
            playerB.setInDuel(0);
            playerB.setTeam(Team.NONE);
            playerB.broadcastUserInfo();
        }

        // if it is an abnormal DuelEnd do not restore hp, mp, cp
        if (abnormalDuelEnd) {
            return;
        }

        // restore player conditions
        playerConditions.values().forEach(PlayerCondition::restoreCondition);
    }

    /**
     * Returns the remaining time
     * @return remaining time
     */
    public int getRemainingTime() {
        return (int) (duelEndTime - ClockBridge.gameMillis());
    }

    public boolean getFinished() {
        return finished;
    }

    /**
     * Teleports all players to a free arena.
     */
    public void teleportPlayers() {
        if (!partyDuel) {
            return;
        }

        dueldInstanceId = InstanceManager.getInstance()
                .createDynamicInstance(ClockBridge.random().nextInt(147, 151))
                .getId(); // Random Olympiad arena.
        final OlympiadStadiumZone zone = ZoneManager.getInstance()
                .getZone(
                        InstanceManager.getInstance().getInstance(dueldInstanceId).getNpcs().stream()
                                .findFirst()
                                .get(),
                        OlympiadStadiumZone.class);
        if (zone == null) {
            throw new RuntimeException("Unable to find a party duel arena!");
        }

        final List<Location> spawns = zone.getSpawns();

        // Remove Olympiad buffers
        for (Npc buffer :
                InstanceManager.getInstance().getInstance(getDueldInstanceId()).getNpcs()) {
            if ((buffer instanceof OlympiadManager) && buffer.isSpawned()) {
                buffer.decayMe();
            }
        }

        final RandomGenerator random = ClockBridge.random();
        final Location spawn1 = spawns.get(spawns.size() > 1 ? random.nextInt(spawns.size() / 2) : 0);
        for (Player temp : playerA.getParty().getMembers()) {
            temp.teleToLocation(spawn1.getX(), spawn1.getY(), spawn1.getZ(), 0, dueldInstanceId, 0);
        }

        final Location spawn2 = spawns.get(random.nextInt(spawns.size() / 2, spawns.size()));
        for (Player temp : playerB.getParty().getMembers()) {
            temp.teleToLocation(spawn2.getX(), spawn2.getY(), spawn2.getZ(), 0, dueldInstanceId, 0);
        }
    }

    /**
     * Broadcast a packet to the challenger team
     * @param packet
     */
    public void broadcastToTeam1(ServerPacket packet) {
        if (playerA == null) {
            return;
        }

        if (partyDuel && (playerA.getParty() != null)) {
            for (Player temp : playerA.getParty().getMembers()) {
                temp.sendPacket(packet);
            }
        } else {
            playerA.sendPacket(packet);
        }
    }

    /**
     * Broadcast a packet to the challenged team
     * @param packet
     */
    public void broadcastToTeam2(ServerPacket packet) {
        if (playerB == null) {
            return;
        }

        if (partyDuel && (playerB.getParty() != null)) {
            for (Player temp : playerB.getParty().getMembers()) {
                temp.sendPacket(packet);
            }
        } else {
            playerB.sendPacket(packet);
        }
    }

    /**
     * Get the duel winner
     * @return winner
     */
    public Player getWinner() {
        if (!finished || (playerA == null) || (playerB == null)) {
            return null;
        }

        if (playerA.getDuelState() == DUELSTATE_WINNER) {
            return playerA;
        }

        if (playerB.getDuelState() == DUELSTATE_WINNER) {
            return playerB;
        }

        return null;
    }

    /**
     * Get the duel looser
     * @return looser
     */
    public Player getLooser() {
        if (!finished || (playerA == null) || (playerB == null)) {
            return null;
        }

        if (playerA.getDuelState() == DUELSTATE_WINNER) {
            return playerB;
        } else if (playerB.getDuelState() == DUELSTATE_WINNER) {
            return playerA;
        }

        return null;
    }

    /**
     * Playback the bow animation for all loosers
     */
    public void playKneelAnimation() {
        final Player looser = getLooser();
        if (looser == null) {
            return;
        }

        if (partyDuel && (looser.getParty() != null)) {
            for (Player temp : looser.getParty().getMembers()) {
                temp.broadcastPacket(new SocialAction(temp.getObjectId(), 7));
            }
        } else {
            looser.broadcastPacket(new SocialAction(looser.getObjectId(), 7));
        }
    }

    /**
     * Do the countdown and send message to players if necessary
     * @return current count
     */
    public int countdown() {
        countdown--;

        if (countdown > 3) {
            return countdown;
        }

        // Broadcast countdown to duelists.
        SystemMessage sm = null;
        if (countdown > 0) {
            sm = new SystemMessage(SystemMessageId.THE_DUEL_WILL_BEGIN_IN_S1_SECOND_S);
            sm.addInt(countdown);
        } else {
            sm = new SystemMessage(SystemMessageId.LET_THE_DUEL_BEGIN);
        }

        broadcastToTeam1(sm);
        broadcastToTeam2(sm);

        return countdown;
    }

    /**
     * The duel has reached a state in which it can no longer continue
     * @param result the duel result.
     */
    public void endDuel(DuelResult result) {
        if ((playerA == null) || (playerB == null)) {
            // clean up
            playerConditions.clear();
            DuelManager.getInstance().removeDuel(this);
            return;
        }

        // inform players of the result
        SystemMessage sm = null;
        switch (result) {
            case TEAM_1_WIN:
            case TEAM_2_SURRENDER: {
                restorePlayerConditions(false);
                sm = partyDuel
                        ? new SystemMessage(SystemMessageId.C1_S_PARTY_HAS_WON_THE_DUEL)
                        : new SystemMessage(SystemMessageId.C1_HAS_WON_THE_DUEL);
                sm.addString(playerA.getName());

                broadcastToTeam1(sm);
                broadcastToTeam2(sm);
                break;
            }
            case TEAM_1_SURRENDER:
            case TEAM_2_WIN: {
                restorePlayerConditions(false);
                sm = partyDuel
                        ? new SystemMessage(SystemMessageId.C1_S_PARTY_HAS_WON_THE_DUEL)
                        : new SystemMessage(SystemMessageId.C1_HAS_WON_THE_DUEL);
                sm.addString(playerB.getName());

                broadcastToTeam1(sm);
                broadcastToTeam2(sm);
                break;
            }
            case CANCELED: {
                stopFighting();

                // Don't restore hp, mp, cp.
                restorePlayerConditions(true);

                // TODO: is there no other message for a canceled duel?
                // send SystemMessage
                sm = new SystemMessage(SystemMessageId.THE_DUEL_HAS_ENDED_IN_A_TIE);
                broadcastToTeam1(sm);
                broadcastToTeam2(sm);
                break;
            }
            case TIMEOUT: {
                stopFighting();

                // hp,mp,cp seem to be restored in a timeout too...
                restorePlayerConditions(false);

                // send SystemMessage
                sm = new SystemMessage(SystemMessageId.THE_DUEL_HAS_ENDED_IN_A_TIE);
                broadcastToTeam1(sm);
                broadcastToTeam2(sm);
                break;
            }
        }

        // Send end duel packet.
        final ExDuelEnd duelEnd = partyDuel ? ExDuelEnd.PARTY_DUEL : ExDuelEnd.PLAYER_DUEL;
        broadcastToTeam1(duelEnd);
        broadcastToTeam2(duelEnd);

        // clean up
        playerConditions.clear();
        DuelManager.getInstance().removeDuel(this);
    }

    /**
     * Did a situation occur in which the duel has to be ended?
     * @return DuelResult duel status
     */
    public DuelResult checkEndDuelCondition() {
        // one of the players might leave during duel
        if ((playerA == null) || (playerB == null) || !playerA.isOnline() || !playerB.isOnline()) {
            return DuelResult.CANCELED;
        }

        // got a duel surrender request?
        if (surrenderRequest != 0) {
            return surrenderRequest == 1 ? DuelResult.TEAM_1_SURRENDER : DuelResult.TEAM_2_SURRENDER;
        }
        // duel timed out
        else if (getRemainingTime() <= 0) {
            return DuelResult.TIMEOUT;
        }
        // Has a player been declared winner yet?
        else if (playerA.getDuelState() == DUELSTATE_WINNER) {
            // If there is a Winner already there should be no more fighting going on.
            stopFighting();
            return DuelResult.TEAM_1_WIN;
        } else if (playerB.getDuelState() == DUELSTATE_WINNER) {
            // If there is a Winner already there should be no more fighting going on.
            stopFighting();
            return DuelResult.TEAM_2_WIN;
        }

        // More end duel conditions for 1on1 duels.
        else if (!partyDuel) {
            // Duel was interrupted e.g.: player was attacked by mobs / other players.
            if ((playerA.getDuelState() == DUELSTATE_INTERRUPTED)
                    || (playerB.getDuelState() == DUELSTATE_INTERRUPTED)) {
                return DuelResult.CANCELED;
            }

            // Are the players too far apart?
            if (!playerA.isInsideRadius2D(playerB, 1600)) {
                return DuelResult.CANCELED;
            }

            // Did one of the players engage in PvP combat?
            if (isDuelistInPvp(true)) {
                return DuelResult.CANCELED;
            }

            // Is one of the players in a Siege, Peace or PvP zone?
            if (playerA.isInsideZone(ZoneId.PEACE)
                    || playerB.isInsideZone(ZoneId.PEACE)
                    || playerA.isInsideZone(ZoneId.NO_PVP)
                    || playerB.isInsideZone(ZoneId.NO_PVP)
                    || playerA.isInsideZone(ZoneId.SIEGE)
                    || playerB.isInsideZone(ZoneId.SIEGE)
                    || playerA.isInsideZone(ZoneId.PVP)
                    || playerB.isInsideZone(ZoneId.PVP)) {
                return DuelResult.CANCELED;
            }
        }

        return DuelResult.CONTINUE;
    }

    /**
     * Register a surrender request
     * @param player the player that surrenders.
     */
    public void doSurrender(Player player) {
        // Already received a surrender request.
        if (surrenderRequest != 0) {
            return;
        }

        // stop the fight
        stopFighting();

        // TODO: Can every party member cancel a party duel? or only the party leaders?
        if (partyDuel) {
            if (playerA.getParty().getMembers().contains(player)) {
                surrenderRequest = 1;
                for (Player temp : playerA.getParty().getMembers()) {
                    temp.setDuelState(DUELSTATE_DEAD);
                }

                for (Player temp : playerB.getParty().getMembers()) {
                    temp.setDuelState(DUELSTATE_WINNER);
                }
            } else if (playerB.getParty().getMembers().contains(player)) {
                surrenderRequest = 2;
                for (Player temp : playerB.getParty().getMembers()) {
                    temp.setDuelState(DUELSTATE_DEAD);
                }

                for (Player temp : playerA.getParty().getMembers()) {
                    temp.setDuelState(DUELSTATE_WINNER);
                }
            }
        } else if (player == playerA) {
            surrenderRequest = 1;
            playerA.setDuelState(DUELSTATE_DEAD);
            playerB.setDuelState(DUELSTATE_WINNER);
        } else if (player == playerB) {
            surrenderRequest = 2;
            playerB.setDuelState(DUELSTATE_DEAD);
            playerA.setDuelState(DUELSTATE_WINNER);
        }
    }

    /**
     * This function is called whenever a player was defeated in a duel
     * @param player the player defeated.
     */
    public void onPlayerDefeat(Player player) {
        // Set player as defeated.
        player.setDuelState(DUELSTATE_DEAD);

        if (partyDuel) {
            boolean teamdefeated = true;
            for (Player temp : player.getParty().getMembers()) {
                if (temp.getDuelState() == DUELSTATE_DUELLING) {
                    teamdefeated = false;
                    break;
                }
            }

            if (teamdefeated) {
                final Player winner = playerA.getParty().getMembers().contains(player) ? playerB : playerA;
                for (Player temp : winner.getParty().getMembers()) {
                    temp.setDuelState(DUELSTATE_WINNER);
                }
            }
        } else {
            if ((player != playerA) && (player != playerB)) {
                log.warn("Error in onPlayerDefeat(): player is not part of this 1vs1 duel");
            }

            if (playerA == player) {
                playerB.setDuelState(DUELSTATE_WINNER);
            } else {
                playerA.setDuelState(DUELSTATE_WINNER);
            }
        }
    }

    /**
     * This function is called whenever a player leaves a party
     * @param player the player quitting.
     */
    public void onRemoveFromParty(Player player) {
        // If it isn't a party duel ignore this.
        if (!partyDuel) {
            return;
        }

        // This player is leaving his party during party duel.
        // If he's either playerA or playerB cancel the duel and port the players back.
        if ((player == playerA) || (player == playerB)) {
            for (PlayerCondition cond : playerConditions.values()) {
                cond.teleportBack();
                cond.getPlayer().setInDuel(0);
            }

            playerA = null;
            playerB = null;
        } else
        // Teleport the player back & delete his PlayerCondition record.
        {
            final PlayerCondition cond = playerConditions.remove(player.getObjectId());
            if (cond != null) {
                cond.teleportBack();
            }

            player.setInDuel(0);
        }
    }

    public void onBuff(Player player, Skill debuff) {
        final PlayerCondition cond = playerConditions.get(player.getObjectId());
        if (cond != null) {
            cond.registerDebuff(debuff);
        }
    }
}
