/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gameserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.GameServerRepository.StoredGameServer;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gameserver.model.ListingStatus;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.LoginVariant;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

            assertThat(table.getServerNameById(1)).isEqualTo("Bartz");
            assertThat(table.getServerNameById(9)).isEqualTo("Teon (EURO)");
            assertThat(table.getServerNameById(2)).isNull();
        }

        @Test
        void load_shouldLeaveServerNamesEmpty_whenFileMissing() {
            final GameServerTable table = new GameServerTable(
                    mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));

            table.load();

            assertThat(table.getServerNameById(1)).isNull();
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
        @MethodSource("app.l2nx.login.gameserver.GameServerTableTest#hexIds")
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

    static Stream<Arguments> hexIds() {
        final byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return Stream.of(
                Arguments.of("leading zero byte", new byte[] {0x00, 0x11}),
                Arguments.of("high first byte", new byte[] {(byte) 0xFF, (byte) 0x80}),
                Arguments.of("random 16 bytes", random));
    }

    @Nested
    class CanLogin {
        @ParameterizedTest(name = "{0}, access level {1} -> {2}")
        @CsvSource({
            "AUTO, 0, true",
            "NORMAL, -1, false",
            "DOWN, 1, false",
            "GM_ONLY, 0, false",
            "GM_ONLY, 1, true",
        })
        void canLogin_shouldAdmitByTheListingStatusAndAccessLevel(
                ListingStatus status, int accessLevel, boolean expected) {
            final GameServerInfo info = new GameServerInfo(1, new byte[] {1});
            info.setStatus(status);
            info.setMaxPlayers(100);
            final LoginClient client = mock(LoginClient.class);
            when(client.getAccessLevel()).thenReturn(accessLevel);

            final boolean actual = info.canLogin(client);

            assertThat(actual).isEqualTo(expected);
        }
    }

    @Nested
    class SetDown {
        @Test
        void setDown_shouldListTheServerAsDown() {
            final GameServerInfo info = new GameServerInfo(1, new byte[] {1});
            info.setStatus(ListingStatus.NORMAL);

            info.setDown();

            assertThat(info.getStatus()).isEqualTo(ListingStatus.DOWN);
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
        void registerWithFirstAvailableId_shouldTakeTheLowestFreeId() throws IOException {
            Files.createDirectories(datapackRoot.resolve("data"));
            Files.writeString(datapackRoot.resolve("data/servername.xml"), """
                <servers_list>
                	<server id="9" name="Teon" />
                	<server id="1" name="Bartz" />
                	<server id="5" name="Kain" />
                	<server id="3" name="Gludio" />
                </servers_list>
                """);
            final GameServerTable table = new GameServerTable(
                    mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
            table.load();
            table.register(1, new GameServerInfo(1, new byte[] {1}));
            final GameServerInfo info = new GameServerInfo(1, new byte[] {2});

            final boolean actual = table.registerWithFirstAvailableId(info);

            assertThat(actual).isTrue();
            assertThat(info.getId()).isEqualTo(3);
        }

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
