/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterQuestRepository;
import app.l2nx.highfive.repository.character.model.CharacterQuestVariable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterQuestRepositoryTest extends RepositoryTest {
    private static final String STATE = "<state>";

    private final CharacterQuestRepository repository = new CharacterQuestRepository(JDBC, registry);
    private int objectId;

    @BeforeEach
    void setUp() {
        objectId = registry.handleOf(insertCharacter());
        repository.save(objectId, "Q00001_A", STATE, "Started");
        repository.save(objectId, "Q00001_A", "cond", "2");
        repository.save(objectId, "Q00002_B", STATE, "Completed");
    }

    @Nested
    class FindStates {
        @Test
        void findStates_shouldReturnStateOfEveryQuest() {
            assertThat(repository.findStates(objectId))
                    .containsExactly(variable("Q00001_A", STATE, "Started"), variable("Q00002_B", STATE, "Completed"));
        }

        @Test
        void findStates_shouldFilterByState() {
            assertThat(repository.findStates(objectId, "Completed"))
                    .containsExactly(variable("Q00002_B", STATE, "Completed"));
        }
    }

    @Nested
    class FindVariables {
        @Test
        void findVariables_shouldSkipStates() {
            assertThat(repository.findVariables(objectId)).containsExactly(variable("Q00001_A", "cond", "2"));
        }

        @Test
        void findVariables_shouldReturnEveryVariableOfTheQuest() {
            assertThat(repository.findVariables(objectId, "Q00001_A"))
                    .containsExactlyInAnyOrder(
                            variable("Q00001_A", STATE, "Started"), variable("Q00001_A", "cond", "2"));
        }
    }

    @Nested
    class Save {
        @Test
        void save_shouldReplaceContent_whenVariableStored() {
            repository.save(objectId, "Q00001_A", "cond", "3");

            assertThat(repository.findVariables(objectId)).containsExactly(variable("Q00001_A", "cond", "3"));
        }

        @Test
        void save_shouldStoreNull_whenContentEmpty() {
            repository.save(objectId, "Q00001_A", "cond", "");

            assertThat(repository.findVariables(objectId)).containsExactly(variable("Q00001_A", "cond", null));
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldChangeStoredVariable() {
            repository.update(objectId, "Q00001_A", STATE, "Completed");

            assertThat(repository.findStates(objectId, "Completed")).hasSize(2);
        }

        @Test
        void update_shouldWriteNothing_whenVariableMissing() {
            repository.update(objectId, "Q00001_A", "missing", "1");

            assertThat(repository.findVariables(objectId, "Q00001_A")).hasSize(2);
        }
    }

    @Nested
    class DeleteVariable {
        @Test
        void deleteVariable_shouldRemoveOnlyTheVariable() {
            repository.deleteVariable(objectId, "Q00001_A", "cond");

            assertThat(repository.findVariables(objectId, "Q00001_A"))
                    .containsExactly(variable("Q00001_A", STATE, "Started"));
        }
    }

    @Nested
    class DeleteQuest {
        @Test
        void deleteQuest_shouldRemoveEveryVariableOfTheQuest() {
            repository.deleteQuest(objectId, "Q00001_A");

            assertThat(repository.findVariables(objectId, "Q00001_A")).isEmpty();
            assertThat(repository.findStates(objectId)).hasSize(1);
        }
    }

    @Nested
    class DeleteQuestKeepingState {
        @Test
        void deleteQuestKeepingState_shouldKeepOnlyTheState() {
            repository.deleteQuestKeepingState(objectId, "Q00001_A");

            assertThat(repository.findVariables(objectId, "Q00001_A"))
                    .containsExactly(variable("Q00001_A", STATE, "Started"));
        }
    }

    private static CharacterQuestVariable variable(String questName, String name, String content) {
        return CharacterQuestVariable.builder()
                .questName(questName)
                .name(name)
                .content(content)
                .build();
    }
}
