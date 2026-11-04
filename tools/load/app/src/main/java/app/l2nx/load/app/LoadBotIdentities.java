/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import app.l2nx.load.bot.model.LoadBotIdentity;
import java.util.function.IntFunction;

final class LoadBotIdentities implements IntFunction<LoadBotIdentity> {
    private final LoadProperties.Accounts accounts;
    private final int characterSlot;

    LoadBotIdentities(LoadProperties.Accounts accounts, int characterSlot) {
        this.accounts = accounts;
        this.characterSlot = characterSlot;
    }

    @Override
    public LoadBotIdentity apply(int index) {
        return LoadBotIdentity.builder()
                .index(index)
                .account(accounts.prefix() + index)
                .password(accounts.password())
                .characterSlot(characterSlot)
                .build();
    }
}
