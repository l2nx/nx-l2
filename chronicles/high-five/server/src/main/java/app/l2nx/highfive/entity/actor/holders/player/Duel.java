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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
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
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExDuelEnd;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExDuelReady;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExDuelStart;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExDuelUpdateUserInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SocialAction;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class Duel {

    public static final int DUELSTATE_NODUEL = 0;
    public static final int DUELSTATE_DUELLING = 1;
    public static final int DUELSTATE_DEAD = 2;
    public static final int DUELSTATE_WINNER = 3;
    public static final int DUELSTATE_INTERRUPTED = 4;

    private static final PlaySound B04_S01 = new PlaySound(1, "B04_S01", 0, 0, 0, 0, 0);

    private static final int PARTY_DUEL_DURATION = 300;
    private static final int PLAYER_DUEL_DURATION = 120;

    private final int id;
    private Player playerA;
    private Player playerB;
    private final boolean partyDuel;
    private final long duelEndTime;
    private int surrenderRequest = 0;
    private int countdown = 5;
    private boolean finished = false;

    // Insertion order: conditions are restored in the order they were saved.
    private final Int2ObjectLinkedOpenHashMap<PlayerCondition> playerConditions = new Int2ObjectLinkedOpenHashMap<>();

    private int dueldInstanceId;

    public Duel(Player playerA, Player playerB, int partyDuel, int duelId) {
        id = duelId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.partyDuel = partyDuel == 1;
        if (this.partyDuel) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                this.playerA.getParty().collectMembers(members);
                for (Player member : members) {
                    member.setStartingDuel();
                }
            }

            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                this.playerB.getParty().collectMembers(members);
                for (Player member : members) {
                    member.setStartingDuel();
                }
            }
        } else {
            this.playerA.setStartingDuel();
            this.playerB.setStartingDuel();
        }

        duelEndTime =
                ClockBridge.gameMillis() + ((this.partyDuel ? PARTY_DUEL_DURATION : PLAYER_DUEL_DURATION) * 1000L);

        if (this.partyDuel) {
            final SystemMessage sm = new SystemMessage(
                    SystemMessageId.IN_A_MOMENT_YOU_WILL_BE_TRANSPORTED_TO_THE_SITE_WHERE_THE_DUEL_WILL_TAKE_PLACE);
            broadcastToTeam1(sm);
            broadcastToTeam2(sm);
        }

        WorldPartition.loop().schedule(new ScheduleStartDuelTask(this), 3000, TimeUnit.MILLISECONDS);
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public Player getPlayerA() {
        WorldPartition.checkOwner();
        return playerA;
    }

    public Player getPlayerB() {
        WorldPartition.checkOwner();
        return playerB;
    }

    public boolean isPartyDuel() {
        WorldPartition.checkOwner();
        return partyDuel;
    }

    public void setFinished(boolean finished) {
        WorldPartition.checkOwner();
        this.finished = finished;
    }

    public int getDueldInstanceId() {
        WorldPartition.checkOwner();
        return dueldInstanceId;
    }

    @SingleWriter
    public static class PlayerCondition {
        private Player player;

        private double hp;
        private double mp;
        private double cp;
        private boolean paDuel;
        private int x;
        private int y;
        private int z;
        // Skill compares by identity, so only a linked set keeps the removal order independent of the run.
        private ObjectLinkedOpenHashSet<Skill> debuffs;

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

        public Player getPlayer() {
            WorldPartition.checkOwner();
            return player;
        }

        public void restoreCondition() {
            WorldPartition.checkOwner();
            if (player == null) {
                return;
            }

            player.setCurrentHp(hp);
            player.setCurrentMp(mp);
            player.setCurrentCp(cp);

            if (paDuel) {
                teleportBack();
            }

            if (debuffs != null) {
                try (ScratchBuffers.Snapshot<Skill> removed = ScratchBuffers.snapshot(debuffs)) {
                    for (Skill skill : removed) {
                        if (skill != null) {
                            player.stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
                        }
                    }
                }
            }
        }

        public void registerDebuff(Skill debuff) {
            WorldPartition.checkOwner();
            if (debuffs == null) {
                debuffs = new ObjectLinkedOpenHashSet<>();
            }

            debuffs.add(debuff);
        }

        public void teleportBack() {
            WorldPartition.checkOwner();
            if (paDuel) {
                player.teleToLocation(x, y, z);
            }
        }
    }

    private class ScheduleDuelTask implements Runnable {
        private final Duel duel;

        private ScheduleDuelTask(Duel duel) {
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
                        WorldPartition.loop().schedule(this, 1000, TimeUnit.MILLISECONDS);
                        break;
                    }
                    default: {
                        setFinished(true);
                        playKneelAnimation();
                        WorldPartition.loop()
                                .schedule(
                                        new ScheduleEndDuelTask(duel, duel.checkEndDuelCondition()),
                                        5000,
                                        TimeUnit.MILLISECONDS);
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
    private static class ScheduleStartDuelTask implements Runnable {
        private final Duel duel;

        private ScheduleStartDuelTask(Duel duel) {
            this.duel = duel;
        }

        @Override
        public void run() {
            try {
                final int count = duel.countdown();
                if (count == 4) {
                    duel.savePlayerConditions();

                    duel.teleportPlayers();

                    // give players 20 seconds to complete teleport and get ready (its ought to be 30 on official..)
                    WorldPartition.loop().schedule(this, duel.isPartyDuel() ? 20000 : 1, TimeUnit.MILLISECONDS);
                } else if (count > 0) {
                    WorldPartition.loop().schedule(this, 1000, TimeUnit.MILLISECONDS);
                } else {
                    duel.startDuel();
                }
            } catch (Exception e) {
                log.error("", e);
            }
        }
    }

    @Slf4j
    private static class ScheduleEndDuelTask implements Runnable {
        private final Duel duel;
        private final DuelResult result;

        private ScheduleEndDuelTask(Duel duel, DuelResult result) {
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

    private void stopFighting() {
        final ActionFailed af = ActionFailed.STATIC_PACKET;
        if (partyDuel) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerA.getParty().collectMembers(members);
                for (Player temp : members) {
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
            }

            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerB.getParty().collectMembers(members);
                for (Player temp : members) {
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

    public boolean isDuelistInPvp(boolean sendMessage) {
        WorldPartition.checkOwner();
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

    public void startDuel() {
        WorldPartition.checkOwner();
        if ((playerA == null) || (playerB == null) || playerA.isInDuel() || playerB.isInDuel()) {
            playerConditions.clear();
            DuelManager.getInstance().removeDuel(this);
            return;
        }

        if (partyDuel) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerA.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.cancelActiveTrade();
                    temp.setInDuel(id);
                    temp.setTeam(Team.BLUE);
                    temp.broadcastUserInfo();
                    broadcastToTeam2(new ExDuelUpdateUserInfo(temp));
                }
            }

            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerB.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.cancelActiveTrade();
                    temp.setInDuel(id);
                    temp.setTeam(Team.RED);
                    temp.broadcastUserInfo();
                    broadcastToTeam1(new ExDuelUpdateUserInfo(temp));
                }
            }

            broadcastToTeam1(ExDuelReady.PARTY_DUEL);
            broadcastToTeam2(ExDuelReady.PARTY_DUEL);
            broadcastToTeam1(ExDuelStart.PARTY_DUEL);
            broadcastToTeam2(ExDuelStart.PARTY_DUEL);

            for (Door door : InstanceManager.getInstance()
                    .getInstance(getDueldInstanceId())
                    .getDoors()) {
                if ((door != null) && !door.isOpen()) {
                    door.openMe();
                }
            }
        } else {
            playerA.setInDuel(id);
            playerA.setTeam(Team.BLUE);
            playerB.setInDuel(id);
            playerB.setTeam(Team.RED);

            broadcastToTeam1(ExDuelReady.PLAYER_DUEL);
            broadcastToTeam2(ExDuelReady.PLAYER_DUEL);
            broadcastToTeam1(ExDuelStart.PLAYER_DUEL);
            broadcastToTeam2(ExDuelStart.PLAYER_DUEL);

            broadcastToTeam1(new ExDuelUpdateUserInfo(playerB));
            broadcastToTeam2(new ExDuelUpdateUserInfo(playerA));
            playerA.broadcastUserInfo();
            playerB.broadcastUserInfo();
        }

        broadcastToTeam1(B04_S01);
        broadcastToTeam2(B04_S01);

        WorldPartition.loop().schedule(new ScheduleDuelTask(this), 1000, TimeUnit.MILLISECONDS);
    }

    public void savePlayerConditions() {
        WorldPartition.checkOwner();
        if (partyDuel) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerA.getParty().collectMembers(members);
                for (Player player : members) {
                    playerConditions.put(player.getObjectId(), new PlayerCondition(player, partyDuel));
                }
            }

            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerB.getParty().collectMembers(members);
                for (Player player : members) {
                    playerConditions.put(player.getObjectId(), new PlayerCondition(player, partyDuel));
                }
            }
        } else {
            playerConditions.put(playerA.getObjectId(), new PlayerCondition(playerA, partyDuel));
            playerConditions.put(playerB.getObjectId(), new PlayerCondition(playerB, partyDuel));
        }
    }

    public void restorePlayerConditions(boolean abnormalDuelEnd) {
        WorldPartition.checkOwner();
        if (partyDuel) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerA.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.setInDuel(0);
                    temp.setTeam(Team.NONE);
                    temp.broadcastUserInfo();
                }
            }

            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerB.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.setInDuel(0);
                    temp.setTeam(Team.NONE);
                    temp.broadcastUserInfo();
                }
            }
        } else {
            playerA.setInDuel(0);
            playerA.setTeam(Team.NONE);
            playerA.broadcastUserInfo();
            playerB.setInDuel(0);
            playerB.setTeam(Team.NONE);
            playerB.broadcastUserInfo();
        }

        if (abnormalDuelEnd) {
            return;
        }

        try (ScratchBuffers.Snapshot<PlayerCondition> saved = ScratchBuffers.snapshot(playerConditions.values())) {
            for (PlayerCondition cond : saved) {
                cond.restoreCondition();
            }
        }
    }

    public int getRemainingTime() {
        WorldPartition.checkOwner();
        return (int) (duelEndTime - ClockBridge.gameMillis());
    }

    public boolean getFinished() {
        WorldPartition.checkOwner();
        return finished;
    }

    public void teleportPlayers() {
        WorldPartition.checkOwner();
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

        for (Npc buffer :
                InstanceManager.getInstance().getInstance(getDueldInstanceId()).getNpcs()) {
            if ((buffer instanceof OlympiadManager) && buffer.isSpawned()) {
                buffer.decayMe();
            }
        }

        final RandomGenerator random = ClockBridge.random();
        final Location spawn1 = spawns.get(spawns.size() > 1 ? random.nextInt(spawns.size() / 2) : 0);
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            playerA.getParty().collectMembers(members);
            for (Player temp : members) {
                temp.teleToLocation(spawn1.getX(), spawn1.getY(), spawn1.getZ(), 0, dueldInstanceId, 0);
            }
        }

        final Location spawn2 = spawns.get(random.nextInt(spawns.size() / 2, spawns.size()));
        try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
            playerB.getParty().collectMembers(members);
            for (Player temp : members) {
                temp.teleToLocation(spawn2.getX(), spawn2.getY(), spawn2.getZ(), 0, dueldInstanceId, 0);
            }
        }
    }

    public void broadcastToTeam1(ServerPacket packet) {
        WorldPartition.checkOwner();
        if (playerA == null) {
            return;
        }

        if (partyDuel && (playerA.getParty() != null)) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerA.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.sendPacket(packet);
                }
            }
        } else {
            playerA.sendPacket(packet);
        }
    }

    public void broadcastToTeam2(ServerPacket packet) {
        WorldPartition.checkOwner();
        if (playerB == null) {
            return;
        }

        if (partyDuel && (playerB.getParty() != null)) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                playerB.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.sendPacket(packet);
                }
            }
        } else {
            playerB.sendPacket(packet);
        }
    }

    public Player getWinner() {
        WorldPartition.checkOwner();
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

    public Player getLooser() {
        WorldPartition.checkOwner();
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

    public void playKneelAnimation() {
        WorldPartition.checkOwner();
        final Player looser = getLooser();
        if (looser == null) {
            return;
        }

        if (partyDuel && (looser.getParty() != null)) {
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                looser.getParty().collectMembers(members);
                for (Player temp : members) {
                    temp.broadcastPacket(new SocialAction(temp.getObjectId(), 7));
                }
            }
        } else {
            looser.broadcastPacket(new SocialAction(looser.getObjectId(), 7));
        }
    }

    public int countdown() {
        WorldPartition.checkOwner();
        countdown--;

        if (countdown > 3) {
            return countdown;
        }

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

    public void endDuel(DuelResult result) {
        WorldPartition.checkOwner();
        if ((playerA == null) || (playerB == null)) {
            playerConditions.clear();
            DuelManager.getInstance().removeDuel(this);
            return;
        }

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

                restorePlayerConditions(true);

                // TODO: is there no other message for a canceled duel?
                sm = new SystemMessage(SystemMessageId.THE_DUEL_HAS_ENDED_IN_A_TIE);
                broadcastToTeam1(sm);
                broadcastToTeam2(sm);
                break;
            }
            case TIMEOUT: {
                stopFighting();

                // hp,mp,cp seem to be restored in a timeout too...
                restorePlayerConditions(false);

                sm = new SystemMessage(SystemMessageId.THE_DUEL_HAS_ENDED_IN_A_TIE);
                broadcastToTeam1(sm);
                broadcastToTeam2(sm);
                break;
            }
        }

        final ExDuelEnd duelEnd = partyDuel ? ExDuelEnd.PARTY_DUEL : ExDuelEnd.PLAYER_DUEL;
        broadcastToTeam1(duelEnd);
        broadcastToTeam2(duelEnd);

        playerConditions.clear();
        DuelManager.getInstance().removeDuel(this);
    }

    public DuelResult checkEndDuelCondition() {
        WorldPartition.checkOwner();
        if ((playerA == null) || (playerB == null) || !playerA.isOnline() || !playerB.isOnline()) {
            return DuelResult.CANCELED;
        }

        if (surrenderRequest != 0) {
            return surrenderRequest == 1 ? DuelResult.TEAM_1_SURRENDER : DuelResult.TEAM_2_SURRENDER;
        } else if (getRemainingTime() <= 0) {
            return DuelResult.TIMEOUT;
        } else if (playerA.getDuelState() == DUELSTATE_WINNER) {
            stopFighting();
            return DuelResult.TEAM_1_WIN;
        } else if (playerB.getDuelState() == DUELSTATE_WINNER) {
            stopFighting();
            return DuelResult.TEAM_2_WIN;
        } else if (!partyDuel) {
            if ((playerA.getDuelState() == DUELSTATE_INTERRUPTED)
                    || (playerB.getDuelState() == DUELSTATE_INTERRUPTED)) {
                return DuelResult.CANCELED;
            }

            if (!playerA.isInsideRadius2D(playerB, 1600)) {
                return DuelResult.CANCELED;
            }

            if (isDuelistInPvp(true)) {
                return DuelResult.CANCELED;
            }

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

    public void doSurrender(Player player) {
        WorldPartition.checkOwner();
        if (surrenderRequest != 0) {
            return;
        }

        stopFighting();

        // TODO: Can every party member cancel a party duel? or only the party leaders?
        if (partyDuel) {
            if (playerA.getParty().containsPlayer(player)) {
                surrenderRequest = 1;
                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    playerA.getParty().collectMembers(members);
                    for (Player temp : members) {
                        temp.setDuelState(DUELSTATE_DEAD);
                    }
                }

                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    playerB.getParty().collectMembers(members);
                    for (Player temp : members) {
                        temp.setDuelState(DUELSTATE_WINNER);
                    }
                }
            } else if (playerB.getParty().containsPlayer(player)) {
                surrenderRequest = 2;
                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    playerB.getParty().collectMembers(members);
                    for (Player temp : members) {
                        temp.setDuelState(DUELSTATE_DEAD);
                    }
                }

                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    playerA.getParty().collectMembers(members);
                    for (Player temp : members) {
                        temp.setDuelState(DUELSTATE_WINNER);
                    }
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

    public void onPlayerDefeat(Player player) {
        WorldPartition.checkOwner();
        player.setDuelState(DUELSTATE_DEAD);

        if (partyDuel) {
            boolean teamdefeated = true;
            try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                player.getParty().collectMembers(members);
                for (Player temp : members) {
                    if (temp.getDuelState() == DUELSTATE_DUELLING) {
                        teamdefeated = false;
                        break;
                    }
                }
            }

            if (teamdefeated) {
                final Player winner = playerA.getParty().containsPlayer(player) ? playerB : playerA;
                try (ScratchBuffers.Snapshot<Player> members = ScratchBuffers.empty()) {
                    winner.getParty().collectMembers(members);
                    for (Player temp : members) {
                        temp.setDuelState(DUELSTATE_WINNER);
                    }
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

    public void onRemoveFromParty(Player player) {
        WorldPartition.checkOwner();
        if (!partyDuel) {
            return;
        }

        if ((player == playerA) || (player == playerB)) {
            try (ScratchBuffers.Snapshot<PlayerCondition> saved = ScratchBuffers.snapshot(playerConditions.values())) {
                for (PlayerCondition cond : saved) {
                    cond.teleportBack();
                    cond.getPlayer().setInDuel(0);
                }
            }

            playerA = null;
            playerB = null;
        } else {
            final PlayerCondition cond = playerConditions.remove(player.getObjectId());
            if (cond != null) {
                cond.teleportBack();
            }

            player.setInDuel(0);
        }
    }

    public void onBuff(Player player, Skill debuff) {
        WorldPartition.checkOwner();
        final PlayerCondition cond = playerConditions.get(player.getObjectId());
        if (cond != null) {
            cond.registerDebuff(debuff);
        }
    }
}
