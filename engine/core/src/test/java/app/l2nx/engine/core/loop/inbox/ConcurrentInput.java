/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.Message;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Builder;

final class ConcurrentInput implements InputSource {
    private final ConcurrentLinkedQueue<Item> items = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean signalled = new AtomicBoolean();
    // Loop thread only.
    int drained;
    int outOfOrder;

    void push(Loop loop, int item) {
        items.add(Item.builder().value(item).source(this).build());
        VarHandle.fullFence();
        if (trySignal()) {
            loop.signal(this);
        }
    }

    boolean isSignalled() {
        return signalled.get();
    }

    @Override
    public long id() {
        return 1;
    }

    @Override
    public Message poll() {
        return items.poll();
    }

    @Override
    public boolean hasInput() {
        return !items.isEmpty();
    }

    @Override
    public boolean trySignal() {
        return signalled.compareAndSet(false, true);
    }

    @Override
    public void clearSignal() {
        signalled.set(false);
    }

    private void run(int item) {
        if (item != drained) {
            outOfOrder++;
        }
        drained++;
    }

    @Builder(toBuilder = true)
    private record Item(int value, ConcurrentInput source) implements Message {
        @Override
        public void execute(Loop loop) {
            source.run(value);
        }
    }
}
