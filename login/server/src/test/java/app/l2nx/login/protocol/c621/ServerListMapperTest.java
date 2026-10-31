/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.gameserver.GameServerRepository;
import app.l2nx.login.gameserver.GameServerTable;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gameserver.model.ListingStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServerListMapperTest {
    // opcode 0x04, 0 servers, last server 1, unknown short 0
    private static final String HEADER = "04" + "00" + "01" + "0000";

    @TempDir
    private Path datapackRoot;

    private final Map<Integer, Integer> counts = new ConcurrentHashMap<>();
    private GameServerTable table;
    private ServerListMapper mapper;
    private LoginClient client;

    @BeforeEach
    void setUp() {
        table = new GameServerTable(
                mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
        mapper = new ServerListMapper(table);
        client = mock(LoginClient.class);
        when(client.getLastServer()).thenReturn(1);
        when(client.getCharsOnServ()).thenReturn(counts);
        when(client.getCharsWaitingDelOnServ()).thenReturn(new ConcurrentHashMap<>());
    }

    @Nested
    class Mapping {
        @Test
        void map_shouldNotBlock_andShowNoCounts_whenTheyHaveNotArrived() {
            // Any interruptible wait (sleep, latch, future, monitor) throws or consumes the interrupt; a poll for the
            // counts reads them more than once.
            Thread.currentThread().interrupt();
            final ServerList list;
            try {
                list = mapper.map(client);
            } finally {
                assertThat(Thread.interrupted())
                        .as("no wait consumed the interrupt")
                        .isTrue();
            }

            assertThat(hex(list)).isEqualTo(HEADER + "00");
            verify(client).getCharsOnServ();
        }

        @Test
        void map_shouldKeepTheCountsAvailableAtThatMoment() {
            counts.put(1, 3);

            final ServerList list = mapper.map(client);

            assertThat(hex(list)).isEqualTo(HEADER + "01" + "01" + "03" + "00");
        }

        @Test
        void map_shouldListTheSecondsLeftUntilEachPendingDeletion() {
            counts.put(1, 2);
            final long deleteAt =
                    System.currentTimeMillis() + Duration.ofHours(1).toMillis() + 500;
            when(client.getCharsWaitingDelOnServ())
                    .thenReturn(new ConcurrentHashMap<>(Map.of(1, new long[] {deleteAt})));

            final ServerList actual = mapper.map(client);

            assertThat(actual.characters()).singleElement().satisfies(characters -> {
                assertThat(characters.count()).isEqualTo(2);
                assertThat(characters.pendingDeletion()).containsExactly(3600);
            });
        }

        @Test
        void map_shouldNotShowACountThatArrivesAfterTheListWasBuilt() {
            final ServerList list = mapper.map(client);

            counts.put(1, 3);

            assertThat(hex(list)).isEqualTo(HEADER + "00");
        }
    }

    @Nested
    class Chronicle {
        @Test
        void map_shouldListOnlyServersOfTheClientsChronicles() {
            table.register(11, server(11, "high-five"));
            table.register(12, server(12, "essence"));
            table.register(13, server(13, null));
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final ServerList actual = mapper.map(client);

            assertThat(actual.servers()).extracting(Server::id).containsExactly(11);
        }
    }

    @Nested
    class Status {
        @ParameterizedTest(name = "{0}, access level {1} -> up {2}")
        @CsvSource({
            "AUTO, 0, true",
            "NORMAL, 0, true",
            "NORMAL, -1, false",
            "DOWN, 1, false",
            "GM_ONLY, 1, true",
        })
        void map_shouldListTheServerUp_byItsStatusAndTheAccessLevel(
                ListingStatus status, int accessLevel, boolean expected) {
            final GameServerInfo info = server(11, "high-five");
            info.setStatus(status);
            table.register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");
            when(client.getAccessLevel()).thenReturn(accessLevel);

            final ServerList actual = mapper.map(client);

            assertThat(actual.servers()).singleElement().extracting(Server::up).isEqualTo(expected);
        }

        @Test
        void map_shouldHideAGmOnlyServer_fromAPlayer() {
            final GameServerInfo info = server(11, "high-five");
            info.setStatus(ListingStatus.GM_ONLY);
            table.register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);

            final ServerList actual = mapper.map(client);

            assertThat(actual.servers()).isEmpty();
        }
    }

    @Nested
    class AgeLimit {
        @Test
        void map_shouldListThePublishedAgeLimit() {
            final GameServerInfo info = server(11, "high-five");
            info.setAgeLimit(18);
            table.register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final String actual = hex(mapper.map(client));

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
            table.register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final String actual = hex(mapper.map(client));

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
