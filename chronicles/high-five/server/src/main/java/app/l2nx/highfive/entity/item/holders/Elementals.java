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
package app.l2nx.highfive.entity.item.holders;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.holders.ElementalItemHolder;
import app.l2nx.highfive.data.xml.ElementalAttributeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.FuncAdd;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class Elementals {
    public static final byte NONE = -1;
    public static final byte FIRE = 0;
    public static final byte WATER = 1;
    public static final byte WIND = 2;
    public static final byte EARTH = 3;
    public static final byte HOLY = 4;
    public static final byte DARK = 5;

    public static final int FIRST_WEAPON_BONUS = 20;
    public static final int NEXT_WEAPON_BONUS = 5;
    public static final int ARMOR_BONUS = 6;

    private static final int[] WEAPON_VALUES = {
        0, // Level 1
        25, // Level 2
        75, // Level 3
        150, // Level 4
        175, // Level 5
        225, // Level 6
        300, // Level 7
        325, // Level 8
        375, // Level 9
        450, // Level 10
        475, // Level 11
        525, // Level 12
        600, // Level 13
        Integer.MAX_VALUE
    };

    private static final int[] ARMOR_VALUES = {
        0, // Level 1
        12, // Level 2
        30, // Level 3
        60, // Level 4
        72, // Level 5
        90, // Level 6
        120, // Level 7
        132, // Level 8
        150, // Level 9
        180, // Level 10
        192, // Level 11
        210, // Level 12
        240, // Level 13
        Integer.MAX_VALUE
    };

    /** The highest attribute value a weapon reaches with stones of the given maximum level. */
    public static int weaponValue(int level) {
        WorldPartition.checkOwner();
        return WEAPON_VALUES[level];
    }

    /** The highest attribute value an armor reaches with stones of the given maximum level. */
    public static int armorValue(int level) {
        WorldPartition.checkOwner();
        return ARMOR_VALUES[level];
    }

    public static byte getItemElement(int itemId) {
        WorldPartition.checkOwner();
        final ElementalItemHolder item = ElementalAttributeData.getInstance().getElementalItem(itemId);
        return item != null ? item.getElementId() : NONE;
    }

    public static ElementalItemHolder getItemElemental(int itemId) {
        WorldPartition.checkOwner();
        return ElementalAttributeData.getInstance().getElementalItem(itemId);
    }

    public static int getMaxElementLevel(int itemId) {
        WorldPartition.checkOwner();
        final ElementalItemHolder item = ElementalAttributeData.getInstance().getElementalItem(itemId);
        return item != null ? item.getType().getMaxLevel() : -1;
    }

    public static String getElementName(byte element) {
        WorldPartition.checkOwner();
        return elementName(element);
    }

    private static String elementName(byte element) {
        switch (element) {
            case FIRE: {
                return "Fire";
            }
            case WATER: {
                return "Water";
            }
            case WIND: {
                return "Wind";
            }
            case EARTH: {
                return "Earth";
            }
            case DARK: {
                return "Dark";
            }
            case HOLY: {
                return "Holy";
            }
        }

        return "None";
    }

    public static byte getElementId(String name) {
        WorldPartition.checkOwner();
        final String tmp = name.toLowerCase();
        if (tmp.equals("fire")) {
            return FIRE;
        }

        if (tmp.equals("water")) {
            return WATER;
        }

        if (tmp.equals("wind")) {
            return WIND;
        }

        if (tmp.equals("earth")) {
            return EARTH;
        }

        if (tmp.equals("dark")) {
            return DARK;
        }

        if (tmp.equals("holy")) {
            return HOLY;
        }

        return NONE;
    }

    public static byte getOppositeElement(byte element) {
        WorldPartition.checkOwner();
        return (byte) (((element % 2) == 0) ? (element + 1) : (element - 1));
    }

    private byte element;

    private int value;

    private boolean bonusActive;

    public Elementals(byte type, int value) {
        element = type;
        this.value = value;
    }

    public byte getElement() {
        WorldPartition.checkOwner();
        return element;
    }

    public int getValue() {
        WorldPartition.checkOwner();
        return value;
    }

    public void setElement(byte type) {
        WorldPartition.checkOwner();
        element = type;
    }

    public void setValue(int value) {
        WorldPartition.checkOwner();
        this.value = value;
    }

    @Override
    public String toString() {
        return elementName(element) + " +" + value;
    }

    public void applyBonus(Player player, boolean isArmor) {
        WorldPartition.checkOwner();
        apply(player, isArmor);
    }

    public void removeBonus(Player player) {
        WorldPartition.checkOwner();
        remove(player);
    }

    public void updateBonus(Player player, boolean isArmor) {
        WorldPartition.checkOwner();
        remove(player);
        apply(player, isArmor);
    }

    private void apply(Player player, boolean isArmor) {
        if (bonusActive) {
            return;
        }

        final Stat stat = bonusStat(isArmor);
        if (stat != null) {
            player.addStatFunc(new FuncAdd(stat, 0x40, this, value, null));
        }

        bonusActive = true;
    }

    private void remove(Player player) {
        if (!bonusActive) {
            return;
        }

        player.removeStatsOwner(this);
        bonusActive = false;
    }

    private Stat bonusStat(boolean isArmor) {
        switch (element) {
            case FIRE: {
                return isArmor ? Stat.FIRE_RES : Stat.FIRE_POWER;
            }
            case WATER: {
                return isArmor ? Stat.WATER_RES : Stat.WATER_POWER;
            }
            case WIND: {
                return isArmor ? Stat.WIND_RES : Stat.WIND_POWER;
            }
            case EARTH: {
                return isArmor ? Stat.EARTH_RES : Stat.EARTH_POWER;
            }
            case DARK: {
                return isArmor ? Stat.DARK_RES : Stat.DARK_POWER;
            }
            case HOLY: {
                return isArmor ? Stat.HOLY_RES : Stat.HOLY_POWER;
            }
            default: {
                return null;
            }
        }
    }
}
