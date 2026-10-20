/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.login.repository.GameServerRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameServerRepositoryTest extends RepositoryTest {
    private final GameServerRepository repository = new GameServerRepository(JDBC);

    @Nested
    class Create {
        @Test
        void create_shouldStoreServer() {
            repository.create(77, "-1f2e3d");

            assertThat(JDBC.sql("SELECT hex_id FROM game_servers WHERE server_id = 77")
                            .query(String.class)
                            .single())
                    .isEqualTo("-1f2e3d");
        }
    }

    @Nested
    class FindHexIds {
        @Test
        void findHexIds_shouldReturnHexIdByServerId() {
            JDBC.sql("INSERT INTO game_servers (server_id, hex_id) VALUES (78, 'abc')")
                    .update();

            assertThat(repository.findHexIds()).containsEntry(78, "abc");
        }
    }
}
