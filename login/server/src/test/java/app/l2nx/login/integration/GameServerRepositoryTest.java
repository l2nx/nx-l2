/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.login.repository.GameServerRepository;
import app.l2nx.login.repository.GameServerRepository.StoredGameServer;
import java.security.SecureRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

class GameServerRepositoryTest extends RepositoryTest {
    private final GameServerRepository repository = new GameServerRepository(JDBC);

    static Stream<Arguments> hexIds() {
        final byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return Stream.of(
                Arguments.of(70, "leading zero byte", new byte[] {0x00, 0x11}),
                Arguments.of(71, "high first byte", new byte[] {(byte) 0xFF, (byte) 0x80}),
                Arguments.of(72, "random 16 bytes", random));
    }

    @Nested
    class Create {
        @Test
        void create_shouldStoreServer() {
            repository.create(77, new byte[] {0x1f, 0x2e, 0x3d});

            assertThat(JDBC.sql("SELECT hex_id FROM game_servers WHERE server_id = 77")
                            .query(byte[].class)
                            .single())
                    .isEqualTo(new byte[] {0x1f, 0x2e, 0x3d});
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {0, 65})
        void create_shouldBeRejected_whenTheHexIdSizeIsOutOfRange(int size) {
            assertThatThrownBy(() -> repository.create(76, new byte[size]))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnEveryServer_withNoChronicleUntilItRegistered() {
            JDBC.sql("INSERT INTO game_servers (server_id, hex_id) VALUES (78, '\\xabcd')")
                    .update();
            JDBC.sql("INSERT INTO game_servers (server_id, hex_id, chronicle) VALUES (79, '\\xdef0', 'high-five')")
                    .update();

            assertThat(repository.findAll())
                    .contains(
                            StoredGameServer.builder()
                                    .serverId(78)
                                    .hexId(new byte[] {(byte) 0xab, (byte) 0xcd})
                                    .build(),
                            StoredGameServer.builder()
                                    .serverId(79)
                                    .hexId(new byte[] {(byte) 0xde, (byte) 0xf0})
                                    .chronicle("high-five")
                                    .build());
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("app.l2nx.login.integration.GameServerRepositoryTest#hexIds")
        void findAll_shouldReturnTheCreatedHexIdByteForByte(int serverId, String name, byte[] hexId) {
            repository.create(serverId, hexId);

            assertThat(repository.findAll())
                    .contains(StoredGameServer.builder()
                            .serverId(serverId)
                            .hexId(hexId)
                            .build());
        }
    }

    @Nested
    class UpdateChronicle {
        @Test
        void updateChronicle_shouldStoreTheChronicleOfThatServer() {
            repository.create(80, new byte[] {0x0a});

            repository.updateChronicle(80, "high-five");

            assertThat(JDBC.sql("SELECT chronicle FROM game_servers WHERE server_id = 80")
                            .query(String.class)
                            .single())
                    .isEqualTo("high-five");
        }
    }
}
