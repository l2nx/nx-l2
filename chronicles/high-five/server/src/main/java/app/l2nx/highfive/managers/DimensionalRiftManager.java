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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.holders.RiftRoomTemplate;
import app.l2nx.highfive.data.holders.RiftSpawn;
import app.l2nx.highfive.data.holders.SpawnPoint;
import app.l2nx.highfive.data.xml.DimensionalRiftData;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.sevensigns.DimensionalRift;
import app.l2nx.highfive.mechanics.sevensigns.DimensionalRiftRoom;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Dimensional Rift manager.
 * @author kombat
 */
@Slf4j
public class DimensionalRiftManager {
    private final Map<Byte, Map<Byte, DimensionalRiftRoom>> rooms = new HashMap<>(7);
    private static final int DIMENSIONAL_FRAGMENT_ITEM_ID = 7079;

    public static DimensionalRiftManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    protected DimensionalRiftManager() {
        loadRooms();
    }

    public DimensionalRiftRoom getRoom(byte type, byte room) {
        return rooms.get(type) == null ? null : rooms.get(type).get(room);
    }

    private void loadRooms() {
        int spawnCount = 0;
        for (RiftRoomTemplate template : DimensionalRiftData.getInstance().getRooms()) {
            final SpawnPoint teleport =
                    template.teleport() == null ? SpawnPoint.builder().build() : template.teleport();
            final DimensionalRiftRoom room = new DimensionalRiftRoom(
                    (byte) template.type(),
                    (byte) template.id(),
                    template.xMin(),
                    template.xMax(),
                    template.yMin(),
                    template.yMax(),
                    template.zMin(),
                    template.zMax(),
                    teleport.x(),
                    teleport.y(),
                    teleport.z(),
                    template.boss());
            rooms.computeIfAbsent(room.getType(), k -> new HashMap<>(9)).put(room.getRoom(), room);
            for (RiftSpawn riftSpawn : template.spawns()) {
                for (int i = 0; i < riftSpawn.count(); i++) {
                    try {
                        final Spawn spawnDat = new Spawn(riftSpawn.npcId());
                        spawnDat.setAmount(1);
                        spawnDat.setXYZ(room.getRandomX(), room.getRandomY(), teleport.z());
                        spawnDat.setHeading(-1);
                        spawnDat.setRespawnDelay(riftSpawn.delay());
                        SpawnTable.getInstance().addSpawn(spawnDat);
                        room.getSpawns().add(spawnDat);
                        spawnCount++;
                    } catch (Exception e) {
                        log.warn(
                                "Could not spawn NPC {} in rift room {}/{}: {}",
                                riftSpawn.npcId(),
                                template.type(),
                                template.id(),
                                e.getMessage(),
                                e);
                    }
                }
            }
        }

        int roomSize = 0;
        for (Map<Byte, DimensionalRiftRoom> room : rooms.values()) {
            roomSize += room.size();
        }

        log.info("Loaded {} room types with {} rooms and {} spawns.", rooms.size(), roomSize, spawnCount);
    }

    public void reload() {
        for (Map<Byte, DimensionalRiftRoom> rooms : this.rooms.values()) {
            for (DimensionalRiftRoom room : rooms.values()) {
                room.getSpawns().clear();
            }

            rooms.clear();
        }

        this.rooms.clear();
        DimensionalRiftData.getInstance().load();
        loadRooms();
    }

    public boolean checkIfInRiftZone(int x, int y, int z, boolean ignorePeaceZone) {
        if (ignorePeaceZone) {
            return rooms.get((byte) 0).get((byte) 1).checkIfInZone(x, y, z);
        }

        return rooms.get((byte) 0).get((byte) 1).checkIfInZone(x, y, z)
                && !rooms.get((byte) 0).get((byte) 0).checkIfInZone(x, y, z);
    }

    public boolean checkIfInPeaceZone(int x, int y, int z) {
        return rooms.get((byte) 0).get((byte) 0).checkIfInZone(x, y, z);
    }

    public void teleportToWaitingRoom(Player player) {
        player.teleToLocation(getRoom((byte) 0, (byte) 0).getTeleportCoorinates());
    }

    public synchronized void start(Player player, byte type, Npc npc) {
        boolean canPass = true;
        if (!player.isInParty()) {
            showHtmlFile(player, "data/html/seven_signs/rift/NoParty.htm", npc);
            return;
        }

        final Party party = player.getParty();
        if (party.getLeaderObjectId() != player.getObjectId()) {
            showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm", npc);
            return;
        }

        if (party.isInDimensionalRift()) {
            handleCheat(player, npc);
            return;
        }

        if (party.getMemberCount() < GeneralConfig.RIFT_MIN_PARTY_SIZE) {
            final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
            html.setFile(player, "data/html/seven_signs/rift/SmallParty.htm");
            html.replace("%npc_name%", npc.getName());
            html.replace("%count%", Integer.toString(GeneralConfig.RIFT_MIN_PARTY_SIZE));
            player.sendPacket(html);
            return;
        }

        // max parties inside is rooms count - 1
        if (!isAllowedEnter(type)) {
            player.sendMessage("Rift is full. Try later.");
            return;
        }

        for (Player p : party.getMembers()) {
            if (!checkIfInPeaceZone(p.getX(), p.getY(), p.getZ())) {
                canPass = false;
                break;
            }
        }

        if (!canPass) {
            showHtmlFile(player, "data/html/seven_signs/rift/NotInWaitingRoom.htm", npc);
            return;
        }

        Item i;
        final int count = getNeededItems(type);
        for (Player p : party.getMembers()) {
            i = p.getInventory().getItemByItemId(DIMENSIONAL_FRAGMENT_ITEM_ID);
            if (i == null) {
                canPass = false;
                break;
            }

            if (i.getCount() > 0) {
                if (i.getCount() < getNeededItems(type)) {
                    canPass = false;
                    break;
                }
            } else {
                canPass = false;
                break;
            }
        }

        if (!canPass) {
            final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
            html.setFile(player, "data/html/seven_signs/rift/NoFragments.htm");
            html.replace("%npc_name%", npc.getName());
            html.replace("%count%", Integer.toString(count));
            player.sendPacket(html);
            return;
        }

        for (Player p : party.getMembers()) {
            i = p.getInventory().getItemByItemId(DIMENSIONAL_FRAGMENT_ITEM_ID);
            if (!p.destroyItem(ItemProcessType.FEE, i, count, null, false)) {
                final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
                html.setFile(player, "data/html/seven_signs/rift/NoFragments.htm");
                html.replace("%npc_name%", npc.getName());
                html.replace("%count%", Integer.toString(count));
                player.sendPacket(html);
                return;
            }
        }

        byte room;
        List<Byte> emptyRooms;
        do {
            emptyRooms = getFreeRooms(type);
            room = emptyRooms.get(ClockBridge.random().nextInt(emptyRooms.size()));
        }

        // find empty room
        while (rooms.get(type).get(room).isPartyInside());
        new DimensionalRift(party, type, room);
    }

    private int getNeededItems(byte type) {
        switch (type) {
            case 1: {
                return GeneralConfig.RIFT_ENTER_COST_RECRUIT;
            }
            case 2: {
                return GeneralConfig.RIFT_ENTER_COST_SOLDIER;
            }
            case 3: {
                return GeneralConfig.RIFT_ENTER_COST_OFFICER;
            }
            case 4: {
                return GeneralConfig.RIFT_ENTER_COST_CAPTAIN;
            }
            case 5: {
                return GeneralConfig.RIFT_ENTER_COST_COMMANDER;
            }
            case 6: {
                return GeneralConfig.RIFT_ENTER_COST_HERO;
            }
            default: {
                throw new IndexOutOfBoundsException();
            }
        }
    }

    public void showHtmlFile(Player player, String file, Npc npc) {
        final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
        html.setFile(player, file);
        html.replace("%npc_name%", npc.getName());
        player.sendPacket(html);
    }

    public void handleCheat(Player player, Npc npc) {
        showHtmlFile(player, "data/html/seven_signs/rift/Cheater.htm", npc);
        if (!player.isGM()) {
            log.warn("{} was cheating in dimension rift area!", player);
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "Warning!! Character " + player.getName() + " tried to cheat in dimensional rift.",
                    GeneralConfig.DEFAULT_PUNISH);
        }
    }

    public boolean isAllowedEnter(byte type) {
        int count = 0;
        for (DimensionalRiftRoom room : rooms.get(type).values()) {
            if (room.isPartyInside()) {
                count++;
            }
        }

        return (count < (rooms.get(type).size() - 1));
    }

    public List<Byte> getFreeRooms(byte type) {
        final List<Byte> list = new ArrayList<>();
        for (DimensionalRiftRoom room : rooms.get(type).values()) {
            if (!room.isPartyInside()) {
                list.add(room.getRoom());
            }
        }

        return list;
    }

    private static class SingletonHolder {
        protected static final DimensionalRiftManager INSTANCE = new DimensionalRiftManager();
    }
}
