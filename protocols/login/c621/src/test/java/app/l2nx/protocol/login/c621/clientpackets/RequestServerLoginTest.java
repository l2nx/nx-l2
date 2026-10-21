/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.clientpackets;

import static app.l2nx.protocol.login.c621.GoldenBytes.read;
import static app.l2nx.protocol.login.c621.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RequestServerLoginTest {
    private static final String PAIR = "78563412" + "efcdab89";

    @Nested
    class Decode {
        @ParameterizedTest(name = "server byte {0} -> {1}")
        @CsvSource({"01, 1", "7f, 127", "81, 129", "ff, 255"})
        void decode_shouldReadThePairAndTheServerAsAnUnsignedByte(String serverByte, int expected) {
            final RequestServerLogin actual = read(PAIR + serverByte, RequestServerLogin::decode);

            assertThat(actual)
                    .isEqualTo(RequestServerLogin.builder()
                            .loginOk1(0x12345678)
                            .loginOk2(0x89ABCDEF)
                            .serverId(expected)
                            .build());
        }

        @Test
        void decode_shouldFail_whenTheServerIsMissing() {
            assertThatThrownBy(() -> read(PAIR, RequestServerLogin::decode)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Encode {
        @Test
        void encode_shouldWriteTheOpcodeThePairAndTheServer() {
            final RequestServerLogin packet = RequestServerLogin.builder()
                    .loginOk1(0x12345678)
                    .loginOk2(0x89ABCDEF)
                    .serverId(2)
                    .build();

            assertThat(written(packet::encode)).isEqualTo("02" + PAIR + "02");
        }
    }
}
