/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository;

import app.l2nx.engine.core.identity.IdentityRegistry;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Resolves a column that references another stored entity. {@link IdentityRegistry#idOf} gives a new entity its UUID,
 * so it serves only a row's own entity: on a reference, a released handle would get a fresh UUID that names no row.
 */
public final class References {
    private References() {}

    /**
     * @throws DataIntegrityViolationException when the handle names no stored entity, as the foreign key would
     */
    public static UUID required(IdentityRegistry registry, int handle) {
        return registry.findId(handle)
                .orElseThrow(
                        () -> new DataIntegrityViolationException("Handle " + handle + " names no stored entity."));
    }

    /**
     * @param handle the referenced entity, {@code 0} or less for none
     * @throws DataIntegrityViolationException when a positive handle names no stored entity
     */
    public static UUID requiredOrNull(IdentityRegistry registry, int handle) {
        return handle > 0 ? required(registry, handle) : null;
    }
}
