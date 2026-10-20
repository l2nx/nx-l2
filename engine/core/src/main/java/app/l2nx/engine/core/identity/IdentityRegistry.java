/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.identity;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jctools.maps.NonBlockingHashMapLong;

/**
 * Maps the persistent UUID of a stored entity to the wire handle game code works with; an entity keeps its
 * handle until destroyed. Objects never stored (NPCs, doors) take {@link #allocateTransient()} and have no UUID.
 */
public final class IdentityRegistry {
    private final WireHandleAllocator allocator;
    private final Map<UUID, Integer> handles = new ConcurrentHashMap<>();
    private final NonBlockingHashMapLong<UUID> ids = new NonBlockingHashMapLong<>();

    public IdentityRegistry(WireHandleAllocator allocator) {
        this.allocator = allocator;
    }

    public int handleOf(UUID id) {
        return handles.computeIfAbsent(id, key -> {
            final int handle = allocator.allocate();
            ids.put(handle, key);
            return handle;
        });
    }

    /**
     * @return the UUID of a stored entity; a new entity, not yet saved, gets its UUID here
     */
    public UUID idOf(int handle) {
        final UUID existing = ids.get(handle);
        if (existing != null) {
            return existing;
        }

        final UUID created = EntityIds.next();
        final UUID raced = ids.putIfAbsent(handle, created);
        if (raced != null) {
            return raced;
        }
        handles.put(created, handle);
        return created;
    }

    public Optional<UUID> findId(int handle) {
        return Optional.ofNullable(ids.get(handle));
    }

    public OptionalInt findHandle(UUID id) {
        final Integer handle = handles.get(id);
        return handle == null ? OptionalInt.empty() : OptionalInt.of(handle);
    }

    public int allocateTransient() {
        return allocator.allocate();
    }

    /**
     * Releases the handle of an object that left the world for good; the handle returns to the pool after a quarantine.
     */
    public void release(int handle) {
        final UUID id = ids.remove(handle);
        if (id != null) {
            handles.remove(id);
        }
        allocator.release(handle);
    }

    public int allocatedCount() {
        return allocator.allocatedCount();
    }
}
