/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.login.config.TestLoginProperties;
import app.l2nx.login.data.GameServerTable;
import app.l2nx.login.data.GameServerTable.GameServerInfo;
import app.l2nx.login.network.LoginClient;
import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.repository.GameServerRepository;
import app.l2nx.network.codec.PacketWriter;
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

@ResourceLock("GameServerTable")
@Execution(ExecutionMode.SAME_THREAD)
class ServerListTest {
    // opcode 0x04, 0 servers, last server 1, unknown short 0
    private static final String HEADER = "04" + "00" + "01" + "0000";

    @TempDir
    private Path datapackRoot;

    private final Map<Integer, Integer> counts = new ConcurrentHashMap<>();
    private LoginClient client;

    @BeforeEach
    void setUp() {
        // The constructor registers itself as GameServerTable.getInstance(); load() drops the servers other tests
        // registered in the static table, and the database has none.
        new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile())).load();
        client = mock(LoginClient.class);
        when(client.getLastServer()).thenReturn(1);
        when(client.getCharsOnServ()).thenReturn(counts);
        when(client.getCharsWaitingDelOnServ()).thenReturn(new ConcurrentHashMap<>());
    }

    @Nested
    class Constructor {
        @Test
        void constructor_shouldNotBlock_andShowNoCounts_whenTheyHaveNotArrived() {
            final ServerList list = assertTimeoutPreemptively(Duration.ofMillis(100), () -> new ServerList(client));

            assertThat(hex(list)).isEqualTo(HEADER + "00");
        }

        @Test
        void constructor_shouldKeepTheCountsAvailableAtThatMoment() {
            counts.put(1, 3);

            final ServerList list = new ServerList(client);

            assertThat(hex(list)).isEqualTo(HEADER + "01" + "01" + "03" + "00");
        }
    }

    @Nested
    class WriteImpl {
        @Test
        void writeImpl_shouldNotShowACountThatArrivesAfterTheListWasBuilt() {
            final ServerList list = new ServerList(client);

            counts.put(1, 3);

            assertThat(hex(list)).isEqualTo(HEADER + "00");
        }
    }

    @Nested
    class Chronicle {
        @Test
        void constructor_shouldListOnlyServersOfTheClientsChronicles() {
            final GameServerTable table = GameServerTable.getInstance();
            table.register(11, server(11, "high-five"));
            table.register(12, server(12, "essence"));
            table.register(13, server(13, null));
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final ServerList actual = new ServerList(client);

            assertThat(actual.serverIds()).containsExactly(11);
        }
    }

    @Nested
    class AgeLimit {
        @Test
        void writeImpl_shouldWriteThePublishedAgeLimit() {
            final GameServerInfo info = server(11, "high-five");
            info.setAgeLimit(18);
            GameServerTable.getInstance().register(11, info);
            when(client.getVariant()).thenReturn(LoginVariant.C621);
            when(client.getIp()).thenReturn("127.0.0.1");

            final String actual = hex(new ServerList(client));

            // opcode, 1 server, last server, server id, IPv4 address, port; the age limit follows
            assertThat(actual.substring(24, 26)).isEqualTo("12");
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
            list.writeImpl(client, new PacketWriter(buffer));
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(buffer));
        } finally {
            buffer.release();
        }
    }
}
