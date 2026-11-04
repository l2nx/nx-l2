/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

import app.l2nx.load.bot.model.Position;

/** Seeding only: the admin teleport of the load bot itself, confirmed by the teleport it causes. */
public record AdminTeleport(Position destination) implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.ADMIN_TELEPORT;
    }

    @Override
    public Object correlation() {
        return destination;
    }
}
