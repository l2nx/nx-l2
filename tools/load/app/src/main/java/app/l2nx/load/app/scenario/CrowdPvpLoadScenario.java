/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.scenario;

import app.l2nx.load.app.seed.model.CharacterKit;
import app.l2nx.load.app.seed.model.CharacterPlan;
import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.ResurrectInPlace;
import app.l2nx.load.bot.model.CharacterTeam;
import app.l2nx.load.bot.observation.ObjectSeen;
import app.l2nx.load.scenario.LoadBehaviour;
import app.l2nx.load.scenario.LoadScenario;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Two teams in the arena zone, where a kill costs no karma. A wall separates the arena from the colosseum restart
 * points, so a dead load bot gets up in place with a Feather of Blessing.
 */
@Component
public final class CrowdPvpLoadScenario implements LoadScenario {
    public static final String ID = "load-crowd-pvp";

    private static final Set<IntentKind> REQUIRES = Collections.unmodifiableSet(
            EnumSet.of(IntentKind.ATTACK, IntentKind.USE_SKILL, IntentKind.RESURRECT_IN_PLACE, IntentKind.MOVE_TO));

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
        return CharacterPlan.ARENA.slot();
    }

    @Override
    public LoadBehaviour behaviour(LoadBotView bot) {
        final int index = bot.identity().index();
        final int team = CharacterTeam.ofIndex(index);
        return new CombatBehaviour(
                bot.position(),
                CharacterKit.of(index).skillId(),
                seen -> seen.kind() == ObjectSeen.Kind.CHARACTER
                        && seen.team() != CharacterTeam.NONE
                        && seen.team() != team,
                false,
                new ResurrectInPlace());
    }
}
