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

import app.l2nx.highfive.entity.item.enums.ItemSkillType;
import app.l2nx.highfive.mechanics.options.OptionSkillHolder;
import app.l2nx.highfive.mechanics.options.Options;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author UnAfraid
 */
@Slf4j
public class OptionData implements IXmlReader {

    private static Options[] options;
    private static Map<Integer, Options> optionMap = new ConcurrentHashMap<>();

    protected OptionData() {
        load();
    }

    @Override
    public synchronized void load() {
        parseDatapackDirectory("data/stats/augmentation/options", false);

        options = new Options[Collections.max(optionMap.keySet()) + 1];
        for (Entry<Integer, Options> option : optionMap.entrySet()) {
            options[option.getKey()] = option.getValue();
        }

        log.info("Loaded {} options.", optionMap.size());
        optionMap.clear();
    }

    @Override
    public void parseDocument(Document document, File file) {
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("option".equalsIgnoreCase(d.getNodeName())) {
                        final int id = parseInteger(d.getAttributes(), "id");
                        final Options option = new Options(id);
                        for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling()) {
                            switch (cd.getNodeName()) {
                                case "stats": {
                                    for (Node fd = cd.getFirstChild(); fd != null; fd = fd.getNextSibling()) {
                                        switch (fd.getNodeName()) {
                                            case "add":
                                            case "sub":
                                            case "mul":
                                            case "div":
                                            case "set":
                                            case "share":
                                            case "enchant":
                                            case "enchanthp": {
                                                parseFuncs(fd.getAttributes(), fd.getNodeName(), option);
                                            }
                                        }
                                    }
                                    break;
                                }
                                case "active_skill": {
                                    final int skillId = parseInteger(cd.getAttributes(), "id");
                                    final int skillLevel = parseInteger(cd.getAttributes(), "level");
                                    final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
                                    if (skill != null) {
                                        option.setActiveSkill(skill);
                                    } else {
                                        log.info(
                                                "Could not find skill {}({}) used by option {}.",
                                                skillId,
                                                skillLevel,
                                                id);
                                    }
                                    break;
                                }
                                case "passive_skill": {
                                    final int skillId = parseInteger(cd.getAttributes(), "id");
                                    final int skillLevel = parseInteger(cd.getAttributes(), "level");
                                    final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
                                    if (skill != null) {
                                        option.setPassiveSkill(skill);
                                    } else {
                                        log.info(
                                                "Could not find skill {}({}) used by option {}.",
                                                skillId,
                                                skillLevel,
                                                id);
                                    }
                                    break;
                                }
                                case "attack_skill": {
                                    final int skillId = parseInteger(cd.getAttributes(), "id");
                                    final int skillLevel = parseInteger(cd.getAttributes(), "level");
                                    final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
                                    if (skill != null) {
                                        option.addActivationSkill(OptionSkillHolder.builder()
                                                .skill(skill)
                                                .chance(parseDouble(cd.getAttributes(), "chance"))
                                                .skillType(ItemSkillType.ON_PHYSICAL_HIT)
                                                .build());
                                    } else {
                                        log.info(
                                                "Could not find skill {}({}) used by option {}.",
                                                skillId,
                                                skillLevel,
                                                id);
                                    }
                                    break;
                                }
                                case "magic_skill": {
                                    final int skillId = parseInteger(cd.getAttributes(), "id");
                                    final int skillLevel = parseInteger(cd.getAttributes(), "level");
                                    final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
                                    if (skill != null) {
                                        option.addActivationSkill(OptionSkillHolder.builder()
                                                .skill(skill)
                                                .chance(parseDouble(cd.getAttributes(), "chance"))
                                                .skillType(ItemSkillType.ON_MAGIC_SKILL_CAST)
                                                .build());
                                    } else {
                                        log.info(
                                                "Could not find skill {}({}) used by option {}.",
                                                skillId,
                                                skillLevel,
                                                id);
                                    }
                                    break;
                                }
                                case "critical_skill": {
                                    final int skillId = parseInteger(cd.getAttributes(), "id");
                                    final int skillLevel = parseInteger(cd.getAttributes(), "level");
                                    final Skill skill = SkillData.getInstance().getSkill(skillId, skillLevel);
                                    if (skill != null) {
                                        option.addActivationSkill(OptionSkillHolder.builder()
                                                .skill(skill)
                                                .chance(parseDouble(cd.getAttributes(), "chance"))
                                                .skillType(ItemSkillType.ON_NORMAL_ATTACK_CRITICAL_HIT)
                                                .build());
                                    } else {
                                        log.info(
                                                "Could not find skill {}({}) used by option {}.",
                                                skillId,
                                                skillLevel,
                                                id);
                                    }
                                    break;
                                }
                            }
                        }

                        optionMap.put(option.getId(), option);
                    }
                }
            }
        }
    }

    private void parseFuncs(NamedNodeMap attrs, String functionName, Options op) {
        final Stat stat = Stat.valueOfXml(parseString(attrs, "stat"));
        final double val = parseDouble(attrs, "val");
        int order = -1;
        final Node orderNode = attrs.getNamedItem("order");
        if (orderNode != null) {
            order = Integer.parseInt(orderNode.getNodeValue());
        }

        op.addFunc(new FuncTemplate(null, null, functionName, order, stat, val));
    }

    public Options getOptions(int id) {
        if (options.length > id) {
            return options[id];
        }

        return null;
    }

    public static OptionData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final OptionData INSTANCE = new OptionData();
    }
}
