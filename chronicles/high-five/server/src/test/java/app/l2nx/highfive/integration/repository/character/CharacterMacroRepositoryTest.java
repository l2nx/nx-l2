/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.character;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.character.CharacterMacroRepository;
import app.l2nx.highfive.repository.character.model.CharacterMacro;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CharacterMacroRepositoryTest extends RepositoryTest {
    private static final CharacterMacro MACRO = CharacterMacro.builder()
            .macroId(1000)
            .icon(3)
            .name("Buff")
            .description("Self buffs")
            .acronym("BF")
            .commands("1,1204,0;3,0,0,/target;")
            .build();

    private final CharacterMacroRepository repository = new CharacterMacroRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldStoreMacro() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.save(objectId, MACRO);

            assertThat(repository.findAll(objectId)).containsExactly(MACRO);
        }

        @Test
        void save_shouldStoreNull_whenTextsEmpty() {
            final int objectId = registry.handleOf(insertCharacter());

            repository.save(
                    objectId,
                    MACRO.toBuilder().description("").acronym("").commands("").build());

            assertThat(repository.findAll(objectId))
                    .containsExactly(MACRO.toBuilder()
                            .description(null)
                            .acronym(null)
                            .commands(null)
                            .build());
        }

        @Test
        void save_shouldReplaceMacro_whenIdStored() {
            final int objectId = registry.handleOf(insertCharacter());
            repository.save(objectId, MACRO);
            final CharacterMacro changed =
                    MACRO.toBuilder().name("Heal").icon(4).build();

            repository.save(objectId, changed);

            assertThat(repository.findAll(objectId)).containsExactly(changed);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldBeEmpty_whenNoMacros() {
            assertThat(repository.findAll(registry.handleOf(insertCharacter()))).isEmpty();
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveTheMacro() {
            final int objectId = registry.handleOf(insertCharacter());
            final CharacterMacro kept = MACRO.toBuilder().macroId(1001).build();
            repository.save(objectId, MACRO);
            repository.save(objectId, kept);

            repository.delete(objectId, MACRO.macroId());

            assertThat(repository.findAll(objectId)).containsExactly(kept);
        }
    }
}
