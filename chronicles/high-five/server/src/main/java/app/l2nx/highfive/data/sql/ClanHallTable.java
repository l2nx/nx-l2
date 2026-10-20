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
package app.l2nx.highfive.data.sql;

import app.l2nx.highfive.data.holders.ClanHallTemplate;
import app.l2nx.highfive.data.xml.ClanHallData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.zone.type.ClanHallZone;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.repository.LegacyValues;
import app.l2nx.highfive.repository.residence.ClanHallFunctionRepository;
import app.l2nx.highfive.repository.residence.ClanHallStateRepository;
import app.l2nx.highfive.repository.residence.model.ClanHallState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import app.l2nx.highfive.util.StatSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * @author Steuf
 */
@Slf4j
@Component
public class ClanHallTable {
    private static volatile ClanHallTable instance;

    @Getter
    private final Map<Integer, AuctionableHall> clanHalls = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, AuctionableHall> freeClanHalls = new ConcurrentHashMap<>();

    @Getter
    private final Map<Integer, AuctionableHall> allAuctionableClanHalls = new HashMap<>();

    @Getter
    private static Map<Integer, ClanHall> allClanHalls = new HashMap<>();

    private boolean loaded = false;

    public boolean loaded() {
        return loaded;
    }

    private final ClanHallStateRepository stateRepository;
    private final ClanHallFunctionRepository functionRepository;

    public ClanHallTable(ClanHallStateRepository stateRepository, ClanHallFunctionRepository functionRepository) {
        this.stateRepository = stateRepository;
        this.functionRepository = functionRepository;
        instance = this;
    }

    /**
     * Loads the auctionable halls. Runs after the clan hall auctions are loaded (a free hall without an auction gets
     * its NPC auction) and before the siegable halls (they load their functions).
     */
    public void load() {
        final Map<Integer, ClanHallState> states = new HashMap<>();
        try {
            // A hall left without an owner outside the game keeps no functions for the next owner.
            functionRepository.deleteOfUnownedHalls();
            for (ClanHallState state : stateRepository.findAll()) {
                states.put(state.clanHallId(), state);
            }
        } catch (DataAccessException e) {
            log.warn("Exception: ClanHallManager.load(): {}", e.getMessage(), e);
            return;
        }

        for (ClanHallTemplate template : ClanHallData.getInstance().getClanHalls()) {
            final int id = template.id();
            final ClanHallState state = states.get(id);
            final StatSet set = new StatSet();
            set.set("id", id);
            set.set("name", template.name());
            set.set("ownerId", state == null ? 0 : state.ownerClanId());
            set.set("lease", template.lease());
            set.set("desc", template.description());
            set.set("location", template.location());
            set.set("paidUntil", state == null ? 0 : LegacyValues.epochMillisOrZero(state.paidUntil()));
            set.set("grade", template.grade());
            set.set("paid", (state != null) && state.paid());
            final AuctionableHall ch = new AuctionableHall(set);
            ch.releaseUnloadedOwner();
            allAuctionableClanHalls.put(id, ch);
            addClanHall(ch);

            if (ch.getOwnerId() > 0) {
                clanHalls.put(id, ch);
                continue;
            }

            freeClanHalls.put(id, ch);

            if ((ClanHallAuctionManager.getInstance().getAuction(id) == null)
                    && (template.auctionStartingBid() != null)) {
                ClanHallAuctionManager.getInstance().initNPC(id);
            }
        }

        log.info("Loaded {} clan halls", clanHalls.size());
        log.info("Loaded {} free clan halls", freeClanHalls.size());
        loaded = true;
    }

    public void saveState(ClanHallState state) {
        try {
            stateRepository.save(state);
        } catch (DataAccessException e) {
            log.warn("Could not save the state of clan hall {}: {}", state.clanHallId(), e.getMessage(), e);
        }
    }

    public List<ResidenceFunction> findFunctions(int clanHallId) {
        try {
            return functionRepository.findByClanHall(clanHallId);
        } catch (DataAccessException e) {
            log.error("Exception: ClanHall.loadFunctions(): {}", e.getMessage(), e);
            return List.of();
        }
    }

    public void saveFunction(int clanHallId, ResidenceFunction function) {
        try {
            functionRepository.save(clanHallId, function);
        } catch (DataAccessException e) {
            log.error("Could not save function {} of clan hall {}: {}", function.type(), clanHallId, e.getMessage(), e);
        }
    }

    public void deleteFunction(int clanHallId, int functionType) {
        try {
            functionRepository.delete(clanHallId, functionType);
        } catch (DataAccessException e) {
            log.error("Exception: ClanHall.removeFunctions(int functionType): {}", e.getMessage(), e);
        }
    }

    public static void addClanHall(ClanHall hall) {
        allClanHalls.put(hall.getId(), hall);
    }

    /**
     * @param chId
     * @return true is free ClanHall
     */
    public boolean isFree(int chId) {
        return freeClanHalls.containsKey(chId);
    }

    /**
     * Free a ClanHall
     * @param chId
     */
    public synchronized void setFree(int chId) {
        freeClanHalls.put(chId, clanHalls.get(chId));
        ClanTable.getInstance().getClan(freeClanHalls.get(chId).getOwnerId()).setHideoutId(0);
        freeClanHalls.get(chId).free();
        clanHalls.remove(chId);
    }

    /**
     * Set ClanHallOwner
     * @param chId
     * @param clan
     */
    public synchronized void setOwner(int chId, Clan clan) {
        if (!clanHalls.containsKey(chId)) {
            clanHalls.put(chId, freeClanHalls.get(chId));
            freeClanHalls.remove(chId);
        } else {
            clanHalls.get(chId).free();
        }

        ClanTable.getInstance().getClan(clan.getId()).setHideoutId(chId);
        clanHalls.get(chId).setOwner(clan);
    }

    /**
     * @param clanHallId
     * @return Clan Hall by Id
     */
    public ClanHall getClanHallById(int clanHallId) {
        return allClanHalls.get(clanHallId);
    }

    public AuctionableHall getAuctionableHallById(int clanHallId) {
        return allAuctionableClanHalls.get(clanHallId);
    }

    /**
     * @param x
     * @param y
     * @param z
     * @return Clan Hall by x,y,z
     */
    public ClanHall getClanHall(int x, int y, int z) {
        for (ClanHall temp : allClanHalls.values()) {
            if (temp.checkIfInZone(x, y, z)) {
                return temp;
            }
        }

        return null;
    }

    public ClanHall getClanHall(WorldObject activeObject) {
        return getClanHall(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public AuctionableHall getNearbyClanHall(int x, int y, int maxDist) {
        ClanHallZone zone = null;
        for (Entry<Integer, AuctionableHall> ch : clanHalls.entrySet()) {
            zone = ch.getValue().getZone();
            if ((zone != null) && (zone.getDistanceToZone(x, y) < maxDist)) {
                return ch.getValue();
            }
        }

        for (Entry<Integer, AuctionableHall> ch : freeClanHalls.entrySet()) {
            zone = ch.getValue().getZone();
            if ((zone != null) && (zone.getDistanceToZone(x, y) < maxDist)) {
                return ch.getValue();
            }
        }

        return null;
    }

    public ClanHall getNearbyAbstractHall(int x, int y, int maxDist) {
        ClanHallZone zone = null;
        for (Entry<Integer, ClanHall> ch : allClanHalls.entrySet()) {
            zone = ch.getValue().getZone();
            if ((zone != null) && (zone.getDistanceToZone(x, y) < maxDist)) {
                return ch.getValue();
            }
        }

        return null;
    }

    /**
     * @param clan
     * @return Clan Hall by Owner
     */
    public AuctionableHall getClanHallByOwner(Clan clan) {
        for (Entry<Integer, AuctionableHall> ch : clanHalls.entrySet()) {
            if (clan.getId() == ch.getValue().getOwnerId()) {
                return ch.getValue();
            }
        }

        return null;
    }

    public ClanHall getAbstractHallByOwner(Clan clan) {
        // Separate loops to avoid iterating over free clan halls.
        for (Entry<Integer, AuctionableHall> ch : clanHalls.entrySet()) {
            if (clan.getId() == ch.getValue().getOwnerId()) {
                return ch.getValue();
            }
        }

        for (Entry<Integer, SiegableHall> ch :
                CHSiegeManager.getInstance().getConquerableHalls().entrySet()) {
            if (clan.getId() == ch.getValue().getOwnerId()) {
                return ch.getValue();
            }
        }

        return null;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static ClanHallTable getInstance() {
        final ClanHallTable current = instance;
        if (current == null) {
            throw new IllegalStateException("ClanHallTable is used before the Spring context created it.");
        }
        return current;
    }
}
