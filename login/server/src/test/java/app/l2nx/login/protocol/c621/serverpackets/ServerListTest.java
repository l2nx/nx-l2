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
import app.l2nx.login.network.LoginClient;
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
        // The constructor registers itself as GameServerTable.getInstance(); no server is registered.
        new GameServerTable(mock(GameServerRepository.class), TestLoginProperties.create(datapackRoot.toFile()));
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

            // one entry: server 1, 3 characters, 0 awaiting deletion
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
