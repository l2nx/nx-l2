/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutgoingPacketTest {
    @Nested
    class Serialize {
        @Test
        void serialize_shouldLeaveHeaderRoomAndWritePayload() {
            final OutgoingPacket<String> packet = new OutgoingPacket<>() {
                @Override
                protected boolean write(String client, PacketWriter writer) {
                    writer.writeByte(0x2E);
                    writer.writeInt(true);
                    writer.writeString(client);
                    return true;
                }
            };

            final ByteBuf actual = packet.serialize("x", UnpooledByteBufAllocator.DEFAULT);

            assertThat(ByteBufUtil.getBytes(actual)).containsExactly(0, 0, 0x2E, 1, 0, 0, 0, 'x', 0, 0, 0);
            actual.release();
        }

        @Test
        void serialize_shouldReserveTheTailroom() {
            final OutgoingPacket<String> packet = new OutgoingPacket<>() {
                @Override
                protected int sizeHint() {
                    return 1;
                }

                @Override
                protected int tailroom() {
                    return 16;
                }

                @Override
                protected boolean write(String client, PacketWriter writer) {
                    writer.writeByte(1);
                    return true;
                }
            };

            final ByteBuf actual = packet.serialize("x", UnpooledByteBufAllocator.DEFAULT);

            assertThat(actual.writableBytes()).isGreaterThanOrEqualTo(16);
            actual.release();
        }

        @Test
        void serialize_shouldReturnNull_whenWriteDeclines() {
            final OutgoingPacket<String> packet = new OutgoingPacket<>() {
                @Override
                protected boolean write(String client, PacketWriter writer) {
                    writer.writeInt(1);
                    return false;
                }
            };

            assertThat(packet.serialize("x", UnpooledByteBufAllocator.DEFAULT)).isNull();
        }
    }
}
