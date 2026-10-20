/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.ClanHallTemplate;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

@Slf4j
public class ClanHallData implements IXmlReader {

    private final Map<Integer, ClanHallTemplate> clanHalls = new ConcurrentSkipListMap<>();

    protected ClanHallData() {
        load();
    }

    @Override
    public void load() {
        clanHalls.clear();
        parseDatapackFile("data/ClanHalls.xml");
        log.info("Loaded {} clan halls.", clanHalls.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "clanHall", hallNode -> {
                    final ClanHallTemplate clanHall = ClanHallTemplate.of(new StatSet(parseAttributes(hallNode)));
                    clanHalls.put(clanHall.id(), clanHall);
                }));
    }

    public Collection<ClanHallTemplate> getClanHalls() {
        return Collections.unmodifiableCollection(clanHalls.values());
    }

    public ClanHallTemplate getClanHall(int clanHallId) {
        return clanHalls.get(clanHallId);
    }

    public static ClanHallData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ClanHallData INSTANCE = new ClanHallData();
    }
}
