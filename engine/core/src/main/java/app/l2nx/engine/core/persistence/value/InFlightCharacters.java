/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Characters with a value transaction in flight, used only on the owning partition's loop: world entry, logout and
 * save wait in {@link #whenIdle} until the last transaction ends, so neither can race a commit.
 */
@Slf4j
public final class InFlightCharacters {
    private final Object2IntOpenHashMap<UUID> counts = new Object2IntOpenHashMap<>();
    private final Map<UUID, Queue<Runnable>> waiting = new HashMap<>();

    public void begin(UUID characterId) {
        counts.addTo(characterId, 1);
    }

    /** Ends one transaction of the character; the last one runs the waiting work in arrival order. */
    public void end(UUID characterId) {
        final int count = counts.getInt(characterId);
        if (count == 0) {
            throw new IllegalStateException("No transaction in flight for " + characterId);
        }
        if (count > 1) {
            counts.put(characterId, count - 1);
            return;
        }
        counts.removeInt(characterId);
        final Queue<Runnable> work = waiting.remove(characterId);
        if (work != null) {
            work.forEach(task -> runIsolated(characterId, task));
        }
    }

    /** @return the characters with a transaction in flight now, a copy */
    public Set<UUID> characters() {
        return Set.copyOf(counts.keySet());
    }

    public boolean isInFlight(UUID characterId) {
        return counts.getInt(characterId) > 0;
    }

    // A failing task must not skip the ones after it (the logout save) nor escape into the outcome handler.
    private static void runIsolated(UUID characterId, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Work waiting for the transactions of {} failed", characterId, e);
        }
    }

    public void whenIdle(UUID characterId, Runnable work) {
        if (!isInFlight(characterId)) {
            runIsolated(characterId, work);
            return;
        }
        waiting.computeIfAbsent(characterId, id -> new ArrayDeque<>()).add(work);
    }
}
