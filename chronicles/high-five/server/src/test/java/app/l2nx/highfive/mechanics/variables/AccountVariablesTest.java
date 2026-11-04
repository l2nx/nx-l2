/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.variables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.managers.AccountVariablesManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.account.AccountVariableRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock(AccountVariablesTest.LOCK)
class AccountVariablesTest {
    static final String LOCK = "app.l2nx.highfive.managers.AccountVariablesManager";

    private static final UUID ACCOUNT_ID = UUID.fromString("0192f0c4-7a3e-7c1d-9d2a-3b4c5d6e7f80");

    private final AccountVariableRepository repository = mock(AccountVariableRepository.class);

    private AccountVariables load(Map<String, String> stored) {
        when(repository.findAll(ACCOUNT_ID)).thenReturn(stored);
        new AccountVariablesManager(repository);
        return WorldLoopExtension.callInPass(() -> new AccountVariables(ACCOUNT_ID));
    }

    @Nested
    class RestoreMe {
        @Test
        void restoreMe_shouldLeaveVariableUnset_whenStoredValueIsEmpty() {
            final Map<String, String> stored = new HashMap<>();
            stored.put("HWID", null);
            stored.put("PRIME_POINTS", "15");

            final AccountVariables actual = load(stored);

            WorldLoopExtension.runInPass(() -> {
                assertThat(actual.hasVariable("HWID")).isFalse();
                assertThat(actual.getString("HWID", null)).isNull();
                assertThat(actual.getInt("PRIME_POINTS", 0)).isEqualTo(15);
                assertThat(actual.hasChanges()).isFalse();
            });
        }
    }

    @Nested
    class SaveNow {
        @Test
        void saveNow_shouldStoreChangedAndDeletedVariablesOfTheAccount() {
            final AccountVariables variables = load(Map.of("OLD", "1", "KEPT", "2"));

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                variables.set("NEW", 3);
                variables.remove("OLD");
                return variables.saveNow();
            });

            assertThat(actual).isTrue();
            verify(repository).saveChanges(ACCOUNT_ID, Set.of("OLD"), Map.of("NEW", "3"));
            assertThat(WorldLoopExtension.callInPass(variables::hasChanges)).isFalse();
        }
    }

    @Nested
    class StoreMe {
        @Test
        void storeMe_shouldSaveOnTheLoopAMinuteLater() {
            final AccountVariables variables = load(Map.of());

            WorldLoopExtension.runInPass(() -> {
                variables.set("NEW", 3);
                variables.storeMe();
            });

            verify(repository, never()).saveChanges(eq(ACCOUNT_ID), any(), anyMap());
            WorldLoopExtension.passAfter(Duration.ofMinutes(1));
            verify(repository).saveChanges(ACCOUNT_ID, Set.of(), Map.of("NEW", "3"));
        }

        @Test
        void storeMe_shouldRideOnTheScheduledSave_whenASaveIsAlreadyScheduled() {
            final AccountVariables variables = load(Map.of());

            WorldLoopExtension.runInPass(() -> {
                variables.set("FIRST", 1);
                variables.storeMe();
                variables.set("SECOND", 2);
                variables.storeMe();
            });

            verify(repository, never()).saveChanges(eq(ACCOUNT_ID), any(), anyMap());
            WorldLoopExtension.passAfter(Duration.ofMinutes(1));
            verify(repository, times(1)).saveChanges(ACCOUNT_ID, Set.of(), Map.of("FIRST", "1", "SECOND", "2"));
        }

        @Test
        void storeMe_shouldSaveNothing_whenNothingChanged() {
            final AccountVariables variables = load(Map.of("OLD", "1"));

            final boolean actual = WorldLoopExtension.callInPass(variables::storeMe);

            assertThat(actual).isFalse();
            WorldLoopExtension.passAfter(Duration.ofMinutes(1));
            verify(repository, never()).saveChanges(eq(ACCOUNT_ID), any(), anyMap());
        }
    }
}
