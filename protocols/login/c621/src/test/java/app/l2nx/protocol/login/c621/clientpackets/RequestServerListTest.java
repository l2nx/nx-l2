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

class RequestServerListTest {
    private static final String BODY = "78563412" + "efcdab89";
    private static final RequestServerList PACKET = RequestServerList.builder()
            .loginOk1(0x12345678)
            .loginOk2(0x89ABCDEF)
            .build();

    @Nested
    class Decode {
        @Test
        void decode_shouldReadTheLoginOkPair_andIgnoreTheRest() {
            final RequestServerList actual = read(BODY + "04", RequestServerList::decode);

            assertThat(actual).isEqualTo(PACKET);
        }

        @Test
        void decode_shouldFail_whenTheBodyIsShorterThanThePair() {
            assertThatThrownBy(() -> read(BODY.substring(2), RequestServerList::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Encode {
        @Test
        void encode_shouldWriteTheOpcodeAndThePair() {
            assertThat(written(PACKET::encode)).isEqualTo("05" + BODY);
        }
    }
}
