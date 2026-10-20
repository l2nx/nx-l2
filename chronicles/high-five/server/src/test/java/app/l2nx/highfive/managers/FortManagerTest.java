/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.repository.residence.FortDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.FortFunctionRepository;
import app.l2nx.highfive.repository.residence.FortStateRepository;
import app.l2nx.highfive.repository.residence.model.FortState;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FortManagerTest {
    private static final int FORT_ID = 101;

    private final FortStateRepository states = mock(FortStateRepository.class);
    private final FortManager manager =
            new FortManager(states, mock(FortFunctionRepository.class), mock(FortDoorUpgradeRepository.class));

    @Nested
    class FindState {
        @Test
        void findState_shouldReturnUnownedFortWithoutContract_whenNoStateIsStored() {
            when(states.findById(FORT_ID)).thenReturn(Optional.empty());

            final FortState actual = manager.findState(FORT_ID);

            assertThat(actual).isEqualTo(FortState.builder().fortId(FORT_ID).build());
        }
    }
}
