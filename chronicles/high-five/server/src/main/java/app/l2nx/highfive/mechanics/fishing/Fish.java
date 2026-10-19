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
package app.l2nx.highfive.mechanics.fishing;

import app.l2nx.highfive.util.StatSet;
import lombok.Getter;
import lombok.Setter;

/**
 * Class for the Fish object.
 * @author nonom
 */
public class Fish implements Cloneable {
    @Getter
    private final int fishId;

    @Getter
    private final int itemId;

    @Getter
    private final String itemName;

    @Getter
    @Setter
    private int fishGroup;

    @Getter
    private final int fishLevel;

    @Getter
    private final double fishBiteRate;

    @Getter
    private final double fishGuts;

    @Getter
    private final int fishHp;

    @Getter
    private final int fishMaxLength;

    @Getter
    private final double fishLengthRate;

    @Getter
    private final double hpRegen;

    @Getter
    private final int startCombatTime;

    @Getter
    private final int combatDuration;

    @Getter
    private final int gutsCheckTime;

    @Getter
    private final double gutsCheckProbability;

    @Getter
    private final double cheatingProb;

    @Getter
    private final int fishGrade;

    public Fish(StatSet set) {
        fishId = set.getInt("fishId");
        itemId = set.getInt("itemId");
        itemName = set.getString("itemName");
        fishGroup = getGroupId(set.getString("fishGroup"));
        fishLevel = set.getInt("fishLevel");
        fishBiteRate = set.getDouble("fishBiteRate"); // TODO: Support needed.
        fishGuts = set.getDouble("fishGuts");
        fishHp = set.getInt("fishHp");
        fishMaxLength = set.getInt("fishMaxLength"); // TODO: Support needed.
        fishLengthRate = set.getDouble("fishLengthRate"); // TODO: Support needed.
        hpRegen = set.getDouble("hpRegen");
        startCombatTime = set.getInt("startCombatTime");
        combatDuration = set.getInt("combatDuration");
        gutsCheckTime = set.getInt("gutsCheckTime");
        gutsCheckProbability = set.getDouble("gutsCheckProbability"); // TODO: Support needed.
        cheatingProb = set.getDouble("cheatingProb"); // TODO: Support needed.
        fishGrade = getGradeId(set.getString("fishGrade"));
    }

    @Override
    public Fish clone() {
        try {
            return (Fish) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    /**
     * @param name the Group Name.
     * @return the fish Group Id.
     */
    private int getGroupId(String name) {
        switch (name) {
            case "swift": {
                return 1;
            }
            case "ugly": {
                return 2;
            }
            case "fish_box": {
                return 3;
            }
            case "easy_wide": {
                return 4;
            }
            case "easy_swift": {
                return 5;
            }
            case "easy_ugly": {
                return 6;
            }
            case "hard_wide": {
                return 7;
            }
            case "hard_swift": {
                return 8;
            }
            case "hard_ugly": {
                return 9;
            }
            case "hs_fish": {
                return 10; // FIXME: Verify the ID
            }
            case "wide":
            default: {
                return 0;
            }
        }
    }

    /**
     * @param name the Grade Name.
     * @return the fish Grade Id.
     */
    private int getGradeId(String name) {
        switch (name) {
            case "fish_easy": {
                return 0;
            }
            case "fish_hard": {
                return 2;
            }
            case "fish_normal":
            default: {
                return 1;
            }
        }
    }
}
