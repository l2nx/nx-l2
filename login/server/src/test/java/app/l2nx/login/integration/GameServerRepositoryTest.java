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
            repository.create(77, "-1f2e3d", "10.0.0.1");

            assertThat(JDBC.sql("SELECT hexid, host FROM gameservers WHERE server_id = 77")
                            .query()
                            .singleRow())
                    .containsEntry("hexid", "-1f2e3d")
                    .containsEntry("host", "10.0.0.1");
        }
    }

    @Nested
    class FindHexIds {
        @Test
        void findHexIds_shouldReturnHexIdByServerId() {
            JDBC.sql("INSERT INTO gameservers (server_id, hexid, host) VALUES (78, 'abc', '')")
                    .update();

            assertThat(repository.findHexIds())
                    .containsEntry(78, "abc")
                    .containsEntry(2, "-2ad66b3f483c22be097019f55c8abdf0");
        }
    }
}
