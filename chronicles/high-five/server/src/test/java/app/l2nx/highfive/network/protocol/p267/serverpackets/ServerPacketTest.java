/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ServerPacketTest {
    private final GameClient client = mock(GameClient.class);

    @Nested
    class Serialize {
        @Test
        void serialize_shouldWriteOnceAndShareTheFrame_whenBroadcast() {
            final Probe packet = new Probe(false);
            packet.sendInBroadcast();

            final ByteBuf first = packet.serialize(client, UnpooledByteBufAllocator.DEFAULT);
            final ByteBuf second = packet.serialize(client, UnpooledByteBufAllocator.DEFAULT);

            try {
                assertThat(packet.writes).isEqualTo(1);
                assertThat(Frames.isShared(first)).isTrue();
            } finally {
                first.release();
                second.release();
                packet.releaseBroadcast();
            }
        }

        @Test
        void serialize_shouldWriteForEachRecipient_whenABroadcastPacketIsReceiverDependent() {
            final Probe packet = new Probe(true);
            packet.sendInBroadcast();

            final ByteBuf first = packet.serialize(client, UnpooledByteBufAllocator.DEFAULT);
            final ByteBuf second = packet.serialize(client, UnpooledByteBufAllocator.DEFAULT);

            try {
                assertThat(packet.writes).isEqualTo(2);
                assertThat(Frames.isShared(first)).isFalse();
                assertThat(Frames.isShared(second)).isFalse();
            } finally {
                first.release();
                second.release();
                packet.releaseBroadcast();
            }
        }
    }

    private static final class Probe extends ServerPacket {
        private final boolean dependent;
        private int writes;

        Probe(boolean dependent) {
            this.dependent = dependent;
        }

        @Override
        protected boolean receiverDependent() {
            return dependent;
        }

        @Override
        protected void writeImpl(GameClient client, PacketWriter buffer) {
            writes++;
            buffer.writeByte(0x7F);
        }
    }
}
