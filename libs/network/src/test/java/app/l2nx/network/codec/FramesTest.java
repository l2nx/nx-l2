/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FramesTest {
    // The tail handler consumes every message that reaches it, so the read tests leave it out.
    private final EmbeddedChannel channel = TestChannels.embedded(Frames.decoder(16 * 1024));
    private final EmbeddedChannel closing =
            TestChannels.embedded(Frames.decoder(16 * 1024), TailExceptionHandler.INSTANCE);

    @Nested
    class Decoder {
        @Test
        void decode_shouldJoinFragmentedFrame() {
            final ByteBuf frame = TestChannels.frame(1, 2, 3, 4);

            channel.writeInbound(frame.readRetainedSlice(3));
            assertThat((Object) channel.readInbound()).isNull();
            channel.writeInbound(frame.readRetainedSlice(3));
            frame.release();

            final ByteBuf actual = channel.readInbound();
            assertThat(ByteBufUtil.getBytes(actual)).containsExactly(1, 2, 3, 4);
            actual.release();
            assertThat(channel.finishAndReleaseAll()).isFalse();
        }

        @Test
        void decode_shouldSplitMergedFrames() {
            channel.writeInbound(Unpooled.wrappedBuffer(TestChannels.frame(9), TestChannels.frame(7, 8)));

            final ByteBuf first = channel.readInbound();
            final ByteBuf second = channel.readInbound();
            assertThat(ByteBufUtil.getBytes(first)).containsExactly(9);
            assertThat(ByteBufUtil.getBytes(second)).containsExactly(7, 8);
            first.release();
            second.release();
            channel.finishAndReleaseAll();
        }

        @Test
        void decode_shouldCloseWithError_whenFrameIsOversize() {
            closing.writeInbound(Unpooled.buffer(2).writeShortLE(16 * 1024 + 1));

            assertThat(closing.isOpen()).isFalse();
            assertThat(Connections.closeReason(closing)).isEqualTo(CloseReason.ERROR);
            closing.finishAndReleaseAll();
        }

        @Test
        void decode_shouldCloseWithError_whenLengthIsBelowHeader() {
            closing.writeInbound(Unpooled.buffer(2).writeShortLE(1));

            assertThat(closing.isOpen()).isFalse();
            closing.finishAndReleaseAll();
        }

        @Test
        void decode_shouldReturnWritableSliceOfCumulation() {
            channel.writeInbound(TestChannels.frame(5, 6));

            final ByteBuf actual = channel.readInbound();
            assertThat(actual.isReadOnly()).isFalse();
            actual.setByte(actual.readerIndex(), 0x55);
            assertThat(actual.getByte(actual.readerIndex())).isEqualTo((byte) 0x55);
            actual.release();
            channel.finishAndReleaseAll();
        }
    }

    @Nested
    class Share {
        @ParameterizedTest
        @MethodSource("app.l2nx.network.test.TestChannels#allocators")
        void share_shouldAddAHolderOfTheSameContent(ByteBufAllocator alloc) {
            final ByteBuf original = Frames.allocate(alloc, 1).writeByte(0x11);

            final ByteBuf shared = Frames.share(original);

            assertThat(Frames.isShared(shared)).isTrue();
            assertThat(Frames.isShared(original)).isTrue();
            shared.release();
            assertThat(Frames.isShared(original)).isFalse();
            original.release();
        }
    }
}
