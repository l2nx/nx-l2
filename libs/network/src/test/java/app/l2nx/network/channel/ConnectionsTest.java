/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.Channel;
import io.netty.channel.local.LocalAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConnectionsTest {
    private final Channel channel = mock(Channel.class);

    @Nested
    class RemoteIp {
        @ParameterizedTest(name = "{0} gives {1}")
        @MethodSource("app.l2nx.network.channel.ConnectionsTest#peers")
        void remoteIp_shouldNameThePeer(SocketAddress peer, String expected) {
            when(channel.remoteAddress()).thenReturn(peer);

            assertThat(Connections.remoteIp(channel)).isEqualTo(expected);
        }

        @Test
        void remoteIp_shouldBeNull_whenTheChannelHasNoPeer() {
            assertThat(Connections.remoteIp(channel)).isNull();
        }
    }

    static Stream<Arguments> peers() {
        return Stream.of(
                Arguments.of(new InetSocketAddress("10.0.0.7", 51000), "10.0.0.7"),
                Arguments.of(new LocalAddress("login"), "local:login"),
                Arguments.of(
                        InetSocketAddress.createUnresolved("proxy.example", 51000),
                        "proxy.example/<unresolved>:51000"));
    }
}
