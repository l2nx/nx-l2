/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.RiftRoomTemplate;
import app.l2nx.highfive.data.holders.RiftSpawn;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

@Slf4j
public class DimensionalRiftData implements IXmlReader {

    private final Map<Integer, Map<Integer, RiftRoomTemplate>> rooms = new ConcurrentSkipListMap<>();

    protected DimensionalRiftData() {
        load();
    }

    @Override
    public void load() {
        rooms.clear();
        parseDatapackFile("data/DimensionalRift.xml");
        log.info("Loaded {} rift areas with {} rooms.", rooms.size(), getRooms().size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "rift",
                riftNode -> forEach(riftNode, "area", areaNode -> {
                    final int type = parseInteger(areaNode.getAttributes(), "type");
                    final Map<Integer, RiftRoomTemplate> areaRooms = new TreeMap<>();
                    forEach(areaNode, "room", roomNode -> {
                        final List<RiftSpawn> spawns = new ArrayList<>();
                        forEach(
                                roomNode,
                                "spawn",
                                spawnNode -> spawns.add(RiftSpawn.of(new StatSet(parseAttributes(spawnNode)))));
                        final RiftRoomTemplate room =
                                RiftRoomTemplate.of(type, new StatSet(parseAttributes(roomNode)), spawns);
                        areaRooms.put(room.id(), room);
                    });
                    rooms.put(type, Collections.unmodifiableMap(areaRooms));
                }));
    }

    public List<RiftRoomTemplate> getRooms() {
        return rooms.values().stream()
                .flatMap(areaRooms -> areaRooms.values().stream())
                .toList();
    }

    public RiftRoomTemplate getRoom(int type, int roomId) {
        final Map<Integer, RiftRoomTemplate> areaRooms = rooms.get(type);
        return areaRooms != null ? areaRooms.get(roomId) : null;
    }

    public static DimensionalRiftData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final DimensionalRiftData INSTANCE = new DimensionalRiftData();
    }
}
