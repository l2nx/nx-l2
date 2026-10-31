/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.protocol.game.p267.ChatChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class P267ChatTypesTest {
    @Nested
    class Channel {
        @ParameterizedTest(name = "{0}")
        @EnumSource(ChatType.class)
        void channel_shouldRoundTripThroughItsType(ChatType type) {
            assertThat(P267ChatTypes.type(P267ChatTypes.channel(type))).isSameAs(type);
        }
    }

    @Nested
    class Type {
        @ParameterizedTest(name = "{0}")
        @EnumSource(ChatChannel.class)
        void type_shouldExistForEveryChannel(ChatChannel channel) {
            assertThat(P267ChatTypes.type(channel)).isNotNull();
        }

        @Test
        void type_shouldBeNull_whenTheChannelIsUnknown() {
            assertThat(P267ChatTypes.type(null)).isNull();
        }
    }
}
