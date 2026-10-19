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
package app.l2nx.highfive.scripts.ai.areas.Gracia;

import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.EnergySeeds;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.Lindvior;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.Maguen;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.AbyssGaze.AbyssGaze;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.DestroyedTumors.DestroyedTumors;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.EkimusMouth.EkimusMouth;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.FortuneTelling.FortuneTelling;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.GeneralDilios.GeneralDilios;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.Klemis.Klemis;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.Lekon.Lekon;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.Nemo.Nemo;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.Nottingale.Nottingale;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.Seyo.Seyo;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.NPC.ZealotOfShilen.ZealotOfShilen;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.SeedOfAnnihilation.SeedOfAnnihilation;
import app.l2nx.highfive.scripts.ai.areas.Gracia.AI.StarStones;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HallOfErosionAttack.HallOfErosionAttack;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HallOfErosionDefence.HallOfErosionDefence;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HallOfSufferingAttack.HallOfSufferingAttack;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HallOfSufferingDefence.HallOfSufferingDefence;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HeartInfinityAttack.HeartInfinityAttack;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.HeartInfinityDefence.HeartInfinityDefence;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.SecretArea.SecretArea;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.SeedOfDestruction.MountedTroops.ChamblainsMountedTroop;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.SeedOfDestruction.MountedTroops.GreatWarriorsMountedTroop;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.SeedOfDestruction.MountedTroops.SoldiersMountedTroop;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.SeedOfDestruction.MountedTroops.WarriorsMountedTroop;
import app.l2nx.highfive.scripts.ai.areas.Gracia.instances.SeedOfDestruction.SeedOfDestruction;
import app.l2nx.highfive.scripts.ai.areas.Gracia.vehicles.AirShipGludioGracia.AirShipGludioGracia;
import app.l2nx.highfive.scripts.ai.areas.Gracia.vehicles.KeucereusNorthController.KeucereusNorthController;
import app.l2nx.highfive.scripts.ai.areas.Gracia.vehicles.KeucereusSouthController.KeucereusSouthController;
import app.l2nx.highfive.scripts.ai.areas.Gracia.vehicles.SoDController.SoDController;
import app.l2nx.highfive.scripts.ai.areas.Gracia.vehicles.SoIController.SoIController;
import lombok.extern.slf4j.Slf4j;

/**
 * Gracia class-loader.
 * @author Mobius
 */
@Slf4j
public class GraciaLoader {

    private static final Class<?>[] SCRIPTS = {
        // AIs
        EnergySeeds.class,
        Lindvior.class,
        Maguen.class,
        StarStones.class,
        DestroyedTumors.class,

        // NPCs
        AbyssGaze.class,
        EkimusMouth.class,
        FortuneTelling.class,
        GeneralDilios.class,
        Klemis.class,
        Lekon.class,
        Nemo.class,
        Nottingale.class,
        Seyo.class,
        ZealotOfShilen.class,

        // Seed of Annihilation
        SeedOfAnnihilation.class,

        // Instances
        SecretArea.class,
        SeedOfDestruction.class,
        ChamblainsMountedTroop.class,
        GreatWarriorsMountedTroop.class,
        SoldiersMountedTroop.class,
        WarriorsMountedTroop.class,
        HallOfErosionAttack.class,
        HallOfErosionDefence.class,
        HallOfSufferingAttack.class,
        HallOfSufferingDefence.class,
        HeartInfinityAttack.class,
        HeartInfinityDefence.class,

        // Vehicles
        AirShipGludioGracia.class,
        KeucereusNorthController.class,
        KeucereusSouthController.class,
        SoIController.class,
        SoDController.class,
    };

    public static void main(String[] args) {
        log.info("Loading Gracia related scripts.");
        for (Class<?> script : SCRIPTS) {
            try {
                script.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed loading {}:", script.getSimpleName(), e);
            }
        }
    }
}
