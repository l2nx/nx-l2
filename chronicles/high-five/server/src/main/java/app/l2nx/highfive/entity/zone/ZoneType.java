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
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for any zone type handles basic operations.
 * @author durgus
 */
@Slf4j
public abstract class ZoneType extends ListenersContainer {

    @Getter
    private final int id;

    @Getter
    protected ZoneForm zone;

    private final Map<Integer, Creature> characterList = new ConcurrentHashMap<>();

    /** Parameters to affect specific characters */
    protected boolean checkAffected = false;

    @Getter
    @Setter
    private String name = null;

    private int minLevel;
    private int maxLevel;
    private int[] race;
    private int[] classIds;
    private char classType;

    @Getter
    private InstanceType targetType = InstanceType.Creature; // Default all chars.

    private boolean allowStore;

    @Getter
    @Setter
    private boolean enabled;

    @Getter
    private AbstractZoneSettings settings;

    @Getter
    @Setter
    private int instanceTemplateId = -1;

    @Getter
    @Setter
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

    /**
     * Setup new parameters for this zone
     * @param name
     * @param value
     */
    public void setParameter(String name, String value) {
        checkAffected = true;

        // Zone name
        if (name.equals("name")) {
            this.name = value;
        }
        // Minimum level
        else if (name.equals("affectedLvlMin")) {
            minLevel = Integer.parseInt(value);
        }
        // Maximum level
        else if (name.equals("affectedLvlMax")) {
            maxLevel = Integer.parseInt(value);
        }
        // Affected Races
        else if (name.equals("affectedRace")) {
            // Create a new array holding the affected race.
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
        }
        // Affected classes
        else if (name.equals("affectedClassId")) {
            // Create a new array holding the affected classIds.
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
        }
        // Affected class type
        else if (name.equals("affectedClassType")) {
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

    /**
     * @param creature the player to verify.
     * @return {@code true} if the given character is affected by this zone, {@code false} otherwise.
     */
    protected boolean isAffected(Creature creature) {
        // Check instance
        if (instanceTemplateId > 0) {
            final InstanceWorld world = InstanceManager.getInstance().getWorld(creature);
            if ((world != null) && (world.getTemplateId() != instanceTemplateId)) {
                return false;
            }
        }

        if (!isEnabled()) {
            return false;
        }

        // Check level
        if ((creature.getLevel() < minLevel) || (creature.getLevel() > maxLevel)) {
            return false;
        }

        // Check obj class.
        if (!creature.isInstanceTypes(targetType)) {
            return false;
        }

        if (creature.isPlayer()) {
            // Check class type.
            if (classType != 0) {
                if (creature.asPlayer().isMageClass()) {
                    if (classType == 1) {
                        return false;
                    }
                } else if (classType == 2) {
                    return false;
                }
            }

            // Check race
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

            // Check class
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

    /**
     * Set the zone for this ZoneType Instance
     * @param zone
     */
    public void setZone(ZoneForm zone) {
        if (this.zone != null) {
            throw new IllegalStateException("Zone already set");
        }

        this.zone = zone;
    }

    /**
     * Checks if the given coordinates are within zone's plane
     * @param x
     * @param y
     * @return
     */
    public boolean isInsideZone(int x, int y) {
        return isInsideZone(x, y, zone.getHighZ());
    }

    /**
     * Checks if the given coordinates are within the zone, ignores instanceId check
     * @param loc
     * @return
     */
    public boolean isInsideZone(ILocational loc) {
        return isInsideZone(loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Checks if the given coordinates are within the zone, ignores instanceId check
     * @param x
     * @param y
     * @param z
     * @return
     */
    public boolean isInsideZone(int x, int y, int z) {
        return zone.isInsideZone(x, y, z);
    }

    public double getDistanceToZone(int x, int y) {
        return zone.getDistanceToZone(x, y);
    }

    public double getDistanceToZone(WorldObject object) {
        return zone.getDistanceToZone(object.getX(), object.getY());
    }

    public void revalidateInZone(Creature creature) {
        // If the object is inside the zone...
        if (isInsideZone(creature)) {
            // If the character can't be affected by this zone return.
            if (checkAffected && !isAffected(creature)) {
                return;
            }

            if (characterList.putIfAbsent(creature.getObjectId(), creature) == null) {
                // Notify to scripts.
                if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ZONE_ENTER, this)) {
                    EventDispatcher.getInstance().notifyEventAsync(new OnCreatureZoneEnter(creature, this), this);
                }

                // Notify Zone implementation.
                onEnter(creature);
            }
        } else {
            removeCharacter(creature);
        }
    }

    /**
     * Force fully removes a character from the zone Should use during teleport / logoff
     * @param creature
     */
    public void removeCharacter(Creature creature) {
        // Was the character inside this zone?
        if (characterList.containsKey(creature.getObjectId())) {
            // Notify to scripts.
            if (EventDispatcher.getInstance().hasListener(EventType.ON_CREATURE_ZONE_EXIT, this)) {
                EventDispatcher.getInstance().notifyEventAsync(new OnCreatureZoneExit(creature, this), this);
            }

            // Unregister player.
            characterList.remove(creature.getObjectId());

            // Notify Zone implementation.
            onExit(creature);
        }
    }

    /**
     * Will scan the zones char list for the character
     * @param creature
     * @return
     */
    public boolean isCharacterInZone(Creature creature) {
        return characterList.containsKey(creature.getObjectId());
    }

    public void setSettings(AbstractZoneSettings settings) {
        if (this.settings != null) {
            this.settings.clear();
        }

        this.settings = settings;
    }

    protected abstract void onEnter(Creature creature);

    protected abstract void onExit(Creature creature);

    public void onDieInside(Creature creature) {}

    public void onReviveInside(Creature creature) {}

    public void onPlayerLoginInside(Player player) {}

    public void onPlayerLogoutInside(Player player) {}

    public Collection<Creature> getCharactersInside() {
        return characterList.values();
    }

    public List<Player> getPlayersInside() {
        final List<Player> players = new ArrayList<>();
        for (Creature ch : characterList.values()) {
            if ((ch != null) && ch.isPlayer()) {
                players.add(ch.asPlayer());
            }
        }

        return players;
    }

    /**
     * Broadcasts packet to all players inside the zone
     * @param packet
     */
    public void broadcastPacket(ServerPacket packet) {
        if (characterList.isEmpty()) {
            return;
        }

        for (Creature creature : characterList.values()) {
            if ((creature != null) && creature.isPlayer()) {
                creature.sendPacket(packet);
            }
        }
    }

    public void setTargetType(InstanceType type) {
        targetType = type;
        checkAffected = true;
    }

    public boolean getAllowStore() {
        return allowStore;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }

    public void visualizeZone(int z) {
        zone.visualizeZone(z);
    }

    public void oustAllPlayers() {
        for (Creature obj : characterList.values()) {
            if ((obj != null) && obj.isPlayer()) {
                final Player player = obj.asPlayer();
                if (player.isOnline()) {
                    player.teleToLocation(TeleportWhereType.TOWN);
                }
            }
        }
    }
}
