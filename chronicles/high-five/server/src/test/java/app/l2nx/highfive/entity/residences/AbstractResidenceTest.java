/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.xml.SkillTreeData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.MockedStatic;

// The constructor reads the SkillTreeData singleton, whose real construction parses the skill XML files.
@Execution(ExecutionMode.SAME_THREAD)
class AbstractResidenceTest {

    private MockedStatic<SkillTreeData> skillTreeData;

    @BeforeEach
    void stubSkillTreeData() {
        SkillTreeData tree = mock(SkillTreeData.class);
        when(tree.getAvailableResidentialSkills(anyInt())).thenReturn(List.of());
        skillTreeData = mockStatic(SkillTreeData.class);
        skillTreeData.when(SkillTreeData::getInstance).thenReturn(tree);
    }

    @AfterEach
    void releaseSkillTreeData() {
        skillTreeData.close();
    }

    private static AbstractResidence residenceWithId(int residenceId) {
        return new AbstractResidence(residenceId) {
            @Override
            protected void load() {}

            @Override
            protected void initResidenceZone() {}
        };
    }

    @Nested
    class HashCodeContract {

        @Test
        void hashCode_shouldMatch_whenResidenceIdsEqual() {
            AbstractResidence first = residenceWithId(7);
            AbstractResidence second = residenceWithId(7);

            assertThat(first).isEqualTo(second);
            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }

        @Test
        void contains_shouldFindEqualInstance_whenHashSetHoldsOther() {
            Set<AbstractResidence> residences = new HashSet<>();
            residences.add(residenceWithId(8));

            assertThat(residences.contains(residenceWithId(8))).isTrue();
        }

        @Test
        void equals_shouldBeFalse_whenResidenceIdsDiffer() {
            assertThat(residenceWithId(1)).isNotEqualTo(residenceWithId(2));
        }
    }
}
