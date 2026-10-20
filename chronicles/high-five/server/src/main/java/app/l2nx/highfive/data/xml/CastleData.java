/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.CastleTemplate;
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
public class CastleData implements IXmlReader {

    private final Map<Integer, CastleTemplate> castles = new ConcurrentSkipListMap<>();

    protected CastleData() {
        load();
    }

    @Override
    public void load() {
        castles.clear();
        parseDatapackFile("data/Castles.xml");
        log.info("Loaded {} castles.", castles.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "castle", castleNode -> {
                    final List<SiegeGuardSpawn> siegeGuards = new ArrayList<>();
                    forEach(
                            castleNode,
                            "siegeGuard",
                            guardNode -> siegeGuards.add(SiegeGuardSpawn.of(new StatSet(parseAttributes(guardNode)))));
                    final CastleTemplate castle =
                            CastleTemplate.of(new StatSet(parseAttributes(castleNode)), siegeGuards);
                    castles.put(castle.id(), castle);
                }));
    }

    public Collection<CastleTemplate> getCastles() {
        return Collections.unmodifiableCollection(castles.values());
    }

    public CastleTemplate getCastle(int castleId) {
        return castles.get(castleId);
    }

    public static CastleData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final CastleData INSTANCE = new CastleData();
    }
}
