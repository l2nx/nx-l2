/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

/** Pick the game server from the server list; the client adapter knows which one the run targets. */
public record SelectServer() implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.SELECT_SERVER;
    }
}
