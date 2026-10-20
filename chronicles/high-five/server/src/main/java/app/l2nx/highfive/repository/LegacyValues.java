/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository;

import app.l2nx.engine.core.identity.IdentityRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/** Legacy callers use 0, -1 and "" for "no value"; the schema stores NULL. */
public final class LegacyValues {
    private LegacyValues() {}

    public static String textOrNull(String value) {
        return (value == null) || value.isEmpty() ? null : value;
    }

    public static Integer nullIfZero(int value) {
        return value == 0 ? null : value;
    }

    public static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    /**
     * @return {@code null} for the legacy epoch-millisecond "not set" values {@code 0} and {@code -1}
     */
    public static Instant instantOrNull(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : null;
    }

    public static long epochMillisOrZero(Instant instant) {
        return instant == null ? 0 : instant.toEpochMilli();
    }

    public static int handleOrZero(IdentityRegistry registry, UUID id) {
        return id == null ? 0 : registry.handleOf(id);
    }

    public static int handleOrZero(IdentityRegistry registry, ResultSet rs, String column) throws SQLException {
        return handleOrZero(registry, rs.getObject(column, UUID.class));
    }
}
