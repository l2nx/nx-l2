/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.crypt;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

class GameFrameCipherTest {
    private static final byte[] KEY = {
        1, 2, 3, 4, 5, 6, 7, 8, (byte) 0xC8, 0x27, (byte) 0x93, 1, (byte) 0xA1, 0x6C, 0x31, (byte) 0x97
    };

    @Test
    void encrypt_shouldLeaveFramesClear_untilAKeyIsSet() {
        final GameFrameCipher cipher = new GameFrameCipher();

        assertThat(roundTrip(cipher, true)).containsExactly(9, 8, 7);
        assertThat(cipher.isEnabled()).isFalse();
    }

    @Test
    void encrypt_shouldSendTheKeyFrameClearAndEnableBothDirections() {
        final GameFrameCipher cipher = new GameFrameCipher();
        cipher.setKey(KEY);

        assertThat(roundTrip(cipher, true)).as("key packet").containsExactly(9, 8, 7);
        assertThat(cipher.isEnabled()).isTrue();

        final GameXorCipher reference = new GameXorCipher();
        reference.setKey(KEY);
        final ByteBuf expected = Unpooled.wrappedBuffer(new byte[] {9, 8, 7});
        reference.encrypt(expected, 0, 3);
        assertThat(roundTrip(cipher, true)).containsExactly(ByteBufUtil.getBytes(expected));
        expected.release();
    }

    @Test
    void decrypt_shouldPassClearFramesBeforeTheKeyPacket() {
        final GameFrameCipher cipher = new GameFrameCipher();
        cipher.setKey(KEY);

        assertThat(roundTrip(cipher, false)).containsExactly(9, 8, 7);
    }

    @Test
    void decrypt_shouldUndoTheClientsXor_afterTheKeyPacket() {
        final GameFrameCipher cipher = new GameFrameCipher();
        cipher.setKey(KEY);
        roundTrip(cipher, true);
        final GameXorCipher client = new GameXorCipher();
        client.setKey(KEY);
        final ByteBuf frame = Unpooled.wrappedBuffer(new byte[] {9, 8, 7});
        client.encrypt(frame, 0, 3);

        assertThat(cipher.decrypt(frame, 0, 3)).isTrue();

        assertThat(ByteBufUtil.getBytes(frame)).containsExactly(9, 8, 7);
        frame.release();
    }

    private static byte[] roundTrip(GameFrameCipher cipher, boolean encrypt) {
        final ByteBuf buf = Unpooled.wrappedBuffer(new byte[] {9, 8, 7});
        if (encrypt) {
            assertThat(cipher.encryptedLength(3)).isEqualTo(3);
            cipher.encrypt(buf, 0, 3);
        } else {
            assertThat(cipher.decrypt(buf, 0, 3)).isTrue();
        }
        final byte[] bytes = ByteBufUtil.getBytes(buf);
        buf.release();
        return bytes;
    }
}
