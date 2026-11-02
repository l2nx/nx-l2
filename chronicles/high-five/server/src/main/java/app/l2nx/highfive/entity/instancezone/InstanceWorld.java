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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Basic instance zone data transfer object.
 * @author Zoey76
 */
@SingleWriter
public class InstanceWorld {

    private Instance instance;

    // Allow order: a script that picks an allowed player takes the first.
    private final IntLinkedOpenHashSet allowed = new IntLinkedOpenHashSet();

    private final InstanceParameters parameters = new InstanceParameters();

    public void setInstance(Instance instance) {
        WorldPartition.checkOwner();
        this.instance = instance;
    }

    public InstanceParameters getParameters() {
        WorldPartition.checkOwner();
        return parameters;
    }

    /**
     * Gets the dynamically generated instance ID.
     * @return the instance ID
     */
    public int getInstanceId() {
        WorldPartition.checkOwner();
        return instance.getId();
    }

    /**
     * Get template ID of instance world.
     * @return instance template ID
     */
    public int getTemplateId() {
        WorldPartition.checkOwner();
        return instance.getTemplateId();
    }

    /**
     * @return the allowed players who are online, in allow order
     */
    public List<Player> getAllowed() {
        WorldPartition.checkOwner();
        final List<Player> result = new ArrayList<>(allowed.size());
        for (int playerId : allowed) {
            final Player player = World.getPlayer(playerId);
            if (player != null) {
                result.add(player);
            }
        }

        return result;
    }

    public void removeAllowed(Player player) {
        WorldPartition.checkOwner();
        allowed.remove(player.getObjectId());
    }

    /**
     * Add player who can enter to instance.
     * @param player player instance
     */
    public void addAllowed(Player player) {
        WorldPartition.checkOwner();
        allowed.add(player.getObjectId());
    }

    /**
     * Check if player can enter to instance.
     * @param player player itself
     * @return {@code true} when can enter, otherwise {@code false}
     */
    public boolean isAllowed(Player player) {
        WorldPartition.checkOwner();
        return allowed.contains(player.getObjectId());
    }

    /**
     * Teleport player out of instance.
     * @param player player that should be moved out
     */
    public void ejectPlayer(Player player) {
        WorldPartition.checkOwner();
        if ((player != null) && (player.getInstanceId() == instance.getId())) {
            player.setInstanceId(0);
            final Location exit = instance.getExitLoc();
            if (exit != null) {
                player.teleToLocation(exit, true);
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
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        return parameters.getInt("INSTANCE_STATUS", 0);
    }

    /**
     * Check if instance status is equal to {@code status}.
     * @param status number used for status comparison
     * @return {@code true} when instance status and {@code status} are equal, otherwise {@code false}
     */
    public boolean isStatus(int status) {
        WorldPartition.checkOwner();
        return parameters.getInt("INSTANCE_STATUS", 0) == status;
    }

    /**
     * Set status of instance world.
     * @param value new world status
     */
    public void setStatus(int value) {
        WorldPartition.checkOwner();
        parameters.set("INSTANCE_STATUS", value);
    }

    /**
     * Increment instance world status
     * @return new world status
     */
    public int incStatus() {
        WorldPartition.checkOwner();
        final int status = parameters.getInt("INSTANCE_STATUS", 0) + 1;
        parameters.set("INSTANCE_STATUS", status);
        return status;
    }

    /**
     * Get spawned NPCs from instance.
     * @return a read-only snapshot of the NPCs in spawn order
     */
    public Collection<Npc> getNpcs() {
        WorldPartition.checkOwner();
        return instance.getNpcs();
    }

    /**
     * Get spawned NPCs from instance with specific IDs.
     * @param id IDs of NPCs which should be found, none when empty
     * @return list of filtered NPCs from instance
     */
    public List<Npc> getNpcs(int... id) {
        WorldPartition.checkOwner();
        final List<Npc> result = new ArrayList<>();
        instance.collectNpcs(Npc.class, id, false, result);
        return result;
    }

    public final <T extends Creature> List<T> getNpcs(Class<T> clazz) {
        WorldPartition.checkOwner();
        final List<T> result = new ArrayList<>();
        instance.collectNpcs(clazz, false, result);
        return result;
    }

    /**
     * Get spawned NPCs from instance with specific IDs and class type.
     * @param <T>
     * @param clazz
     * @param ids IDs of NPCs which should be found, none when empty
     * @return list of filtered NPCs from instance
     */
    public final <T extends Creature> List<T> getNpcs(Class<T> clazz, int... ids) {
        WorldPartition.checkOwner();
        final List<T> result = new ArrayList<>();
        instance.collectNpcs(clazz, ids, false, result);
        return result;
    }

    /**
     * Get alive NPCs from instance, whatever their IDs.
     * @return list of NPCs from instance
     */
    public List<Npc> getAliveNpcs() {
        WorldPartition.checkOwner();
        final List<Npc> result = new ArrayList<>();
        instance.collectNpcs(Npc.class, true, result);
        return result;
    }

    /**
     * Get alive NPCs from instance with specific IDs.
     * @param id IDs of NPCs which should be found, none when empty
     * @return list of filtered NPCs from instance
     */
    public List<Npc> getAliveNpcs(int... id) {
        WorldPartition.checkOwner();
        final List<Npc> result = new ArrayList<>();
        instance.collectNpcs(Npc.class, id, true, result);
        return result;
    }

    public final <T extends Creature> List<T> getAliveNpcs(Class<T> clazz) {
        WorldPartition.checkOwner();
        final List<T> result = new ArrayList<>();
        instance.collectNpcs(clazz, true, result);
        return result;
    }

    /**
     * Get spawned and alive NPCs from instance with specific IDs and class type.
     * @param <T>
     * @param clazz
     * @param ids IDs of NPCs which should be found, none when empty
     * @return list of filtered NPCs from instance
     */
    public final <T extends Creature> List<T> getAliveNpcs(Class<T> clazz, int... ids) {
        WorldPartition.checkOwner();
        final List<T> result = new ArrayList<>();
        instance.collectNpcs(clazz, ids, true, result);
        return result;
    }

    /**
     * Get first found spawned NPC with specific ID.
     * @param id ID of NPC to be found
     * @return first found NPC with specified ID, otherwise {@code null}
     */
    public Npc getNpc(int id) {
        WorldPartition.checkOwner();
        return instance.getNpc(id);
    }

    /**
     * Spawns group of instance NPCs
     * @param groupName the name of group from XML definition to spawn
     * @return list of spawned NPCs
     */
    public List<Npc> spawnGroup(String groupName) {
        WorldPartition.checkOwner();
        return instance.spawnGroup(groupName);
    }

    /**
     * Open a door if it is present in the instance and it is not open.
     * @param doorId the ID of the door to open
     */
    public void openDoor(int doorId) {
        WorldPartition.checkOwner();
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
        WorldPartition.checkOwner();
        final Door door = instance.getDoor(doorId);
        if ((door != null) && door.isOpen()) {
            door.closeMe();
        }
    }

    /**
     * Get all doors spawned inside instance world.
     * @return a read-only snapshot of the doors in spawn order
     */
    public Collection<Door> getDoors() {
        WorldPartition.checkOwner();
        return instance.getDoors();
    }

    /**
     * Get spawned door by template ID.
     * @param doorId template ID of door
     * @return instance of door if found, otherwise {@code null}
     */
    public Door getDoor(int doorId) {
        WorldPartition.checkOwner();
        return instance.getDoor(doorId);
    }

    /**
     * Get count of players inside instance.
     * @return players count inside instance
     */
    public int getPlayersCount() {
        WorldPartition.checkOwner();
        return instance.getPlayerCount();
    }

    /**
     * Destroy current instance world.<br>
     * <b><font color=red>Use this method to destroy instance world properly.</font></b>
     */
    public void destroy() {
        WorldPartition.checkOwner();
        instance.setEmptyDestroyTime(0);
        instance.setDuration(1000);
    }

    /**
     * Send packet to each player from instance world.
     * @param packets packets to be send
     */
    public void broadcastPacket(ServerPacket... packets) {
        WorldPartition.checkOwner();
        final int instanceId = instance.getId();
        try (IntSnapshot inside = ScratchBuffers.intEmpty()) {
            instance.collectPlayers(inside);
            for (int i = 0; i < inside.size(); i++) {
                final Player player = World.getPlayer(inside.getInt(i));
                if ((player != null) && (player.getInstanceId() == instanceId)) {
                    for (ServerPacket packet : packets) {
                        player.sendPacket(packet);
                    }
                }
            }
        }
    }
}
