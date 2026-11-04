/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

/** Attack until the target dies: the server repeats the hits, so a live target is never attacked again. */
public record Attack(int targetObjectId) implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.ATTACK;
    }

    @Override
    public Object correlation() {
        return targetObjectId;
    }
}
