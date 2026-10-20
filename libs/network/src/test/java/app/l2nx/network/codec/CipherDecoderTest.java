/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.codec;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.test.TestChannels;
import app.l2nx.network.test.XorTestCipher;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

class CipherDecoderTest {
    // The tail handler consumes every message that reaches it, so the read tests leave it out.
    private final EmbeddedChannel channel =
            TestChannels.embedded(Frames.decoder(16 * 1024), new CipherDecoder(new XorTestCipher(1)));

    @Test
    void channelRead_shouldDecryptInPlaceAndCountBytes() {
        channel.writeInbound(TestChannels.frame(0x5A ^ 0x10, 0x5A ^ 0x20));

        final ByteBuf actual = channel.readInbound();
        assertThat(ByteBufUtil.getBytes(actual)).containsExactly(0x10, 0x20);
        actual.release();
        assertThat(TestChannels.registry(channel)
                        .get("l2nx.network.bytes")
                        .tag("direction", "in")
                        .counter()
                        .count())
                .isEqualTo(4.0);
        channel.finishAndReleaseAll();
    }

    @Test
    void channelRead_shouldSkipKeepAliveFrame() {
        channel.writeInbound(TestChannels.frame());

        assertThat((Object) channel.readInbound()).isNull();
        assertThat(channel.isOpen()).isTrue();
        channel.finishAndReleaseAll();
    }

    @Test
    void channelRead_shouldCloseWithError_whenCipherRejectsFrame() {
        channel.writeInbound(TestChannels.frame(0xFF ^ 0x5A, 0));

        assertThat(channel.isOpen()).isFalse();
        assertThat(Connections.closeReason(channel)).isEqualTo(CloseReason.ERROR);
        channel.finishAndReleaseAll();
    }

    @Test
    void channelRead_shouldCopyReadOnlyFrame() {
        final EmbeddedChannel direct = TestChannels.embedded(new CipherDecoder(new XorTestCipher(1)));
        final ByteBuf readOnly = Unpooled.wrappedBuffer(new byte[] {0x5A ^ 1}).asReadOnly();

        direct.writeInbound(readOnly);

        final ByteBuf actual = direct.readInbound();
        assertThat(ByteBufUtil.getBytes(actual)).containsExactly(1);
        assertThat(readOnly.refCnt()).isZero();
        actual.release();
        direct.finishAndReleaseAll();
    }
}
