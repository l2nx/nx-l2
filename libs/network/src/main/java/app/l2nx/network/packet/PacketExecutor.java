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
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
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
    static final String RUN_TIME = "l2nx.legacy.packet";
    static final String TYPE_TAG = "type";
    static final String OUTCOME_TAG = "outcome";
    static final String EXCEPTION_TAG = "exception";
    static final String SUCCESS = "SUCCESS";
    static final String ERROR = "ERROR";
    static final String NO_EXCEPTION = "none";

    private final ThreadPoolExecutor executor;

    // Bound after the port opens; a packet submitted before runs untimed.
    private volatile PacketTimers packetTimers = PacketTimers.UNTIMED;

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
        packetTimers = new MeteredPacketTimers(registry);
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

    public void execute(ReadablePacket<T> packet) {
        try {
            executor.execute(new PacketRunnable<>(packet, packetTimers));
        } catch (Exception e) {
            log.warn("Failed to submit {}", packet.getClass().getSimpleName(), e);
        }
    }

    private interface PacketTimers {
        PacketTimers UNTIMED = Runnable::run;

        /** Runs the packet; a failure is rethrown. */
        void run(Runnable packet);
    }

    private static final class MeteredPacketTimers implements PacketTimers {
        private final Clock clock;
        private final MeterProvider<Timer> runTime;
        private final ClassValue<Timer> successTimers;

        MeteredPacketTimers(MeterRegistry registry) {
            clock = registry.config().clock();
            runTime = Timer.builder(RUN_TIME)
                    .description("Run time of client packets")
                    .withRegistry(registry);
            successTimers = new ClassValue<>() {
                @Override
                protected Timer computeValue(Class<?> type) {
                    return runTime.withTags(
                            TYPE_TAG, type.getName(), OUTCOME_TAG, SUCCESS, EXCEPTION_TAG, NO_EXCEPTION);
                }
            };
        }

        @Override
        public void run(Runnable packet) {
            final Timer success = successTimers.get(packet.getClass());
            final long start = clock.monotonicTime();
            try {
                packet.run();
            } catch (Throwable t) {
                // The duration is taken before the error timer is looked up, which may register it.
                final long duration = clock.monotonicTime() - start;
                runTime.withTags(
                                TYPE_TAG,
                                packet.getClass().getName(),
                                OUTCOME_TAG,
                                ERROR,
                                EXCEPTION_TAG,
                                exceptionName(t))
                        .record(duration, TimeUnit.NANOSECONDS);
                throw t;
            }
            success.record(clock.monotonicTime() - start, TimeUnit.NANOSECONDS);
        }

        // As Spring's DefaultServerRequestObservationConvention: the binary name stands in for an anonymous class.
        private static String exceptionName(Throwable failure) {
            final String simpleName = failure.getClass().getSimpleName();
            return simpleName.isEmpty() ? failure.getClass().getName() : simpleName;
        }
    }

    private static class PacketRunnable<T extends Client<Connection<T>>> implements Runnable {
        private final ReadablePacket<T> packet;
        private final PacketTimers timers;

        PacketRunnable(ReadablePacket<T> packet, PacketTimers timers) {
            this.packet = packet;
            this.timers = timers;
        }

        @Override
        public void run() {
            try {
                timers.run(packet);
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
