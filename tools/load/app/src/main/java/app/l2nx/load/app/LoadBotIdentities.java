/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.bot.model.LoadBotIdentity;
import java.util.function.IntFunction;

/** Load bot {@code i} of a run logs in as {@code <prefix><i>} with the shared password and plays character slot 0. */
final class LoadBotIdentities implements IntFunction<LoadBotIdentity> {
    static final int CHARACTER_SLOT = 0;

    private final LoadProperties.Accounts accounts;

    LoadBotIdentities(LoadProperties.Accounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public LoadBotIdentity apply(int index) {
        return LoadBotIdentity.builder()
                .index(index)
                .account(accounts.prefix() + index)
                .password(accounts.password())
                .characterSlot(CHARACTER_SLOT)
                .build();
    }
}
