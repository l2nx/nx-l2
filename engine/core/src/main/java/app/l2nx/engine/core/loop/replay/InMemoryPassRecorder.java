/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.replay;

import app.l2nx.engine.core.loop.Message;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

public final class InMemoryPassRecorder implements PassRecorder {
    private final int capacity;
    private final ArrayDeque<PassRecord> passes = new ArrayDeque<>();
    private final List<RecordedInput> currentInputs = new ArrayList<>();
    private String loop;
    private long seed;
    private long startGameMillis;
    private long startWallMillis;
    private boolean open;
    private long currentPass;
    private long currentGameMillis;
    private long currentWallMillis;
    private boolean currentDrain;

    public InMemoryPassRecorder(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("A replay record keeps at least one pass: " + capacity);
        }
        this.capacity = capacity;
    }

    @Override
    public void loopStarted(String loop, long seed, long gameMillis, long wallMillis) {
        this.loop = loop;
        this.seed = seed;
        this.startGameMillis = gameMillis;
        this.startWallMillis = wallMillis;
    }

    @Override
    public void passStarted(long pass, long gameMillis, long wallMillis, boolean drain) {
        finishPass();
        open = true;
        currentPass = pass;
        currentGameMillis = gameMillis;
        currentWallMillis = wallMillis;
        currentDrain = drain;
        currentInputs.clear();
    }

    @Override
    public void message(long pass, int order, Message message) {
        currentInputs.add(new RecordedMessage(order, message));
    }

    @Override
    public void inputItem(long pass, int order, long sourceId, Message item) {
        currentInputs.add(RecordedItem.builder()
                .order(order)
                .sourceId(sourceId)
                .item(item)
                .build());
    }

    /** The last {@code capacity} passes; the current one is included with the inputs recorded so far. */
    public LoopRecord snapshot() {
        final List<PassRecord> kept = new ArrayList<>(passes);
        if (open) {
            if (kept.size() == capacity) {
                kept.removeFirst();
            }
            kept.add(currentPass());
        }
        return LoopRecord.builder()
                .loop(loop)
                .seed(seed)
                .startGameMillis(startGameMillis)
                .startWallMillis(startWallMillis)
                .passes(kept)
                .build();
    }

    private PassRecord currentPass() {
        return PassRecord.builder()
                .pass(currentPass)
                .gameMillis(currentGameMillis)
                .wallMillis(currentWallMillis)
                .drain(currentDrain)
                .inputs(currentInputs)
                .build();
    }

    private void finishPass() {
        if (!open) {
            return;
        }
        passes.addLast(currentPass());
        if (passes.size() > capacity) {
            passes.removeFirst();
        }
        open = false;
    }

    @Builder(toBuilder = true)
    public record LoopRecord(
            String loop, long seed, long startGameMillis, long startWallMillis, List<PassRecord> passes) {
        public LoopRecord {
            passes = List.copyOf(passes);
        }
    }

    @Builder(toBuilder = true)
    public record PassRecord(long pass, long gameMillis, long wallMillis, boolean drain, List<RecordedInput> inputs) {
        public PassRecord {
            inputs = List.copyOf(inputs);
        }
    }

    public sealed interface RecordedInput permits RecordedMessage, RecordedItem {
        int order();
    }

    @Builder(toBuilder = true)
    public record RecordedMessage(int order, Message message) implements RecordedInput {}

    @Builder(toBuilder = true)
    public record RecordedItem(int order, long sourceId, Message item) implements RecordedInput {}
}
