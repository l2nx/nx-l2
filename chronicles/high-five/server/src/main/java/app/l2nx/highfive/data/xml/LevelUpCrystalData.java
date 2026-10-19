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
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.enums.AbsorbCrystalType;
import app.l2nx.highfive.data.holders.LevelingSoulCrystalInfo;
import app.l2nx.highfive.data.holders.SoulCrystal;
import app.l2nx.highfive.data.holders.SoulCrystalDisplay;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Loads item enchant data.
 * @author UnAfraid
 */
@Slf4j
public class LevelUpCrystalData implements IXmlReader {

    private static final Map<Integer, SoulCrystal> soulCrystals = new HashMap<>();
    private static final Map<Integer, Map<Integer, LevelingSoulCrystalInfo>> npcsSoulInfo = new HashMap<>();
    private static List<SoulCrystalDisplay> soulCrystalInfo = new ArrayList<>();

    protected LevelUpCrystalData() {
        load();
    }

    @Override
    public synchronized void load() {
        soulCrystals.clear();
        npcsSoulInfo.clear();
        parseDatapackFile("data/LevelUpCrystalData.xml");
        log.info("Soul crystal data : Loaded {}", soulCrystals.size());
        log.info("Npc leveling crystal data : Loaded {}", npcsSoulInfo.size());
    }

    /**
     * Gets the enchant scroll.
     * @return Leveling SoulCrystal Information Map based NPC
     */
    public Map<Integer, Map<Integer, LevelingSoulCrystalInfo>> getNpcsSoulInfo() {
        return npcsSoulInfo;
    }

    /**
     * Gets the enchant scroll.
     * @param npcid
     * @return Leveling SoulCrystal Information map by items
     */
    public Map<Integer, LevelingSoulCrystalInfo> getNpcSoulInfo(int npcid) {
        return npcsSoulInfo.get(npcid);
    }

    /**
     * Gets the enchant scroll.
     * @return Map soul cristal based key itemid
     */
    public Map<Integer, SoulCrystal> getSoulCrystals() {
        return soulCrystals;
    }

    /**
     * Gets the enchant scroll.
     * @param itemid
     * @return Leveling SoulCrystal Information map by items
     */
    public SoulCrystal getSoulCrystal(int itemid) {
        return soulCrystals.get(itemid);
    }

    /**
     * Gets the enchant scroll.
     * @return SoulCrystalDisplay list
     */
    public List<SoulCrystalDisplay> getSoulCrystalInfo() {
        return soulCrystalInfo;
    }

    @Override
    public void parseDocument(Document document, File file) {
        if (!file.exists()) {
            log.warn("Missing LevelUpCrystalData.xml. The quest wont work without it!");
            return;
        }

        Node first = document.getFirstChild();
        if ((first != null) && "list".equalsIgnoreCase(first.getNodeName())) {
            for (Node n = first.getFirstChild(); n != null; n = n.getNextSibling()) {
                if ("crystal".equalsIgnoreCase(n.getNodeName())) {
                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        if ("item".equalsIgnoreCase(d.getNodeName())) {
                            NamedNodeMap attrs = d.getAttributes();
                            Node att = attrs.getNamedItem("itemId");
                            if (att == null) {
                                log.info("Missing itemId in Crystal List, skipping!");
                                continue;
                            }

                            int itemId = Integer.parseInt(
                                    attrs.getNamedItem("itemId").getNodeValue());

                            att = attrs.getNamedItem("level");
                            if (att == null) {
                                log.info("Missing level in Crystal List item Id {} skipping!", itemId);
                                continue;
                            }

                            att = attrs.getNamedItem("leveledItemId");
                            if (att == null) {
                                log.info("Missing leveledItemId in Crystal List item Id {} skipping!", itemId);
                                continue;
                            }

                            int level =
                                    Integer.parseInt(attrs.getNamedItem("level").getNodeValue());
                            int leveledItemId = Integer.parseInt(
                                    attrs.getNamedItem("leveledItemId").getNodeValue());
                            soulCrystals.put(
                                    itemId,
                                    SoulCrystal.builder()
                                            .level(level)
                                            .itemId(itemId)
                                            .leveledItemId(leveledItemId)
                                            .build());
                        }
                    }
                } else if ("npc".equalsIgnoreCase(n.getNodeName())) {
                    for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                        if ("item".equalsIgnoreCase(d.getNodeName())) {
                            NamedNodeMap attrs = d.getAttributes();
                            Node att = attrs.getNamedItem("npcId");
                            if (att == null) {
                                log.info("Missing npc Id in NPC List, skipping!");
                                continue;
                            }

                            int npcId = Integer.parseInt(att.getNodeValue());
                            Map<Integer, LevelingSoulCrystalInfo> temp = new HashMap<>();
                            for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling()) {
                                boolean isSkillNeeded = false;
                                int chance = 5;
                                AbsorbCrystalType absorbType = AbsorbCrystalType.LAST_HIT;

                                if ("detail".equalsIgnoreCase(cd.getNodeName())) {
                                    attrs = cd.getAttributes();

                                    att = attrs.getNamedItem("absorbType");
                                    if (att != null) {
                                        absorbType = Enum.valueOf(AbsorbCrystalType.class, att.getNodeValue());
                                    }

                                    att = attrs.getNamedItem("chance");
                                    if (att != null) {
                                        chance = Integer.parseInt(att.getNodeValue());
                                    }

                                    att = attrs.getNamedItem("skill");
                                    if (att != null) {
                                        isSkillNeeded = Boolean.parseBoolean(att.getNodeValue());
                                    }

                                    Node att1 = attrs.getNamedItem("maxLevel");
                                    Node att2 = attrs.getNamedItem("levelList");
                                    if ((att1 == null) && (att2 == null)) {
                                        log.info("Missing maxlevel/levelList in NPC List npc Id {} skipping!", npcId);
                                        continue;
                                    }

                                    chance = (int) (chance * PlayerConfig.SOUL_CRYSTAL_CHANCE_MULTIPLIER);
                                    chance = (chance > 100) ? 100 : chance;
                                    LevelingSoulCrystalInfo info = LevelingSoulCrystalInfo.builder()
                                            .absorbCrystalType(absorbType)
                                            .skillNeeded(isSkillNeeded)
                                            .chance(chance)
                                            .build();
                                    if (att1 != null) {
                                        int maxLevel = Integer.parseInt(att1.getNodeValue());
                                        for (int i = 0; i <= maxLevel; i++) {
                                            temp.put(i, info);
                                        }
                                    } else if (att2 != null) {
                                        StringTokenizer st = new StringTokenizer(att2.getNodeValue(), ",");
                                        int tokenCount = st.countTokens();
                                        for (int i = 0; i < tokenCount; i++) {
                                            Integer value = Integer.decode(
                                                    st.nextToken().trim());
                                            if (value == null) {
                                                log.info("Bad Level value!! npc Id {}, token {}!", npcId, i);
                                                value = 0;
                                            }

                                            temp.put(value, info);
                                        }
                                    }
                                }
                            }

                            if (temp.isEmpty()) {
                                log.info("No leveling info for npc Id {} skipping!", npcId);
                                continue;
                            }

                            npcsSoulInfo.put(npcId, temp);
                        }
                    }
                }
            }
        }

        final List<SoulCrystalDisplay> crystalDisplayAux = new ArrayList<>();
        for (Entry<Integer, Map<Integer, LevelingSoulCrystalInfo>> entry : npcsSoulInfo.entrySet()) {
            NpcTemplate npc = NpcData.getInstance().getTemplate(entry.getKey());
            int npcLvl = npc.getLevel();
            String name = npc.getName();
            for (Entry<Integer, LevelingSoulCrystalInfo> entry1 :
                    entry.getValue().entrySet()) {
                int fromLevel = entry1.getKey();
                int chance = entry1.getValue().chance();
                AbsorbCrystalType absorb = entry1.getValue().absorbCrystalType();
                crystalDisplayAux.add(new SoulCrystalDisplay(fromLevel, chance, absorb, npcLvl, name));
            }
        }

        soulCrystalInfo = crystalDisplayAux.stream()
                .sorted(Comparator.comparingInt(SoulCrystalDisplay::getFromLevel)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(SoulCrystalDisplay::getChance))
                        .reversed())
                .toList();

        for (Entry<Integer, SoulCrystal> entry : soulCrystals.entrySet()) {
            entry.getValue().leveledItemId();
        }
    }

    public static LevelUpCrystalData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final LevelUpCrystalData INSTANCE = new LevelUpCrystalData();
    }
}
