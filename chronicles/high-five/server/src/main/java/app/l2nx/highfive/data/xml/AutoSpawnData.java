/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.data.holders.AutoSpawnGroup;
import app.l2nx.highfive.data.holders.SpawnPoint;
import app.l2nx.highfive.util.StatSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;

@Slf4j
public class AutoSpawnData implements IXmlReader {

    private final List<AutoSpawnGroup> groups = new CopyOnWriteArrayList<>();

    protected AutoSpawnData() {
        load();
    }

    @Override
    public void load() {
        groups.clear();
        parseDatapackFile("data/AutoSpawns.xml");
        log.info("Loaded {} auto spawn groups.", groups.size());
    }

    @Override
    public void parseDocument(Document document, File file) {
        forEach(
                document,
                "list",
                listNode -> forEach(listNode, "group", groupNode -> {
                    final List<SpawnPoint> locations = new ArrayList<>();
                    forEach(
                            groupNode,
                            "location",
                            locationNode -> locations.add(SpawnPoint.of(new StatSet(parseAttributes(locationNode)))));
                    groups.add(AutoSpawnGroup.of(new StatSet(parseAttributes(groupNode)), locations));
                }));
    }

    public List<AutoSpawnGroup> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    public static AutoSpawnData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final AutoSpawnData INSTANCE = new AutoSpawnData();
    }
}
