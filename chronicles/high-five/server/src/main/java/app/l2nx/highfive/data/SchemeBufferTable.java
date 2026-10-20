/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.data;

import app.l2nx.highfive.config.custom.SchemeBufferConfig;
import app.l2nx.highfive.entity.actor.holders.npc.BuffSkillHolder;
import app.l2nx.highfive.repository.character.BufferSchemeRepository;
import app.l2nx.highfive.repository.character.model.BufferScheme;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Loads available scheme buffer skills and manages player schemes in memory and database.<br>
 * Provides category-aware lookups to support duplicated skill IDs across different categories.
 * <ul>
 * <li>Loads skills from {@value #SKILLS_XML_PATH}.</li>
 * <li>Loads and saves player schemes from/to database.</li>
 * <li>Resolves category-aware skill holders for manual pages.</li>
 * </ul>
 * @author Mobius, BazookaRpm
 */
@Slf4j
@Component
public class SchemeBufferTable {
    private static volatile SchemeBufferTable instance;

    // Constants.
    private static final String SKILLS_XML_PATH = "./data/SchemeBufferSkills.xml";
    private static final String TYPE_MAGE_GROUP = "MAGE_GROUP";
    private static final String TYPE_FIGHTER_GROUP = "FIGHTER_GROUP";

    // Player schemes storage.
    private final Map<Integer, Map<String, List<Integer>>> schemesTable = new ConcurrentHashMap<>();

    // Skills registry.
    private final Map<Integer, BuffSkillHolder> availableBuffs = new LinkedHashMap<>();
    private final Map<String, List<Integer>> skillIdsByType = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Map<Integer, BuffSkillHolder>> availableBuffsByType =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<String> skillTypesOrder = new ArrayList<>();

    private final BufferSchemeRepository bufferSchemeRepository;

    public SchemeBufferTable(BufferSchemeRepository bufferSchemeRepository) {
        this.bufferSchemeRepository = bufferSchemeRepository;
        instance = this;
    }

    /**
     * Initializes skills registry and loads player schemes.
     */
    public void load() {
        loadAvailableBuffs();
        loadPlayerSchemes();
        log.info("Loaded {} players and {} available skills.", schemesTable.size(), availableBuffs.size());
    }

    /**
     * Saves all loaded schemes to database, replacing the stored ones.
     */
    public void saveSchemes() {
        final List<BufferScheme> schemes = new ArrayList<>();
        for (Entry<Integer, Map<String, List<Integer>>> playerEntry : schemesTable.entrySet()) {
            for (Entry<String, List<Integer>> schemeEntry :
                    playerEntry.getValue().entrySet()) {
                schemes.add(BufferScheme.builder()
                        .characterId(playerEntry.getKey())
                        .name(schemeEntry.getKey())
                        .skillIds(schemeEntry.getValue())
                        .build());
            }
        }

        try {
            bufferSchemeRepository.replaceAll(schemes);
        } catch (DataAccessException e) {
            log.warn("Error while saving schemes. Players: {}.", schemesTable.size(), e);
        }
    }

    /**
     * Registers or replaces a scheme for the given player.
     * @param playerId
     * @param schemeName
     * @param skillIds
     */
    public void setScheme(int playerId, String schemeName, List<Integer> skillIds) {
        if (StringUtils.isBlank(schemeName)) {
            return;
        }

        final Map<String, List<Integer>> schemes = schemesTable.computeIfAbsent(
                playerId, key -> new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER));
        if ((schemes.size() >= SchemeBufferConfig.BUFFER_MAX_SCHEMES) && !schemes.containsKey(schemeName)) {
            return;
        }

        final List<Integer> safeList = (skillIds != null) ? List.copyOf(skillIds) : Collections.emptyList();
        schemes.put(schemeName, safeList);
    }

    /**
     * Deletes a scheme for the given player.
     * @param playerId
     * @param schemeName
     * @return true if removed.
     */
    public boolean deleteScheme(int playerId, String schemeName) {
        final Map<String, List<Integer>> schemes = schemesTable.get(playerId);
        if (schemes == null) {
            return false;
        }

        final boolean removed = schemes.remove(schemeName) != null;
        if (schemes.isEmpty()) {
            schemesTable.remove(playerId);
        }
        return removed;
    }

    /**
     * Adds a skill to a scheme (copy-on-write). No-op if scheme doesn't exist.
     * @param playerId
     * @param schemeName
     * @param skillId
     * @return true if added.
     */
    public boolean addSkillToScheme(int playerId, String schemeName, int skillId) {
        final Map<String, List<Integer>> schemes = schemesTable.get(playerId);
        if (schemes == null) {
            return false;
        }

        final List<Integer> current = schemes.get(schemeName);
        if (current == null) {
            return false;
        }

        if (current.contains(skillId)) {
            return false;
        }

        final List<Integer> updated = new ArrayList<>(current);
        updated.add(skillId);
        schemes.put(schemeName, Collections.unmodifiableList(updated));
        return true;
    }

    /**
     * Removes a skill from a scheme (copy-on-write). No-op if scheme doesn't exist.
     * @param playerId
     * @param schemeName
     * @param skillId
     * @return true if removed.
     */
    public boolean removeSkillFromScheme(int playerId, String schemeName, int skillId) {
        final Map<String, List<Integer>> schemes = schemesTable.get(playerId);
        if (schemes == null) {
            return false;
        }

        final List<Integer> current = schemes.get(schemeName);
        if (current == null) {
            return false;
        }

        if (!current.contains(skillId)) {
            return false;
        }

        final List<Integer> updated = new ArrayList<>(current);
        updated.remove(Integer.valueOf(skillId));
        schemes.put(schemeName, Collections.unmodifiableList(updated));
        return true;
    }

    /**
     * Returns the scheme map for a player (read-only view).
     * @param playerId
     * @return the schemes map or null.
     */
    public Map<String, List<Integer>> getPlayerSchemes(int playerId) {
        final Map<String, List<Integer>> schemes = schemesTable.get(playerId);
        return (schemes != null) ? Collections.unmodifiableMap(schemes) : null;
    }

    /**
     * Returns the skill ID list for a specific player scheme.
     * @param playerId
     * @param schemeName
     * @return the scheme skill list or an empty list.
     */
    public List<Integer> getScheme(int playerId, String schemeName) {
        final Map<String, List<Integer>> schemes = schemesTable.get(playerId);
        if ((schemes == null) || (schemes.get(schemeName) == null)) {
            return Collections.emptyList();
        }
        return schemes.get(schemeName);
    }

    /**
     * Returns the skill ID list for a category/type without copying.<br>
     * The returned list is immutable and safe to share across threads.
     * @param groupType
     * @return the category skill IDs.
     */
    public List<Integer> getSkillsIdsByType(String groupType) {
        return skillIdsByType.getOrDefault(groupType, Collections.emptyList());
    }

    /**
     * Returns available category/type names for scheme editing.<br>
     * Internal groups (MAGE_GROUP / FIGHTER_GROUP) are hidden from the edit UI.
     * @return the list of visible category names.
     */
    public List<String> getSkillTypes() {
        final List<String> skillTypes = new ArrayList<>(skillTypesOrder.size());
        for (String type : skillTypesOrder) {
            if (type.equalsIgnoreCase(TYPE_MAGE_GROUP) || type.equalsIgnoreCase(TYPE_FIGHTER_GROUP)) {
                continue;
            }
            skillTypes.add(type);
        }
        return skillTypes;
    }

    /**
     * Returns the global holder for a skill ID.
     * @param skillId
     * @return the global holder or null.
     */
    public BuffSkillHolder getAvailableBuff(int skillId) {
        return availableBuffs.get(skillId);
    }

    /**
     * Returns the category-aware holder for a skill ID.
     * @param groupType
     * @param skillId
     * @return the resolved holder or null.
     */
    public BuffSkillHolder getAvailableBuff(String groupType, int skillId) {
        final Map<Integer, BuffSkillHolder> holdersByType = availableBuffsByType.get(groupType);
        return (holdersByType != null)
                ? holdersByType.getOrDefault(skillId, availableBuffs.get(skillId))
                : availableBuffs.get(skillId);
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static SchemeBufferTable getInstance() {
        final SchemeBufferTable current = instance;
        if (current == null) {
            throw new IllegalStateException("SchemeBufferTable is used before the Spring context created it.");
        }
        return current;
    }

    private void loadAvailableBuffs() {
        int categoryCount = 0;
        int skillCount = 0;

        try {
            final Node rootNode = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new File(SKILLS_XML_PATH))
                    .getDocumentElement();
            for (Node categoryNode = (rootNode != null) ? rootNode.getFirstChild() : null;
                    categoryNode != null;
                    categoryNode = categoryNode.getNextSibling()) {
                if (!"category".equalsIgnoreCase(categoryNode.getNodeName())) {
                    continue;
                }

                final NamedNodeMap categoryAttributes = categoryNode.getAttributes();
                if (categoryAttributes == null) {
                    continue;
                }

                final Node typeNode = categoryAttributes.getNamedItem("type");
                if (typeNode == null) {
                    continue;
                }

                final String category = typeNode.getNodeValue();
                if (!skillIdsByType.containsKey(category)) {
                    skillIdsByType.put(category, new ArrayList<>());
                    availableBuffsByType.put(category, new LinkedHashMap<>());
                    skillTypesOrder.add(category);
                    categoryCount++;
                }

                for (Node buffNode = categoryNode.getFirstChild();
                        buffNode != null;
                        buffNode = buffNode.getNextSibling()) {
                    if (!"buff".equalsIgnoreCase(buffNode.getNodeName())) {
                        continue;
                    }

                    final NamedNodeMap attributes = buffNode.getAttributes();
                    if (attributes == null) {
                        continue;
                    }

                    final Node idNode = attributes.getNamedItem("id");
                    final Node levelNode = attributes.getNamedItem("level");
                    final Node priceNode = attributes.getNamedItem("price");
                    if ((idNode == null) || (levelNode == null) || (priceNode == null)) {
                        continue;
                    }

                    final int skillId = Integer.parseInt(idNode.getNodeValue());
                    final int level = Integer.parseInt(levelNode.getNodeValue());
                    final int price = Integer.parseInt(priceNode.getNodeValue());
                    final Node descNode = attributes.getNamedItem("desc");
                    final String description = (descNode != null) ? descNode.getNodeValue() : "";

                    final List<Integer> skillIds = skillIdsByType.get(category);
                    if (!skillIds.contains(skillId)) {
                        skillIds.add(skillId);
                    }

                    final BuffSkillHolder holder = BuffSkillHolder.builder()
                            .id(skillId)
                            .level(level)
                            .price(price)
                            .type(category)
                            .description(description)
                            .build();
                    availableBuffsByType.get(category).put(skillId, holder);

                    final BuffSkillHolder existing = availableBuffs.get(skillId);
                    if ((existing == null) || (holder.level() > existing.level())) {
                        availableBuffs.put(skillId, holder);
                    }

                    skillCount++;
                }
            }

            for (Entry<String, List<Integer>> entry : skillIdsByType.entrySet()) {
                entry.setValue(Collections.unmodifiableList(entry.getValue()));
            }

            log.info("Loaded {} categories and {} entries.", categoryCount, skillCount);
        } catch (Exception e) {
            log.warn("Failed to load skills XML: {}.", SKILLS_XML_PATH, e);
        }
    }

    private void loadPlayerSchemes() {
        try {
            final List<BufferScheme> schemes = bufferSchemeRepository.findAll();
            for (BufferScheme scheme : schemes) {
                final List<Integer> schemeSkillIds =
                        new ArrayList<>(scheme.skillIds().size());
                for (int skillId : scheme.skillIds()) {
                    if (availableBuffs.containsKey(skillId)) {
                        schemeSkillIds.add(skillId);
                    }
                }

                setScheme(scheme.characterId(), scheme.name(), schemeSkillIds);
            }

            log.info("Loaded {} scheme entries from database.", schemes.size());
        } catch (DataAccessException e) {
            log.warn("Failed to load buff schemes from database.", e);
        }
    }
}
