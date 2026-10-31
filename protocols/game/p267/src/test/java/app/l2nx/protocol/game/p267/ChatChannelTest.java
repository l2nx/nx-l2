/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ChatChannelTest {
    @Nested
    class Of {
        @ParameterizedTest
        @EnumSource(ChatChannel.class)
        void of_shouldFindEveryChannelByItsCode(ChatChannel channel) {
            assertThat(ChatChannel.of(channel.code())).isSameAs(channel);
        }

        @Test
        void of_shouldBeNull_whenTheCodeIsUnknown() {
            assertThat(ChatChannel.of(24)).isNull();
            assertThat(ChatChannel.of(-1)).isNull();
        }
    }

    @Nested
    class Code {
        @Test
        void code_shouldBeTheHighFiveChannelId() {
            assertThat(ChatChannel.GENERAL.code()).isZero();
            assertThat(ChatChannel.WHISPER.code()).isEqualTo(2);
            assertThat(ChatChannel.NPC_SHOUT.code()).isEqualTo(23);
        }
    }
}
