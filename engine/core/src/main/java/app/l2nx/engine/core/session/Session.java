/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.engine.core.loop.FailureHandler;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.Message;
import app.l2nx.engine.core.loop.PassPhase;
import app.l2nx.engine.core.loop.inbox.InputSource;
import app.l2nx.engine.core.session.command.CoalescableCommand;
import app.l2nx.engine.core.session.command.Command;
import app.l2nx.engine.core.session.command.CommandDispatcher;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.network.channel.CloseReason;
import app.l2nx.network.channel.RateLimitedLog;
import app.l2nx.network.config.NetworkProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;
import io.github.bucket4j.local.SynchronizationStrategy;
import java.lang.invoke.VarHandle;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jctools.queues.atomic.SpscAtomicArrayQueue;

/**
 * The IO thread is the single producer, one drain at a time the single consumer. A {@link ContextBound} item's context
 * is checked when it executes, not when offered. Once closed, only the pending close counts as input, so a close
 * racing an idle session still gets its drain.
 */
@Slf4j
public final class Session implements InputSource {
    private static final AtomicLong IDS = new AtomicLong();
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private final long id = IDS.incrementAndGet();
    private final int queueLimit;
    private final int perDrain;
    private final SpscAtomicArrayQueue<Input> queue;
    private final Bucket rate;
    private final Bucket failures;
    private final DrainScheduler connectedScheduler;
    private final DrainScheduler authedScheduler;
    private final CommandDispatcher dispatcher;
    private final SessionMetrics metrics;
    private final SessionTransport transport;
    private final Runnable finished;
    private final AtomicBoolean signalled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean closeHandled = new AtomicBoolean();
    // Transitions are rare; holding the lock across the notification keeps the transport's view in transition order.
    private final ReentrantLock transitions = new ReentrantLock();
    private volatile SessionState state = SessionState.CONNECTED;
    private volatile DrainScheduler scheduler;
    private volatile Runnable onClosed = () -> {};
    private volatile Object attachment;
    private volatile ClientPort port = ClientPort.DETACHED;

    /**
     * @param failureLimit failed items tolerated within {@code failureWindow}; one more closes the connection
     */
    @Builder(toBuilder = true)
    public record Limits(
            int queueLimit, int perDrain, int perSecond, int burst, int failureLimit, Duration failureWindow) {
        public static Limits of(NetworkProperties properties) {
            final NetworkProperties.DecodeErrorLimit errors = properties.decodeErrorLimit();
            return builder()
                    .queueLimit(properties.inboundQueueLimit())
                    .perDrain(properties.packetsPerDrain())
                    .perSecond(properties.packetsPerSecond())
                    .burst(properties.inboundQueueLimit())
                    .failureLimit(errors.count())
                    .failureWindow(errors.window())
                    .build();
        }
    }

    /**
     * @param finished runs once the close is fully handled, after the closed callback and the move to
     *     {@code DISCONNECTED}, even when the callback fails
     */
    Session(
            Limits limits,
            DrainScheduler connectedScheduler,
            DrainScheduler authedScheduler,
            CommandDispatcher dispatcher,
            SessionMetrics metrics,
            SessionTransport transport,
            TimeMeter time,
            Runnable finished) {
        queueLimit = limits.queueLimit();
        perDrain = limits.perDrain();
        queue = new SpscAtomicArrayQueue<>(limits.queueLimit());
        // Only the IO thread takes tokens, so the rate bucket needs no synchronization.
        rate = Bucket.builder()
                .addLimit(
                        limit -> limit.capacity(limits.burst()).refillGreedy(limits.perSecond(), Duration.ofSeconds(1)))
                .withCustomTimePrecision(time)
                .withSynchronizationStrategy(SynchronizationStrategy.NONE)
                .build();
        failures = Bucket.builder()
                .addLimit(limit -> limit.capacity(limits.failureLimit())
                        .refillGreedy(limits.failureLimit(), limits.failureWindow()))
                .withCustomTimePrecision(time)
                .build();
        this.connectedScheduler = connectedScheduler;
        this.authedScheduler = authedScheduler;
        this.scheduler = connectedScheduler;
        this.dispatcher = dispatcher;
        this.metrics = metrics;
        this.transport = transport;
        this.finished = finished;
    }

    public Input input(Object item) {
        if (!(item instanceof Command) && !(item instanceof Runnable)) {
            throw new IllegalArgumentException("Session input is a command or a legacy packet, not "
                    + item.getClass().getName());
        }
        return new Input(this, item);
    }

    /** IO thread only. {@code false} is a flood and the caller closes the connection. */
    public boolean offer(Input input) {
        if (input.session != this) {
            throw new IllegalArgumentException("Input of session " + input.session.id + " offered to session " + id);
        }
        if (closed.get()) {
            return true;
        }
        return rate.tryConsume(1) && enqueue(input);
    }

    /** Any thread: a command the server addresses to this session. It skips the rate limit. */
    public void offerInternal(Command command) {
        transport.execute(() -> {
            if (!closed.get() && !enqueue(new Input(this, command))) {
                transport.close(CloseReason.FLOOD);
            }
        });
    }

    private boolean enqueue(Input input) {
        // The queue rounds its capacity up to a power of two; the limit is enforced here.
        if ((queue.size() >= queueLimit) || !queue.offer(input)) {
            return false;
        }
        metrics.queued(queue.size());
        signal();
        return true;
    }

    // Producer side of the InputSource protocol: the offer is a release store, so the fence orders it before the flag.
    void signal() {
        VarHandle.fullFence();
        if (trySignal()) {
            scheduler.schedule(this);
        }
    }

    public SessionState state() {
        return state;
    }

    /**
     * Once closing, a move is a silent no-op (a handler racing the close must not fail); {@link #close()} owns
     * {@code CLOSING} and {@code DISCONNECTED}.
     */
    public void setState(SessionState next) {
        if ((next == SessionState.CLOSING) || (next == SessionState.DISCONNECTED)) {
            throw new IllegalArgumentException("Session moves to " + next + " only through close()");
        }
        transitions.lock();
        try {
            final SessionState previous = state;
            if ((previous == SessionState.CLOSING) || (previous == SessionState.DISCONNECTED)) {
                return;
            }
            if (!previous.canMoveTo(next)) {
                throw new IllegalStateException("Session cannot move from " + previous + " to " + next);
            }
            state = next;
            if (next == SessionState.AUTHED) {
                scheduler = authedScheduler;
            }
            transport.stateChanged(previous, next);
        } finally {
            transitions.unlock();
        }
    }

    /** Any thread; the drain discards what is still queued and runs the closed callback once. */
    public void close() {
        transitions.lock();
        try {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            final SessionState previous = state;
            state = SessionState.CLOSING;
            transport.stateChanged(previous, SessionState.CLOSING);
        } finally {
            transitions.unlock();
        }
        scheduler.scheduleClose(this);
    }

    public void onClosed(Runnable callback) {
        onClosed = callback;
    }

    public void attach(Object owner) {
        attachment = owner;
    }

    public <T> T attachment(Class<T> type) {
        return type.cast(attachment);
    }

    public ClientPort port() {
        return port;
    }

    public void bindPort(ClientPort port) {
        this.port = port;
    }

    public int queued() {
        return queue.size();
    }

    public long id() {
        return id;
    }

    /**
     * The next item, or {@code null} when the queue is empty or the session is closing. A run of coalescable
     * commands of one type yields only its latest.
     */
    @Override
    public Message poll() {
        while (!closed.get()) {
            final Input input = queue.poll();
            if (input == null) {
                return null;
            }
            if (!(input.item() instanceof CoalescableCommand) || !sameTypeIsNext(input)) {
                return input;
            }
        }
        finishClose();
        return null;
    }

    @Override
    public boolean hasInput() {
        return closed.get() ? !closeHandled.get() : !queue.isEmpty();
    }

    @Override
    public boolean trySignal() {
        return signalled.compareAndSet(false, true);
    }

    @Override
    public void clearSignal() {
        signalled.set(false);
    }

    void drainOnPool(FailureHandler failureHandler) {
        for (int ran = 0; (ran < perDrain) && (scheduler == connectedScheduler); ran++) {
            final Message input;
            try {
                input = poll();
            } catch (RuntimeException e) {
                failureHandler.onFailure(PassPhase.INBOX, this, e);
                break;
            }
            if (input == null) {
                break;
            }
            try {
                // Pool items run without a loop; Input ignores the argument.
                input.execute(null);
            } catch (RuntimeException e) {
                failureHandler.onFailure(PassPhase.INBOX, input, e);
            }
        }
        if (hasInput()) {
            // Keeps the signal and queues behind the drains already waiting for a permit.
            scheduler.schedule(this);
            return;
        }
        clearSignal();
        // Pairs with the producer's fence: either this check sees the item or the producer sees the cleared flag.
        VarHandle.fullFence();
        if (hasInput() && trySignal()) {
            scheduler.schedule(this);
        }
    }

    /** The pool closed before an accepted drain started: queued items are not drained and the close may stay unhandled. */
    void drainAbandoned() {
        log.warn(
                "Session {} ({}, {} queued) lost its drain: the pool closed before the drain started",
                id,
                state,
                queue.size());
        clearSignal();
    }

    private boolean sameTypeIsNext(Input input) {
        final Input next = queue.peek();
        return (next != null) && (next.item().getClass() == input.item().getClass());
    }

    private void run(Object item) {
        try {
            if ((item instanceof ContextBound bound) && !bound.hasContext()) {
                reject(item, bound);
                return;
            }
            if (item instanceof Command command) {
                metrics.command(command, () -> dispatcher.dispatch(this, command));
            } else {
                metrics.legacyPacket((Runnable) item);
            }
        } catch (RuntimeException e) {
            itemFailed();
            throw e;
        }
    }

    // An input racing the close is discarded like the rest of the queue, uncounted.
    private void reject(Object item, ContextBound bound) {
        if (closed.get()) {
            return;
        }
        metrics.rejected(item, bound.requiredContext());
        if (log.isDebugEnabled()
                && RATE.tryAcquire("rejected-" + item.getClass().getName())) {
            log.debug("Session {} rejected {}: needs {}", id, item.getClass().getName(), bound.requiredContext());
        }
    }

    // A client can make legacy code throw on purpose; each failure is an ERROR line, so their rate is bounded.
    private void itemFailed() {
        if (!failures.tryConsume(1) && !closed.get()) {
            log.warn("Closing session {}: too many failed packets", id);
            transport.close(CloseReason.ERROR);
        }
    }

    Message closeMessage() {
        return new Close(this);
    }

    void finishClose() {
        if (!closeHandled.compareAndSet(false, true)) {
            return;
        }
        queue.clear();
        try {
            onClosed.run();
        } finally {
            transitions.lock();
            try {
                state = SessionState.DISCONNECTED;
                transport.stateChanged(SessionState.CLOSING, SessionState.DISCONNECTED);
            } finally {
                transitions.unlock();
                finished.run();
            }
        }
    }

    private record Close(Session session) implements Message {
        @Override
        public void execute(Loop loop) {
            session.finishClose();
        }

        @Override
        public String toString() {
            return "Close[session=" + session.id + "]";
        }
    }

    public static final class Input implements Message {
        private final Session session;
        private final Object item;

        private Input(Session session, Object item) {
            this.session = session;
            this.item = item;
        }

        public Object item() {
            return item;
        }

        @Override
        public void execute(Loop loop) {
            session.run(item);
        }

        @Override
        public String toString() {
            return "Input[session=" + session.id + ", item=" + item + "]";
        }
    }
}
