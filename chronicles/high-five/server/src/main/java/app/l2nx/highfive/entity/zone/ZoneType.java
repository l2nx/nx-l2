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
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.instancezone.InstanceWorld;
import app.l2nx.highfive.interfaces.ILocational;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureZoneEnter;
import app.l2nx.highfive.mechanics.events.holders.actor.creature.OnCreatureZoneExit;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for any zone type handles basic operations.
 * @author durgus
 */
@Slf4j
@SingleWriter
public abstract class ZoneType extends ListenersContainer {
    private final int id;

    private ZoneForm zone;

    // By object id in entry order, so walks over the creatures inside follow the same order on every run.
    private final Int2ObjectLinkedOpenHashMap<Creature> characters = new Int2ObjectLinkedOpenHashMap<>();

    protected boolean checkAffected = false;

    private String name = null;

    private int minLevel;
    private int maxLevel;
    private int[] race;
    private int[] classIds;
    private char classType;

    private InstanceType targetType = InstanceType.Creature;
    private boolean allowStore;

    private boolean enabled;

    private AbstractZoneSettings settings;

    private int instanceTemplateId = -1;

    private int instanceId = -1;

    protected ZoneType(int id) {
        this.id = id;
        minLevel = 0;
        maxLevel = 0xFF;
        classType = 0;
        race = null;
        classIds = null;
        allowStore = true;
        enabled = true;
    }

    protected ZoneType(int id, InstanceType targetType) {
        this(id);
        this.targetType = targetType;
        checkAffected = true;
    }

    /** A zone that keeps state in settings, which a zone reload hands over to the zone that replaces it. */
    protected ZoneType(int id, AbstractZoneSettings settings) {
        this(id);
        this.settings = settings;
    }

    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public ZoneForm getZone() {
        WorldPartition.checkOwner();
        return zone;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void setName(String name) {
        WorldPartition.checkOwner();
        this.name = name;
    }

    public boolean isEnabled() {
        WorldPartition.checkOwner();
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        WorldPartition.checkOwner();
        this.enabled = enabled;
    }

    public InstanceType getTargetType() {
        WorldPartition.checkOwner();
        return targetType;
    }

    public AbstractZoneSettings getSettings() {
        WorldPartition.checkOwner();
        return settings;
    }

    /** Unchecked: subclasses read their typed settings from their own checked methods. */
    protected final AbstractZoneSettings currentSettings() {
        return settings;
    }

    public int getInstanceTemplateId() {
        WorldPartition.checkOwner();
        return instanceTemplateId;
    }

    public void setInstanceTemplateId(int instanceTemplateId) {
        WorldPartition.checkOwner();
        this.instanceTemplateId = instanceTemplateId;
    }

    public int getInstanceId() {
        WorldPartition.checkOwner();
        return instanceId;
    }

    public void setInstanceId(int instanceId) {
        WorldPartition.checkOwner();
        this.instanceId = instanceId;
    }

    public void setParameter(String name, String value) {
        WorldPartition.checkOwner();
        checkAffected = true;

        if (name.equals("name")) {
            this.name = value;
        } else if (name.equals("affectedLvlMin")) {
            minLevel = Integer.parseInt(value);
        } else if (name.equals("affectedLvlMax")) {
            maxLevel = Integer.parseInt(value);
        } else if (name.equals("affectedRace")) {
            if (race == null) {
                race = new int[1];
                race[0] = Integer.parseInt(value);
            } else {
                final int[] temp = new int[race.length + 1];
                int i = 0;
                for (; i < race.length; i++) {
                    temp[i] = race[i];
                }

                temp[i] = Integer.parseInt(value);
                race = temp;
            }
        } else if (name.equals("affectedClassId")) {
            if (classIds == null) {
                classIds = new int[1];
                classIds[0] = Integer.parseInt(value);
            } else {
                final int[] temp = new int[classIds.length + 1];
                int i = 0;
                for (; i < classIds.length; i++) {
                    temp[i] = classIds[i];
                }

                temp[i] = Integer.parseInt(value);
                classIds = temp;
            }
        } else if (name.equals("affectedClassType")) {
            if (value.equals("Fighter")) {
                classType = 1;
            } else {
                classType = 2;
            }
        } else if (name.equals("targetClass")) {
            targetType = Enum.valueOf(InstanceType.class, value);
        } else if (name.equals("allowStore")) {
            allowStore = Boolean.parseBoolean(value);
        } else if (name.equals("default_enabled")) {
            enabled = Boolean.parseBoolean(value);
        } else if (name.equals("instanceId")) {
            instanceTemplateId = Integer.parseInt(value);
        } else {
            LoggerFactory.getLogger(getClass()).info("Unknown parameter - {} in zone: {}", name, id);
        }
    }

    /** Returns {@code current} when the parsed value is zero or negative; the world loop needs a positive period. */
    protected int parsePositiveParameter(String name, String value, int current) {
        final int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            log.warn("Zone {}: parameter {} must be positive, was {}; keeping {}.", id, name, parsed, current);
            return current;
        }

        return parsed;
    }

    protected boolean isAffected(Creature creature) {
        if (instanceTemplateId > 0) {
            final InstanceWorld world = InstanceManager.getInstance().getWorld(creature);
            if ((world != null) && (world.getTemplateId() != instanceTemplateId)) {
                return false;
            }
        }

        if (!enabled) {
            return false;
        }

        if ((creature.getLevel() < minLevel) || (creature.getLevel() > maxLevel)) {
            return false;
        }

        if (!creature.isInstanceTypes(targetType)) {
            return false;
        }

        if (creature.isPlayer()) {
            if (classType != 0) {
                if (creature.asPlayer().isMageClass()) {
                    if (classType == 1) {
                        return false;
                    }
                } else if (classType == 2) {
                    return false;
                }
            }

            if (race != null) {
                boolean ok = false;
                for (int element : race) {
                    if (creature.getRace().ordinal() == element) {
                        ok = true;
                        break;
                    }
                }

                if (!ok) {
                    return false;
                }
            }

            if (classIds != null) {
                boolean ok = false;
                for (int clas : classIds) {
                    if (creature.asPlayer().getPlayerClass().getId() == clas) {
                        ok = true;
                        break;
                    }
                }

                if (!ok) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setZone(ZoneForm zone) {
        WorldPartition.checkOwner();
        if (this.zone != null) {
            throw new IllegalStateException("Zone already set");
        }

        this.zone = zone;
    }

    public boolean isInsideZone(int x, int y) {
        WorldPartition.checkOwner();
        return covers(x, y);
    }

    public boolean isInsideZone(ILocational loc) {
        WorldPartition.checkOwner();
        return zone.isInsideZone(loc.getX(), loc.getY(), loc.getZ());
    }

    public boolean isInsideZone(int x, int y, int z) {
        WorldPartition.checkOwner();
        return zone.isInsideZone(x, y, z);
    }

    /** Unchecked: the checked lookups of the region call it for every zone of the region. */
    final boolean covers(int x, int y) {
        return zone.isInsideZone(x, y, zone.getHighZ());
    }

    /** Unchecked: the checked lookups of the region call it for every zone of the region. */
    final boolean covers(int x, int y, int z) {
        return zone.isInsideZone(x, y, z);
    }

    public double getDistanceToZone(int x, int y) {
        WorldPartition.checkOwner();
        return zone.getDistanceToZone(x, y);
    }

    public double getDistanceToZone(WorldObject object) {
        WorldPartition.checkOwner();
        return zone.getDistanceToZone(object.getX(), object.getY());
    }

    public void revalidateInZone(Creature creature) {
        WorldPartition.checkOwner();
        revalidate(creature);
    }

    /** Unchecked: the region revalidates every zone of the region on each move, after its own check. */
    final void revalidate(Creature creature) {
        if (zone.isInsideZone(creature.getX(), creature.getY(), creature.getZ())) {
            if (checkAffected && !isAffected(creature)) {
                return;
            }

            if (characters.putIfAbsent(creature.getObjectId(), creature) == null) {
                if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ZONE_ENTER, this)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnCreatureZoneEnter(creature, this), this);
                }

                onEnter(creature);
            }
        } else {
            remove(creature);
        }
    }

    public void removeCharacter(Creature creature) {
        WorldPartition.checkOwner();
        remove(creature);
    }

    /** Unchecked: the region removes a creature from every zone of the region, after its own check. */
    final void remove(Creature creature) {
        if (characters.containsKey(creature.getObjectId())) {
            if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ZONE_EXIT, this)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnCreatureZoneExit(creature, this), this);
            }

            characters.remove(creature.getObjectId());

            onExit(creature);
        }
    }

    public boolean isCharacterInZone(Creature creature) {
        WorldPartition.checkOwner();
        return characters.containsKey(creature.getObjectId());
    }

    public void setSettings(AbstractZoneSettings settings) {
        WorldPartition.checkOwner();
        if (this.settings != null) {
            this.settings.clear();
        }

        this.settings = settings;
    }

    protected abstract void onEnter(Creature creature);

    protected abstract void onExit(Creature creature);

    public void onDieInside(Creature creature) {
        WorldPartition.checkOwner();
    }

    public void onReviveInside(Creature creature) {
        WorldPartition.checkOwner();
    }

    public void onPlayerLoginInside(Player player) {
        WorldPartition.checkOwner();
    }

    public void onPlayerLogoutInside(Player player) {
        WorldPartition.checkOwner();
    }

    /** Read-only snapshot in entry order. */
    public List<Creature> getCharactersInside() {
        WorldPartition.checkOwner();
        if (characters.isEmpty()) {
            return List.of();
        }

        return ObjectLists.unmodifiable(new ObjectArrayList<>(characters.values()));
    }

    /** Read-only snapshot of the players inside, in entry order. */
    public List<Player> getPlayersInside() {
        WorldPartition.checkOwner();
        final ObjectArrayList<Player> players = new ObjectArrayList<>();
        collectPlayers(players);
        return ObjectLists.unmodifiable(players);
    }

    public void collectCharactersInside(List<? super Creature> into) {
        WorldPartition.checkOwner();
        into.addAll(characters.values());
    }

    public void collectPlayersInside(List<? super Player> into) {
        WorldPartition.checkOwner();
        collectPlayers(into);
    }

    private void collectPlayers(List<? super Player> into) {
        for (Creature creature : characters.values()) {
            if (creature.isPlayer()) {
                into.add(creature.asPlayer());
            }
        }
    }

    public boolean hasCharactersInside() {
        WorldPartition.checkOwner();
        return !characters.isEmpty();
    }

    public boolean hasPlayersInside() {
        WorldPartition.checkOwner();
        for (Creature creature : characters.values()) {
            if (creature.isPlayer()) {
                return true;
            }
        }

        return false;
    }

    public int getCharacterCount() {
        WorldPartition.checkOwner();
        return characters.size();
    }

    public int getPlayerCount() {
        WorldPartition.checkOwner();
        int count = 0;
        for (Creature creature : characters.values()) {
            if (creature.isPlayer()) {
                count++;
            }
        }

        return count;
    }

    public void broadcastPacket(ServerPacket packet) {
        WorldPartition.checkOwner();
        if (characters.isEmpty()) {
            return;
        }

        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayers(players);
            for (Player player : players) {
                player.sendPacket(packet);
            }
        }
    }

    public void setTargetType(InstanceType type) {
        WorldPartition.checkOwner();
        targetType = type;
        checkAffected = true;
    }

    public boolean getAllowStore() {
        WorldPartition.checkOwner();
        return allowStore;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }

    public void visualizeZone(int z) {
        WorldPartition.checkOwner();
        zone.visualizeZone(z);
    }

    public void oustAllPlayers() {
        WorldPartition.checkOwner();
        // A teleport takes the player out of this zone, so the walk runs over a copy.
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayers(players);
            for (Player player : players) {
                if (player.isOnline()) {
                    player.teleToLocation(TeleportWhereType.TOWN);
                }
            }
        }
    }
}
