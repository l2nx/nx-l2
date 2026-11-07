/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import app.l2nx.load.app.seed.model.CharacterKit;
import app.l2nx.load.app.seed.model.CharacterPlan;
import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.ReviveAtRestartPoint;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class PveLoadScenario implements LoadScenario {
    public static final String ID = "load-pve";

    private static final Set<IntentKind> REQUIRES = Collections.unmodifiableSet(EnumSet.of(
            IntentKind.ATTACK, IntentKind.USE_SKILL, IntentKind.REVIVE_AT_RESTART_POINT, IntentKind.MOVE_TO));

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<IntentKind> requires() {
        return REQUIRES;
    }

    @Override
    public int characterSlot() {
        return CharacterPlan.HUNTING.slot();
    }

    @Override
    public LoadBehaviour behaviour(LoadBotView bot) {
        return new CombatBehaviour(
                bot.position(),
                CharacterKit.of(bot.identity().index()).skillId(),
                seen -> seen.kind() == ObjectSeen.Kind.MONSTER,
                true,
                new ReviveAtRestartPoint());
    }
}
