/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.config.Transport;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TransportResolverTest {
    private static final Throwable MISSING = new UnsatisfiedLinkError("no native library");

    @Test
    void resolve_shouldFallBackToNio_whenAutoAndEpollIsUnavailable() {
        final TransportResolver resolver =
                new TransportResolver(() -> false, () -> MISSING, () -> false, () -> MISSING);

        final ResolvedTransport actual = resolver.resolve(Transport.AUTO);

        assertThat(actual.transport()).isEqualTo(Transport.NIO);
        assertThat(actual.serverChannelClass()).isEqualTo(NioServerSocketChannel.class);
        assertThat(actual.socketChannelClass()).isEqualTo(NioSocketChannel.class);
    }

    @Test
    void resolve_shouldNeverPickIoUring_whenAuto() {
        final TransportResolver resolver = new TransportResolver(() -> false, () -> MISSING, () -> true, () -> null);

        assertThat(resolver.resolve(Transport.AUTO).transport()).isEqualTo(Transport.NIO);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"EPOLL, epoll", "IO_URING, io_uring"})
    void resolve_shouldFailStartup_whenRequestedNativeTransportIsUnavailable(Transport requested, String name) {
        final TransportResolver resolver =
                new TransportResolver(() -> false, () -> MISSING, () -> false, () -> MISSING);

        assertThatThrownBy(() -> resolver.resolve(requested))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name)
                .hasCause(MISSING);
    }

    @Test
    void resolve_shouldPickNio_whenNioIsRequested() {
        assertThat(TransportResolver.nativeAvailability().resolve(Transport.NIO).transport())
                .isEqualTo(Transport.NIO);
    }
}
