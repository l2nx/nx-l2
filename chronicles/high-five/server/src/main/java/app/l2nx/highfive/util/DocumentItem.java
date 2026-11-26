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

import app.l2nx.highfive.data.xml.IXmlReader;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemSkillType;
import app.l2nx.highfive.entity.item.holders.ExtractableProduct;
import app.l2nx.highfive.entity.item.type.ArmorType;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.FuncEnchant;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * @author mkizub, JIV, Mobius
 */
@Slf4j
public class DocumentItem extends DocumentBase implements IXmlReader {

    private DocumentItemDataHolder currentItem = null;

    @Getter
    private final List<ItemTemplate> itemList = new ArrayList<>();

    private class DocumentItemDataHolder {
        public DocumentItemDataHolder() {}

        int id;
        String type;
        StatSet set;
        int currentLevel;
        ItemTemplate item;
    }

    public DocumentItem(File file) {
        super(file);
    }

    @Override
    protected StatSet getStatSet() {
        return currentItem.set;
    }

    @Override
    protected String getTableValue(String name) {
        return tables.get(name)[currentItem.currentLevel];
    }

    @Override
    protected String getTableValue(String name, int idx) {
        return tables.get(name)[idx - 1];
    }

    @Override
    protected void parseDocument(Document document) {
        for (Node n = document.getFirstChild(); n != null; n = n.getNextSibling()) {
            if ("list".equalsIgnoreCase(n.getNodeName())) {
                for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling()) {
                    if ("item".equalsIgnoreCase(d.getNodeName())) {
                        try {
                            currentItem = new DocumentItemDataHolder();
                            parseItem(d);
                            itemList.add(currentItem.item);
                            resetTable();
                        } catch (Exception e) {
                            log.warn("Cannot create item {}", currentItem.id, e);
                        }
                    }
                }
            }
        }
    }

    private void parseItem(Node node) throws InvocationTargetException {
        Node n = node;
        final int itemId = Integer.parseInt(n.getAttributes().getNamedItem("id").getNodeValue());
        final String className = n.getAttributes().getNamedItem("type").getNodeValue();
        final String itemName = n.getAttributes().getNamedItem("name").getNodeValue();

        currentItem.id = itemId;
        currentItem.type = className;
        currentItem.set = new StatSet();
        currentItem.set.set("item_id", itemId);
        currentItem.set.set("name", itemName);

        final Node first = n.getFirstChild();
        for (n = first; n != null; n = n.getNextSibling()) {
            // Check if this is a direct element value node (like <icon>value</icon>).
            if ((n.getNodeType() == Node.ELEMENT_NODE)
                    && (n.getFirstChild() != null)
                    && (n.getAttributes().getLength() == 0)) {
                boolean hasChildElements = false;
                for (Node child = n.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        hasChildElements = true;
                        break;
                    }
                }

                if (!hasChildElements && (n.getFirstChild().getNodeType() == Node.TEXT_NODE)) {
                    // Parse element value, regardless of whether the item is created or not.
                    parseElementValue(n, currentItem.set, 1);

                    if (currentItem.item != null) {
                        currentItem.item.set(currentItem.set);
                    }
                    continue;
                }
            }

            if ("table".equalsIgnoreCase(n.getNodeName())) {
                if (currentItem.item != null) {
                    throw new IllegalStateException("Item created but table node found! Item " + itemId);
                }

                parseTable(n);
            } else if ("set".equalsIgnoreCase(n.getNodeName())) {
                if (currentItem.item != null) {
                    throw new IllegalStateException("Item created but set node found! Item " + itemId);
                }

                parseBeanSet(n, currentItem.set, 1);
            } else if ("stats".equalsIgnoreCase(n.getNodeName())) {
                makeItem();
                for (Node b = n.getFirstChild(); b != null; b = b.getNextSibling()) {
                    if ("stat".equals(b.getNodeName())) {
                        final Stat type = Stat.valueOfXml(
                                b.getAttributes().getNamedItem("type").getNodeValue());
                        final double value = Double.parseDouble(b.getTextContent());

                        switch (type) {
                            case POWER_ATTACK:
                            case POWER_ATTACK_SPEED:
                            case POWER_ATTACK_RANGE:
                            case CRITICAL_RATE:
                            case MAGIC_ATTACK:
                            case SHIELD_DEFENCE:
                            case SHIELD_RATE: {
                                currentItem.item.attach(new FuncTemplate(null, null, "set", 0x00, type, value));
                                break;
                            }
                            case ACCURACY_COMBAT:
                            case EVASION_RATE:
                            case RANDOM_DAMAGE:
                            case POWER_DEFENCE:
                            case MAGIC_DEFENCE:
                            case MAX_MP:
                            case FIRE_POWER:
                            case WATER_POWER:
                            case WIND_POWER:
                            case EARTH_POWER:
                            case HOLY_POWER:
                            case DARK_POWER:
                            case FIRE_RES:
                            case WATER_RES:
                            case WIND_RES:
                            case EARTH_RES:
                            case HOLY_RES:
                            case DARK_RES: {
                                currentItem.item.attach(new FuncTemplate(null, null, "add", 0x00, type, value));
                                break;
                            }
                            case MAGIC_SUCCESS_RES: {
                                currentItem.item.attach(new FuncTemplate(null, null, "mul", 0x00, type, value));
                                break;
                            }
                            default: {
                                log.warn("Unhandled stat type {}", type);
                                break;
                            }
                        }
                    }
                }

                if (currentItem.item.isArmor()) {
                    if (!currentItem.item.hasFunction(FuncEnchant.class)) {
                        if (currentItem.item.getItemType() == ArmorType.SHIELD) {
                            currentItem.item.attach(
                                    new FuncTemplate(null, null, "enchant", 0x00, Stat.SHIELD_DEFENCE, 0d));
                        } else if ((currentItem.item.getType1() == ItemTemplate.TYPE1_WEAPON_RING_EARRING_NECKLACE)
                                && (currentItem.item.getType2() == ItemTemplate.TYPE2_ACCESSORY)) {
                            currentItem.item.attach(
                                    new FuncTemplate(null, null, "enchant", 0x00, Stat.MAGIC_DEFENCE, 0d));
                        } else {
                            currentItem.item.attach(
                                    new FuncTemplate(null, null, "enchant", 0x00, Stat.POWER_DEFENCE, 0d));
                        }
                    }
                } else if (currentItem.item.isWeapon()) {
                    if (!currentItem.item.hasFunction(FuncEnchant.class)) {
                        currentItem.item.attach(new FuncTemplate(null, null, "enchant", 0x00, Stat.POWER_ATTACK, 0d));
                        currentItem.item.attach(new FuncTemplate(null, null, "enchant", 0x00, Stat.MAGIC_ATTACK, 0d));
                    }
                }
            } else if ("skills".equalsIgnoreCase(n.getNodeName())) {
                for (Node b = n.getFirstChild(); b != null; b = b.getNextSibling()) {
                    if ("skill".equalsIgnoreCase(b.getNodeName())) {
                        final int id = parseInteger(b.getAttributes(), "id");
                        final int level = parseInteger(b.getAttributes(), "level");
                        final int chance = parseInteger(b.getAttributes(), "type_chance", 100);
                        final Node typeNode = b.getAttributes().getNamedItem("type");
                        final ItemSkillType type = typeNode == null ? untypedSkillType() : itemSkillType(typeNode);
                        if ((type == null) || (type == ItemSkillType.ON_PHYSICAL_HIT)) {
                            log.warn(
                                    "Skipped skill {}-{} of item {}: type {} is not valid for an item",
                                    id,
                                    level,
                                    currentItem.id,
                                    typeNode.getNodeValue());
                            continue;
                        }

                        switch (type) {
                            case EQUIPPED, ON_USE: {
                                if (currentItem.set.getString("item_skill", null) == null) {
                                    currentItem.set.set("item_skill", id + "-" + level);
                                } else {
                                    currentItem.set.set(
                                            "item_skill",
                                            currentItem.set.getString("item_skill") + ";" + id + "-" + level);
                                }
                                break;
                            }
                            case EQUIPPED_ENCHANT: {
                                currentItem.set.set("enchant4_skill", id + "-" + level);
                                break;
                            }
                            case ON_UNEQUIP: {
                                currentItem.set.set("unequip_skill", id + "-" + level);
                                break;
                            }
                            case ON_NORMAL_ATTACK_CRITICAL_HIT: {
                                currentItem.set.set("oncrit_skill", id + "-" + level);
                                currentItem.set.set("oncrit_chance", chance);
                                break;
                            }
                            case ON_MAGIC_SKILL_CAST: {
                                currentItem.set.set("onmagic_skill", id + "-" + level);
                                currentItem.set.set("onmagic_chance", chance);
                                break;
                            }
                        }
                    }
                }
            } else if ("capsuled_items".equalsIgnoreCase(n.getNodeName())) {
                makeItem();
                for (Node b = n.getFirstChild(); b != null; b = b.getNextSibling()) {
                    if ("item".equals(b.getNodeName())) {
                        final int id = parseInteger(b.getAttributes(), "id");
                        final long min = parseLong(b.getAttributes(), "min");
                        final long max = parseLong(b.getAttributes(), "max");
                        final double chance = parseDouble(b.getAttributes(), "chance");
                        final int minEnchant = parseInteger(b.getAttributes(), "minEnchant", 0);
                        final int maxEnchant = parseInteger(b.getAttributes(), "maxEnchant", 0);
                        currentItem.item.addCapsuledItem(
                                new ExtractableProduct(id, min, max, chance, minEnchant, maxEnchant));
                    }
                }
            } else if ("conditions".equalsIgnoreCase(n.getNodeName())) {
                makeItem();
                final Condition condition = parseCondition(n.getFirstChild(), currentItem.item);
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

                currentItem.item.attach(condition);
            }
        }

        // The item is still uncreated here when the element had no stats or conditions.
        makeItem();
    }

    private ItemSkillType untypedSkillType() {
        return "EtcItem".equals(currentItem.type) ? ItemSkillType.ON_USE : ItemSkillType.EQUIPPED;
    }

    private static ItemSkillType itemSkillType(Node typeNode) {
        try {
            return ItemSkillType.valueOf(typeNode.getNodeValue());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void makeItem() throws InvocationTargetException {
        if (currentItem.item != null) {
            currentItem.item.set(currentItem.set);
            return;
        }

        try {
            final Constructor<?> itemClass = Class.forName("app.l2nx.highfive.entity.item." + currentItem.type)
                    .getConstructor(StatSet.class);
            currentItem.item = (ItemTemplate) itemClass.newInstance(currentItem.set);
        } catch (Exception e) {
            throw new InvocationTargetException(e);
        }
    }

    @Override
    public void load() {}

    @Override
    public void parseDocument(Document document, File file) {}
}
