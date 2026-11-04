/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

/**
 * Seeding only: an admin command as typed after {@code //}. The server has no common answer, so the adapter confirms
 * on sending; a last {@link AdminTeleport} proves the earlier commands ran.
 */
public record AdminCommand(String command) implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.ADMIN_COMMAND;
    }
}
