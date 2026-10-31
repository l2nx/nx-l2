/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IncomingPacketTest {
    @Nested
    class Decode {
        @Test
        void decode_shouldReadLittleEndianFieldsAndForgetTheFrame() {
            final ByteBuf frame = Unpooled.buffer()
                    .writeByte(7)
                    .writeShortLE(-2)
                    .writeIntLE(0x01020304)
                    .writeLongLE(5L)
                    .writeFloatLE(1.5F);
            Wire.writeString(frame, "name");
            final Probe packet = new Probe();

            assertThat(packet.decode("client", frame)).isTrue();

            assertThat(packet.values).containsExactly(7, -2, 0x01020304, 5L, 1.5F, "name", 0);
            assertThat(packet.getClient()).isEqualTo("client");
            assertThatThrownBy(packet::remainingAfterDecode).isInstanceOf(IllegalStateException.class);
            frame.release();
        }

        @Test
        void decode_shouldFailWithDecodeException_whenFrameIsTruncated() {
            final ByteBuf frame = Unpooled.buffer().writeByte(1);

            assertThatThrownBy(() -> new Probe().decode("c", frame)).isInstanceOf(PacketDecodeException.class);
            frame.release();
        }
    }

    @Nested
    class ReadBytes {
        @Test
        void readBytes_shouldFailBeforeAllocating_whenLengthExceedsFrame() {
            final ByteBuf frame = Unpooled.buffer().writeIntLE(Integer.MAX_VALUE);
            final IncomingPacket<String> packet = new IncomingPacket<>() {
                @Override
                protected boolean read() {
                    readBytes(readInt());
                    return true;
                }

                @Override
                public void run() {}
            };

            assertThatThrownBy(() -> packet.decode("c", frame))
                    .isInstanceOf(PacketDecodeException.class)
                    .hasMessageContaining("2147483647 bytes requested");
            frame.release();
        }
    }

    @Nested
    class ReadString {
        @Test
        void readString_shouldReturnTheCharactersRead_whenTheTerminatorIsMissing() {
            final ByteBuf frame = Unpooled.buffer().writeShortLE('a').writeShortLE('b');
            final List<String> read = new ArrayList<>();
            final IncomingPacket<String> packet = new IncomingPacket<>() {
                @Override
                protected boolean read() {
                    read.add(readString());
                    return true;
                }

                @Override
                public void run() {}
            };

            assertThat(packet.decode("c", frame)).isTrue();

            assertThat(read).containsExactly("ab");
            frame.release();
        }
    }

    private static final class Probe extends IncomingPacket<String> {
        private final List<Object> values = new ArrayList<>();

        @Override
        protected boolean read() {
            values.add(readUnsignedByte());
            values.add((int) readShort());
            values.add(readInt());
            values.add(readLong());
            values.add(readFloat());
            values.add(readString());
            values.add(remaining());
            return true;
        }

        @Override
        public void run() {}

        int remainingAfterDecode() {
            return remaining();
        }
    }
}
