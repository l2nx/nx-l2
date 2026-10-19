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
package app.l2nx.highfive.entity.instancezone;

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.network.serverpackets.ServerPacket;
import app.l2nx.highfive.util.ArrayUtil;
import app.l2nx.highfive.util.StatSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;

/**
 * Basic instance zone data transfer object.
 * @author Zoey76
 */
public class InstanceWorld {
    @Setter
    private Instance instance;

    private final Set<Integer> allowed = ConcurrentHashMap.newKeySet();

    @Getter
    private final StatSet parameters = new StatSet();

    /**
     * Gets the dynamically generated instance ID.
     * @return the instance ID
     */
    public int getInstanceId() {
        return instance.getId();
    }

    /**
     * Get template ID of instance world.
     * @return instance template ID
     */
    public int getTemplateId() {
        return instance.getTemplateId();
    }

    public List<Player> getAllowed() {
        final List<Player> allowed = new ArrayList<>(this.allowed.size());
        for (int playerId : this.allowed) {
            final Player player = World.getPlayer(playerId);
            if (player != null) {
                allowed.add(player);
            }
        }

        return allowed;
    }

    public void removeAllowed(Player player) {
        allowed.remove(player.getObjectId());
    }

    /**
     * Add player who can enter to instance.
     * @param player player instance
     */
    public void addAllowed(Player player) {
        if (!allowed.contains(player.getObjectId())) {
            allowed.add(player.getObjectId());
        }
    }

    /**
     * Check if player can enter to instance.
     * @param player player itself
     * @return {@code true} when can enter, otherwise {@code false}
     */
    public boolean isAllowed(Player player) {
        return allowed.contains(player.getObjectId());
    }

    /**
     * Teleport player out of instance.
     * @param player player that should be moved out
     */
    public void ejectPlayer(Player player) {
        if ((player != null) && (player.getInstanceId() == instance.getId())) {
            player.setInstanceId(0);
            if (instance.getExitLoc() != null) {
                player.teleToLocation(instance.getExitLoc(), true);
            } else {
                player.teleToLocation(TeleportWhereType.TOWN);
            }
        }
    }

    /**
     * Set instance world parameter.
     * @param key parameter name
     * @param value parameter value
     */
    public void setParameter(String key, Object value) {
        if (value == null) {
            parameters.remove(key);
        } else {
            parameters.set(key, value);
        }
    }

    /**
     * Get status of instance world.
     * @return instance status, otherwise 0
     */
    public int getStatus() {
        return parameters.getInt("INSTANCE_STATUS", 0);
    }

    /**
     * Check if instance status is equal to {@code status}.
     * @param status number used for status comparison
     * @return {@code true} when instance status and {@code status} are equal, otherwise {@code false}
     */
    public boolean isStatus(int status) {
        return getStatus() == status;
    }

    /**
     * Set status of instance world.
     * @param value new world status
     */
    public void setStatus(int value) {
        parameters.set("INSTANCE_STATUS", value);
    }

    /**
     * Increment instance world status
     * @return new world status
     */
    public int incStatus() {
        final int status = getStatus() + 1;
        setStatus(status);
        return status;
    }

    /**
     * Get spawned NPCs from instance.
     * @return set of NPCs from instance
     */
    public Collection<Npc> getNpcs() {
        return instance.getNpcs();
    }

    /**
     * Get spawned NPCs from instance with specific IDs.
     * @param id IDs of NPCs which should be found
     * @return list of filtered NPCs from instance
     */
    public List<Npc> getNpcs(int... id) {
        final List<Npc> result = new ArrayList<>();
        for (Npc npc : instance.getNpcs()) {
            if (ArrayUtil.contains(id, npc.getId())) {
                result.add(npc);
            }
        }

        return result;
    }

    /**
     * Get spawned NPCs from instance with specific IDs and class type.
     * @param <T>
     * @param clazz
     * @param ids IDs of NPCs which should be found
     * @return list of filtered NPCs from instance
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <T extends Creature> List<T> getNpcs(Class<T> clazz, int... ids) {
        final List<T> result = new ArrayList<>();
        for (Npc npc : instance.getNpcs()) {
            if (((ids.length == 0) || ArrayUtil.contains(ids, npc.getId())) && clazz.isInstance(npc)) {
                result.add((T) npc);
            }
        }

        return result;
    }

    /**
     * Get alive NPCs from instance.
     * @return set of NPCs from instance
     */
    public List<Npc> getAliveNpcs() {
        final List<Npc> result = new ArrayList<>();
        for (Npc npc : instance.getNpcs()) {
            if (npc.getCurrentHp() > 0) {
                result.add(npc);
            }
        }

        return result;
    }

    /**
     * Get alive NPCs from instance with specific IDs.
     * @param id IDs of NPCs which should be found
     * @return list of filtered NPCs from instance
     */
    public List<Npc> getAliveNpcs(int... id) {
        final List<Npc> result = new ArrayList<>();
        for (Npc npc : instance.getNpcs()) {
            if ((npc.getCurrentHp() > 0) && ArrayUtil.contains(id, npc.getId())) {
                result.add(npc);
            }
        }

        return result;
    }

    /**
     * Get spawned and alive NPCs from instance with specific IDs and class type.
     * @param <T>
     * @param clazz
     * @param ids IDs of NPCs which should be found
     * @return list of filtered NPCs from instance
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    public final <T extends Creature> List<T> getAliveNpcs(Class<T> clazz, int... ids) {
        final List<T> result = new ArrayList<>();
        for (Npc npc : instance.getNpcs()) {
            if ((((ids.length == 0) || ArrayUtil.contains(ids, npc.getId())) && (npc.getCurrentHp() > 0))
                    && clazz.isInstance(npc)) {
                result.add((T) npc);
            }
        }

        return result;
    }

    /**
     * Get first found spawned NPC with specific ID.
     * @param id ID of NPC to be found
     * @return first found NPC with specified ID, otherwise {@code null}
     */
    public Npc getNpc(int id) {
        for (Npc npc : instance.getNpcs()) {
            if (npc.getId() == id) {
                return npc;
            }
        }

        return null;
    }

    /**
     * Spawns group of instance NPCs
     * @param groupName the name of group from XML definition to spawn
     * @return list of spawned NPCs
     */
    public List<Npc> spawnGroup(String groupName) {
        return instance.spawnGroup(groupName);
    }

    /**
     * Open a door if it is present in the instance and it is not open.
     * @param doorId the ID of the door to open
     */
    public void openDoor(int doorId) {
        final Door door = instance.getDoor(doorId);
        if ((door != null) && !door.isOpen()) {
            door.openMe();
        }
    }

    /**
     * Close a door if it is present in the instance and it is open.
     * @param doorId the ID of the door to close
     */
    public void closeDoor(int doorId) {
        final Door door = instance.getDoor(doorId);
        if ((door != null) && door.isOpen()) {
            door.closeMe();
        }
    }

    /**
     * Get all doors spawned inside instance world.
     * @return collection of spawned doors
     */
    public Collection<Door> getDoors() {
        return instance.getDoors();
    }

    /**
     * Get spawned door by template ID.
     * @param doorId template ID of door
     * @return instance of door if found, otherwise {@code null}
     */
    public Door getDoor(int doorId) {
        return instance.getDoor(doorId);
    }

    /**
     * Get count of players inside instance.
     * @return players count inside instance
     */
    public int getPlayersCount() {
        return instance.getPlayers().size();
    }

    /**
     * Destroy current instance world.<br>
     * <b><font color=red>Use this method to destroy instance world properly.</font></b>
     */
    public void destroy() {
        instance.setEmptyDestroyTime(0);
        instance.setDuration(1000);
    }

    /**
     * Send packet to each player from instance world.
     * @param packets packets to be send
     */
    public void broadcastPacket(ServerPacket... packets) {
        for (int objectId : instance.getPlayers()) {
            final Player player = World.getPlayer(objectId);
            if ((player != null) && (player.getInstanceId() == instance.getId())) {
                for (ServerPacket packet : packets) {
                    player.sendPacket(packet);
                }
            }
        }
    }
}
