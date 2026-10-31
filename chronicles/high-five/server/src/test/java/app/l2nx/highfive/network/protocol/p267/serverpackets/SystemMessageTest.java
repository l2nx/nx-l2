/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class SystemMessageTest {
    @Test
    void writeImpl_shouldCountOnlyTheParametersItWrites() {
        final SystemMessage message = new SystemMessage(SystemMessageId.S1_S2).addString("a");

        assertThat(body(message)).isEqualTo("62" + d(SystemMessageId.S1_S2.getId()) + d(1) + d(0) + "6100" + "0000");
    }

    @Test
    void writeImpl_shouldWriteANullTextAsAnEmptyString() {
        final SystemMessage message = new SystemMessage(SystemMessageId.S1_2).addString(null);

        assertThat(body(message)).isEqualTo("62" + d(SystemMessageId.S1_2.getId()) + d(1) + d(0) + "0000");
    }

    static String body(ServerPacket packet) {
        final ByteBuf frame = packet.serialize(mock(GameClient.class), UnpooledByteBufAllocator.DEFAULT);
        try {
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2));
        } finally {
            frame.release();
        }
    }

    static String d(int value) {
        return HexFormat.of()
                .formatHex(
                        new byte[] {(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)});
    }
}
