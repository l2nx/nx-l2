/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.intent;

import app.l2nx.load.bot.model.Position;

public record MoveTo(Position destination) implements Intent {
    @Override
    public IntentKind kind() {
        return IntentKind.MOVE_TO;
    }

    @Override
    public Object correlation() {
        return destination;
    }
}
