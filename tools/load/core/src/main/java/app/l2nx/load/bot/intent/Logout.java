/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

/** Leave the game; the client adapter then closes the session. */
public record Logout() implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.LOGOUT;
    }
}
