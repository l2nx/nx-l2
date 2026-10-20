/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.identity;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/**
 * Persistent identity of everything the database stores: UUIDv7, generated in process so an item can be created on a
 * simulation thread without a database round trip. Ids are monotonic within the process and never reused.
 */
public final class EntityIds {
    private EntityIds() {}

    public static UUID next() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
