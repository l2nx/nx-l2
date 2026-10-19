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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.SpawnTable;
import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.groups.Party;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.mechanics.sevensigns.DimensionalRift;
import app.l2nx.highfive.mechanics.sevensigns.DimensionalRiftRoom;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

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
        loadSpawns();
    }

    public DimensionalRiftRoom getRoom(byte type, byte room) {
        return rooms.get(type) == null ? null : rooms.get(type).get(room);
    }

    private void loadRooms() {
        try (Connection con = DatabaseFactory.getConnection();
                Statement s = con.createStatement();
                ResultSet rs = s.executeQuery("SELECT * FROM dimensional_rift")) {
            while (rs.next()) {
                // 0 waiting room, 1 recruit, 2 soldier, 3 officer, 4 captain , 5 commander, 6 hero
                final byte type = rs.getByte("type");
                final byte roomId = rs.getByte("room_id");

                // coords related
                final int xMin = rs.getInt("xMin");
                final int xMax = rs.getInt("xMax");
                final int yMin = rs.getInt("yMin");
                final int yMax = rs.getInt("yMax");
                final int z1 = rs.getInt("zMin");
                final int z2 = rs.getInt("zMax");
                final int xT = rs.getInt("xT");
                final int yT = rs.getInt("yT");
                final int zT = rs.getInt("zT");
                final boolean isBossRoom = rs.getByte("boss") > 0;
                rooms.computeIfAbsent(type, k -> new HashMap<>(9));

                rooms.get(type)
                        .put(
                                roomId,
                                new DimensionalRiftRoom(
                                        type, roomId, xMin, xMax, yMin, yMax, z1, z2, xT, yT, zT, isBossRoom));
            }
        } catch (Exception e) {
            log.warn("Can't load Dimension Rift zones. {}", e.getMessage(), e);
        }

        final int typeSize = rooms.keySet().size();
        int roomSize = 0;
        for (Map<Byte, DimensionalRiftRoom> room : rooms.values()) {
            roomSize += room.keySet().size();
        }

        log.info("Loaded {} room types with {} rooms.", typeSize, roomSize);
    }

    public void loadSpawns() {
        int countGood = 0;
        int countBad = 0;
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringComments(true);

            final File file = new File(ServerConfig.DATAPACK_ROOT, "data/DimensionalRift.xml");
            if (!file.exists()) {
                log.warn("Couldn't find data/{}", file.getName());
                return;
            }

            final Document document = factory.newDocumentBuilder().parse(file);
            NamedNodeMap attrs;
            byte type;
            byte roomId;
            int mobId;
            int x;
            int y;
            int z;
            int delay;
            int count;
            for (Node rift = document.getFirstChild(); rift != null; rift = rift.getNextSibling()) {
                if ("rift".equalsIgnoreCase(rift.getNodeName())) {
                    for (Node area = rift.getFirstChild(); area != null; area = area.getNextSibling()) {
                        if ("area".equalsIgnoreCase(area.getNodeName())) {
                            attrs = area.getAttributes();
                            type = Byte.parseByte(attrs.getNamedItem("type").getNodeValue());
                            for (Node room = area.getFirstChild(); room != null; room = room.getNextSibling()) {
                                if ("room".equalsIgnoreCase(room.getNodeName())) {
                                    attrs = room.getAttributes();
                                    roomId = Byte.parseByte(
                                            attrs.getNamedItem("id").getNodeValue());
                                    for (Node spawn = room.getFirstChild();
                                            spawn != null;
                                            spawn = spawn.getNextSibling()) {
                                        if ("spawn".equalsIgnoreCase(spawn.getNodeName())) {
                                            attrs = spawn.getAttributes();
                                            mobId = Integer.parseInt(
                                                    attrs.getNamedItem("mobId").getNodeValue());
                                            delay = Integer.parseInt(
                                                    attrs.getNamedItem("delay").getNodeValue());
                                            count = Integer.parseInt(
                                                    attrs.getNamedItem("count").getNodeValue());
                                            if (!rooms.containsKey(type)) {
                                                log.warn("Type {} not found!", type);
                                            } else if (!rooms.get(type).containsKey(roomId)) {
                                                log.warn("Room {} in Type {} not found!", roomId, type);
                                            }

                                            for (int i = 0; i < count; i++) {
                                                final DimensionalRiftRoom riftRoom =
                                                        rooms.get(type).get(roomId);
                                                x = riftRoom.getRandomX();
                                                y = riftRoom.getRandomY();
                                                z = riftRoom.getTeleportCoorinates()
                                                        .getZ();
                                                if (rooms.containsKey(type)
                                                        && rooms.get(type).containsKey(roomId)) {
                                                    final Spawn spawnDat = new Spawn(mobId);
                                                    spawnDat.setAmount(1);
                                                    spawnDat.setXYZ(x, y, z);
                                                    spawnDat.setHeading(-1);
                                                    spawnDat.setRespawnDelay(delay);
                                                    SpawnTable.getInstance().addSpawn(spawnDat);
                                                    rooms.get(type)
                                                            .get(roomId)
                                                            .getSpawns()
                                                            .add(spawnDat);
                                                    countGood++;
                                                } else {
                                                    countBad++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error on loading dimensional rift spawns: {}", e.getMessage(), e);
        }

        log.info("Loaded {} dimensional rift spawns, {} errors.", countGood, countBad);
    }

    public void reload() {
        for (Map<Byte, DimensionalRiftRoom> rooms : this.rooms.values()) {
            for (DimensionalRiftRoom room : rooms.values()) {
                room.getSpawns().clear();
            }

            rooms.clear();
        }

        this.rooms.clear();
        loadRooms();
        loadSpawns();
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
            room = emptyRooms.get(ThreadLocalRandom.current().nextInt(emptyRooms.size()));
        }

        // find empty room
        while (rooms.get(type).get(room).isPartyInside());
        new DimensionalRift(party, type, room);
    }

    public void killRift(DimensionalRift d) {
        if (d.getTeleportTimerTask() != null) {
            d.getTeleportTimerTask().cancel();
        }

        d.setTeleportTimerTask(null);

        if (d.getTeleportTimer() != null) {
            d.getTeleportTimer().cancel();
        }

        d.setTeleportTimer(null);

        if (d.getSpawnTimerTask() != null) {
            d.getSpawnTimerTask().cancel();
        }

        d.setSpawnTimerTask(null);

        if (d.getSpawnTimer() != null) {
            d.getSpawnTimer().cancel();
        }

        d.setSpawnTimer(null);
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
