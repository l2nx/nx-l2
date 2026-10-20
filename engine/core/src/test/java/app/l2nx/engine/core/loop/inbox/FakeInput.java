/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.Message;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Builder;

public final class FakeInput implements InputSource {
    private final long id;
    private final String label;
    private final List<String> log;
    private final ArrayDeque<Item> items = new ArrayDeque<>();
    private final AtomicBoolean signalled = new AtomicBoolean();
    private final Set<String> failing = new HashSet<>();
    private final Map<String, Integer> runs = new HashMap<>();
    private int next;
    // Runs inside clearSignal, as if a producer offered right then and found the source still signalled.
    public Runnable onClear = () -> {};
    public final Set<Operation> broken = EnumSet.noneOf(Operation.class);

    public FakeInput(long id, String label, List<String> log) {
        this.id = id;
        this.label = label;
        this.log = log;
    }

    public void push(Loop loop, int count) {
        enqueue(count);
        if (trySignal()) {
            loop.signal(this);
        }
    }

    /** Enqueues without signalling, as a producer does that finds the source still signalled. */
    public void enqueue(int count) {
        for (int i = 0; i < count; i++) {
            items.add(Item.builder().name(label + next++).source(this).build());
        }
    }

    public void fail(String name) {
        failing.add(name);
    }

    public int runs(String name) {
        return runs.getOrDefault(name, 0);
    }

    public boolean isSignalled() {
        return signalled.get();
    }

    @Override
    public long id() {
        check(Operation.ID);
        return id;
    }

    @Override
    public Message poll() {
        check(Operation.POLL);
        return items.poll();
    }

    @Override
    public boolean hasInput() {
        check(Operation.HAS_INPUT);
        return !items.isEmpty();
    }

    @Override
    public boolean trySignal() {
        check(Operation.TRY_SIGNAL);
        return signalled.compareAndSet(false, true);
    }

    @Override
    public void clearSignal() {
        onClear.run();
        check(Operation.CLEAR_SIGNAL);
        signalled.set(false);
    }

    private void check(Operation operation) {
        if (broken.contains(operation)) {
            throw new IllegalStateException(operation + " of " + label + " failed");
        }
    }

    private void run(String name) {
        runs.merge(name, 1, Integer::sum);
        if (failing.contains(name)) {
            throw new IllegalStateException(name + " failed");
        }
        log.add(name);
    }

    public enum Operation {
        ID,
        POLL,
        HAS_INPUT,
        TRY_SIGNAL,
        CLEAR_SIGNAL
    }

    @Builder(toBuilder = true)
    public record Item(String name, FakeInput source) implements Message {
        @Override
        public void execute(Loop loop) {
            source.run(name);
        }
    }
}
