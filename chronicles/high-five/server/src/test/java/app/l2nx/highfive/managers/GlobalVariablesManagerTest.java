/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.world.GlobalVariableRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class GlobalVariablesManagerTest {
    private final GlobalVariableRepository repository = mock(GlobalVariableRepository.class);

    private final GlobalVariablesManager manager = new GlobalVariablesManager(repository);

    @Test
    void storeMe_shouldReplaceEveryRowAtOnce_whenAVariableChanged() {
        final boolean actual = WorldLoopExtension.callInPass(() -> {
            manager.set("KEPT", "1", false);
            manager.set("NEW", 2);
            return manager.storeMe();
        });

        assertThat(actual).isTrue();
        verify(repository).replaceAll(Map.of("KEPT", "1", "NEW", 2));
        assertThat(WorldLoopExtension.callInPass(manager::hasChanges)).isFalse();
    }

    @Test
    void storeMe_shouldWriteNothing_whenNothingChanged() {
        final boolean actual = WorldLoopExtension.callInPass(manager::storeMe);

        assertThat(actual).isFalse();
        verify(repository, never()).replaceAll(anyMap());
    }

    @Test
    void storeMe_shouldKeepTheChangesPending_whenTheWriteFails() {
        doThrow(new DataAccessResourceFailureException("write failed"))
                .when(repository)
                .replaceAll(anyMap());

        final boolean actual = WorldLoopExtension.callInPass(() -> {
            manager.set("NEW", 2);
            return manager.storeMe();
        });

        assertThat(actual).isFalse();
        assertThat(WorldLoopExtension.callInPass(manager::hasChanges)).isTrue();
    }
}
