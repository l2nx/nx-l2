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
package app.l2nx.highfive.network.holders;

import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import lombok.Getter;
import lombok.Setter;

/**
 * Used to Store data sent to Client for Character.<br>
 * Selection screen.
 * @version $Revision: 1.2.2.2.2.4 $ $Date: 2005/03/27 15:29:33 $
 */
public class CharacterInfoHolder {
    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private int objectId = 0;

    @Getter
    @Setter
    private long exp = 0;

    @Getter
    @Setter
    private long sp = 0;

    @Getter
    @Setter
    private int clanId = 0;

    @Getter
    @Setter
    private int race = 0;

    @Getter
    @Setter
    private int classId = 0;

    @Getter
    @Setter
    private int baseClassId = 0;

    @Getter
    @Setter
    private long deleteTimer = 0;

    @Getter
    @Setter
    private long lastAccess = 0;

    @Getter
    @Setter
    private int face = 0;

    @Getter
    @Setter
    private int hairStyle = 0;

    @Getter
    @Setter
    private int hairColor = 0;

    @Getter
    @Setter
    private int sex = 0;

    @Getter
    @Setter
    private int level = 1;

    @Getter
    @Setter
    private int maxHp = 0;

    @Getter
    @Setter
    private double currentHp = 0;

    @Getter
    @Setter
    private int maxMp = 0;

    @Getter
    @Setter
    private double currentMp = 0;

    private final int[][] paperdoll;

    @Getter
    @Setter
    private int karma = 0;

    @Getter
    @Setter
    private int pkKills = 0;

    @Getter
    @Setter
    private int pvPKills = 0;

    @Getter
    @Setter
    private int augmentationId = 0;

    @Getter
    @Setter
    private int x = 0;

    @Getter
    @Setter
    private int y = 0;

    @Getter
    @Setter
    private int z = 0;

    @Getter
    @Setter
    private String htmlPrefix = null;

    @Getter
    private boolean good = false;

    @Getter
    private boolean evil = false;

    @Getter
    @Setter
    private int vitalityPoints = 0;

    @Getter
    @Setter
    private int accessLevel = 0;

    /**
     * Constructor for CharSelectInfoPackage.
     * @param objectId character object Id.
     * @param name the character's name.
     */
    public CharacterInfoHolder(int objectId, String name) {
        setObjectId(objectId);
        this.name = name;
        paperdoll = PlayerInventory.restoreVisibleInventory(objectId);
    }

    public void setGood() {
        good = true;
        evil = false;
    }

    public void setEvil() {
        good = false;
        evil = true;
    }

    public int getPaperdollObjectId(int slot) {
        return paperdoll[slot][0];
    }

    public int getPaperdollItemId(int slot) {
        return paperdoll[slot][1];
    }

    public int getEnchantEffect() {
        if (paperdoll[Inventory.PAPERDOLL_RHAND][2] > 0) {
            return paperdoll[Inventory.PAPERDOLL_RHAND][2];
        }

        return paperdoll[Inventory.PAPERDOLL_RHAND][2];
    }
}
