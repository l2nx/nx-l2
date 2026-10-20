/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.variables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.managers.AccountVariablesManager;
import app.l2nx.highfive.repository.account.AccountVariableRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(AccountVariablesTest.LOCK)
class AccountVariablesTest {
    static final String LOCK = "app.l2nx.highfive.managers.AccountVariablesManager";

    private static final UUID ACCOUNT_ID = UUID.fromString("0192f0c4-7a3e-7c1d-9d2a-3b4c5d6e7f80");

    private final AccountVariableRepository repository = mock(AccountVariableRepository.class);

    private AccountVariables load(Map<String, String> stored) {
        when(repository.findAll(ACCOUNT_ID)).thenReturn(stored);
        new AccountVariablesManager(repository);
        return new AccountVariables(ACCOUNT_ID);
    }

    @Nested
    class RestoreMe {
        @Test
        void restoreMe_shouldLeaveVariableUnset_whenStoredValueIsEmpty() {
            final Map<String, String> stored = new HashMap<>();
            stored.put("HWID", null);
            stored.put("PRIME_POINTS", "15");

            final AccountVariables actual = load(stored);

            assertThat(actual.hasVariable("HWID")).isFalse();
            assertThat(actual.getString("HWID", null)).isNull();
            assertThat(actual.getInt("PRIME_POINTS", 0)).isEqualTo(15);
            assertThat(actual.hasChanges()).isFalse();
        }
    }

    @Nested
    class SaveNow {
        @Test
        void saveNow_shouldStoreChangedAndDeletedVariablesOfTheAccount() {
            final AccountVariables variables = load(Map.of("OLD", "1", "KEPT", "2"));
            variables.set("NEW", 3);
            variables.remove("OLD");

            assertThat(variables.saveNow()).isTrue();

            verify(repository).saveChanges(ACCOUNT_ID, Set.of("OLD"), Map.of("NEW", "3"));
            assertThat(variables.hasChanges()).isFalse();
        }
    }
}
