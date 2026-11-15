/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.AccountVariablesManager;
import app.l2nx.highfive.mechanics.variables.AccountVariables;
import app.l2nx.highfive.repository.account.AccountVariableRepository;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("app.l2nx.highfive.managers.AccountVariablesManager")
class LoadedGameAccountsTest {
    private static final String POINTS = "PRIME_POINTS";

    private final AccountVariableRepository repository = mock(AccountVariableRepository.class);
    private final LoadedGameAccounts registry = new LoadedGameAccounts();
    private final UUID accountId = EntityIds.next();
    private final Player trader = mock(Player.class);
    private final Player playing = mock(Player.class);

    @BeforeEach
    void setUp() {
        new AccountVariablesManager(repository);
    }

    private AccountVariables attach(Player player, Map<String, String> snapshot) {
        return WorldLoopExtension.callInPass(() -> registry.attach(player, accountId, snapshot));
    }

    private void spend(AccountVariables variables, int points) {
        WorldLoopExtension.runInPass(() -> variables.set(POINTS, points));
    }

    private int points(AccountVariables variables) {
        return WorldLoopExtension.callInPass(() -> variables.getInt(POINTS, 0));
    }

    private boolean held() {
        return WorldLoopExtension.callInPass(() -> registry.isLoaded(accountId));
    }

    @Nested
    class Attach {
        @Test
        void attach_shouldShareOneInstance_betweenTheLoadedCharactersOfTheAccount() {
            final AccountVariables first = attach(trader, Map.of(POINTS, "100"));

            final AccountVariables actual = attach(playing, Map.of(POINTS, "100"));

            assertThat(actual).isSameAs(first);
        }

        @Test
        void attach_shouldDropTheSnapshot_whenTheAccountHasALiveInstance() {
            final AccountVariables live = attach(trader, Map.of(POINTS, "100"));
            spend(live, 40);

            final AccountVariables actual = attach(playing, Map.of(POINTS, "100"));

            assertThat(points(actual)).isEqualTo(40);
        }
    }

    @Nested
    class Detach {
        @Test
        void detach_shouldSaveAndKeepTheInstance_whenAnotherCharacterHoldsIt() {
            final AccountVariables live = attach(trader, Map.of(POINTS, "100"));
            attach(playing, Map.of(POINTS, "100"));
            spend(live, 40);

            WorldLoopExtension.runInPass(() -> registry.detach(trader, accountId));

            verify(repository).saveChanges(accountId, Set.of(), Map.of(POINTS, "40"));
            assertThat(held()).isTrue();
            assertThat(attach(trader, Map.of(POINTS, "100"))).isSameAs(live);
        }

        @Test
        void detach_shouldTakeTheAccountOutOfMemory_whenTheLastCharacterLeaves() {
            attach(trader, Map.of());

            WorldLoopExtension.runInPass(() -> registry.detach(trader, accountId));

            assertThat(held()).isFalse();
        }

        @Test
        void detach_shouldKeepTheInstanceInMemory_whenItsSaveFails() {
            final AccountVariables live = attach(trader, Map.of(POINTS, "100"));
            spend(live, 40);
            doThrow(new DataAccessResourceFailureException("database down"))
                    .when(repository)
                    .saveChanges(any(), any(), anyMap());

            WorldLoopExtension.runInPass(() -> registry.detach(trader, accountId));

            assertThat(held()).isTrue();
            final AccountVariables actual = attach(playing, Map.of(POINTS, "100"));
            assertThat(actual).isSameAs(live);
            assertThat(points(actual)).isEqualTo(40);
        }

        @Test
        void detach_shouldRetryTheSaveAndThenForgetTheAccount_whenTheSaveFailed() {
            final AccountVariables live = attach(trader, Map.of(POINTS, "100"));
            spend(live, 40);
            doThrow(new DataAccessResourceFailureException("database down"))
                    .doNothing()
                    .when(repository)
                    .saveChanges(any(), any(), anyMap());
            WorldLoopExtension.runInPass(() -> registry.detach(trader, accountId));

            WorldLoopExtension.passAfter(LoadedGameAccounts.SAVE_RETRY);

            verify(repository, times(2)).saveChanges(accountId, Set.of(), Map.of(POINTS, "40"));
            assertThat(held()).isFalse();
        }

        @Test
        void detach_shouldSaveNothing_whenThePlayerHoldsNoInstance() {
            attach(trader, Map.of());

            WorldLoopExtension.runInPass(() -> registry.detach(playing, accountId));

            verify(repository, never()).saveChanges(any(), any(), anyMap());
            assertThat(held()).isTrue();
        }
    }

    @Nested
    class Pin {
        @Test
        void pin_shouldKeepTheReleasedInstance_forALoadThatReadItsSnapshotBeforeTheRelease() {
            final AccountVariables live = attach(trader, Map.of(POINTS, "100"));
            WorldLoopExtension.runInPass(() -> registry.pin(accountId));
            spend(live, 40);
            WorldLoopExtension.runInPass(() -> registry.detach(trader, accountId));

            final AccountVariables actual = attach(playing, Map.of(POINTS, "100"));
            WorldLoopExtension.runInPass(() -> registry.unpin(accountId));

            assertThat(actual).isSameAs(live);
            assertThat(points(actual)).isEqualTo(40);
            assertThat(held()).as("the built character holds it").isTrue();
        }

        @Test
        void unpin_shouldTakeTheAccountOutOfMemory_whenNoCharacterHoldsIt() {
            WorldLoopExtension.runInPass(() -> registry.pin(accountId));

            WorldLoopExtension.runInPass(() -> registry.unpin(accountId));

            assertThat(held()).isFalse();
        }

        @Test
        void unpin_shouldFail_whenTheAccountIsNotPinned() {
            attach(trader, Map.of());

            assertThatThrownBy(() -> WorldLoopExtension.runInPass(() -> registry.unpin(accountId)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
