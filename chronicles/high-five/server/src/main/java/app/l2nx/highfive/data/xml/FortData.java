/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.FortSpawn;
import app.l2nx.highfive.data.holders.FortTemplate;
import app.l2nx.highfive.data.holders.SiegeGuardSpawn;
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
public class FortData implements IXmlReader {

    private final Map<Integer, FortTemplate> forts = new ConcurrentSkipListMap<>();

    protected FortData() {
        load();
    }

    @Override
    public void load() {
        forts.clear();
        parseDatapackFile("data/Forts.xml");
        log.info("Loaded {} forts.", forts.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "fort", fortNode -> {
                    final List<SiegeGuardSpawn> siegeGuards = new ArrayList<>();
                    forEach(
                            fortNode,
                            "siegeGuard",
                            guardNode -> siegeGuards.add(SiegeGuardSpawn.of(new StatSet(parseAttributes(guardNode)))));
                    final List<FortSpawn> spawns = new ArrayList<>();
                    forEach(
                            fortNode,
                            "spawn",
                            spawnNode -> spawns.add(FortSpawn.of(new StatSet(parseAttributes(spawnNode)))));
                    final FortTemplate fort =
                            FortTemplate.of(new StatSet(parseAttributes(fortNode)), siegeGuards, spawns);
                    forts.put(fort.id(), fort);
                }));
    }

    public Collection<FortTemplate> getForts() {
        return Collections.unmodifiableCollection(forts.values());
    }

    public FortTemplate getFort(int fortId) {
        return forts.get(fortId);
    }

    public static FortData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final FortData INSTANCE = new FortData();
    }
}
