/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario;

import app.l2nx.load.bot.LoadBotView;
import app.l2nx.load.bot.intent.IntentKind;
import java.util.Set;

/** Ids carry the {@code load-} prefix. */
public interface LoadScenario {
    String id();

    /** Intent kinds the behaviours send; a protocol missing any of them fails the run before it starts. */
    Set<IntentKind> requires();

    /** The behaviour of one load bot, created when it first reaches {@code IN_GAME}. */
    LoadBehaviour behaviour(LoadBotView bot);
}
