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
package app.l2nx.highfive.mechanics.siege.clanhalls;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.ConquerableHallSiegeConfig;
import app.l2nx.highfive.data.holders.SiegeGuardSpawn;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.data.xml.SiegableHallData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.mechanics.script.Script;
import app.l2nx.highfive.mechanics.siege.Siegable;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.network.NpcStringId;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.enums.ChatType;
import app.l2nx.highfive.network.serverpackets.NpcSay;
import app.l2nx.highfive.network.serverpackets.SystemMessage;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * @author BiggBoss
 */
@Slf4j
public abstract class ClanHallSiegeEngine extends Script implements Siegable {
    public static final int FORTRESS_RESSISTANCE = 21;
    public static final int DEVASTATED_CASTLE = 34;
    public static final int BANDIT_STRONGHOLD = 35;
    public static final int RAINBOW_SPRINGS = 62;
    public static final int BEAST_FARM = 63;
    public static final int FORTRESS_OF_DEAD = 64;

    @Getter
    private final Map<Integer, SiegeClan> attackers = new ConcurrentHashMap<>();

    private Collection<Spawn> guards;

    public SiegableHall hall;
    public ScheduledFuture<?> siegeTask;
    public boolean missionAccomplished = false;

    public ClanHallSiegeEngine(int hallId) {
        hall = CHSiegeManager.getInstance().getSiegableHall(hallId);
        hall.setSiege(this);

        siegeTask =
                ThreadPool.schedule(new PrepareOwner(), hall.getNextSiegeTime() - ClockBridge.wallMillis() - 3600000);
        log.info(
                "{} siege scheduled for {}.",
                hall.getName(),
                hall.getSiegeDate().getTime());
        loadAttackers();
    }

    public void loadAttackers() {
        for (int id : CHSiegeManager.getInstance().findSiegeAttackers(hall.getId())) {
            attackers.put(id, new SiegeClan(id, SiegeClanType.ATTACKER));
        }
    }

    public void saveAttackers() {
        final List<Integer> clanIds = new ArrayList<>(attackers.size());
        for (SiegeClan clan : attackers.values()) {
            clanIds.add(clan.getClanId());
        }

        CHSiegeManager.getInstance().saveSiegeAttackers(hall.getId(), clanIds);
    }

    public void loadGuards() {
        if (guards != null) {
            guards.forEach(Spawn::startRespawn);
            return;
        }

        guards = new ArrayList<>();
        try {
            for (SiegeGuardSpawn guard :
                    SiegableHallData.getInstance().getHall(hall.getId()).siegeGuards()) {
                final Spawn spawn = new Spawn(guard.npcId());
                spawn.setXYZ(
                        guard.spawnPoint().x(),
                        guard.spawnPoint().y(),
                        guard.spawnPoint().z());
                spawn.setHeading(guard.spawnPoint().heading());
                spawn.setRespawnDelay(guard.respawnDelay());
                spawn.setAmount(1);
                guards.add(spawn);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(getClass()).warn("Could not load siege guards!");
        }
    }

    private void spawnSiegeGuards() {
        for (Spawn guard : guards) {
            guard.init();
            if (guard.getRespawnDelay() == 0) {
                guard.stopRespawn();
            }
        }
    }

    private void unSpawnSiegeGuards() {
        if (guards != null) {
            for (Spawn guard : guards) {
                guard.stopRespawn();
                if (guard.getLastSpawn() != null) {
                    guard.getLastSpawn().deleteMe();
                }
            }
        }
    }

    @Override
    public Set<Npc> getFlag(Clan clan) {
        Set<Npc> result = null;
        final SiegeClan sClan = getAttackerClan(clan);
        if (sClan != null) {
            result = sClan.getFlag();
        }

        return result;
    }

    @Override
    public boolean checkIsAttacker(Clan clan) {
        if (clan == null) {
            return false;
        }

        return attackers.containsKey(clan.getId());
    }

    @Override
    public boolean checkIsDefender(Clan clan) {
        return false;
    }

    @Override
    public SiegeClan getAttackerClan(int clanId) {
        return attackers.get(clanId);
    }

    @Override
    public SiegeClan getAttackerClan(Clan clan) {
        return getAttackerClan(clan.getId());
    }

    @Override
    public Collection<SiegeClan> getAttackerClans() {
        return Collections.unmodifiableCollection(attackers.values());
    }

    @Override
    public List<Player> getAttackersInZone() {
        final List<Player> attackers = new ArrayList<>();
        for (Player pc : hall.getSiegeZone().getPlayersInside()) {
            final Clan clan = pc.getClan();
            if ((clan != null) && this.attackers.containsKey(clan.getId())) {
                attackers.add(pc);
            }
        }

        return attackers;
    }

    @Override
    public SiegeClan getDefenderClan(int clanId) {
        return null;
    }

    @Override
    public SiegeClan getDefenderClan(Clan clan) {
        return null;
    }

    @Override
    public List<SiegeClan> getDefenderClans() {
        return null;
    }

    public void prepareOwner() {
        if (hall.getOwnerId() > 0) {
            final SiegeClan clan = new SiegeClan(hall.getOwnerId(), SiegeClanType.ATTACKER);
            attackers.put(clan.getClanId(), new SiegeClan(clan.getClanId(), SiegeClanType.ATTACKER));
        }

        hall.free();
        hall.banishForeigners();
        final SystemMessage msg = new SystemMessage(SystemMessageId.THE_REGISTRATION_TERM_FOR_S1_HAS_ENDED);
        msg.addString(getName());
        World.broadcastToAllOnlinePlayers(msg);
        hall.updateSiegeStatus(SiegeStatus.WAITING_BATTLE);

        siegeTask = ThreadPool.schedule(new SiegeStarts(), 3600000);
    }

    @Override
    public void startSiege() {
        if ((attackers.isEmpty()) && (hall.getId() != 21)) // Fortress of resistance don't have attacker list.
        {
            onSiegeEnds();
            attackers.clear();
            hall.updateNextSiege();
            siegeTask =
                    ThreadPool.schedule(new PrepareOwner(), hall.getSiegeDate().getTimeInMillis());
            hall.updateSiegeStatus(SiegeStatus.WAITING_BATTLE);
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.THE_SIEGE_OF_S1_HAS_BEEN_CANCELED_DUE_TO_LACK_OF_INTEREST);
            sm.addString(hall.getName());
            World.broadcastToAllOnlinePlayers(sm);
            return;
        }

        hall.spawnDoor();
        loadGuards();
        spawnSiegeGuards();
        hall.updateSiegeZone(true);

        final byte state = 1;
        for (SiegeClan sClan : attackers.values()) {
            final Clan clan = ClanTable.getInstance().getClan(sClan.getClanId());
            if (clan == null) {
                continue;
            }

            for (Player pc : clan.getOnlineMembers(0)) {
                pc.setSiegeState(state);
                pc.broadcastUserInfo();
                pc.setInHideoutSiege(true);
            }
        }

        hall.updateSiegeStatus(SiegeStatus.RUNNING);
        onSiegeStarts();
        siegeTask = ThreadPool.schedule(new SiegeEnds(), hall.getSiegeLength());
    }

    @Override
    public void endSiege() {
        final SystemMessage end = new SystemMessage(SystemMessageId.THE_S1_SIEGE_HAS_FINISHED);
        end.addString(hall.getName());
        World.broadcastToAllOnlinePlayers(end);

        final Clan winner = getWinner();
        SystemMessage finalMsg = null;
        if (missionAccomplished && (winner != null)) {
            hall.setOwner(winner);
            winner.setHideoutId(hall.getId());
            finalMsg = new SystemMessage(SystemMessageId.CLAN_S1_IS_VICTORIOUS_OVER_S2_S_CASTLE_SIEGE);
            finalMsg.addString(winner.getName());
        } else {
            finalMsg = new SystemMessage(SystemMessageId.THE_SIEGE_OF_S1_HAS_ENDED_IN_A_DRAW);
        }

        finalMsg.addString(hall.getName());
        World.broadcastToAllOnlinePlayers(finalMsg);
        missionAccomplished = false;
        hall.updateSiegeZone(false);
        hall.updateNextSiege();
        hall.spawnDoor(false);
        hall.banishForeigners();

        final byte state = 0;
        for (SiegeClan sClan : attackers.values()) {
            final Clan clan = ClanTable.getInstance().getClan(sClan.getClanId());
            if (clan == null) {
                continue;
            }

            for (Player player : clan.getOnlineMembers(0)) {
                player.setSiegeState(state);
                player.broadcastUserInfo();
                player.setInHideoutSiege(false);
            }
        }

        // Update pvp flag for winners when siege zone becomes inactive.
        for (Creature chr : hall.getSiegeZone().getCharactersInside()) {
            if ((chr != null) && chr.isPlayer()) {
                chr.asPlayer().startPvPFlag();
            }
        }

        attackers.clear();

        onSiegeEnds();

        siegeTask =
                ThreadPool.schedule(new PrepareOwner(), hall.getNextSiegeTime() - ClockBridge.wallMillis() - 3600000);
        log.info(
                "Siege of {} scheduled for {}.",
                hall.getName(),
                hall.getSiegeDate().getTime());
        hall.updateSiegeStatus(SiegeStatus.REGISTERING);
        unSpawnSiegeGuards();
    }

    @Override
    public void updateSiege() {
        cancelSiegeTask();
        siegeTask = ThreadPool.schedule(new PrepareOwner(), hall.getNextSiegeTime() - 3600000);
        log.info(
                "{} siege scheduled for {}.",
                hall.getName(),
                hall.getSiegeDate().getTime());
    }

    public void cancelSiegeTask() {
        if (siegeTask != null) {
            siegeTask.cancel(false);
        }
    }

    @Override
    public Calendar getSiegeDate() {
        return hall.getSiegeDate();
    }

    @Override
    public boolean giveFame() {
        return ConquerableHallSiegeConfig.CHS_ENABLE_FAME;
    }

    @Override
    public int getFameAmount() {
        return ConquerableHallSiegeConfig.CHS_FAME_AMOUNT;
    }

    @Override
    public int getFameFrequency() {
        return ConquerableHallSiegeConfig.CHS_FAME_FREQUENCY;
    }

    public void broadcastNpcSay(Npc npc, ChatType type, NpcStringId messageId) {
        final NpcSay npcSay = new NpcSay(npc.getObjectId(), type, npc.getId(), messageId);
        final int sourceRegion = MapRegionData.getInstance().getMapRegionLocId(npc);
        for (Player pc : World.getPlayers()) {
            if ((pc != null) && (MapRegionData.getInstance().getMapRegionLocId(pc) == sourceRegion)) {
                pc.sendPacket(npcSay);
            }
        }
    }

    public Location getInnerSpawnLoc(Player player) {
        return null;
    }

    public boolean canPlantFlag() {
        return true;
    }

    public boolean doorIsAutoAttackable() {
        return true;
    }

    public void onSiegeStarts() {}

    public void onSiegeEnds() {}

    public abstract Clan getWinner();

    public class PrepareOwner implements Runnable {
        @Override
        public void run() {
            prepareOwner();
        }
    }

    public class SiegeStarts implements Runnable {
        @Override
        public void run() {
            startSiege();
        }
    }

    public class SiegeEnds implements Runnable {
        @Override
        public void run() {
            endSiege();
        }
    }
}
