/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import app.l2nx.engine.core.identity.IdentityRegistry;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Object ids for legacy callers: every id is a wire handle from the {@link IdentityRegistry}. A stored entity created
 * with {@link #getNextId()} gets its UUID when it is first saved.
 */
@Component
public class IdManager {
    private static volatile IdManager instance;

    private final IdentityRegistry registry;

    public IdManager(IdentityRegistry registry) {
        this.registry = registry;
        instance = this;
    }

    public int getNextId() {
        return registry.allocateTransient();
    }

    public void releaseId(int objectId) {
        registry.release(objectId);
    }

    /** @return the UUID of a stored entity; empty for an entity never saved */
    public Optional<UUID> findId(int objectId) {
        return registry.findId(objectId);
    }

    public int handleOf(UUID id) {
        return registry.handleOf(id);
    }

    public OptionalInt findHandle(UUID id) {
        return registry.findHandle(id);
    }

    public int getAllocatedIdCount() {
        return registry.allocatedCount();
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static IdManager getInstance() {
        final IdManager current = instance;
        if (current == null) {
            throw new IllegalStateException("IdManager is used before the Spring context created it.");
        }
        return current;
    }
}
