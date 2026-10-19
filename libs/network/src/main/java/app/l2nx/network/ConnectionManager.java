/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.network;

import app.l2nx.network.config.ConnectionConfig;
import app.l2nx.network.config.NetworkSettings;
import app.l2nx.network.handler.ReadHandler;
import app.l2nx.network.handler.WriteHandler;
import app.l2nx.network.packet.PacketExecutor;
import app.l2nx.network.packet.PacketHandler;
import app.l2nx.network.packet.PacketThreadFactory;
import app.l2nx.network.packet.ReadablePacket;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Binds a server socket, accepts incoming TCP connections and initialises a {@link Client} for each one via a caller-supplied factory.<br>
 * <br>
 * The underlying {@link AsynchronousChannelGroup} uses a bounded thread pool sized by {@link ConnectionConfig#threadPoolSize}. A {@link LinkedBlockingQueue} work queue allows the pool to buffer short bursts of connection-accept events without spawning unbounded threads.
 * @param <T> the concrete client type created for each accepted connection
 * @author Mobius
 */
public class ConnectionManager<T extends Client<Connection<T>>> {
    private final ThreadPoolExecutor ioPool;
    private final PacketExecutor<T> packetExecutor;
    private final AsynchronousChannelGroup group;
    private final AsynchronousServerSocketChannel socketChannel;
    private final ConnectionConfig config;
    private final WriteHandler<T> writeHandler;
    private final ReadHandler<T> readHandler;
    private final Function<Connection<T>, T> clientFactory;

    /**
     * Binds the server to {@code address} and starts accepting connections.
     * @param address the local address and port to listen on
     * @param clientFactory function that creates a {@link Client} for a given {@link Connection}
     * @param packetHandler handler that maps incoming bytes to concrete {@link ReadablePacket}s
     * @param settings the network settings of the server
     * @throws IOException if the server socket cannot be opened or bound
     */
    public ConnectionManager(
            InetSocketAddress address,
            Function<Connection<T>, T> clientFactory,
            PacketHandler<T> packetHandler,
            NetworkSettings settings)
            throws IOException {
        config = new ConnectionConfig(address, settings);
        this.clientFactory = clientFactory;
        packetExecutor = new PacketExecutor<>(config);
        readHandler = new ReadHandler<>(packetHandler, packetExecutor);
        writeHandler = new WriteHandler<>();

        // Bounded thread pool: core == max == threadPoolSize; idle threads kept alive for 1 minute.
        // LinkedBlockingQueue buffers short accept bursts without spawning extra threads.
        final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
                config.threadPoolSize,
                config.threadPoolSize,
                1,
                TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(),
                new PacketThreadFactory("Server", config.threadPriority));
        threadPool.allowCoreThreadTimeOut(true);
        ioPool = threadPool;

        group = AsynchronousChannelGroup.withThreadPool(threadPool);
        try {
            socketChannel = group.provider().openAsynchronousServerSocketChannel(group);
            socketChannel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            socketChannel.bind(config.address);
        } catch (IOException | RuntimeException e) {
            // The group's threads are non-daemon: left running, they would keep a server that failed to bind alive.
            group.shutdownNow();
            throw e;
        }

        // Begin the accept loop.
        socketChannel.accept(null, new AcceptConnectionHandler());
    }

    public void bindMetrics(MeterRegistry registry, String name) {
        new ExecutorServiceMetrics(ioPool, name + "-io", Tags.empty()).bindTo(registry);
        packetExecutor.bindMetrics(registry, name + "-packets");
    }

    public void stopAccepting() {
        try {
            socketChannel.close();
        } catch (IOException e) {
        }
    }

    /**
     * Stops accepting new connections and shuts down the channel group.<br>
     * Waits up to {@link ConnectionConfig#shutdownWaitTime} ms for in-flight I/O to complete.
     */
    public void shutdown() {
        try {
            socketChannel.close();
            group.shutdown();
            group.awaitTermination(config.shutdownWaitTime, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Best-effort shutdown; errors here are not actionable.
        } finally {
            try {
                group.shutdownNow();
            } catch (IOException e) {
            }
            packetExecutor.shutdown(config.shutdownWaitTime);
        }
    }

    private class AcceptConnectionHandler implements CompletionHandler<AsynchronousSocketChannel, Void> {
        @Override
        public void completed(AsynchronousSocketChannel clientChannel, Void attachment) {
            // Re-arm the accept loop before processing the new channel so that further connections are not delayed by
            // client initialisation.
            if (socketChannel.isOpen()) {
                socketChannel.accept(null, this);
            }

            processNewConnection(clientChannel);
        }

        @Override
        public void failed(Throwable t, Void attachment) {
            // Re-arm even on failure so transient errors do not kill the accept loop.
            if (socketChannel.isOpen()) {
                socketChannel.accept(null, this);
            }
        }

        private void processNewConnection(AsynchronousSocketChannel channel) {
            if ((channel == null) || !channel.isOpen()) {
                return;
            }

            try {
                channel.setOption(StandardSocketOptions.TCP_NODELAY, !config.useNagle);

                final Connection<T> connection = new Connection<>(channel, readHandler, writeHandler, config);
                final T client = clientFactory.apply(connection);
                connection.setClient(client);

                client.onConnected();
                client.read();
            } catch (ClosedChannelException e) {
                // Channel was closed between accept and setup - nothing to do.
            } catch (Exception e) {
                try {
                    channel.close();
                } catch (IOException ioe) {
                    // Ignore; channel is already unusable.
                }
            }
        }
    }
}
