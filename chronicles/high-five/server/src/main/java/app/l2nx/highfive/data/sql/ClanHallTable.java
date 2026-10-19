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

import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.AuctionableHall;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.zone.type.ClanHallZone;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.ClanHallAuctionManager;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.util.StatSet;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Steuf
 */
@Slf4j
public class ClanHallTable {

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

    protected ClanHallTable() {
        load();
    }

    /** Load All Clan Hall */
    private void load() {
        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs = s.executeQuery("SELECT * FROM clanhall ORDER BY id")) {
            int id;
            int ownerId;
            int lease;
            while (rs.next()) {
                final StatSet set = new StatSet();
                id = rs.getInt("id");
                ownerId = rs.getInt("ownerId");
                lease = rs.getInt("lease");
                set.set("id", id);
                set.set("name", rs.getString("name"));
                set.set("ownerId", ownerId);
                set.set("lease", lease);
                set.set("desc", rs.getString("desc"));
                set.set("location", rs.getString("location"));
                set.set("paidUntil", rs.getLong("paidUntil"));
                set.set("grade", rs.getInt("Grade"));
                set.set("paid", rs.getBoolean("paid"));
                final AuctionableHall ch = new AuctionableHall(set);
                allAuctionableClanHalls.put(id, ch);
                addClanHall(ch);

                if (ch.getOwnerId() > 0) {
                    clanHalls.put(id, ch);
                    continue;
                }

                freeClanHalls.put(id, ch);

                if ((ClanHallAuctionManager.getInstance().getAuction(id) == null) && (lease > 0)) {
                    ClanHallAuctionManager.getInstance().initNPC(id);
                }
            }

            log.info("Loaded {} clan halls", clanHalls.size());
            log.info("Loaded {} free clan halls", freeClanHalls.size());
            loaded = true;
        } catch (Exception e) {
            log.warn("Exception: ClanHallManager.load(): {}", e.getMessage(), e);
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

    public static ClanHallTable getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ClanHallTable INSTANCE = new ClanHallTable();
    }
}
