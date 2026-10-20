/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.residence.CastleDoorUpgradeRepository;
import app.l2nx.highfive.repository.residence.CastleFunctionRepository;
import app.l2nx.highfive.repository.residence.CastleStateRepository;
import app.l2nx.highfive.repository.residence.model.CastleState;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class CastleManagerTest {
    private static final int CASTLE_ID = 5;

    private final CastleStateRepository states = mock(CastleStateRepository.class);
    private final CastleManager manager = new CastleManager(
            states,
            mock(CastleFunctionRepository.class),
            mock(CastleDoorUpgradeRepository.class),
            mock(ItemRepository.class));

    @Nested
    class FindState {
        private final CastleState expected =
                CastleState.builder().castleId(CASTLE_ID).registrationOver(true).build();

        @Test
        void findState_shouldReturnInitialState_whenNoStateIsStored() {
            when(states.findById(CASTLE_ID)).thenReturn(Optional.empty());

            final CastleState actual = manager.findState(CASTLE_ID);

            assertThat(actual).isEqualTo(expected);
        }

        @Test
        void findState_shouldReturnInitialState_whenTheReadFails() {
            when(states.findById(CASTLE_ID)).thenThrow(new DataAccessResourceFailureException("down"));

            final CastleState actual = manager.findState(CASTLE_ID);

            assertThat(actual).isEqualTo(expected);
        }
    }
}
