/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.highfive.entity.actor.Player;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;

/**
 * Characters being read and characters in memory, spawned or not. A value transaction treats a built one as online
 * and a loading one as busy; offline-row writers wait until it is settled (in the world or out of memory).
 * Loop-confined.
 */
@Slf4j
public final class LoadedCharacters {
    private final Map<UUID, Player> players = new HashMap<>();
    private final Set<UUID> loading = new HashSet<>();
    private final Set<UUID> entered = new HashSet<>();
    private final Map<UUID, Queue<Runnable>> afterLoad = new HashMap<>();
    private final Map<UUID, Queue<Runnable>> afterSettle = new HashMap<>();

    public boolean startLoading(UUID characterId) {
        return loading.add(characterId);
    }

    public boolean isLoading(UUID characterId) {
        return loading.contains(characterId);
    }

    public void loaded(UUID characterId, Player player) {
        players.put(characterId, player);
        entered.remove(characterId);
    }

    public void loadEnded(UUID characterId) {
        loading.remove(characterId);
        drain(characterId, afterLoad, loading::contains);
        settle(characterId);
    }

    public void entered(UUID characterId, Player player) {
        if (players.get(characterId) == player) {
            entered.add(characterId);
            settle(characterId);
        }
    }

    public void release(UUID characterId, Player player) {
        if (players.remove(characterId, player)) {
            entered.remove(characterId);
            settle(characterId);
        }
    }

    public Optional<Player> find(UUID characterId) {
        return Optional.ofNullable(players.get(characterId));
    }

    public boolean isBusy(UUID characterId) {
        return loading.contains(characterId) || (players.containsKey(characterId) && !entered.contains(characterId));
    }

    public Set<UUID> busy() {
        final Set<UUID> busy = new HashSet<>(loading);
        for (UUID characterId : players.keySet()) {
            if (!entered.contains(characterId)) {
                busy.add(characterId);
            }
        }
        return busy;
    }

    public void whenLoaded(UUID characterId, Runnable work) {
        if (!loading.contains(characterId)) {
            runIsolated(characterId, work);
            return;
        }
        afterLoad.computeIfAbsent(characterId, id -> new ArrayDeque<>()).add(work);
    }

    public void whenSettled(UUID characterId, Runnable work) {
        if (!isBusy(characterId)) {
            runIsolated(characterId, work);
            return;
        }
        afterSettle.computeIfAbsent(characterId, id -> new ArrayDeque<>()).add(work);
    }

    private void settle(UUID characterId) {
        if (!isBusy(characterId)) {
            drain(characterId, afterSettle, this::isBusy);
        }
    }

    // One task at a time: once a task makes the character wait again, the rest wait too, ahead of what it queued.
    private static void drain(UUID characterId, Map<UUID, Queue<Runnable>> queues, Predicate<UUID> waits) {
        final Queue<Runnable> waiting = queues.remove(characterId);
        while ((waiting != null) && !waiting.isEmpty()) {
            if (waits.test(characterId)) {
                final Queue<Runnable> later = queues.put(characterId, waiting);
                if (later != null) {
                    waiting.addAll(later);
                }
                return;
            }
            runIsolated(characterId, waiting.poll());
        }
    }

    private static void runIsolated(UUID characterId, Runnable task) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("Work waiting for character {} failed", characterId, e);
        }
    }
}
