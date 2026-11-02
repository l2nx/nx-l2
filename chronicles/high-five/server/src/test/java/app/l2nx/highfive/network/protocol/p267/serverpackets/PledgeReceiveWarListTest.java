/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.network.codec.PacketWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PledgeReceiveWarListTest {
    private static final int KNOWN_ID = 2;
    private static final int GONE_ID = 3;

    private final ClanTable table = mock(ClanTable.class);
    private final Clan clan = new Clan(1, "Owner");
    private MockedStatic<ClanTable> tableInstance;

    @BeforeEach
    void bindClanTable() {
        tableInstance = mockStatic(ClanTable.class);
        tableInstance.when(ClanTable::getInstance).thenReturn(table);
        when(table.getClan(KNOWN_ID)).thenReturn(new Clan(KNOWN_ID, "Known"));
    }

    @AfterEach
    void releaseClanTable() {
        tableInstance.close();
    }

    @Nested
    class WriteImpl {
        @Test
        void writeImpl_shouldCountOnlyKnownClans_whenWarredClanIsGone() {
            final ByteBuf buffer = Unpooled.buffer();

            WorldLoopExtension.runInPass(() -> {
                clan.setEnemyClan(GONE_ID);
                clan.setEnemyClan(KNOWN_ID);
                new PledgeReceiveWarList(clan, 0).writeImpl(null, new PacketWriter(buffer));
            });

            buffer.skipBytes(3 + 4 + 4);
            final int count = buffer.readIntLE();
            final List<String> names = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                names.add(readString(buffer));
                buffer.skipBytes(4 + 4);
            }

            assertThat(names).containsExactly("Known");
            assertThat(buffer.isReadable()).isFalse();
        }
    }

    private static String readString(ByteBuf buffer) {
        final StringBuilder text = new StringBuilder();
        for (char c = (char) buffer.readShortLE(); c != 0; c = (char) buffer.readShortLE()) {
            text.append(c);
        }

        return text.toString();
    }
}
