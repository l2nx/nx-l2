/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class P267SettingsTest {
    private static final P267Settings VALID = P267Settings.builder()
            .login(new InetSocketAddress(InetAddress.getLoopbackAddress(), 2106))
            .serverId(1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Nested
    class Validate {
        @Test
        void builder_shouldAcceptTheLargestServerId() {
            assertThat(VALID.toBuilder().serverId(255).build().serverId()).isEqualTo(255);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.load.protocol.p267.P267SettingsTest#invalid")
        void builder_shouldReject(String name, UnaryOperator<P267Settings.P267SettingsBuilder> change, String message) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> change.apply(VALID.toBuilder()).build())
                    .withMessageContaining(message);
        }
    }

    static Stream<Arguments> invalid() {
        return Stream.of(
                Arguments.of("no login server", change(builder -> builder.login(null)), "login server address"),
                Arguments.of("server id 0", change(builder -> builder.serverId(0)), "game server id"),
                Arguments.of("server id past a byte", change(builder -> builder.serverId(256)), "game server id"),
                Arguments.of("no connect timeout", change(builder -> builder.connectTimeout(null)), "connect timeout"),
                Arguments.of(
                        "zero connect timeout",
                        change(builder -> builder.connectTimeout(Duration.ZERO)),
                        "connect timeout"));
    }

    private static UnaryOperator<P267Settings.P267SettingsBuilder> change(
            UnaryOperator<P267Settings.P267SettingsBuilder> change) {
        return change;
    }
}
