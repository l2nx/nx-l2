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

class AuthGameGuardTest {
    private static final String BODY = "0df0ad0b" + "00000000000000000000000000000000";

    @Nested
    class Decode {
        @Test
        void decode_shouldReadTheSessionId_andSkipTheGameGuardData() {
            final AuthGameGuard actual = read("0df0ad0b" + "01000000020000000300000004000000", AuthGameGuard::decode);

            assertThat(actual).isEqualTo(new AuthGameGuard(0x0BADF00D));
        }

        @Test
        void decode_shouldFail_whenTheBodyIsShorterThanTwentyBytes() {
            assertThatThrownBy(() -> read(BODY.substring(2), AuthGameGuard::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class Encode {
        @Test
        void encode_shouldWriteTheOpcodeAndTheBody() {
            final String actual = written(new AuthGameGuard(0x0BADF00D)::encode);

            assertThat(actual).isEqualTo("07" + BODY);
        }
    }
}
