/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel.guard;

import app.l2nx.network.channel.Connections;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.model.DropReason;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.local.SynchronizationStrategy;
import io.netty.channel.Channel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Runs in {@code initChannel}, before any byte is read. Per-address state is touched only inside
 * {@link ConcurrentHashMap#compute}, which serializes it, so its bucket needs no synchronization of its own.
 */
public final class ConnectionLimiter {
    private static final long IDLE_NANOS = Duration.ofSeconds(1).toNanos();

    private final int maxConnections;
    private final int connectionsPerIp;
    private final int acceptPerSecond;
    private final Predicate<InetAddress> banned;
    private final TimeMeter time;
    private final AtomicInteger total = new AtomicInteger();
    private final ConcurrentHashMap<InetAddress, AddressState> addresses = new ConcurrentHashMap<>();

    public ConnectionLimiter(NetworkProperties properties, Predicate<InetAddress> banned) {
        this(properties, banned, TimeMeter.SYSTEM_NANOTIME);
    }

    ConnectionLimiter(NetworkProperties properties, Predicate<InetAddress> banned, TimeMeter time) {
        maxConnections = properties.maxConnections();
        connectionsPerIp = properties.connectionsPerIp();
        acceptPerSecond = properties.acceptPerIpPerSecond();
        this.banned = banned;
        this.time = time;
    }

    public boolean tryAccept(Channel channel) {
        final InetAddress address =
                channel.remoteAddress() instanceof InetSocketAddress socket ? socket.getAddress() : null;
        final DropReason rejected = admit(address);
        if (rejected != null) {
            Connections.metrics(channel).dropped(rejected);
            return false;
        }
        channel.closeFuture().addListener(future -> release(address));
        return true;
    }

    /**
     * @param address {@code null} for a peer without an IP address (a local channel, an unresolved address): only the
     *     total limit applies
     */
    DropReason admit(InetAddress address) {
        if ((address != null) && banned.test(address)) {
            return DropReason.BANNED;
        }
        if (total.incrementAndGet() > maxConnections) {
            total.decrementAndGet();
            return DropReason.MAX_CONNECTIONS;
        }
        if (address == null) {
            return null;
        }

        final DropReason[] rejected = {null};
        addresses.compute(address, (key, existing) -> {
            final AddressState state = existing == null ? new AddressState(newBucket()) : existing;
            state.lastAcceptNanos = time.currentTimeNanos();
            if (!state.bucket.tryConsume(1)) {
                rejected[0] = DropReason.ACCEPT_RATE;
            } else if (state.connections >= connectionsPerIp) {
                rejected[0] = DropReason.CONNECTIONS_PER_IP;
            } else {
                state.connections++;
            }
            return state;
        });
        if (rejected[0] != null) {
            total.decrementAndGet();
        }
        return rejected[0];
    }

    void release(InetAddress address) {
        total.decrementAndGet();
        if (address == null) {
            return;
        }
        addresses.computeIfPresent(address, (key, state) -> {
            state.connections--;
            return state;
        });
    }

    /** An address without connections whose last accept is older than one refill period has nothing to remember. */
    public void sweep() {
        final long now = time.currentTimeNanos();
        for (InetAddress address : addresses.keySet()) {
            addresses.computeIfPresent(
                    address,
                    (key, state) ->
                            ((state.connections == 0) && ((now - state.lastAcceptNanos) >= IDLE_NANOS)) ? null : state);
        }
    }

    int trackedAddresses() {
        return addresses.size();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(acceptPerSecond).refillGreedy(acceptPerSecond, Duration.ofSeconds(1)))
                .withCustomTimePrecision(time)
                .withSynchronizationStrategy(SynchronizationStrategy.NONE)
                .build();
    }

    private static final class AddressState {
        private final Bucket bucket;
        private int connections;
        private long lastAcceptNanos;

        private AddressState(Bucket bucket) {
            this.bucket = bucket;
        }
    }
}
