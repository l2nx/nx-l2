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
package app.l2nx.highfive.entity.actor.holders.npc;

import app.l2nx.highfive.data.xml.FakePlayerData;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * @author Mobius
 */
public class FakePlayerHolder {
    @Getter
    private final PlayerClass playerClass;

    @Getter
    private final int hair;

    @Getter
    private final int hairColor;

    @Getter
    private final int face;

    @Getter
    private final int nameColor;

    @Getter
    private final int titleColor;

    @Getter
    private final int equipHead;

    @Getter
    private final int equipRHand;

    @Getter
    private final int equipLHand;

    @Getter
    private final int equipGloves;

    @Getter
    private final int equipChest;

    @Getter
    private final int equipLegs;

    @Getter
    private final int equipFeet;

    @Getter
    private final int equipCloak;

    @Getter
    private final int equipHair;

    @Getter
    private final int equipHair2;

    @Getter
    private final int agathionId;

    @Getter
    private final int weaponEnchantLevel;

    @Getter
    private final int armorEnchantLevel;

    @Getter
    private final boolean fishing;

    @Getter
    private final int baitLocationX;

    @Getter
    private final int baitLocationY;

    @Getter
    private final int baitLocationZ;

    @Getter
    private final int recommends;

    @Getter
    private final int nobleLevel;

    @Getter
    private final boolean hero;

    @Getter
    private final int clanId;

    @Getter
    private final int pledgeStatus;

    @Getter
    private final boolean sitting;

    @Getter
    private final int privateStoreType;

    @Getter
    private final String privateStoreMessage;

    @Getter
    private final boolean talkable;

    public FakePlayerHolder(StatSet set) {
        playerClass = PlayerClass.getPlayerClass(set.getInt("classId", 1));
        hair = set.getInt("hair", 1);
        hairColor = set.getInt("hairColor", 1);
        face = set.getInt("face", 1);
        nameColor = set.getInt("nameColor", 0xFFFFFF);
        titleColor = set.getInt("titleColor", 0xECF9A2);
        equipHead = set.getInt("equipHead", 0);
        equipRHand = set.getInt("equipRHand", 0); // Or dual hand.
        equipLHand = set.getInt("equipLHand", 0);
        equipGloves = set.getInt("equipGloves", 0);
        equipChest = set.getInt("equipChest", 0);
        equipLegs = set.getInt("equipLegs", 0);
        equipFeet = set.getInt("equipFeet", 0);
        equipCloak = set.getInt("equipCloak", 0);
        equipHair = set.getInt("equipHair", 0);
        equipHair2 = set.getInt("equipHair2", 0);
        agathionId = set.getInt("agathionId", 0);
        weaponEnchantLevel = set.getInt("weaponEnchantLevel", 0);
        armorEnchantLevel = set.getInt("armorEnchantLevel", 0);
        fishing = set.getBoolean("fishing", false);
        baitLocationX = set.getInt("baitLocationX", 0);
        baitLocationY = set.getInt("baitLocationY", 0);
        baitLocationZ = set.getInt("baitLocationZ", 0);
        recommends = set.getInt("recommends", 0);
        nobleLevel = set.getInt("nobleLevel", 0);
        hero = set.getBoolean("hero", false);
        clanId = set.getInt("clanId", 0);
        pledgeStatus = set.getInt("pledgeStatus", 0);
        sitting = set.getBoolean("sitting", false);
        privateStoreType = set.getInt("privateStoreType", 0);
        privateStoreMessage = set.getString("privateStoreMessage", "");
        talkable = set.getBoolean("fakePlayerTalkable", true);

        // Populate FakePlayerData mappings.
        final String name = set.getString("name", "");
        FakePlayerData.getInstance().addFakePlayerId(name, set.getInt("id", 0)); // Map name to npcId.
        final String lowercaseName = name.toLowerCase();
        FakePlayerData.getInstance().addFakePlayerName(lowercaseName, name); // Map lowercase name to original name.
        if (talkable) {
            FakePlayerData.getInstance().addTalkableFakePlayerName(lowercaseName);
        }
    }
}
