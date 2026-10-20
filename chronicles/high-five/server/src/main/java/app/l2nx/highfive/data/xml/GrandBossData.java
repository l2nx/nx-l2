/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.GrandBossSpawn;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

@Slf4j
public class GrandBossData implements IXmlReader {

    private final Map<Integer, GrandBossSpawn> bosses = new ConcurrentSkipListMap<>();

    protected GrandBossData() {
        load();
    }

    @Override
    public void load() {
        bosses.clear();
        parseDatapackFile("data/GrandBosses.xml");
        log.info("Loaded {} grand bosses.", bosses.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "boss", bossNode -> {
                    final GrandBossSpawn boss = GrandBossSpawn.of(new StatSet(parseAttributes(bossNode)));
                    bosses.put(boss.npcId(), boss);
                }));
    }

    public Collection<GrandBossSpawn> getBosses() {
        return Collections.unmodifiableCollection(bosses.values());
    }

    public GrandBossSpawn getBoss(int npcId) {
        return bosses.get(npcId);
    }

    public static GrandBossData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final GrandBossData INSTANCE = new GrandBossData();
    }
}
