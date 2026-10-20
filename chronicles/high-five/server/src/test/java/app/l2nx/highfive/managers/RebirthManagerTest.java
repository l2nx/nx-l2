/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.repository.character.CharacterRebirthRepository;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RebirthManagerTest {
    private static final int CHARACTER_ID = 268_435_458;

    private final CharacterRebirthRepository rebirths = mock(CharacterRebirthRepository.class);
    private final RebirthManager manager = new RebirthManager(rebirths);

    @Nested
    class GetSelectedSkills {
        @Test
        void getSelectedSkills_shouldReturnChangeableList_whenNothingIsStored() {
            when(rebirths.findSelectedSkillIds(CHARACTER_ID)).thenReturn(List.of());

            final List<Integer> actual = manager.getSelectedSkills(CHARACTER_ID);
            actual.add(1204);

            assertThat(actual).containsExactly(1204);
        }
    }
}
