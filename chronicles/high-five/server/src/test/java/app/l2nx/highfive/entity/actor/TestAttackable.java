/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.util.StatSet;
import java.util.Map;

/** An {@link Attackable} over a bare template: no datapack, no skills. Build it on the world loop. */
class TestAttackable extends Attackable {
    static final int NPC_ID = 20_545;

    TestAttackable() {
        super(template());
    }

    private static NpcTemplate template() {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", NPC_ID, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        return template;
    }
}
