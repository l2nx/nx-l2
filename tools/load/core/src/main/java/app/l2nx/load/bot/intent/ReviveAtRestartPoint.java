/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

public record ResurrectAtRestartPoint() implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.RESURRECT_AT_RESTART_POINT;
    }
}
