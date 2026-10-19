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
package app.l2nx.highfive.scripts.quests.TerritoryWarScripts;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

/**
 * @author Mobius
 */
public class TerritoryWarSuperClassLoader {
    @Getter
    private static Map<Integer, TerritoryWarSuperClass> forTheSakeScripts = new HashMap<>();

    @Getter
    private static Map<Integer, TerritoryWarSuperClass> protectTheScripts = new HashMap<>();

    @Getter
    private static Map<Integer, TerritoryWarSuperClass> killTheScripts = new HashMap<>();

    public static void main(String[] args) {
        // initialize superclass
        new TerritoryWarSuperClass(-1);

        // initialize subclasses
        // "For The Sake" quests
        final TerritoryWarSuperClass gludio = new Q00717_ForTheSakeOfTheTerritoryGludio();
        forTheSakeScripts.put(gludio.TERRITORY_ID, gludio);
        final TerritoryWarSuperClass dion = new Q00718_ForTheSakeOfTheTerritoryDion();
        forTheSakeScripts.put(dion.TERRITORY_ID, dion);
        final TerritoryWarSuperClass giran = new Q00719_ForTheSakeOfTheTerritoryGiran();
        forTheSakeScripts.put(giran.TERRITORY_ID, giran);
        final TerritoryWarSuperClass oren = new Q00720_ForTheSakeOfTheTerritoryOren();
        forTheSakeScripts.put(oren.TERRITORY_ID, oren);
        final TerritoryWarSuperClass aden = new Q00721_ForTheSakeOfTheTerritoryAden();
        forTheSakeScripts.put(aden.TERRITORY_ID, aden);
        final TerritoryWarSuperClass innadril = new Q00722_ForTheSakeOfTheTerritoryInnadril();
        forTheSakeScripts.put(innadril.TERRITORY_ID, innadril);
        final TerritoryWarSuperClass goddard = new Q00723_ForTheSakeOfTheTerritoryGoddard();
        forTheSakeScripts.put(goddard.TERRITORY_ID, goddard);
        final TerritoryWarSuperClass rune = new Q00724_ForTheSakeOfTheTerritoryRune();
        forTheSakeScripts.put(rune.TERRITORY_ID, rune);
        final TerritoryWarSuperClass schuttgart = new Q00725_ForTheSakeOfTheTerritorySchuttgart();
        forTheSakeScripts.put(schuttgart.TERRITORY_ID, schuttgart);

        // "Protect the" quests
        final TerritoryWarSuperClass catapult = new Q00729_ProtectTheTerritoryCatapult();
        protectTheScripts.put(catapult.getId(), catapult);
        final TerritoryWarSuperClass supplies = new Q00730_ProtectTheSuppliesSafe();
        protectTheScripts.put(supplies.getId(), supplies);
        final TerritoryWarSuperClass military = new Q00731_ProtectTheMilitaryAssociationLeader();
        protectTheScripts.put(military.getId(), military);
        final TerritoryWarSuperClass religious = new Q00732_ProtectTheReligiousAssociationLeader();
        protectTheScripts.put(religious.getId(), religious);
        final TerritoryWarSuperClass economic = new Q00733_ProtectTheEconomicAssociationLeader();
        protectTheScripts.put(economic.getId(), economic);

        // "Kill" quests
        final TerritoryWarSuperClass knights = new Q00734_PierceThroughAShield();
        for (int i : knights.CLASS_IDS) {
            killTheScripts.put(i, knights);
        }

        final TerritoryWarSuperClass warriors = new Q00735_MakeSpearsDull();
        for (int i : warriors.CLASS_IDS) {
            killTheScripts.put(i, warriors);
        }

        final TerritoryWarSuperClass wizards = new Q00736_WeakenTheMagic();
        for (int i : wizards.CLASS_IDS) {
            killTheScripts.put(i, wizards);
        }

        final TerritoryWarSuperClass priests = new Q00737_DenyBlessings();
        for (int i : priests.CLASS_IDS) {
            killTheScripts.put(i, priests);
        }

        final TerritoryWarSuperClass keys = new Q00738_DestroyKeyTargets();
        for (int i : keys.CLASS_IDS) {
            killTheScripts.put(i, keys);
        }
    }
}
