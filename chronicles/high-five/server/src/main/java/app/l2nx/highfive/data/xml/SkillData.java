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
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.DocumentBase;
import app.l2nx.highfive.util.DocumentSkill;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * The {@code SkillData} class is responsible for parsing, loading, and managing skill data within the game server.
 */
@Slf4j
public class SkillData {

    private final Map<Integer, Skill> skillsByHash = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> maxSkillLevels = new ConcurrentHashMap<>();
    private final Set<Integer> enchantable = ConcurrentHashMap.newKeySet();
    private final List<File> skillFiles = new ArrayList<>();

    protected SkillData() {
        processDirectory("data/stats/skills", skillFiles);
        if (GeneralConfig.CUSTOM_SKILLS_LOAD) {
            processDirectory("data/stats/skills/custom", skillFiles);
        }

        load();
    }

    private void processDirectory(String dirName, List<File> list) {
        final File dir = new File(ServerConfig.DATAPACK_ROOT, dirName);
        if (!dir.exists()) {
            log.warn("Directory {} does not exist.", dir.getAbsolutePath());
            return;
        }

        final File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
                    list.add(file);
                }
            }
        }
    }

    private void load() {
        final Map<Integer, Skill> temp = new ConcurrentHashMap<>();
        loadAllSkills(temp);

        skillsByHash.clear();
        skillsByHash.putAll(temp);

        maxSkillLevels.clear();
        enchantable.clear();
        for (Skill skill : skillsByHash.values()) {
            final int skillId = skill.getId();
            final int skillLevel = skill.getLevel();
            if (skillLevel > 99) {
                if (!enchantable.contains(skillId)) {
                    enchantable.add(skillId);
                }
                continue;
            }

            // Only non-enchanted skills.
            final int maxLevel = getMaxLevel(skillId);
            if (skillLevel > maxLevel) {
                maxSkillLevels.put(skillId, skillLevel);
            }
        }
    }

    public List<Skill> loadSkills(File file) {
        if (file == null) {
            log.warn("Skill file not found.");
            return null;
        }

        final DocumentSkill doc = new DocumentSkill(file);
        doc.parse();
        return doc.getSkills();
    }

    public void loadAllSkills(Map<Integer, Skill> allSkills) {
        // In file order, so a skill defined twice ends as the later file has it.
        final List<Skill> loaded = new ArrayList<>();
        IXmlReader.parseAll(
                skillFiles,
                DocumentBase::read,
                (file, document) -> document.ifPresent(read -> {
                    final DocumentSkill skills = new DocumentSkill(file);
                    skills.parse(read);
                    loaded.addAll(skills.getSkills());
                }));
        for (Skill skill : loaded) {
            allSkills.put(SkillData.getSkillHashCode(skill), skill);
        }

        log.info("Loaded {} Skill templates from XML files.", loaded.size());
    }

    public void reload() {
        load();

        // Reload Skill Tree as well.
        SkillTreeData.getInstance().load();
    }

    /**
     * Generates a unique hash code for a skill based on its ID and level.
     * @param skill The {@link Skill} instance to be hashed.
     * @return A unique hash code combining skill ID and level.
     */
    public static int getSkillHashCode(Skill skill) {
        return getSkillHashCode(skill.getId(), skill.getLevel());
    }

    /**
     * Generates a unique hash code for a skill based on its ID and level.
     * @param skillId The ID of the skill.
     * @param skillLevel The level of the skill.
     * @return A unique hash code combining skill ID and level.
     */
    public static int getSkillHashCode(int skillId, int skillLevel) {
        return (skillId * 1021) + skillLevel;
    }

    /**
     * Retrieves a skill based on its ID and level.
     * @param skillId The ID of the skill.
     * @param level The level of the skill.
     * @return The {@link Skill} object if found, or null if not found.
     */
    public Skill getSkill(int skillId, int level) {
        final Skill result = skillsByHash.get(getSkillHashCode(skillId, level));
        if (result != null) {
            return result;
        }

        // Skill/level not found, fix for transformation scripts.
        final int maxLevel = getMaxLevel(skillId);

        // Requested level too high.
        if ((maxLevel > 0) && (level > maxLevel)) {
            log.warn(
                    "Call to unexisting skill level id: {} requested level: {} max level: {}.{}{}",
                    skillId,
                    level,
                    maxLevel,
                    System.lineSeparator(),
                    ExceptionUtils.getStackTrace(new Exception()));
            return skillsByHash.get(getSkillHashCode(skillId, maxLevel));
        }

        log.warn(
                "No skill info found for skill id {} and skill level {}.{}{}",
                skillId,
                level,
                System.lineSeparator(),
                ExceptionUtils.getStackTrace(new Exception()));
        return null;
    }

    /**
     * Retrieves the maximum level available for a skill based on its ID.
     * @param skillId The ID of the skill.
     * @return The maximum level for the specified skill ID, or 0 if the skill ID is not found.
     */
    public int getMaxLevel(int skillId) {
        final Integer maxLevel = maxSkillLevels.get(skillId);
        return maxLevel != null ? maxLevel : 0;
    }

    /**
     * Verifies if the given skill ID correspond to an enchantable skill.
     * @param skillId the skill ID
     * @return {@code true} if the skill is enchantable, {@code false} otherwise
     */
    public boolean isEnchantable(int skillId) {
        return enchantable.contains(skillId);
    }

    /**
     * Retrieves an array of siege-related skills based on certain conditions.
     * <p>
     * If {@code addNoble} is true, the list will include the Advanced Headquarters skill. If {@code hasCastle} is true, additional castle-specific skills such as Outpost Construction and Outpost Demolition are added.
     * </p>
     * @param addNoble Whether to include Advanced Headquarters skill.
     * @param hasCastle Whether to include castle-related skills.
     * @return A {@link List} of siege-related {@link Skill} objects.
     */
    public Skill[] getSiegeSkills(boolean addNoble, boolean hasCastle) {
        final Skill[] result = new Skill[2 + (addNoble ? 1 : 0) + (hasCastle ? 2 : 0)];
        int i = 0;
        result[i++] = skillsByHash.get(getSkillHashCode(246, 1));
        result[i++] = skillsByHash.get(getSkillHashCode(247, 1));
        if (addNoble) {
            result[i++] = skillsByHash.get(getSkillHashCode(326, 1));
        }

        if (hasCastle) {
            result[i++] = skillsByHash.get(getSkillHashCode(844, 1));
            result[i++] = skillsByHash.get(getSkillHashCode(845, 1));
        }

        return result;
    }

    public static SkillData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final SkillData INSTANCE = new SkillData();
    }
}
