/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.HexFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class P267HandshakeRefusalTest {
    @Nested
    class Frame {
        @Test
        void frame_shouldWriteTheRefusedVersionCheckWithEncryptionOff() {
            final ByteBuf frame = P267HandshakeRefusal.frame(UnpooledByteBufAllocator.DEFAULT);
            try {
                assertThat(HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2)))
                        .isEqualTo("2e" + "00" + "0000000000000000" + "00000000" + "00000000" + "01" + "00000000");
            } finally {
                frame.release();
            }
        }
    }
}
