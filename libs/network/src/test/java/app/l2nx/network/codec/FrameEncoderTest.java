/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.test.TestChannels;
import app.l2nx.network.test.XorTestCipher;
import io.netty.buffer.AbstractByteBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FrameEncoderTest {
    @Nested
    class Finish {
        static Stream<Arguments> sharers() {
            final UnaryOperator<ByteBuf> share = Frames::share;
            final UnaryOperator<ByteBuf> retainedDuplicate = ByteBuf::retainedDuplicate;
            final UnaryOperator<ByteBuf> retainedSlice = ByteBuf::retainedSlice;
            return TestChannels.allocators()
                    .flatMap(alloc -> Stream.of(
                            Arguments.of(alloc, "share", share),
                            Arguments.of(alloc, "retainedDuplicate", retainedDuplicate),
                            Arguments.of(alloc, "retainedSlice", retainedSlice)));
        }

        @ParameterizedTest
        @MethodSource("app.l2nx.network.test.TestChannels#allocators")
        void finish_shouldEncryptInPlace_whenBufferIsNotShared(ByteBufAllocator alloc) {
            final ByteBuf frame = payload(alloc, 0x11);

            final ByteBuf actual = new FrameEncoder(new XorTestCipher(1)).finish(alloc, frame);

            assertThat(actual).isSameAs(frame);
            actual.release();
        }

        @ParameterizedTest(name = "{0} {1}")
        @MethodSource("sharers")
        void finish_shouldCopyFirst_whenBufferIsShared(
                ByteBufAllocator alloc, String how, UnaryOperator<ByteBuf> sharer) {
            final ByteBuf original = payload(alloc, 0x11, 0x22);
            final ByteBuf shared = sharer.apply(original);

            final ByteBuf actual = new FrameEncoder(new XorTestCipher(1)).finish(alloc, shared);

            assertThat(actual).isNotSameAs(shared);
            assertThat(ByteBufUtil.getBytes(actual)).containsExactly(4, 0, 0x11 ^ 0x5A, 0x22 ^ 0x5A);
            assertThat(ByteBufUtil.getBytes(original, original.readerIndex() + 2, 2))
                    .containsExactly(0x11, 0x22);
            assertThat(original.refCnt()).isEqualTo(1);
            actual.release();
            original.release();
            assertThat(original.refCnt()).isZero();
        }

        @ParameterizedTest
        @MethodSource("app.l2nx.network.test.TestChannels#allocators")
        void finish_shouldEncryptTheLastHolderInPlace_afterTheOthersReleased(ByteBufAllocator alloc) {
            final ByteBuf original = payload(alloc, 0x11);
            final ByteBuf last = Frames.share(original);
            original.release();

            final ByteBuf actual = new FrameEncoder(new XorTestCipher(1)).finish(alloc, last);

            assertThat(actual).isSameAs(last);
            actual.release();
            assertThat(original.refCnt()).isZero();
        }

        @ParameterizedTest
        @MethodSource("app.l2nx.network.test.TestChannels#allocators")
        void finish_shouldGrowForPadding_whenCipherNeedsTailroom(ByteBufAllocator alloc) {
            final ByteBuf frame = alloc.buffer(5)
                    .writeZero(Frames.HEADER)
                    .writeByte(1)
                    .writeByte(2)
                    .writeByte(3);

            final ByteBuf actual = new FrameEncoder(new XorTestCipher(8)).finish(alloc, frame);

            assertThat(actual.readableBytes()).isEqualTo(10);
            assertThat(actual.getUnsignedShortLE(actual.readerIndex())).isEqualTo(10);
            actual.release();
        }

        @ParameterizedTest
        @MethodSource("app.l2nx.network.test.TestChannels#allocators")
        void finish_shouldReleaseAndFail_whenFrameHasNoHeaderRoom(ByteBufAllocator alloc) {
            final ByteBuf frame = alloc.buffer(1).writeByte(1);

            assertThatThrownBy(() -> new FrameEncoder(FrameCipher.NONE).finish(alloc, frame))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(frame.refCnt()).isZero();
        }

        @Test
        void finish_shouldReleaseTheFrame_whenTheCopyFails() {
            final ByteBuf original = payload(UnpooledByteBufAllocator.DEFAULT, 0x11, 0x22);
            final ByteBuf shared = Frames.share(original);

            assertThatThrownBy(() -> new FrameEncoder(FrameCipher.NONE).finish(new OneByteAllocator(), shared))
                    .isInstanceOf(IndexOutOfBoundsException.class);

            assertThat(original.refCnt()).as("the shared holder was released").isEqualTo(1);
            original.release();
        }

        @Test
        void finish_shouldReleaseAndFail_whenPaddingPushesTheFramePastTheLengthField() {
            final ByteBuf frame = Frames.allocate(UnpooledByteBufAllocator.DEFAULT, Frames.MAX_LENGTH - Frames.HEADER)
                    .writeZero(Frames.MAX_LENGTH - Frames.HEADER);

            assertThatThrownBy(() ->
                            new FrameEncoder(new XorTestCipher(8)).finish(UnpooledByteBufAllocator.DEFAULT, frame))
                    .isInstanceOf(EncoderException.class);
            assertThat(frame.refCnt()).isZero();
        }
    }

    @Nested
    class Write {
        @Test
        void write_shouldFillLengthAndEncryptPayloadOnly() {
            final EmbeddedChannel channel = TestChannels.embedded(new FrameEncoder(new XorTestCipher(1)));

            channel.writeOutbound(payload(channel.alloc(), 0x01, 0x02, 0x03));

            final ByteBuf actual = channel.readOutbound();
            assertThat(ByteBufUtil.getBytes(actual)).containsExactly(5, 0, 0x01 ^ 0x5A, 0x02 ^ 0x5A, 0x03 ^ 0x5A);
            actual.release();
            assertThat(TestChannels.registry(channel)
                            .get("l2nx.network.bytes")
                            .tag("direction", "out")
                            .counter()
                            .count())
                    .isEqualTo(5.0);
            channel.finishAndReleaseAll();
        }

        @Test
        void write_shouldFailThePromiseAndReleaseTheFrame_whenFrameExceedsTheLengthField() {
            final EmbeddedChannel channel = TestChannels.embedded(new FrameEncoder(FrameCipher.NONE));
            final ByteBuf frame = oversize();

            final ChannelFuture written = channel.writeOneOutbound(frame);
            channel.flushOutbound();

            assertThat(written.cause()).isInstanceOf(EncoderException.class);
            assertThat(frame.refCnt()).isZero();
            assertThat((Object) channel.readOutbound()).isNull();
            channel.finishAndReleaseAll();
        }
    }

    private static ByteBuf oversize() {
        final int payload = Frames.MAX_LENGTH - Frames.HEADER + 1;
        return Frames.allocate(UnpooledByteBufAllocator.DEFAULT, payload).writeZero(payload);
    }

    private static ByteBuf payload(ByteBufAllocator alloc, int... bytes) {
        final ByteBuf frame = Frames.allocate(alloc, bytes.length);
        for (int b : bytes) {
            frame.writeByte(b);
        }
        return frame;
    }

    /** Hands out buffers that cannot grow past one byte, so copying a frame into them fails. */
    private static final class OneByteAllocator extends AbstractByteBufAllocator {
        @Override
        protected ByteBuf newHeapBuffer(int initialCapacity, int maxCapacity) {
            return Unpooled.buffer(0, 1);
        }

        @Override
        protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
            return Unpooled.buffer(0, 1);
        }

        @Override
        public boolean isDirectBufferPooled() {
            return false;
        }
    }
}
