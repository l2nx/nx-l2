/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.RaidBossSpawn;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

@Slf4j
public class RaidBossSpawnData implements IXmlReader {

    private final Map<Integer, RaidBossSpawn> spawns = new ConcurrentSkipListMap<>();

    protected RaidBossSpawnData() {
        load();
    }

    @Override
    public void load() {
        spawns.clear();
        parseDatapackFile("data/RaidBossSpawns.xml");
        log.info("Loaded {} raid boss spawns.", spawns.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "boss", bossNode -> {
                    final RaidBossSpawn spawn = RaidBossSpawn.of(new StatSet(parseAttributes(bossNode)));
                    spawns.put(spawn.npcId(), spawn);
                }));
    }

    public Collection<RaidBossSpawn> getSpawns() {
        return Collections.unmodifiableCollection(spawns.values());
    }

    public RaidBossSpawn getSpawn(int npcId) {
        return spawns.get(npcId);
    }

    public static RaidBossSpawnData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final RaidBossSpawnData INSTANCE = new RaidBossSpawnData();
    }
}
