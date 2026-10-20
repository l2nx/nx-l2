/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.SiegableHallTemplate;
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

/**
 * The siegable clan halls with their siege schedule and siege guards, keyed by clan hall id.
 */
@Slf4j
public class SiegableHallData implements IXmlReader {

    private final Map<Integer, SiegableHallTemplate> halls = new ConcurrentSkipListMap<>();

    protected SiegableHallData() {
        load();
    }

    @Override
    public void load() {
        halls.clear();
        parseDatapackFile("data/SiegableClanHalls.xml");
        log.info("Loaded {} siegable clan halls.", halls.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "clanHall", hallNode -> {
                    final List<SiegeGuardSpawn> siegeGuards = new ArrayList<>();
                    forEach(
                            hallNode,
                            "siegeGuard",
                            guardNode -> siegeGuards.add(SiegeGuardSpawn.of(new StatSet(parseAttributes(guardNode)))));
                    final SiegableHallTemplate hall =
                            SiegableHallTemplate.of(new StatSet(parseAttributes(hallNode)), siegeGuards);
                    halls.put(hall.id(), hall);
                }));
    }

    public Collection<SiegableHallTemplate> getHalls() {
        return Collections.unmodifiableCollection(halls.values());
    }

    public SiegableHallTemplate getHall(int clanHallId) {
        return halls.get(clanHallId);
    }

    public static SiegableHallData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final SiegableHallData INSTANCE = new SiegableHallData();
    }
}
