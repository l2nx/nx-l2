/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.world;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.world.GlobalVariableRepository;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("global_variables")
class GlobalVariableRepositoryTest extends RepositoryTest {
    private final GlobalVariableRepository repository = new GlobalVariableRepository(JDBC, JDBC_TEMPLATE);

    @Nested
    class ReplaceAll {
        @Test
        void replaceAll_shouldStoreValuesAsText_whenVariablesGiven() {
            TRANSACTIONS.executeWithoutResult(
                    status -> repository.replaceAll(Map.of("HBLevel", 5, "fishChampionshipEnd", 1_700_000_000_000L)));

            assertThat(repository.findAll())
                    .containsOnly(Map.entry("HBLevel", "5"), Map.entry("fishChampionshipEnd", "1700000000000"));
        }

        @Test
        void replaceAll_shouldSkipEmptyAndNullValues_whenGiven() {
            final Map<String, Object> variables = new HashMap<>();
            variables.put("kept", "x");
            variables.put("empty", "");
            variables.put("missing", null);

            TRANSACTIONS.executeWithoutResult(status -> repository.replaceAll(variables));

            assertThat(repository.findAll()).containsExactly(Map.entry("kept", "x"));
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnStoredVariables_whenRowsExist() {
            JDBC.sql("DELETE FROM global_variables").update();
            JDBC.sql("INSERT INTO global_variables (name, content) VALUES ('HBLevel', '11'), ('HBTrust', '4000000')")
                    .update();

            assertThat(repository.findAll()).containsOnly(Map.entry("HBLevel", "11"), Map.entry("HBTrust", "4000000"));
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldRemoveEveryVariable_whenCalled() {
            JDBC.sql("INSERT INTO global_variables (name, content) VALUES ('gone', '1') ON CONFLICT DO NOTHING")
                    .update();

            repository.deleteAll();

            assertThat(repository.findAll()).isEmpty();
        }
    }
}
