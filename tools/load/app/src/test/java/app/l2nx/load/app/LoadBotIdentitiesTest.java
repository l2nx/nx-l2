/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.load.bot.model.LoadBotIdentity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoadBotIdentitiesTest {
    @Nested
    class Apply {
        @Test
        void apply_shouldNameTheAccountByPrefixAndIndex() {
            final LoadBotIdentities identities = new LoadBotIdentities(accounts("load", "secret"), 1);

            final LoadBotIdentity actual = identities.apply(42);

            assertThat(actual)
                    .isEqualTo(LoadBotIdentity.builder()
                            .index(42)
                            .account("load42")
                            .password("secret")
                            .characterSlot(1)
                            .build());
        }
    }

    private static LoadProperties.Accounts accounts(String prefix, String password) {
        return LoadProperties.Accounts.builder()
                .prefix(prefix)
                .password(password)
                .build();
    }
}
