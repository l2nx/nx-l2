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
package app.l2nx.highfive.entity.zone;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.zone.type.PeaceZone;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;

/**
 * @author NosBit
 */
@SingleWriter
public class ZoneRegion {
    private final int regionX;

    private final int regionY;

    // Only the zone loader mutates it, never a zone callback, so walks that call out need no copy.
    private final ObjectArrayList<ZoneType> zones = new ObjectArrayList<>();

    public ZoneRegion(int regionX, int regionY) {
        this.regionX = regionX;
        this.regionY = regionY;
    }

    public int getRegionX() {
        WorldPartition.checkOwner();
        return regionX;
    }

    public int getRegionY() {
        WorldPartition.checkOwner();
        return regionY;
    }

    /** Adds the zone, or replaces the zone of the same id in its place. */
    public void addZone(ZoneType zone) {
        WorldPartition.checkOwner();
        final int index = indexOf(zone.getId());
        if (index < 0) {
            zones.add(zone);
        } else {
            zones.set(index, zone);
        }
    }

    public void clearZones() {
        WorldPartition.checkOwner();
        zones.clear();
    }

    public boolean containsZone(int zoneId) {
        WorldPartition.checkOwner();
        return indexOf(zoneId) >= 0;
    }

    private int indexOf(int zoneId) {
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).getId() == zoneId) {
                return i;
            }
        }

        return -1;
    }

    public void collectZones(int x, int y, List<? super ZoneType> into) {
        WorldPartition.checkOwner();
        for (int i = 0; i < zones.size(); i++) {
            final ZoneType zone = zones.get(i);
            if (zone.covers(x, y)) {
                into.add(zone);
            }
        }
    }

    public void collectZones(int x, int y, int z, List<? super ZoneType> into) {
        WorldPartition.checkOwner();
        for (int i = 0; i < zones.size(); i++) {
            final ZoneType zone = zones.get(i);
            if (zone.covers(x, y, z)) {
                into.add(zone);
            }
        }
    }

    public <T extends ZoneType> T getZone(int x, int y, int z, Class<T> type) {
        WorldPartition.checkOwner();
        for (int i = 0; i < zones.size(); i++) {
            final ZoneType zone = zones.get(i);
            if (type.isInstance(zone) && zone.covers(x, y, z)) {
                return type.cast(zone);
            }
        }

        return null;
    }

    public void revalidateZones(Creature creature) {
        WorldPartition.checkOwner();
        // Revalidation runs once the teleport completes, not mid-teleport.
        if (creature.isTeleporting()) {
            return;
        }

        for (int i = 0; i < zones.size(); i++) {
            zones.get(i).revalidate(creature);
        }
    }

    public void removeFromZones(Creature creature) {
        WorldPartition.checkOwner();
        for (int i = 0; i < zones.size(); i++) {
            zones.get(i).remove(creature);
        }
    }

    public boolean checkEffectRangeInsidePeaceZone(Skill skill, int x, int y, int z) {
        WorldPartition.checkOwner();
        final int range = skill.getEffectRange();
        final int up = y + range;
        final int down = y - range;
        final int left = x + range;
        final int right = x - range;
        for (int i = 0; i < zones.size(); i++) {
            final ZoneType e = zones.get(i);
            if (e instanceof PeaceZone) {
                if (e.covers(x, up, z)) {
                    return false;
                }

                if (e.covers(x, down, z)) {
                    return false;
                }

                if (e.covers(left, y, z)) {
                    return false;
                }

                if (e.covers(right, y, z)) {
                    return false;
                }

                if (e.covers(x, y, z)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void onDeath(Creature creature) {
        WorldPartition.checkOwner();
        for (int i = 0; i < zones.size(); i++) {
            final ZoneType zone = zones.get(i);
            if (zone.covers(creature.getX(), creature.getY(), creature.getZ())) {
                zone.onDieInside(creature);
            }
        }
    }

    public void onRevive(Creature creature) {
        WorldPartition.checkOwner();
        for (int i = 0; i < zones.size(); i++) {
            final ZoneType zone = zones.get(i);
            if (zone.covers(creature.getX(), creature.getY(), creature.getZ())) {
                zone.onReviveInside(creature);
            }
        }
    }
}
