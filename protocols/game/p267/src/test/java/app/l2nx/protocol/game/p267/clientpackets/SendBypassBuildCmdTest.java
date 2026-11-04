/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SendBypassBuildCmdTest {
    @Nested
    class Codec {
        @Test
        void decode_shouldRoundTripTheCommand() {
            final String body = s("set_level 75");
            final SendBypassBuildCmd packet = new SendBypassBuildCmd("set_level 75");

            assertThat(read(body, SendBypassBuildCmd::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("74" + body);
        }

        @Test
        void decode_shouldKeepTheCharactersRead_whenTheTerminatorIsMissing() {
            final String body = s("target").substring(0, "target".length() * 4);

            assertThat(read(body, SendBypassBuildCmd::decode)).isEqualTo(new SendBypassBuildCmd("target"));
        }
    }
}
