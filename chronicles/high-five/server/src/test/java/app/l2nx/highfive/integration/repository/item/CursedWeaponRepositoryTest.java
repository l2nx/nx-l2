/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.CursedWeaponRepository;
import app.l2nx.highfive.repository.item.model.CursedWeaponRow;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("cursed_weapons")
class CursedWeaponRepositoryTest extends RepositoryTest {
    private static final int ZARICHE = 8190;
    private static final int AKAMANAH = 8689;

    private final CursedWeaponRepository repository = new CursedWeaponRepository(JDBC, registry);

    @Nested
    class Save {
        @Test
        void save_shouldInsertThenReplaceHolder() {
            final CursedWeaponRow first = weapon(ZARICHE, insertCharacter());
            final CursedWeaponRow second =
                    weapon(ZARICHE, insertCharacter()).toBuilder().nbKills(12).build();
            repository.save(first);

            repository.save(second);

            assertThat(repository.findAll())
                    .filteredOn(row -> row.itemId() == ZARICHE)
                    .containsExactly(second);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldTranslateHolderToHandle() {
            final UUID holder = insertCharacter();
            final CursedWeaponRow weapon = weapon(AKAMANAH, holder);
            repository.save(weapon);

            assertThat(repository.findAll())
                    .filteredOn(row -> row.itemId() == AKAMANAH)
                    .singleElement()
                    .extracting(CursedWeaponRow::playerId, CursedWeaponRow::endTime)
                    .containsExactly(registry.handleOf(holder), NOW);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveWeapon() {
            repository.save(weapon(ZARICHE, insertCharacter()));

            repository.delete(ZARICHE);

            assertThat(repository.findAll()).noneMatch(row -> row.itemId() == ZARICHE);
        }
    }

    private CursedWeaponRow weapon(int itemId, UUID holder) {
        return CursedWeaponRow.builder()
                .itemId(itemId)
                .playerId(registry.handleOf(holder))
                .playerKarma(100)
                .playerPkKills(3)
                .nbKills(0)
                .endTime(NOW)
                .build();
    }
}
