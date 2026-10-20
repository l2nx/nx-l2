/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.transport;

import app.l2nx.network.config.Transport;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.channel.uring.IoUringServerSocketChannel;
import io.netty.channel.uring.IoUringSocketChannel;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TransportResolver {
    private final BooleanSupplier epollAvailable;
    private final Supplier<Throwable> epollCause;
    private final BooleanSupplier ioUringAvailable;
    private final Supplier<Throwable> ioUringCause;

    public TransportResolver(
            BooleanSupplier epollAvailable,
            Supplier<Throwable> epollCause,
            BooleanSupplier ioUringAvailable,
            Supplier<Throwable> ioUringCause) {
        this.epollAvailable = epollAvailable;
        this.epollCause = epollCause;
        this.ioUringAvailable = ioUringAvailable;
        this.ioUringCause = ioUringCause;
    }

    public static TransportResolver nativeAvailability() {
        return new TransportResolver(
                Epoll::isAvailable, Epoll::unavailabilityCause, IoUring::isAvailable, IoUring::unavailabilityCause);
    }

    public ResolvedTransport resolve(Transport requested) {
        return switch (requested) {
            case AUTO -> {
                if (epollAvailable.getAsBoolean()) {
                    yield epoll();
                }
                final Throwable cause = epollCause.get();
                log.info("epoll is not available ({}), using NIO", cause == null ? "unknown cause" : cause.toString());
                yield nio();
            }
            case EPOLL -> {
                if (!epollAvailable.getAsBoolean()) {
                    throw new IllegalStateException(
                            "l2nx.network.transport=epoll, but epoll is not available", epollCause.get());
                }
                yield epoll();
            }
            case IO_URING -> {
                if (!ioUringAvailable.getAsBoolean()) {
                    throw new IllegalStateException(
                            "l2nx.network.transport=io-uring, but io_uring is not available", ioUringCause.get());
                }
                yield ResolvedTransport.builder()
                        .transport(Transport.IO_URING)
                        .ioHandlerFactory(IoUringIoHandler.newFactory())
                        .serverChannelClass(IoUringServerSocketChannel.class)
                        .socketChannelClass(IoUringSocketChannel.class)
                        .build();
            }
            case NIO -> nio();
        };
    }

    private static ResolvedTransport epoll() {
        return ResolvedTransport.builder()
                .transport(Transport.EPOLL)
                .ioHandlerFactory(EpollIoHandler.newFactory())
                .serverChannelClass(EpollServerSocketChannel.class)
                .socketChannelClass(EpollSocketChannel.class)
                .build();
    }

    private static ResolvedTransport nio() {
        return ResolvedTransport.builder()
                .transport(Transport.NIO)
                .ioHandlerFactory(NioIoHandler.newFactory())
                .serverChannelClass(NioServerSocketChannel.class)
                .socketChannelClass(NioSocketChannel.class)
                .build();
    }
}
