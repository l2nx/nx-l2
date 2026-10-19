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
package app.l2nx.network.packet;

import app.l2nx.network.Client;
import app.l2nx.network.Connection;
import app.l2nx.network.config.ConnectionConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-pool-based executor for incoming network packets.<br>
 * Received packets are submitted here after successful parsing so that their {@link ReadablePacket#run()} logic executes off the I/O thread.
 * @param <T> the client type associated with the packets being executed
 * @author Mobius
 */
@Slf4j
public class PacketExecutor<T extends Client<Connection<T>>> {

    private final ThreadPoolExecutor executor;

    /**
     * Creates an executor sized according to {@link ConnectionConfig#threadPoolSize}.
     * @param config the connection configuration supplying pool size and thread priority
     */
    public PacketExecutor(ConnectionConfig config) {
        executor = new ThreadPoolExecutor(
                config.threadPoolSize,
                Integer.MAX_VALUE,
                1,
                TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(),
                new PacketThreadFactory("PacketExecutor", config.threadPriority));
    }

    public void bindMetrics(MeterRegistry registry, String name) {
        new ExecutorServiceMetrics(executor, name, Tags.empty()).bindTo(registry);
    }

    public void shutdown(long waitMillis) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(waitMillis, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Submits a packet for execution on a worker thread.
     * @param packet the packet to execute
     */
    public void execute(ReadablePacket<T> packet) {
        try {
            executor.execute(new PacketRunnable<>(packet));
        } catch (Exception e) {
            log.warn("Failed to submit {}", packet.getClass().getSimpleName(), e);
        }
    }

    @Slf4j
    private static class PacketRunnable<T extends Client<Connection<T>>> implements Runnable {
        private final ReadablePacket<T> packet;

        public PacketRunnable(ReadablePacket<T> packet) {
            this.packet = packet;
        }

        @Override
        public void run() {
            try {
                packet.run();
            } catch (Throwable t) {
                final Thread currentThread = Thread.currentThread();
                final UncaughtExceptionHandler handler = currentThread.getUncaughtExceptionHandler();
                if (handler != null) {
                    handler.uncaughtException(currentThread, t);
                } else {
                    log.error("Uncaught exception in {}", packet.getClass().getSimpleName(), t);
                }
            }
        }
    }
}
