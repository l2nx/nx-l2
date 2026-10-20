/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WireTest {
    @Nested
    class WriteString {
        @Test
        void writeString_shouldWriteUtf16LeWithTerminator() {
            final ByteBuf buf = Unpooled.buffer();
            Wire.writeString(buf, "Ab");

            assertThat(ByteBufUtil.getBytes(buf)).containsExactly('A', 0, 'b', 0, 0, 0);
            assertThat(Wire.readString(buf)).isEqualTo("Ab");
            buf.release();
        }

        @Test
        void writeString_shouldWriteOnlyTerminator_whenNull() {
            final ByteBuf buf = Unpooled.buffer();
            Wire.writeString(buf, null);

            assertThat(ByteBufUtil.getBytes(buf)).containsExactly(0, 0);
            buf.release();
        }
    }

    @Nested
    class ReadString {
        @Test
        void readString_shouldFail_whenTerminatorIsMissing() {
            final ByteBuf buf = Unpooled.buffer().writeShortLE('x');

            assertThatThrownBy(() -> Wire.readString(buf)).isInstanceOf(PacketDecodeException.class);
            buf.release();
        }

        @Test
        void readString_shouldFail_whenLongerThanBound() {
            final ByteBuf buf = Unpooled.buffer();
            Wire.writeString(buf, "abcd");

            assertThatThrownBy(() -> Wire.readString(buf, 3)).isInstanceOf(PacketDecodeException.class);
            buf.release();
        }
    }

    @Nested
    class WriteSizedString {
        @Test
        void writeSizedString_shouldRoundTripThroughReadSizedString() {
            final ByteBuf buf = Unpooled.buffer();
            Wire.writeSizedString(buf, "hi");

            assertThat(buf.getShortLE(0)).isEqualTo((short) 2);
            assertThat(Wire.readSizedString(buf)).isEqualTo("hi");
            buf.release();
        }

        @Test
        void writeSizedString_shouldReject_whenLongerThanTheSignedCount() {
            final ByteBuf buf = Unpooled.buffer();

            assertThatThrownBy(() -> Wire.writeSizedString(buf, "x".repeat(Wire.MAX_SIZED_CHARS + 1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(buf.readableBytes()).isZero();
            buf.release();
        }
    }

    @Nested
    class ReadSizedString {
        @Test
        void readSizedString_shouldFail_whenCountExceedsFrame() {
            final ByteBuf buf = Unpooled.buffer().writeShortLE(10).writeShortLE('a');

            assertThatThrownBy(() -> Wire.readSizedString(buf)).isInstanceOf(PacketDecodeException.class);
            buf.release();
        }

        @Test
        void readSizedString_shouldFail_whenCountIsMissing() {
            final ByteBuf buf = Unpooled.buffer().writeByte(1);

            assertThatThrownBy(() -> Wire.readSizedString(buf)).isInstanceOf(PacketDecodeException.class);
            buf.release();
        }
    }
}
