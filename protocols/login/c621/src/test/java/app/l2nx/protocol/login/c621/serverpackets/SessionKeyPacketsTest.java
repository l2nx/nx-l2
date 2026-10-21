/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.c621.serverpackets;

import static app.l2nx.protocol.c621.GoldenBytes.read;
import static app.l2nx.protocol.c621.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionKeyPacketsTest {
    @Nested
    class GGAuthCodec {
        private static final String BODY = "0df0ad0b" + "00".repeat(16);

        @Test
        void write_shouldWriteTheResponseAndFourZeroWords() {
            assertThat(written(new GGAuth(0x0BADF00D)::write)).isEqualTo("0b" + BODY);
        }

        @Test
        void read_shouldReadTheResponse() {
            assertThat(read(BODY, GGAuth::read)).isEqualTo(new GGAuth(0x0BADF00D));
        }

        @Test
        void read_shouldFail_whenTheBodyIsShort() {
            assertThatThrownBy(() -> read(BODY.substring(2), GGAuth::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class LoginOkCodec {
        private static final String BODY = "04030201" + "08070605" + "00000000" + "00000000" + "ea030000" + "00000000"
                + "00000000" + "00000000" + "00".repeat(16);
        private static final LoginOk PACKET =
                LoginOk.builder().loginOk1(0x01020304).loginOk2(0x05060708).build();

        @Test
        void write_shouldWriteThePairAndTheReservedFields() {
            assertThat(written(PACKET::write)).isEqualTo("03" + BODY);
        }

        @Test
        void read_shouldReadThePair() {
            assertThat(read(BODY, LoginOk::read)).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheReservedFieldsAreMissing() {
            assertThatThrownBy(() -> read(BODY.substring(0, 16), LoginOk::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class PlayOkCodec {
        private static final String BODY = "11111111" + "efcdab89";
        private static final PlayOk PACKET =
                PlayOk.builder().playOk1(0x11111111).playOk2(0x89ABCDEF).build();

        @Test
        void write_shouldWriteThePair() {
            assertThat(written(PACKET::write)).isEqualTo("07" + BODY);
        }

        @Test
        void read_shouldReadThePair() {
            assertThat(read(BODY, PlayOk::read)).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenThePairIsShort() {
            assertThatThrownBy(() -> read(BODY.substring(2), PlayOk::read)).isInstanceOf(PacketDecodeException.class);
        }
    }
}
