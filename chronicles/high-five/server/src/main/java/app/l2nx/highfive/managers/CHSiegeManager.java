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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.config.ConquerableHallSiegeConfig;
import app.l2nx.highfive.data.holders.SiegableHallTemplate;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.xml.SiegableHallData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.zone.type.ClanHallZone;
import app.l2nx.highfive.mechanics.siege.clanhalls.ClanHallSiegeEngine;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.repository.LegacyValues;
import app.l2nx.highfive.repository.residence.SiegableClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.SiegableClanHallState;
import app.l2nx.highfive.repository.siege.ClanHallSiegeAttackerRepository;
import app.l2nx.highfive.repository.siege.FlagWarAttackerRepository;
import app.l2nx.highfive.repository.siege.RainbowSpringsAttackerRepository;
import app.l2nx.highfive.repository.siege.model.FlagWarAttacker;
import app.l2nx.highfive.util.StatSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author BiggBoss
 */
@Slf4j
@Component
public class CHSiegeManager {
    private static volatile CHSiegeManager instance;

    @Getter
    private final Map<Integer, SiegableHall> conquerableHalls = new HashMap<>();

    private final SiegableClanHallStateRepository stateRepository;
    private final ClanHallSiegeAttackerRepository attackerRepository;
    private final FlagWarAttackerRepository flagWarAttackerRepository;
    private final RainbowSpringsAttackerRepository rainbowSpringsAttackerRepository;

    public CHSiegeManager(
            SiegableClanHallStateRepository stateRepository,
            ClanHallSiegeAttackerRepository attackerRepository,
            FlagWarAttackerRepository flagWarAttackerRepository,
            RainbowSpringsAttackerRepository rainbowSpringsAttackerRepository) {
        this.stateRepository = stateRepository;
        this.attackerRepository = attackerRepository;
        this.flagWarAttackerRepository = flagWarAttackerRepository;
        this.rainbowSpringsAttackerRepository = rainbowSpringsAttackerRepository;
        instance = this;
    }

    public void loadClanHalls() {
        final Map<Integer, SiegableClanHallState> states = new HashMap<>();
        try {
            for (SiegableClanHallState state : stateRepository.findAll()) {
                states.put(state.clanHallId(), state);
            }
        } catch (DataAccessException e) {
            log.warn("Could not load siegable clan halls!:{}", e.getMessage());
            return;
        }

        conquerableHalls.clear();
        for (SiegableHallTemplate template : SiegableHallData.getInstance().getHalls()) {
            final int id = template.id();
            final SiegableClanHallState state = states.get(id);
            final StatSet set = new StatSet();
            set.set("id", id);
            set.set("name", template.name());
            set.set("ownerId", state == null ? 0 : state.ownerClanId());
            set.set("desc", template.description());
            set.set("location", template.location());
            set.set("nextSiege", state == null ? 0 : LegacyValues.epochMillisOrZero(state.nextSiege()));
            set.set("siegeLength", template.siegeLength().toMillis());
            set.set("scheduleDays", template.scheduleDays());
            set.set("scheduleMonths", template.scheduleMonths());
            set.set("scheduleYears", template.scheduleYears());
            set.set("siegeHour", template.siegeHour());
            set.set("siegeMinute", template.siegeMinute());
            final SiegableHall hall = new SiegableHall(set);
            hall.releaseUnloadedOwner();
            conquerableHalls.put(id, hall);
            ClanHallTable.addClanHall(hall);
        }

        log.info("Loaded {} conquerable clan halls.", conquerableHalls.size());
    }

    public SiegableHall getSiegableHall(int clanHall) {
        return conquerableHalls.get(clanHall);
    }

    public SiegableHall getNearbyClanHall(Creature creature) {
        return getNearbyClanHall(creature.getX(), creature.getY(), 10000);
    }

    public SiegableHall getNearbyClanHall(int x, int y, int maxDist) {
        ClanHallZone zone = null;
        for (Entry<Integer, SiegableHall> ch : conquerableHalls.entrySet()) {
            zone = ch.getValue().getZone();
            if ((zone != null) && (zone.getDistanceToZone(x, y) < maxDist)) {
                return ch.getValue();
            }
        }

        return null;
    }

    public ClanHallSiegeEngine getSiege(Creature creature) {
        final SiegableHall hall = getNearbyClanHall(creature);
        return hall == null ? null : hall.getSiege();
    }

    public void registerClan(Clan clan, SiegableHall hall, Player player) {
        if (clan.getLevel() < ConquerableHallSiegeConfig.CHS_CLAN_MINLEVEL) {
            player.sendMessage("Only clans of level " + ConquerableHallSiegeConfig.CHS_CLAN_MINLEVEL
                    + " or higher may register for a castle siege");
        } else if (hall.isWaitingBattle()) {
            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.THE_DEADLINE_TO_REGISTER_FOR_THE_SIEGE_OF_S1_HAS_PASSED);
            sm.addString(hall.getName());
            player.sendPacket(sm);
        } else if (hall.isInSiege()) {
            player.sendPacket(
                    SystemMessageId
                            .THIS_IS_NOT_THE_TIME_FOR_SIEGE_REGISTRATION_AND_SO_REGISTRATION_AND_CANCELLATION_CANNOT_BE_DONE);
        } else if (hall.getOwnerId() == clan.getId()) {
            player.sendPacket(SystemMessageId.CASTLE_OWNING_CLANS_ARE_AUTOMATICALLY_REGISTERED_ON_THE_DEFENDING_SIDE);
        } else if ((clan.getCastleId() != 0) || (clan.getHideoutId() != 0)) {
            player.sendPacket(SystemMessageId.A_CLAN_THAT_OWNS_A_CASTLE_CANNOT_PARTICIPATE_IN_ANOTHER_SIEGE);
        } else if (hall.getSiege().checkIsAttacker(clan)) {
            player.sendPacket(SystemMessageId.YOU_HAVE_ALREADY_REQUESTED_A_CASTLE_SIEGE);
        } else if (isClanParticipating(clan)) {
            player.sendPacket(
                    SystemMessageId
                            .YOUR_APPLICATION_HAS_BEEN_DENIED_BECAUSE_YOU_HAVE_ALREADY_SUBMITTED_A_REQUEST_FOR_ANOTHER_CASTLE_SIEGE);
        } else if (hall.getSiege().getAttackers().size() >= ConquerableHallSiegeConfig.CHS_MAX_ATTACKERS) {
            player.sendPacket(SystemMessageId.NO_MORE_REGISTRATIONS_MAY_BE_ACCEPTED_FOR_THE_ATTACKER_SIDE);
        } else {
            hall.addAttacker(clan);
        }
    }

    public void unRegisterClan(Clan clan, SiegableHall hall) {
        if (!hall.isRegistering()) {
            return;
        }

        hall.removeAttacker(clan);
    }

    public boolean isClanParticipating(Clan clan) {
        for (SiegableHall hall : conquerableHalls.values()) {
            if ((hall.getSiege() != null) && hall.getSiege().checkIsAttacker(clan)) {
                return true;
            }
        }

        return false;
    }

    public void onServerShutDown() {
        for (SiegableHall hall : conquerableHalls.values()) {
            if (hall.getSiege() == null) {
                continue;
            }

            hall.getSiege().saveAttackers();
        }
    }

    public void saveState(SiegableClanHallState state) {
        try {
            stateRepository.save(state);
        } catch (DataAccessException e) {
            log.warn("Exception: SiegableHall.updateDb(): {}", e.getMessage(), e);
        }
    }

    public List<Integer> findSiegeAttackers(int clanHallId) {
        try {
            return attackerRepository.findClanIdsByHall(clanHallId);
        } catch (DataAccessException e) {
            log.warn("Could not load the siege attackers of clan hall {}: {}", clanHallId, e.getMessage(), e);
            return List.of();
        }
    }

    public void saveSiegeAttackers(int clanHallId, Collection<Integer> clanIds) {
        try {
            attackerRepository.replaceAll(clanHallId, clanIds);
            log.info("Successfully saved the attackers of clan hall {}.", clanHallId);
        } catch (DataAccessException e) {
            log.warn("Couldn't save the attacker list of clan hall {}: {}", clanHallId, e.getMessage(), e);
        }
    }

    public List<FlagWarAttacker> findFlagWarAttackers(int clanHallId) {
        try {
            return flagWarAttackerRepository.findByHall(clanHallId);
        } catch (DataAccessException e) {
            log.warn("loadAttackers() failed", e);
            return List.of();
        }
    }

    public List<Integer> findFlagWarMembers(int clanHallId, int clanId) {
        try {
            return flagWarAttackerRepository.findMemberIds(clanHallId, clanId);
        } catch (DataAccessException e) {
            log.warn("loadAttackerMembers() failed", e);
            return List.of();
        }
    }

    public void saveFlagWarAttacker(int clanHallId, int clanId, int flagNpcId) {
        try {
            flagWarAttackerRepository.insert(clanHallId, clanId, flagNpcId);
        } catch (DataAccessException e) {
            log.warn("saveClan() failed", e);
        }
    }

    public void saveFlagWarAllyNpc(int clanHallId, int clanId, int allyNpcId) {
        try {
            flagWarAttackerRepository.updateAllyNpc(clanHallId, clanId, allyNpcId);
        } catch (DataAccessException e) {
            log.warn("saveNpc() failed", e);
        }
    }

    public void saveFlagWarMember(int clanHallId, int clanId, int characterId) {
        try {
            flagWarAttackerRepository.insertMember(clanHallId, clanId, characterId);
        } catch (DataAccessException e) {
            log.warn("saveMember() failed", e);
        }
    }

    public void deleteFlagWarAttackers(int clanHallId) {
        try {
            flagWarAttackerRepository.deleteByHall(clanHallId);
        } catch (DataAccessException e) {
            log.warn("clearTables() failed", e);
        }
    }

    /**
     * @return War Decree count by clan id
     */
    public Map<Integer, Long> findRainbowSpringsAttackers() {
        try {
            return rainbowSpringsAttackerRepository.findAll();
        } catch (DataAccessException e) {
            log.warn("loadAttackers() failed", e);
            return Map.of();
        }
    }

    public void saveRainbowSpringsAttacker(int clanId, long warDecreeCount) {
        try {
            rainbowSpringsAttackerRepository.insert(clanId, warDecreeCount);
        } catch (DataAccessException e) {
            log.warn("Problem with updateAttacker: {}", e.getMessage());
        }
    }

    public void deleteRainbowSpringsAttacker(int clanId) {
        try {
            rainbowSpringsAttackerRepository.delete(clanId);
        } catch (DataAccessException e) {
            log.warn("Problem with updateAttacker: {}", e.getMessage());
        }
    }

    public void deleteRainbowSpringsAttackers() {
        try {
            rainbowSpringsAttackerRepository.deleteAll();
        } catch (DataAccessException e) {
            log.warn("clearTables() failed", e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CHSiegeManager getInstance() {
        final CHSiegeManager current = instance;
        if (current == null) {
            throw new IllegalStateException("CHSiegeManager is used before the Spring context created it.");
        }
        return current;
    }
}
