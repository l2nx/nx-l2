/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.repository.GameServerRepository;
import app.l2nx.login.repository.GameServerRepository.StoredGameServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class GameServerTableTest {
    @TempDir
    private Path datapackRoot;

    @Nested
    class Load {
        @Test
        void load_shouldReadServerNames_fromDatapackRoot() throws IOException {
            Files.createDirectories(datapackRoot.resolve("data"));
            Files.writeString(datapackRoot.resolve("data/servername.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <servers_list xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="servername.xsd">
                	<server id="1" name="Bartz" />
                	<server id="9" name="Teon (EURO)" />
                </servers_list>
                """);
            final GameServerTable table = new GameServerTable(
                    mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));

            table.load();

            assertThat(table.getServerNames()).containsOnly(entry(1, "Bartz"), entry(9, "Teon (EURO)"));
        }

        @Test
        void load_shouldLeaveServerNamesEmpty_whenFileMissing() {
            final GameServerTable table = new GameServerTable(
                    mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));

            table.load();

            assertThat(table.getServerNames()).isEmpty();
        }

        @Test
        void load_shouldKeepTheStoredChronicle_andNoneForAServerThatNeverRegistered() {
            final GameServerRepository repository = mock(GameServerRepository.class);
            when(repository.findAll())
                    .thenReturn(List.of(
                            StoredGameServer.builder()
                                    .serverId(1)
                                    .hexId(new byte[] {0x11})
                                    .chronicle("high-five")
                                    .build(),
                            StoredGameServer.builder()
                                    .serverId(2)
                                    .hexId(new byte[] {0x22})
                                    .build()));
            final GameServerTable table =
                    new GameServerTable(repository, TestLoginProperties.create(datapackRoot.toFile()));

            table.load();

            assertThat(table.getRegisteredGameServerById(1).getChronicle()).isEqualTo("high-five");
            assertThat(table.getRegisteredGameServerById(2).getChronicle()).isNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.login.data.GameServerTableTest#hexIds")
        void load_shouldKeepTheStoredHexIdByteForByte(String name, byte[] hexId) {
            final GameServerRepository repository = mock(GameServerRepository.class);
            when(repository.findAll())
                    .thenReturn(List.of(
                            StoredGameServer.builder().serverId(1).hexId(hexId).build()));
            final GameServerTable table =
                    new GameServerTable(repository, TestLoginProperties.create(datapackRoot.toFile()));

            table.load();

            assertThat(table.getRegisteredGameServerById(1).getHexId()).isEqualTo(hexId);
        }
    }

    @Nested
    class RegisterServerOnDb {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.login.data.GameServerTableTest#hexIds")
        void registerServerOnDB_shouldStoreTheHexIdByteForByte(String name, byte[] hexId) {
            final GameServerRepository repository = mock(GameServerRepository.class);
            final GameServerTable table =
                    new GameServerTable(repository, TestLoginProperties.create(datapackRoot.toFile()));
            table.load();

            table.registerServerOnDB(hexId, 1);

            verify(repository).create(1, hexId);
            assertThat(table.getRegisteredGameServerById(1).getHexId()).isEqualTo(hexId);
        }
    }

    static Stream<Arguments> hexIds() {
        final byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return Stream.of(
                Arguments.of("leading zero byte", new byte[] {0x00, 0x11}),
                Arguments.of("high first byte", new byte[] {(byte) 0xFF, (byte) 0x80}),
                Arguments.of("random 16 bytes", random));
    }

    @Nested
    class StoreChronicle {
        @Test
        void storeChronicle_shouldWriteTheChronicleOfTheServer() {
            final GameServerRepository repository = mock(GameServerRepository.class);
            final GameServerTable table =
                    new GameServerTable(repository, TestLoginProperties.create(datapackRoot.toFile()));

            table.storeChronicle(1, "high-five");

            verify(repository).updateChronicle(1, "high-five");
        }
    }

    @Nested
    class IsOpenTo {
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"high-five, true", "essence, false", ", false"})
        void isOpenTo_shouldAdmitOnlyAVariantThatEntersTheChronicle(String chronicle, boolean expected) {
            final GameServerInfo info = new GameServerInfo(1, new byte[] {1});
            info.setChronicle(chronicle);

            assertThat(info.isOpenTo(LoginVariant.C621)).isEqualTo(expected);
        }
    }

    @Nested
    class Register {
        @Test
        void register_shouldNotBreakAnIterationInProgress() {
            final GameServerTable table = new GameServerTable(
                    mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
            table.load();
            table.register(1, new GameServerInfo(1, new byte[] {1}));
            table.register(3, new GameServerInfo(3, new byte[] {3}));
            final Iterator<GameServerInfo> servers =
                    table.getRegisteredGameServers().values().iterator();
            servers.next();

            table.register(2, new GameServerInfo(2, new byte[] {2}));

            assertThatCode(() -> servers.forEachRemaining(server -> {})).doesNotThrowAnyException();
        }

        @Test
        void register_shouldKeepTheFirstServer_whenTheIdIsTaken() {
            final GameServerTable table = new GameServerTable(
                    mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
            table.load();
            final GameServerInfo first = new GameServerInfo(1, new byte[] {1});
            table.register(1, first);

            final boolean actual = table.register(1, new GameServerInfo(1, new byte[] {2}));

            assertThat(actual).isFalse();
            assertThat(table.getRegisteredGameServerById(1)).isSameAs(first);
        }
    }
}
