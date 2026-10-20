/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.TerritorySpawn;
import app.l2nx.highfive.data.holders.TerritoryTemplate;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

@Slf4j
public class TerritoryData implements IXmlReader {

    private final Map<Integer, TerritoryTemplate> territories = new ConcurrentSkipListMap<>();

    protected TerritoryData() {
        load();
    }

    @Override
    public void load() {
        territories.clear();
        parseDatapackFile("data/Territories.xml");
        log.info("Loaded {} territories.", territories.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "territory", territoryNode -> {
                    final List<TerritorySpawn> spawns = new ArrayList<>();
                    forEach(
                            territoryNode,
                            "spawn",
                            spawnNode -> spawns.add(TerritorySpawn.of(new StatSet(parseAttributes(spawnNode)))));
                    final TerritoryTemplate territory =
                            TerritoryTemplate.of(new StatSet(parseAttributes(territoryNode)), spawns);
                    territories.put(territory.id(), territory);
                }));
    }

    public Collection<TerritoryTemplate> getTerritories() {
        return Collections.unmodifiableCollection(territories.values());
    }

    public TerritoryTemplate getTerritory(int territoryId) {
        return territories.get(territoryId);
    }

    public static TerritoryData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final TerritoryData INSTANCE = new TerritoryData();
    }
}
