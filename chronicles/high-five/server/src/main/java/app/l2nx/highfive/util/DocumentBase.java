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
package app.l2nx.highfive.util;

import app.l2nx.highfive.data.enums.CategoryType;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.PlayerState;
import app.l2nx.highfive.entity.actor.templates.DoorTemplate;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.type.ArmorType;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.conditions.ConditionCategoryType;
import app.l2nx.highfive.mechanics.conditions.ConditionChangeWeapon;
import app.l2nx.highfive.mechanics.conditions.ConditionGameChance;
import app.l2nx.highfive.mechanics.conditions.ConditionGameTime;
import app.l2nx.highfive.mechanics.conditions.ConditionLogicAnd;
import app.l2nx.highfive.mechanics.conditions.ConditionLogicNot;
import app.l2nx.highfive.mechanics.conditions.ConditionLogicOr;
import app.l2nx.highfive.mechanics.conditions.ConditionMinDistance;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerActiveEffectId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerActiveSkillId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerAgathionId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCallPc;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanCreateBase;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanCreateOutpost;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanEscape;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanRefuelAirship;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanResurrect;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanSummon;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanSummonSiegeGolem;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanSweep;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanTakeCastle;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanTakeFort;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanTransform;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCanUntransform;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCharges;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCheckAbnormal;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerClassIdRestriction;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCloakStatus;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerCp;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerFlyMounted;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerGrade;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerHasCastle;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerHasClanHall;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerHasFort;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerHasPet;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerHp;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerInsideZoneId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerInstanceId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerInvSize;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerIsClanLeader;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerIsHero;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerIsPvpFlagged;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerIsRidingStrider;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerLandingZone;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerLevel;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerLevelRange;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerMp;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerPkCount;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerPledgeClass;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerRace;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerRangeFromNpc;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerServitorNpcId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerSex;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerSiegeSide;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerSouls;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerState;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerSubclass;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerTransformationId;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerVehicleMounted;
import app.l2nx.highfive.mechanics.conditions.ConditionPlayerWeight;
import app.l2nx.highfive.mechanics.conditions.ConditionSiegeZone;
import app.l2nx.highfive.mechanics.conditions.ConditionSlotItemId;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetAbnormal;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetActiveEffectId;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetActiveSkillId;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetAggro;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetClassIdRestriction;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetInvSize;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetLevel;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetLevelRange;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetMyPartyExceptMe;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetNpcId;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetNpcType;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetPlayable;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetRace;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetUsesWeaponKind;
import app.l2nx.highfive.mechanics.conditions.ConditionTargetWeight;
import app.l2nx.highfive.mechanics.conditions.ConditionUsingItemType;
import app.l2nx.highfive.mechanics.conditions.ConditionUsingSkill;
import app.l2nx.highfive.mechanics.conditions.ConditionUsingSlotType;
import app.l2nx.highfive.mechanics.conditions.ConditionWithSkill;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.AbnormalType;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author mkizub, Mobius
 */
@Slf4j
public abstract class DocumentBase {

    private static final Set<String> IGNORED_ELEMENTS =
            Set.of("param", "conditions", "effect", "add", "sub", "mul", "div", "set", "share", "enchant", "enchanthp");

    private final File file;
    protected final Map<String, String[]> tables = new HashMap<>();

    protected DocumentBase(File pFile) {
        file = pFile;
    }

    public Document parse() {
        final Document document = read(file).orElse(null);
        if (document != null) {
            parse(document);
        }

        return document;
    }

    public void parse(Document document) {
        try {
            parseDocument(document);
        } catch (Exception e) {
            log.error("Error loading file {}", file, e);
        }
    }

    /** Reads the XML only, so it may run on the parse pool; building the templates registers enchant routes. */
    public static Optional<Document> read(File file) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            factory.setIgnoringComments(true);
            return Optional.of(factory.newDocumentBuilder().parse(file));
        } catch (Exception e) {
            log.error("Error loading file {}", file, e);
            return Optional.empty();
        }
    }

    protected abstract void parseDocument(Document document);

    protected abstract StatSet getStatSet();

    protected abstract String getTableValue(String name);

    protected abstract String getTableValue(String name, int idx);

    protected void resetTable() {
        tables.clear();
    }

    protected void setTable(String name, String[] table) {
        tables.put(name, table);
    }

    protected void parseTemplate(Node node, Object template) {
        parseTemplate(node, template, null);
    }

    protected void parseTemplate(Node node, Object template, EffectScope effectScope) {
        Condition condition = null;
        Node n = node.getFirstChild();
        if (n == null) {
            return;
        }

        if ("conditions".equalsIgnoreCase(n.getNodeName())) {
            condition = parseCondition(n.getFirstChild(), template);
            final Node msg = n.getAttributes().getNamedItem("msg");
            final Node msgId = n.getAttributes().getNamedItem("msgId");
            if ((condition != null) && (msg != null)) {
                condition.setMessage(msg.getNodeValue());
            } else if ((condition != null) && (msgId != null)) {
                condition.setMessageId(Integer.decode(getValue(msgId.getNodeValue(), null)));
                final Node addName = n.getAttributes().getNamedItem("addName");
                if ((addName != null) && (Integer.decode(getValue(msgId.getNodeValue(), null)) > 0)) {
                    condition.addName();
                }
            }

            n = n.getNextSibling();
        }

        for (; n != null; n = n.getNextSibling()) {
            final String name = n.getNodeName().toLowerCase();

            switch (name) {
                case "effect": {
                    if (template instanceof AbstractEffect) {
                        throw new RuntimeException("Nested effects");
                    }

                    attachEffect(n, template, condition, effectScope);
                    break;
                }
                case "add":
                case "sub":
                case "mul":
                case "div":
                case "set":
                case "share":
                case "enchant":
                case "enchanthp": {
                    // Check if we need to handle an alternative format for the "enchant" or "enchanthp" tag.
                    if ((n.getAttributes().getNamedItem("stat") == null)
                            && (n.getAttributes().getNamedItem("val") == null)) {
                        // This is the alternative format with element-based stats.
                        processAlternativeFuncFormat(n, template, name, condition);
                    } else {
                        // Standard attribute-based format.
                        attachFunc(n, template, name, condition);
                    }
                    break;
                }
            }
        }
    }

    protected void attachFunc(Node n, Object template, String functionName, Condition attachCond) {
        final NamedNodeMap attrs = n.getAttributes();
        final Node statNode = attrs.getNamedItem("stat");
        if (statNode == null) {
            return;
        }

        final Stat stat = Stat.valueOfXml(statNode.getNodeValue());
        int order = -1;
        final Node orderNode = attrs.getNamedItem("order");
        if (orderNode != null) {
            order = Integer.parseInt(orderNode.getNodeValue());
        }

        // Hybrid logic for capturing the value.
        String valueString = null;
        final Node valAttr = attrs.getNamedItem("val");
        if (valAttr != null) {
            valueString = valAttr.getNodeValue();
        } else {
            for (Node child = n.getFirstChild(); child != null; child = child.getNextSibling()) {
                if ((child.getNodeType() == Node.ELEMENT_NODE) && "value".equalsIgnoreCase(child.getNodeName())) {
                    valueString = child.getTextContent().trim();
                    break;
                }
            }

            if ((valueString == null) || valueString.isEmpty()) {
                valueString = n.getTextContent().trim();
            }
        }

        double value = 0;
        if ((valueString != null) && !valueString.isEmpty()) {
            value = valueString.charAt(0) == '#'
                    ? Double.parseDouble(getTableValue(valueString))
                    : Double.parseDouble(valueString);
        }

        final Condition applyCond = parseCondition(n.getFirstChild(), template);
        final FuncTemplate ft = new FuncTemplate(attachCond, applyCond, functionName, order, stat, value);
        if (template instanceof ItemTemplate) {
            ((ItemTemplate) template).attach(ft);
        } else if (template instanceof AbstractEffect) {
            ((AbstractEffect) template).attach(ft);
        }
    }

    /**
     * Process the alternative stats format for "share", "enchant", and "enchanthp" tags where stats are defined as element nodes.<br>
     * For example: &lt;enchant&gt;&lt;pDef&gt;0&lt;/pDef&gt;&lt;/enchant&gt; instead of &lt;enchant stat="pDef" val="0" /&gt;
     * @param n the node containing stat elements
     * @param template the template to attach stats to
     * @param functionName the function name ("share", "enchant" or "enchanthp")
     * @param attachCond the condition for attachment
     */
    protected void processAlternativeFuncFormat(Node n, Object template, String functionName, Condition attachCond) {
        // Process each child element as a stat.
        Node statNode = n.getFirstChild();
        while (statNode != null) {
            // Skip non-element nodes (like whitespace text nodes).
            if (statNode.getNodeType() == Node.ELEMENT_NODE) {
                // The element name is the stat name.
                String statName = statNode.getNodeName();

                // The element content is the value.
                String valueString = statNode.getTextContent().trim();

                // Convert the stat name to a Stat enum value.
                try {
                    final Stat stat = Stat.valueOfXml(statName);

                    // Parse the value.
                    double value;
                    if (!valueString.isEmpty() && (valueString.charAt(0) == '#')) {
                        value = Double.parseDouble(getTableValue(valueString));
                    } else {
                        value = Double.parseDouble(valueString);
                    }

                    // Use default order (-1).
                    final int order = -1;

                    // Parse any conditions that might be inside the stat element.
                    final Condition applyCond = parseCondition(statNode.getFirstChild(), template);

                    // Create and attach the function template with the original function name (enchant or enchanthp).
                    final FuncTemplate ft = new FuncTemplate(attachCond, applyCond, functionName, order, stat, value);
                    if (template instanceof ItemTemplate) {
                        ((ItemTemplate) template).attach(ft);
                    } else if (template instanceof AbstractEffect) {
                        ((AbstractEffect) template).attach(ft);
                    } else {
                        throw new RuntimeException("Attaching stat to a non-effect template!!!");
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid numeric value: '{}' for stat: {}: {}", valueString, statName, e.getMessage());
                } catch (IllegalArgumentException e) {
                    log.warn(
                            "Unknown stat name: {} in alternative {} format: {}",
                            statName,
                            functionName,
                            e.getMessage());
                } catch (Exception e) {
                    log.warn(
                            "Error processing alternative {} format for {}: {}",
                            functionName,
                            statName,
                            e.getMessage());
                }
            }

            // Move to the next stat element.
            statNode = statNode.getNextSibling();
        }
    }

    protected void attachEffect(Node n, Object template, Condition attachCond) {
        attachEffect(n, template, attachCond, null);
    }

    protected void attachEffect(Node n, Object template, Condition attachCond, EffectScope effectScope) {
        final NamedNodeMap attrs = n.getAttributes();
        final StatSet set = new StatSet();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node att = attrs.item(i);
            set.set(att.getNodeName(), getValue(att.getNodeValue(), template));
        }

        final StatSet parameters = parseParameters(n.getFirstChild(), template);
        final Condition applyCond = parseCondition(n.getFirstChild(), template);
        if (template instanceof ItemTemplate) {
            set.set("id", ((ItemTemplate) template).getId());
        } else if (template instanceof NpcTemplate) {
            set.set("id", ((NpcTemplate) template).getId());
        } else if (template instanceof DoorTemplate) {
            set.set("id", ((DoorTemplate) template).getId());
        }

        // Process <items> for RestorationRandom effects.
        if ("RestorationRandom".equals(set.getString("name"))) {
            Node itemsNode = null;
            for (Node child = n.getFirstChild(); child != null; child = child.getNextSibling()) {
                if ((child.getNodeType() == Node.ELEMENT_NODE) && "items".equals(child.getNodeName())) {
                    itemsNode = child;
                    break;
                }
            }

            if (itemsNode != null) {
                // Find first element child to determine format.
                Node firstChild = itemsNode.getFirstChild();
                while ((firstChild != null) && (firstChild.getNodeType() != Node.ELEMENT_NODE)) {
                    firstChild = firstChild.getNextSibling();
                }

                // Check if we have level-based format.
                if ((firstChild != null) && "value".equals(firstChild.getNodeName())) {
                    // Apparently this can only be a Skill.
                    final Skill skill = (Skill) template;

                    // Complex format with multiple level support.
                    for (Node valueNode = itemsNode.getFirstChild();
                            valueNode != null;
                            valueNode = valueNode.getNextSibling()) {
                        if ((valueNode.getNodeType() == Node.ELEMENT_NODE) && "value".equals(valueNode.getNodeName())) {
                            final NamedNodeMap valueAttrs = valueNode.getAttributes();
                            final Node levelAttr = valueAttrs.getNamedItem("level");
                            final int level = levelAttr != null ? Integer.parseInt(levelAttr.getNodeValue()) : 1;
                            if (skill.getLevel() != level) {
                                continue;
                            }

                            // Process all item nodes (with chance attribute).
                            final List<StatSet> items = new ArrayList<>();
                            for (Node itemNode = valueNode.getFirstChild();
                                    itemNode != null;
                                    itemNode = itemNode.getNextSibling()) {
                                if ((itemNode.getNodeType() == Node.ELEMENT_NODE)
                                        && "item".equals(itemNode.getNodeName())) {
                                    parseItemNode(itemNode, items);
                                }
                            }

                            parameters.set("items", items);
                        }
                    }
                } else // Simple format (single level).
                {
                    // Process all item nodes (with chance attribute).
                    final List<StatSet> items = new ArrayList<>();
                    for (Node itemNode = itemsNode.getFirstChild();
                            itemNode != null;
                            itemNode = itemNode.getNextSibling()) {
                        if ((itemNode.getNodeType() == Node.ELEMENT_NODE) && "item".equals(itemNode.getNodeName())) {
                            parseItemNode(itemNode, items);
                        }
                    }

                    parameters.set("items", items);
                }
            }
        }

        final AbstractEffect effect = AbstractEffect.createEffect(attachCond, applyCond, set, parameters);
        parseTemplate(n, effect);

        if (template instanceof ItemTemplate) {
            log.error("Item {} with effects!!!", template);
        } else if (template instanceof Skill) {
            final Skill skill = (Skill) template;
            if (effectScope != null) {
                skill.addEffect(effectScope, effect);
            } else if (skill.isPassive()) {
                skill.addEffect(EffectScope.PASSIVE, effect);
            } else {
                skill.addEffect(EffectScope.GENERAL, effect);
            }
        }
    }

    /**
     * Parses an item node from RestorationRandom effect XML and adds it to the items list.
     * @param itemNode the node containing item information
     * @param items the list to add parsed item data to
     */
    private void parseItemNode(Node itemNode, List<StatSet> items) {
        final StatSet group = new StatSet();
        final NamedNodeMap attrs = itemNode.getAttributes();
        final Node chanceNode = attrs.getNamedItem("chance");
        float chance = chanceNode != null ? Float.parseFloat(chanceNode.getNodeValue()) : 100f;
        group.set(".chance", chance);

        // Process inner item nodes.
        final List<StatSet> innerItems = new ArrayList<>();
        for (Node innerNode = itemNode.getFirstChild(); innerNode != null; innerNode = innerNode.getNextSibling()) {
            if ((innerNode.getNodeType() == Node.ELEMENT_NODE) && "item".equals(innerNode.getNodeName())) {
                final NamedNodeMap itemAttrs = innerNode.getAttributes();

                // Get item ID (required).
                final Node idNode = itemAttrs.getNamedItem("id");
                if (idNode == null) {
                    log.warn("Missing item ID in RestorationRandom effect");
                    continue;
                }

                final int id = Integer.parseInt(idNode.getNodeValue());
                if (id <= 0) {
                    log.warn("Invalid item ID found in RestorationRandom effect: {}", id);
                    continue;
                }

                // Get other attributes with defaults.
                final Node countNode = itemAttrs.getNamedItem("count");
                final Node minEnchantNode = itemAttrs.getNamedItem("minEnchant");
                final Node maxEnchantNode = itemAttrs.getNamedItem("maxEnchant");

                final StatSet itemData = new StatSet();
                itemData.set(".id", id);
                itemData.set(".count", countNode != null ? Long.parseLong(countNode.getNodeValue()) : 1L);
                itemData.set(
                        ".minEnchant", minEnchantNode != null ? Integer.parseInt(minEnchantNode.getNodeValue()) : 0);
                itemData.set(
                        ".maxEnchant", maxEnchantNode != null ? Integer.parseInt(maxEnchantNode.getNodeValue()) : 0);

                innerItems.add(itemData);
            }
        }

        group.set(".", innerItems);
        items.add(group);
    }

    /**
     * Parse effect's parameters.
     * @param node the node to start the parsing
     * @param template the effect template
     * @return the list of parameters if any, {@code null} otherwise
     */
    private StatSet parseParameters(Node node, Object template) {
        StatSet parameters = null;
        Node n;

        // First pass: Process <param> tags (traditional format).
        n = node;
        while (n != null) {
            // Parse all parameters.
            if ((n.getNodeType() == Node.ELEMENT_NODE) && "param".equals(n.getNodeName())) {
                if (parameters == null) {
                    parameters = new StatSet();
                }

                final NamedNodeMap params = n.getAttributes();
                for (int i = 0; i < params.getLength(); i++) {
                    final Node att = params.item(i);
                    parameters.set(att.getNodeName(), getValue(att.getNodeValue(), template));
                }
            }

            n = n.getNextSibling();
        }

        // Second pass: Process direct named elements as parameters.
        n = node;

        // Define elements to ignore (these have special handling elsewhere).
        while (n != null) {
            if ((n.getNodeType() == Node.ELEMENT_NODE)
                    && !IGNORED_ELEMENTS.contains(n.getNodeName().toLowerCase())) {
                if (parameters == null) {
                    parameters = new StatSet();
                }

                // Extract parameter name (element name) and value (element content).
                final String name = n.getNodeName();

                // Check if this element contains level-specific values.
                if (hasLevelSpecificValues(n)) {
                    // Parse level-specific values into a map.
                    final Map<Integer, String> levelValues = parseLevelSpecificValues(n, template);

                    // Resolve to current level's value if template is a Skill.
                    if (template instanceof Skill) {
                        final Skill skill = (Skill) template;
                        final String resolvedValue = levelValues.get(skill.getLevel());
                        if (resolvedValue != null) {
                            parameters.set(name, resolvedValue);
                        }
                    } else if (template instanceof Integer) {
                        // Support for Integer template (level-based parsing).
                        final String resolvedValue = levelValues.get(((Integer) template).intValue());
                        if (resolvedValue != null) {
                            parameters.set(name, resolvedValue);
                        }
                    } else {
                        // If no level info, store the entire map (for backward compatibility).
                        parameters.set(name, levelValues);
                    }
                } else if (hasOnlyTextContent(n)) {
                    // Single value for all levels (no child elements, just text).
                    final String value = n.getTextContent().trim();
                    parameters.set(name, getValue(value, template));
                }
                // else: has other child elements, skip it (not a simple parameter).
            }

            n = n.getNextSibling();
        }

        return parameters == null ? StatSet.EMPTY_STATSET : parameters;
    }

    protected Condition parseCondition(Node node, Object template) {
        Node n = node;

        // It skips over text nodes and the <value> tag to focus on the actual condition.
        while (n != null) {
            if ((n.getNodeType() == Node.ELEMENT_NODE) && !"value".equalsIgnoreCase(n.getNodeName())) {
                break;
            }

            n = n.getNextSibling();
        }

        if (n == null) {
            return null;
        }

        switch (n.getNodeName().toLowerCase()) {
            case "and": {
                return parseLogicAnd(n, template);
            }
            case "or": {
                return parseLogicOr(n, template);
            }
            case "not": {
                return parseLogicNot(n, template);
            }
            case "player": {
                return parsePlayerCondition(n, template);
            }
            case "target": {
                return parseTargetCondition(n, template);
            }
            case "using": {
                return parseUsingCondition(n);
            }
            case "game": {
                return parseGameCondition(n);
            }
        }

        return null;
    }

    protected Condition parseLogicAnd(Node node, Object template) {
        final ConditionLogicAnd cond = new ConditionLogicAnd();
        Node n = node;
        for (n = n.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                cond.add(parseCondition(n, template));
            }
        }

        if ((cond.conditions == null) || (cond.conditions.length == 0)) {
            log.error("Empty <and> condition in {}", file);
        }

        return cond;
    }

    protected Condition parseLogicOr(Node node, Object template) {
        final ConditionLogicOr cond = new ConditionLogicOr();
        Node n = node;
        for (n = n.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                cond.add(parseCondition(n, template));
            }
        }

        if ((cond.conditions == null) || (cond.conditions.length == 0)) {
            log.error("Empty <or> condition in {}", file);
        }

        return cond;
    }

    protected Condition parseLogicNot(Node node, Object template) {
        Node n = node;
        for (n = n.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                return new ConditionLogicNot(parseCondition(n, template));
            }
        }

        log.error("Empty <not> condition in {}", file);
        return null;
    }

    protected Condition parsePlayerCondition(Node n, Object template) {
        Condition cond = null;
        final NamedNodeMap attrs = n.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node a = attrs.item(i);
            switch (a.getNodeName().toLowerCase()) {
                case "races": {
                    final String[] racesVal = a.getNodeValue().split(",");
                    final Set<Race> races = EnumSet.noneOf(Race.class);
                    for (int r = 0; r < racesVal.length; r++) {
                        if (racesVal[r] != null) {
                            races.add(Race.valueOf(racesVal[r]));
                        }
                    }

                    cond = joinAnd(cond, new ConditionPlayerRace(races));
                    break;
                }
                case "level": {
                    final int lvl = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerLevel(lvl));
                    break;
                }
                case "levelrange": {
                    final String[] range = getValue(a.getNodeValue(), template).split(";");
                    if (range.length == 2) {
                        final int[] lvlRange = new int[2];
                        lvlRange[0] = Integer.decode(
                                getValue(a.getNodeValue(), template).split(";")[0]);
                        lvlRange[1] = Integer.decode(
                                getValue(a.getNodeValue(), template).split(";")[1]);
                        cond = joinAnd(cond, new ConditionPlayerLevelRange(lvlRange));
                    }
                    break;
                }
                case "resting": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.RESTING, val));
                    break;
                }
                case "flying": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.FLYING, val));
                    break;
                }
                case "moving": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.MOVING, val));
                    break;
                }
                case "running": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.RUNNING, val));
                    break;
                }
                case "standing": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.STANDING, val));
                    break;
                }
                case "behind": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.BEHIND, val));
                    break;
                }
                case "front": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.FRONT, val));
                    break;
                }
                case "chaotic": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.CHAOTIC, val));
                    break;
                }
                case "olympiad": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerState(PlayerState.OLYMPIAD, val));
                    break;
                }
                case "ishero": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerIsHero(val));
                    break;
                }
                case "ispvpflagged": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerIsPvpFlagged(val));
                    break;
                }
                case "transformationid": {
                    final int id = Integer.parseInt(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerTransformationId(id));
                    break;
                }
                case "isridingstrider": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerIsRidingStrider(val));
                    break;
                }
                case "hp": {
                    final int hp = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerHp(hp));
                    break;
                }
                case "mp": {
                    final int mp = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerMp(mp));
                    break;
                }
                case "cp": {
                    final int cp = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerCp(cp));
                    break;
                }
                case "grade": {
                    final int expIndex = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerGrade(expIndex));
                    break;
                }
                case "pkcount": {
                    final int expIndex = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerPkCount(expIndex));
                    break;
                }
                case "siegezone": {
                    final int value = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionSiegeZone(value, true));
                    break;
                }
                case "siegeside": {
                    final int value = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerSiegeSide(value));
                    break;
                }
                case "charges": {
                    final int value = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerCharges(value));
                    break;
                }
                case "souls": {
                    final int value = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerSouls(value));
                    break;
                }
                case "weight": {
                    final int weight = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerWeight(weight));
                    break;
                }
                case "invsize": {
                    final int size = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerInvSize(size));
                    break;
                }
                case "isclanleader": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerIsClanLeader(val));
                    break;
                }
                case "pledgeclass": {
                    final int pledgeClass = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerPledgeClass(pledgeClass));
                    break;
                }
                case "clanhall": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final List<Integer> array = new ArrayList<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        array.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionPlayerHasClanHall(array));
                    break;
                }
                case "fort": {
                    final int fort = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerHasFort(fort));
                    break;
                }
                case "castle": {
                    final int castle = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerHasCastle(castle));
                    break;
                }
                case "sex": {
                    final int sex = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionPlayerSex(sex));
                    break;
                }
                case "flymounted": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerFlyMounted(val));
                    break;
                }
                case "vehiclemounted": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerVehicleMounted(val));
                    break;
                }
                case "landingzone": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerLandingZone(val));
                    break;
                }
                case "active_effect_id": {
                    final int effectId = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerActiveEffectId(effectId));
                    break;
                }
                case "active_effect_id_lvl": {
                    final String val = getValue(a.getNodeValue(), template);
                    final int effect_id = Integer.decode(getValue(val.split(",")[0], template));
                    final int effectLvl = Integer.decode(getValue(val.split(",")[1], template));
                    cond = joinAnd(cond, new ConditionPlayerActiveEffectId(effect_id, effectLvl));
                    break;
                }
                case "active_skill_id": {
                    final int skillId = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionPlayerActiveSkillId(skillId));
                    break;
                }
                case "active_skill_id_lvl": {
                    final String val = getValue(a.getNodeValue(), template);
                    final int skill_id = Integer.decode(getValue(val.split(",")[0], template));
                    final int skillLvl = Integer.decode(getValue(val.split(",")[1], template));
                    cond = joinAnd(cond, new ConditionPlayerActiveSkillId(skill_id, skillLvl));
                    break;
                }
                case "class_id_restriction": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final Set<Integer> array = new HashSet<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        array.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionPlayerClassIdRestriction(array));
                    break;
                }
                case "subclass": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerSubclass(val));
                    break;
                }
                case "instanceid": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final Set<Integer> set = new HashSet<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        set.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionPlayerInstanceId(set));
                    break;
                }
                case "agathionid": {
                    final int agathionId = Integer.decode(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerAgathionId(agathionId));
                    break;
                }
                case "cloakstatus": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionPlayerCloakStatus(val));
                    break;
                }
                case "haspet": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final List<Integer> array = new ArrayList<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        array.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionPlayerHasPet(array));
                    break;
                }
                case "servitornpcid": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final List<Integer> array = new ArrayList<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        array.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionPlayerServitorNpcId(array));
                    break;
                }
                case "npcidradius": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    if (st.countTokens() == 3) {
                        final String[] ids = st.nextToken().split(";");
                        final Set<Integer> npcIds = new HashSet<>(ids.length);
                        for (int index = 0; index < ids.length; index++) {
                            npcIds.add(Integer.parseInt(getValue(ids[index], template)));
                        }

                        final int radius = Integer.parseInt(st.nextToken());
                        final boolean val = Boolean.parseBoolean(st.nextToken());
                        cond = joinAnd(cond, new ConditionPlayerRangeFromNpc(npcIds, radius, val));
                    }
                    break;
                }
                case "callpc": {
                    cond = joinAnd(cond, new ConditionPlayerCallPc(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cancreatebase": {
                    cond = joinAnd(cond, new ConditionPlayerCanCreateBase(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cancreateoutpost": {
                    cond = joinAnd(cond, new ConditionPlayerCanCreateOutpost(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "canescape": {
                    cond = joinAnd(cond, new ConditionPlayerCanEscape(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "canrefuelairship": {
                    cond = joinAnd(cond, new ConditionPlayerCanRefuelAirship(Integer.parseInt(a.getNodeValue())));
                    break;
                }
                case "canresurrect": {
                    cond = joinAnd(cond, new ConditionPlayerCanResurrect(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cansummon": {
                    cond = joinAnd(cond, new ConditionPlayerCanSummon(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cansummonsiegegolem": {
                    cond = joinAnd(
                            cond, new ConditionPlayerCanSummonSiegeGolem(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cansweep": {
                    cond = joinAnd(cond, new ConditionPlayerCanSweep(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cantakecastle": {
                    cond = joinAnd(cond, new ConditionPlayerCanTakeCastle());
                    break;
                }
                case "cantakefort": {
                    cond = joinAnd(cond, new ConditionPlayerCanTakeFort(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "cantransform": {
                    cond = joinAnd(cond, new ConditionPlayerCanTransform(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "canuntransform": {
                    cond = joinAnd(cond, new ConditionPlayerCanUntransform(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "insidezoneid": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final Set<Integer> set = new HashSet<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        set.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionPlayerInsideZoneId(set));
                    break;
                }
                case "checkabnormal": {
                    final String value = a.getNodeValue();
                    if (value.contains(",")) {
                        final String[] values = value.split(",");
                        cond = joinAnd(
                                cond,
                                new ConditionPlayerCheckAbnormal(
                                        AbnormalType.valueOf(values[0]),
                                        Integer.decode(getValue(values[1], template))));
                    } else {
                        cond = joinAnd(cond, new ConditionPlayerCheckAbnormal(AbnormalType.valueOf(value)));
                    }
                    break;
                }
                case "categorytype": {
                    final String[] values = a.getNodeValue().split(",");
                    final Set<CategoryType> array = new HashSet<>(values.length);
                    for (String value : values) {
                        array.add(CategoryType.valueOf(getValue(value, null)));
                    }

                    cond = joinAnd(cond, new ConditionCategoryType(array));
                    break;
                }
            }
        }

        if (cond == null) {
            log.error("Unrecognized <player> condition in {}", file);
        }

        return cond;
    }

    protected Condition parseTargetCondition(Node n, Object template) {
        Condition cond = null;
        final NamedNodeMap attrs = n.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node a = attrs.item(i);
            switch (a.getNodeName().toLowerCase()) {
                case "aggro": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionTargetAggro(val));
                    break;
                }
                case "siegezone": {
                    final int value = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionSiegeZone(value, false));
                    break;
                }
                case "level": {
                    final int lvl = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionTargetLevel(lvl));
                    break;
                }
                case "levelrange": {
                    final String[] range = getValue(a.getNodeValue(), template).split(";");
                    if (range.length == 2) {
                        final int[] lvlRange = new int[2];
                        lvlRange[0] = Integer.decode(
                                getValue(a.getNodeValue(), template).split(";")[0]);
                        lvlRange[1] = Integer.decode(
                                getValue(a.getNodeValue(), template).split(";")[1]);
                        cond = joinAnd(cond, new ConditionTargetLevelRange(lvlRange));
                    }
                    break;
                }
                case "mypartyexceptme": {
                    cond = joinAnd(cond, new ConditionTargetMyPartyExceptMe(Boolean.parseBoolean(a.getNodeValue())));
                    break;
                }
                case "playable": {
                    cond = joinAnd(cond, new ConditionTargetPlayable());
                    break;
                }
                case "class_id_restriction": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final Set<Integer> set = new HashSet<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        set.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionTargetClassIdRestriction(set));
                    break;
                }
                case "active_effect_id": {
                    final int effectId = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionTargetActiveEffectId(effectId));
                    break;
                }
                case "active_effect_id_lvl": {
                    final String val = getValue(a.getNodeValue(), template);
                    final int effect_id = Integer.decode(getValue(val.split(",")[0], template));
                    final int effectLvl = Integer.decode(getValue(val.split(",")[1], template));
                    cond = joinAnd(cond, new ConditionTargetActiveEffectId(effect_id, effectLvl));
                    break;
                }
                case "active_skill_id": {
                    final int skillId = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionTargetActiveSkillId(skillId));
                    break;
                }
                case "active_skill_id_lvl": {
                    final String val = getValue(a.getNodeValue(), template);
                    final int skill_id = Integer.decode(getValue(val.split(",")[0], template));
                    final int skillLvl = Integer.decode(getValue(val.split(",")[1], template));
                    cond = joinAnd(cond, new ConditionTargetActiveSkillId(skill_id, skillLvl));
                    break;
                }
                case "abnormal": {
                    final int abnormalId = Integer.decode(getValue(a.getNodeValue(), template));
                    cond = joinAnd(cond, new ConditionTargetAbnormal(abnormalId));
                    break;
                }
                case "mindistance": {
                    final int distance = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionMinDistance(distance));
                    break;
                }
                case "race": {
                    cond = joinAnd(cond, new ConditionTargetRace(Race.valueOf(a.getNodeValue())));
                    break;
                }
                case "using": {
                    int mask = 0;
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        for (WeaponType wt : WeaponType.values()) {
                            if (wt.name().equals(item)) {
                                mask |= wt.mask();
                                break;
                            }
                        }

                        for (ArmorType at : ArmorType.values()) {
                            if (at.name().equals(item)) {
                                mask |= at.mask();
                                break;
                            }
                        }
                    }

                    cond = joinAnd(cond, new ConditionTargetUsesWeaponKind(mask));
                    break;
                }
                case "npcid": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    final Set<Integer> set = new HashSet<>(st.countTokens());
                    while (st.hasMoreTokens()) {
                        final String item = st.nextToken().trim();
                        set.add(Integer.decode(getValue(item, null)));
                    }

                    cond = joinAnd(cond, new ConditionTargetNpcId(set));
                    break;
                }
                case "npctype": {
                    final String values = getValue(a.getNodeValue(), template).trim();
                    final String[] valuesSplit = values.split(",");
                    final InstanceType[] types = new InstanceType[valuesSplit.length];
                    InstanceType type;
                    for (int j = 0; j < valuesSplit.length; j++) {
                        type = Enum.valueOf(InstanceType.class, valuesSplit[j]);
                        if (type == null) {
                            throw new IllegalArgumentException("Instance type not recognized: " + valuesSplit[j]);
                        }

                        types[j] = type;
                    }

                    cond = joinAnd(cond, new ConditionTargetNpcType(types));
                    break;
                }
                case "weight": {
                    final int weight = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionTargetWeight(weight));
                    break;
                }
                case "invsize": {
                    final int size = Integer.decode(getValue(a.getNodeValue(), null));
                    cond = joinAnd(cond, new ConditionTargetInvSize(size));
                    break;
                }
            }
        }

        if (cond == null) {
            log.error("Unrecognized <target> condition in {}", file);
        }

        return cond;
    }

    protected Condition parseUsingCondition(Node n) {
        Condition cond = null;
        final NamedNodeMap attrs = n.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node a = attrs.item(i);
            switch (a.getNodeName().toLowerCase()) {
                case "kind": {
                    int mask = 0;
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    while (st.hasMoreTokens()) {
                        final int old = mask;
                        final String item = st.nextToken().trim();
                        for (WeaponType wt : WeaponType.values()) {
                            if (wt.name().equals(item)) {
                                mask |= wt.mask();
                            }
                        }

                        for (ArmorType at : ArmorType.values()) {
                            if (at.name().equals(item)) {
                                mask |= at.mask();
                            }
                        }

                        if (old == mask) {
                            log.info("[parseUsingCondition=\"kind\"] Unknown item type name: {}", item);
                        }
                    }

                    cond = joinAnd(cond, new ConditionUsingItemType(mask));
                    break;
                }
                case "slot": {
                    int mask = 0;
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ",");
                    while (st.hasMoreTokens()) {
                        final int old = mask;
                        final String item = st.nextToken().trim();
                        final BodyPart bodyPart = BodyPart.fromName(item);
                        if (bodyPart != null) {
                            mask |= bodyPart.getMask();
                        }

                        if (old == mask) {
                            log.info("[parseUsingCondition=\"slot\"] Unknown item slot name: {}", item);
                        }
                    }

                    cond = joinAnd(cond, new ConditionUsingSlotType(mask));
                    break;
                }
                case "skill": {
                    final int id = Integer.parseInt(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionUsingSkill(id));
                    break;
                }
                case "slotitem": {
                    final StringTokenizer st = new StringTokenizer(a.getNodeValue(), ";");
                    final int id = Integer.parseInt(st.nextToken().trim());
                    final int slot = Integer.parseInt(st.nextToken().trim());
                    int enchant = 0;
                    if (st.hasMoreTokens()) {
                        enchant = Integer.parseInt(st.nextToken().trim());
                    }

                    cond = joinAnd(cond, new ConditionSlotItemId(slot, id, enchant));
                    break;
                }
                case "weaponchange": {
                    final boolean val = Boolean.parseBoolean(a.getNodeValue());
                    cond = joinAnd(cond, new ConditionChangeWeapon(val));
                    break;
                }
            }
        }

        if (cond == null) {
            log.error("Unrecognized <using> condition in {}", file);
        }

        return cond;
    }

    protected Condition parseGameCondition(Node n) {
        Condition cond = null;
        final NamedNodeMap attrs = n.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            final Node a = attrs.item(i);
            if ("skill".equalsIgnoreCase(a.getNodeName())) {
                final boolean val = Boolean.parseBoolean(a.getNodeValue());
                cond = joinAnd(cond, new ConditionWithSkill(val));
            }

            if ("night".equalsIgnoreCase(a.getNodeName())) {
                final boolean val = Boolean.parseBoolean(a.getNodeValue());
                cond = joinAnd(cond, new ConditionGameTime(val));
            }

            if ("chance".equalsIgnoreCase(a.getNodeName())) {
                final int val = Integer.decode(getValue(a.getNodeValue(), null));
                cond = joinAnd(cond, new ConditionGameChance(val));
            }
        }

        if (cond == null) {
            log.error("Unrecognized <game> condition in {}", file);
        }

        return cond;
    }

    protected void parseTable(Node n) {
        final NamedNodeMap attrs = n.getAttributes();
        final String name = attrs.getNamedItem("name").getNodeValue();
        if (name.charAt(0) != '#') {
            throw new IllegalArgumentException("Table name must start with #");
        }

        final StringTokenizer data = new StringTokenizer(n.getFirstChild().getNodeValue());
        final List<String> array = new ArrayList<>(data.countTokens());
        while (data.hasMoreTokens()) {
            array.add(data.nextToken());
        }

        setTable(name, array.toArray(new String[0]));
    }

    protected void parseBeanSet(Node n, StatSet set, Integer level) {
        final NamedNodeMap attrs = n.getAttributes();
        final Node nameNode = attrs.getNamedItem("name");
        if (nameNode == null) {
            return;
        }

        final String name = nameNode.getNodeValue().trim();
        String value;

        // Try the 'val' attribute (old), otherwise get the text from the tag (new).
        final Node valNode = attrs.getNamedItem("val");
        if (valNode != null) {
            value = valNode.getNodeValue().trim();
        } else {
            value = n.getTextContent().trim();
        }

        if (value.isEmpty()) {
            return;
        }

        final char ch = value.charAt(0);
        if ((ch == '#') || (ch == '-') || Character.isDigit(ch)) {
            set.set(name, getValue(value, level));
        } else {
            set.set(name, value);
        }
    }

    /**
     * Parse an XML element with its value directly inside the element Example: <reuseDelay>3000</reuseDelay>
     * @param n the XML node to parse
     * @param set the StatSet to store the data into
     * @param level the current level
     */
    protected void parseElementValue(Node n, StatSet set, Integer level) {
        final String value = n.getTextContent().trim();
        if (value.isEmpty()) {
            return;
        }

        // If the tag is <enchant1 name="power">, the stat is 'power'.
        final Node nameAttr = n.getAttributes().getNamedItem("name");
        final String statName = (nameAttr != null)
                ? nameAttr.getNodeValue().trim()
                : n.getNodeName().trim();

        final char ch = value.charAt(0);
        if ((ch == '#') || (ch == '-') || Character.isDigit(ch)) {
            set.set(statName, getValue(value, level));
        } else {
            set.set(statName, value);
        }
    }

    /**
     * Check if a node contains only text content (no element children).
     * @param node the node to check
     * @return true if the node has only text content
     */
    protected boolean hasOnlyTextContent(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return false; // Has element children.
            }

            child = child.getNextSibling();
        }

        return true;
    }

    /**
     * Check if a node contains level-specific value elements.
     * @param node the node to check
     * @return true if the node has child elements with level attributes
     */
    protected boolean hasLevelSpecificValues(Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            if ((child.getNodeType() == Node.ELEMENT_NODE) && "value".equals(child.getNodeName())) {
                final NamedNodeMap attributes = child.getAttributes();
                if ((attributes != null) && (attributes.getNamedItem("level") != null)) {
                    return true;
                }
            }

            child = child.getNextSibling();
        }

        return false;
    }

    /**
     * Parse level-specific values from a parameter node.
     * @param node the parameter node containing value elements
     * @param template the effect template
     * @return a map of level to value
     */
    protected Map<Integer, String> parseLevelSpecificValues(Node node, Object template) {
        final Map<Integer, String> levelValues = new HashMap<>();
        Node child = node.getFirstChild();
        while (child != null) {
            if ((child.getNodeType() == Node.ELEMENT_NODE) && "value".equals(child.getNodeName())) {
                final NamedNodeMap attributes = child.getAttributes();
                final Node levelAttr = attributes.getNamedItem("level");
                if (levelAttr != null) {
                    final int level = Integer.parseInt(levelAttr.getNodeValue());
                    final String value = child.getTextContent().trim();
                    levelValues.put(level, getValue(value, template));
                }
            }

            child = child.getNextSibling();
        }

        return levelValues;
    }

    protected String getValue(String value, Object template) {
        // Is it a table?
        if ((value != null) && !value.isEmpty() && (value.charAt(0) == '#')) {
            if (template instanceof Skill) {
                return getTableValue(value);
            } else if (template instanceof Integer) {
                return getTableValue(value, ((Integer) template).intValue());
            } else {
                throw new IllegalStateException();
            }
        }

        return value;
    }

    protected Condition joinAnd(Condition cond, Condition c) {
        if (cond == null) {
            return c;
        }

        if (cond instanceof ConditionLogicAnd) {
            ((ConditionLogicAnd) cond).add(c);
            return cond;
        }

        final ConditionLogicAnd and = new ConditionLogicAnd();
        and.add(cond);
        and.add(c);
        return and;
    }
}
