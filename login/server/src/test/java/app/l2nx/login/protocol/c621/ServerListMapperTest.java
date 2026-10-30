/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.GameServerRepository;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.gameserver.GameServerTable.GameServerInfo;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.protocol.login.c621.serverpackets.ServerList;
import app.l2nx.protocol.login.c621.serverpackets.ServerList.Server;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class ServerListMapperTest {
    // opcode 0x04, 0 servers, last server 1, unknown short 0
    private static final String HEADER = "04" + "00" + "01" + "0000";

    @TempDir
    private Path datapackRoot;

    private final Map<Integer, Integer> counts = new ConcurrentHashMap<>();
    private LoginClient client;

    @BeforeEach
    void setUp() {
        // Registers itself as GameServerTable.getInstance(); load() drops other tests' servers.
        new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile())).load();
        client = mock(LoginClient.class);
        when(client.getLastServer()).thenReturn(1);
        when(client.getCharsOnServ()).thenReturn(counts);
        when(client.getCharsWaitingDelOnServ()).thenReturn(new ConcurrentHashMap<>());
    }

    @Nested
    class Mapping {
        @Test
        void map_shouldNotBlock_andShowNoCounts_whenTheyHaveNotArrived() {
            final ServerList list =
                    assertTimeoutPreemptively(Duration.ofMillis(100), () -> ServerListMapper.map(client));

            assertThat(hex(list)).isEqualTo(HEADER + "00");
        }

        @Test
        void map_shouldKeepTheCountsAvailableAtThatMoment() {
            counts.put(1, 3);

            final ServerList list = ServerListMapper.map(client);

            assertThat(hex(list)).isEqualTo(HEADER + "01" + "01" + "03" + "00");
        }

        @Test
        void map_shouldListTheSecondsLeftUntilEachPendingDeletion() {
            counts.put(1, 2);
            final long deleteAt =
                    System.currentTimeMillis() + Duration.ofHours(1).toMillis() + 500;
            when(client.getCharsWaitingDelOnServ())
                    .thenReturn(new ConcurrentHashMap<>(Map.of(1, new long[] {deleteAt})));

            final ServerList actual = ServerListMapper.map(client);

            assertThat(actual.characters()).singleElement().satisfies(characters -> {
                assertThat(characters.count()).isEqualTo(2);
                assertThat(characters.pendingDeletion()).containsExactly(3600);
            });
        }

        @Test
        void map_shouldNotShowACountThatArrivesAfterTheListWasBuilt() {
            final ServerList list = ServerListMapper.map(client);

            counts.put(1, 3);

            assertThat(hex(list)).isEqualTo(HEADER + "00");
        }
    }

    @Nested
    class Chronicle {
        @Test
        void map_shouldListOnlyServersOfTheClientsChronicles() {
            final GameServerTable table = GameServerTable.getInstance();
            table.register(11, server(11, "high-five"));
            table.register(12, server(12, "essence"));
            table.register(13, server(13, null));
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final ServerList actual = ServerListMapper.map(client);

            assertThat(actual.servers()).extracting(Server::id).containsExactly(11);
        }
    }

    @Nested
    class AgeLimit {
        @Test
        void map_shouldListThePublishedAgeLimit() {
            final GameServerInfo info = server(11, "high-five");
            info.setAgeLimit(18);
            GameServerTable.getInstance().register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final String actual = hex(ServerListMapper.map(client));

            // opcode, 1 server, last server, server id, IPv4 address, port; the age limit follows
            assertThat(actual.substring(24, 26)).isEqualTo("12");
        }
    }

    @Nested
    class Address {
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"127.0.0.5, 7f000005", "::1, 7f000001", "localhost, 7f000001"})
        void map_shouldListAnIpv4Address(String host, String expected) throws Exception {
            final GameServerInfo info = server(11, "high-five");
            info.addServerAddress("0.0.0.0/0", host);
            GameServerTable.getInstance().register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final String actual = hex(ServerListMapper.map(client));

            // opcode, 1 server, last server, server id; the address follows
            assertThat(actual.substring(8, 16)).isEqualTo(expected);
        }
    }

    private static GameServerInfo server(int id, String chronicle) {
        final GameServerInfo info = new GameServerInfo(id, new byte[] {(byte) id});
        info.setChronicle(chronicle);
        return info;
    }

    private String hex(ServerList list) {
        final ByteBuf buffer = Unpooled.buffer();
        try {
            list.write(buffer);
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(buffer));
        } finally {
            buffer.release();
        }
    }
}
