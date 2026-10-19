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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.mechanics.siege.Fort;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FortManager {

    private static final List<Fort> forts = new CopyOnWriteArrayList<>();

    public int findNearestFortIndex(WorldObject obj) {
        return findNearestFortIndex(obj, Long.MAX_VALUE);
    }

    public int findNearestFortIndex(WorldObject obj, long maxDistanceValue) {
        int index = getFortIndex(obj);
        if (index < 0) {
            Fort fort;
            double distance;
            long maxDistance = maxDistanceValue;
            for (int i = 0; i < forts.size(); i++) {
                fort = forts.get(i);
                if (fort == null) {
                    continue;
                }

                distance = fort.getDistance(obj);
                if (maxDistance > distance) {
                    maxDistance = (long) distance;
                    index = i;
                }
            }
        }

        return index;
    }

    public Fort getFortById(int fortId) {
        for (Fort fort : forts) {
            if (fort.getResidenceId() == fortId) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFortByOwner(Clan clan) {
        if (clan == null) {
            return null;
        }

        for (Fort fort : forts) {
            if (fort.getOwnerClan() == clan) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFort(String name) {
        for (Fort fort : forts) {
            if (fort.getName().equalsIgnoreCase(name.trim())) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFort(int x, int y, int z) {
        for (Fort fort : forts) {
            if (fort.checkIfInZone(x, y, z)) {
                return fort;
            }
        }

        return null;
    }

    public Fort getFort(WorldObject activeObject) {
        return getFort(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public int getFortIndex(int fortId) {
        Fort fort;
        for (int i = 0; i < forts.size(); i++) {
            fort = forts.get(i);
            if ((fort != null) && (fort.getResidenceId() == fortId)) {
                return i;
            }
        }

        return -1;
    }

    public int getFortIndex(WorldObject activeObject) {
        return getFortIndex(activeObject.getX(), activeObject.getY(), activeObject.getZ());
    }

    public int getFortIndex(int x, int y, int z) {
        Fort fort;
        for (int i = 0; i < forts.size(); i++) {
            fort = forts.get(i);
            if ((fort != null) && fort.checkIfInZone(x, y, z)) {
                return i;
            }
        }

        return -1;
    }

    public List<Fort> getForts() {
        return forts;
    }

    public void loadInstances() {
        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs = s.executeQuery("SELECT id FROM fort ORDER BY id")) {
            while (rs.next()) {
                forts.add(new Fort(rs.getInt("id")));
            }

            log.info("Loaded {} fortress", forts.size());
            for (Fort fort : forts) {
                fort.getSiege().getSiegeGuardManager().loadSiegeGuard();
            }
        } catch (Exception e) {
            log.warn("Exception: loadFortData(): {}", e.getMessage(), e);
        }
    }

    public void activateInstances() {
        for (Fort fort : forts) {
            fort.activateInstance();
        }
    }

    public static FortManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final FortManager INSTANCE = new FortManager();
    }
}
