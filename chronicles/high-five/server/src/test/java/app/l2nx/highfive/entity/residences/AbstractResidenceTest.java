/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

// The constructor reads the SkillTreeData singleton, whose real construction parses the skill XML files.
@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
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
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AbstractResidence> call) {
            final AbstractResidence residence = residenceWithId(5);

            assertThatThrownBy(() -> call.accept(residence))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AbstractResidence>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getResidenceId (accessor)", AbstractResidence::getResidenceId),
                    Named.of("getName", AbstractResidence::getName),
                    Named.of("setName", r -> r.setName("Gludio")),
                    Named.of("getResidenceZone", AbstractResidence::getResidenceZone),
                    Named.of("giveResidentialSkills", r -> r.giveResidentialSkills(null)),
                    Named.of("removeResidentialSkills", r -> r.removeResidentialSkills(null)));
        }
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
