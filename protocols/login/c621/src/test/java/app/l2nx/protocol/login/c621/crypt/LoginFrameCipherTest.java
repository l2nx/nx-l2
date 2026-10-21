/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.crypt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.crypt.LittleEndianBlowfishCipher;
import app.l2nx.network.crypt.RollingXor;
import app.l2nx.network.crypt.XorChecksum;
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LoginFrameCipherTest {
    private static final byte[] SESSION_KEY = HexFormat.of().parseHex("0102030405060708090a0b0c0d0e0f10");
    private static final int XOR_KEY = 0x1234ABCD;

    private final LoginFrameCipher cipher = LoginFrameCipher.server(SESSION_KEY, () -> XOR_KEY);
    private final EmbeddedChannel channel = TestChannels.embedded(new FrameEncoder(cipher));

    @AfterEach
    void closeChannel() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class EncryptedLength {
        @ParameterizedTest(name = "Init payload {0} -> {1}")
        @CsvSource({"0, 24", "7, 24", "8, 32", "170, 192"})
        void encryptedLength_shouldPadAndAddStaticTrailer_forFirstFrame(int payload, int expected) {
            assertThat(cipher.encryptedLength(payload)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "payload {0} -> {1}")
        @CsvSource({"0, 16", "3, 16", "4, 24", "10, 24", "12, 32"})
        void encryptedLength_shouldPadAndAddSessionTrailer_afterFirstFrame(int payload, int expected) {
            encode(payload(170));

            assertThat(cipher.encryptedLength(payload)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "payload {0}")
        @ValueSource(ints = {0, 1, 7, 8, 9, 15, 16, 170})
        void encryptedLength_shouldStayWithinTheMaximumOverhead_inEitherPhase(int payload) {
            final int first = cipher.encryptedLength(payload);
            encode(payload(170));
            final int later = cipher.encryptedLength(payload);

            assertThat(first - payload).as("Init").isLessThanOrEqualTo(LoginFrameCipher.MAX_OVERHEAD);
            assertThat(later - payload).as("session").isLessThanOrEqualTo(LoginFrameCipher.MAX_OVERHEAD);
        }
    }

    @Nested
    class Encrypt {
        @Test
        void encrypt_shouldUseStaticKeyAndRollingXor_forFirstFrame() {
            final byte[] init = payload(170);

            final byte[] actual = encode(init);

            final byte[] expected = Arrays.copyOf(init, 192);
            RollingXor.encrypt(expected, 0, expected.length, XOR_KEY);
            new LittleEndianBlowfishCipher(LoginFrameCipher.STATIC_KEY).encrypt(expected, 0, expected.length);
            assertThat(actual).hasSize(194);
            assertThat(actual[0] & 0xFF | (actual[1] & 0xFF) << 8).isEqualTo(194);
            assertThat(Arrays.copyOfRange(actual, 2, actual.length)).isEqualTo(expected);
        }

        @Test
        void encrypt_shouldUseChecksumAndSessionKey_afterFirstFrame() {
            encode(payload(170));
            final byte[] packet = payload(10);

            final byte[] actual = encode(packet);

            final byte[] expected = Arrays.copyOf(packet, 24);
            XorChecksum.append(expected, 0, expected.length);
            new LittleEndianBlowfishCipher(SESSION_KEY).encrypt(expected, 0, expected.length);
            assertThat(Arrays.copyOfRange(actual, 2, actual.length)).isEqualTo(expected);
        }

        @Test
        void encrypt_shouldZeroPadding_whenBufferHoldsStaleBytes() {
            encode(payload(170));
            final byte[] packet = payload(10);
            final ByteBuf frame = Unpooled.buffer(64);
            frame.setBytes(0, filled(64, 0xFF));
            frame.writeZero(Frames.HEADER).writeBytes(packet);

            final byte[] actual = encode(frame);

            final byte[] expected = Arrays.copyOf(packet, 24);
            XorChecksum.append(expected, 0, expected.length);
            new LittleEndianBlowfishCipher(SESSION_KEY).encrypt(expected, 0, expected.length);
            assertThat(Arrays.copyOfRange(actual, 2, actual.length)).isEqualTo(expected);
        }
    }

    @Nested
    class Decrypt {
        @Test
        void decrypt_shouldAccept_whenChecksumIsValid() {
            final byte[] wire = clientFrame(payload(12));
            final ByteBuf buf = Unpooled.wrappedBuffer(wire);

            assertThat(cipher.decrypt(buf, 0, wire.length)).isTrue();
            assertThat(ByteBufUtil.getBytes(buf, 0, 12)).isEqualTo(payload(12));
            buf.release();
        }

        @Test
        void decrypt_shouldReject_whenChecksumIsWrong() {
            final byte[] wire = clientFrame(payload(12));
            wire[0] ^= 1;
            final ByteBuf buf = Unpooled.wrappedBuffer(wire);

            assertThat(cipher.decrypt(buf, 0, wire.length)).isFalse();
            buf.release();
        }

        @Test
        void decrypt_shouldReject_whenLengthIsNotBlockAligned() {
            final ByteBuf buf = Unpooled.wrappedBuffer(new byte[12]);

            assertThat(cipher.decrypt(buf, 0, 12)).isFalse();
            buf.release();
        }
    }

    @Nested
    class ClientSide {
        private final LoginFrameCipher client = LoginFrameCipher.client();

        @ParameterizedTest(name = "payload {0} -> {1}")
        @CsvSource({"1, 8", "4, 8", "5, 16", "12, 16", "21, 32", "257, 264"})
        void encryptedLength_shouldPadPayloadAndChecksumToTheBlock(int payload, int expected) {
            assertThat(client.encryptedLength(payload)).isEqualTo(expected);
        }

        @Test
        void decrypt_shouldUndoTheStaticKeyAndTheXorPass_ofInit() {
            final byte[] init = payload(170);
            final ByteBuf wire = Unpooled.wrappedBuffer(Arrays.copyOfRange(encode(init), 2, 194));

            assertThat(client.decrypt(wire, 0, wire.capacity())).isTrue();

            assertThat(ByteBufUtil.getBytes(wire, 0, 170)).isEqualTo(init);
            wire.release();
        }

        @Test
        void decrypt_shouldVerifyTheChecksum_afterInit() {
            encode(payload(170));
            final byte[] packet = payload(10);
            final ByteBuf wire = Unpooled.wrappedBuffer(Arrays.copyOfRange(encode(packet), 2, 26));
            client.decrypt(Unpooled.wrappedBuffer(staticFrame()), 0, 192);
            client.sessionKey(SESSION_KEY);

            assertThat(client.decrypt(wire, 0, wire.capacity())).isTrue();

            assertThat(ByteBufUtil.getBytes(wire, 0, 10)).isEqualTo(packet);
            wire.release();
        }

        @Test
        void encrypt_shouldProduceWhatTheServerAccepts() {
            client.decrypt(Unpooled.wrappedBuffer(staticFrame()), 0, 192);
            client.sessionKey(SESSION_KEY);
            final byte[] packet = payload(21);
            final ByteBuf wire = Unpooled.buffer(32).writeBytes(packet).writeZero(32 - packet.length);

            client.encrypt(wire, 0, packet.length);

            assertThat(Arrays.copyOf(ByteBufUtil.getBytes(wire), 32)).isEqualTo(clientFrame(packet, 32));
            assertThat(cipher.decrypt(wire, 0, 32)).isTrue();
            assertThat(ByteBufUtil.getBytes(wire, 0, packet.length)).isEqualTo(packet);
            wire.release();
        }

        @Test
        void encrypt_shouldRefuse_whenInitHasNotBeenRead() {
            final ByteBuf wire = Unpooled.buffer(16).writeZero(16);

            assertThatIllegalStateException().isThrownBy(() -> client.encrypt(wire, 0, 4));
            wire.release();
        }

        @Test
        void decrypt_shouldReject_whenInitIsTooShortForTheXorPass() {
            final ByteBuf wire = Unpooled.wrappedBuffer(new byte[8]);

            assertThat(client.decrypt(wire, 0, 8)).isFalse();
            wire.release();
        }

        private byte[] staticFrame() {
            final byte[] frame = Arrays.copyOf(payload(170), 192);
            RollingXor.encrypt(frame, 0, frame.length, XOR_KEY);
            new LittleEndianBlowfishCipher(LoginFrameCipher.STATIC_KEY).encrypt(frame, 0, frame.length);
            return frame;
        }
    }

    private byte[] encode(byte[] payload) {
        return encode(Frames.allocate(channel.alloc(), payload.length).writeBytes(payload));
    }

    private byte[] encode(ByteBuf frame) {
        channel.writeOutbound(frame);
        final ByteBuf written = channel.readOutbound();
        final byte[] bytes = ByteBufUtil.getBytes(written);
        written.release();
        return bytes;
    }

    private static byte[] clientFrame(byte[] payload) {
        return clientFrame(payload, 24);
    }

    private static byte[] clientFrame(byte[] payload, int length) {
        final byte[] wire = Arrays.copyOf(payload, length);
        XorChecksum.append(wire, 0, wire.length);
        new LittleEndianBlowfishCipher(SESSION_KEY).encrypt(wire, 0, wire.length);
        return wire;
    }

    private static byte[] payload(int length) {
        final byte[] payload = new byte[length];
        for (int i = 0; i < length; i++) {
            payload[i] = (byte) (i * 7 + 3);
        }
        return payload;
    }

    private static byte[] filled(int length, int value) {
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
