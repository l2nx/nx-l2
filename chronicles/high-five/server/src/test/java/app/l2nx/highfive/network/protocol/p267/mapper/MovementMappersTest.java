/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.movement.view.PositionCorrected;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.test.GoldenBytes;
import app.l2nx.protocol.game.p267.serverpackets.ValidateLocation;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.HexFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovementMappersTest {
    private static final PositionCorrected EVENT = PositionCorrected.builder()
            .handle(0x10000001)
            .x(1)
            .y(-2)
            .z(3)
            .heading(4)
            .build();
    private static final ValidateLocation EXPECTED = ValidateLocation.builder()
            .objectId(0x10000001)
            .x(1)
            .y(-2)
            .z(3)
            .heading(4)
            .build();

    @Nested
    class PositionCorrectedMapping {
        @Test
        void map_shouldBeTheValidateLocationOfTheObject() {
            assertThat(new PositionCorrectedMapper().map(EVENT, null)).isEqualTo(EXPECTED);
        }

        @Test
        void serialize_shouldWriteTheRecordThroughTheAdapterTable() {
            final ByteBuf frame = P267Mappers.serialize(EVENT, null, UnpooledByteBufAllocator.DEFAULT);
            try {
                final String actual = HexFormat.of()
                        .formatHex(ByteBufUtil.getBytes(frame, Frames.HEADER, frame.readableBytes() - Frames.HEADER));

                assertThat(actual).isEqualTo(GoldenBytes.written(EXPECTED::write));
            } finally {
                frame.release();
            }
        }
    }
}
