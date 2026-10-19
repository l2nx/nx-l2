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
package app.l2nx.highfive.scripts.ai.areas.ForgeOfTheGods;

import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.PolygonTerritory;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Tar Beetle zone spawn
 * @author malyelfik
 */
@Slf4j
public class TarBeetleSpawn implements IXmlReader {
    private final List<SpawnZone> zones = new ArrayList<>();
    private ScheduledFuture<?> spawnTask;
    private ScheduledFuture<?> shotTask;

    public TarBeetleSpawn() {
        load();
    }

    @Override
    public void load() {
        parseDatapackFile("data/scripts/ai/areas/ForgeOfTheGods/tar_beetle.xml");
        if (!zones.isEmpty()) {
            spawnTask = ThreadPool.scheduleAtFixedRate(() -> zones.forEach(SpawnZone::refreshSpawn), 1000, 60000);
            shotTask = ThreadPool.scheduleAtFixedRate(() -> zones.forEach(SpawnZone::refreshShots), 300000, 300000);
        }
    }

    @Override
    public void parseDocument(Document document, File file) {
        int i = 0;
        for (Node d = document.getFirstChild(); d != null; d = d.getNextSibling()) {
            if (d.getNodeName().equals("list")) {
                for (Node r = d.getFirstChild(); r != null; r = r.getNextSibling()) {
                    if (r.getNodeName().equals("spawnZone")) {
                        NamedNodeMap attrs = r.getAttributes();
                        final int npcCount = parseInteger(attrs, "maxNpcCount");
                        final SpawnZone sp = new SpawnZone(npcCount, i);
                        for (Node b = r.getFirstChild(); b != null; b = b.getNextSibling()) {
                            if (b.getNodeName().equals("zone")) {
                                attrs = b.getAttributes();
                                final int minZ = parseInteger(attrs, "minZ");
                                final int maxZ = parseInteger(attrs, "maxZ");
                                final Zone zone = new Zone();
                                for (Node c = b.getFirstChild(); c != null; c = c.getNextSibling()) {
                                    attrs = c.getAttributes();
                                    if (c.getNodeName().equals("point")) {
                                        final int x = parseInteger(attrs, "x");
                                        final int y = parseInteger(attrs, "y");
                                        zone.add(x, y, minZ, maxZ, 0);
                                    } else if (c.getNodeName().equals("bannedZone")) {
                                        final Zone bannedZone = new Zone();
                                        final int bMinZ = parseInteger(attrs, "minZ");
                                        final int bMaxZ = parseInteger(attrs, "maxZ");
                                        for (Node h = c.getFirstChild(); h != null; h = h.getNextSibling()) {
                                            if (h.getNodeName().equals("point")) {
                                                attrs = h.getAttributes();
                                                final int x = parseInteger(attrs, "x");
                                                final int y = parseInteger(attrs, "y");
                                                bannedZone.add(x, y, bMinZ, bMaxZ, 0);
                                            }
                                        }

                                        zone.addBannedZone(bannedZone);
                                    }
                                }

                                sp.addZone(zone);
                            }
                        }

                        zones.add(i++, sp);
                    }
                }
            }
        }
    }

    public void unload() {
        if (spawnTask != null) {
            spawnTask.cancel(false);
        }

        if (shotTask != null) {
            shotTask.cancel(false);
        }

        zones.forEach(SpawnZone::unload);
        zones.clear();
    }

    public void removeBeetle(Npc npc) {
        zones.get(npc.getVariables().getInt("zoneIndex", 0)).removeSpawn(npc);
        npc.deleteMe();
    }

    private class Zone extends PolygonTerritory {
        private List<Zone> bannedZones;

        public Zone() {
            super(1);
        }

        @Override
        public Location getRandomPoint() {
            Location location = super.getRandomPoint();
            while ((location != null) && isInsideBannedZone(location)) {
                location = super.getRandomPoint();
            }

            return location;
        }

        public void addBannedZone(Zone bZone) {
            if (bannedZones == null) {
                bannedZones = new ArrayList<>();
            }

            bannedZones.add(bZone);
        }

        private boolean isInsideBannedZone(Location location) {
            if (bannedZones != null) {
                for (Zone z : bannedZones) {
                    if (z.isInside(location.getX(), location.getY())) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private class SpawnZone {
        private final List<Zone> zones = new ArrayList<>();
        private final Collection<Npc> spawn = ConcurrentHashMap.newKeySet();
        private final int maxNpcCount;
        private final int index;

        public SpawnZone(int maxNpcCount, int index) {
            this.maxNpcCount = maxNpcCount;
            this.index = index;
        }

        public void addZone(Zone zone) {
            zones.add(zone);
        }

        public void removeSpawn(Npc obj) {
            spawn.remove(obj);
        }

        public void unload() {
            spawn.forEach(Npc::deleteMe);
            spawn.clear();
            zones.clear();
        }

        public void refreshSpawn() {
            try {
                while (this.spawn.size() < maxNpcCount) {
                    final ThreadLocalRandom random = ThreadLocalRandom.current();
                    final Location location =
                            zones.get(random.nextInt(zones.size())).getRandomPoint();
                    if (location != null) {
                        final Spawn spawn = new Spawn(18804);
                        spawn.setHeading(random.nextInt(65535));
                        spawn.setXYZ(
                                location.getX(),
                                location.getY(),
                                GeoEngine.getInstance().getHeight(location.getX(), location.getY(), location.getZ()));
                        final Npc npc = spawn.doSpawn();
                        spawn.stopRespawn();
                        npc.setRandomWalking(false);
                        npc.setImmobilized(true);
                        npc.setInvul(true);
                        npc.disableCoreAI(true);
                        npc.setScriptValue(5);
                        npc.getVariables().set("zoneIndex", index);
                        this.spawn.add(npc);
                    }
                }
            } catch (Exception e) {
                log.warn("Problem with TarBeetleSpawn: {}", e.getMessage());
            }
        }

        public void refreshShots() {
            for (Npc npc : spawn) {
                final int val = npc.getScriptValue();
                if (val == 5) {
                    npc.deleteMe();
                    spawn.remove(npc);
                } else {
                    npc.setScriptValue(val + 1);
                }
            }
        }
    }
}
