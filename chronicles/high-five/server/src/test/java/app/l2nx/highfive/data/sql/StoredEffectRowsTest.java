/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.mechanics.skill.StoredEffect;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoredEffectRowsTest {
    @Test
    void read_shouldReadRemainingAndTickPhase() throws SQLException {
        final Optional<StoredEffect> actual = StoredEffectRows.read(row(52_700L, 7_300L), 268_480_001);

        assertThat(actual)
                .contains(StoredEffect.builder()
                        .remainingMillis(52_700L)
                        .tickPhaseMillis(7_300)
                        .build());
    }

    @Test
    void read_shouldHaveNoRemaining_whenTheColumnIsNull() throws SQLException {
        final Optional<StoredEffect> actual = StoredEffectRows.read(row(null, 4_000L), 268_480_001);

        assertThat(actual)
                .contains(StoredEffect.builder().tickPhaseMillis(4_000).build());
    }

    @ParameterizedTest(name = "remaining_ms={0}, tick_phase_ms={1}")
    @CsvSource({"0, 0", "-1, 0", "1000, -1"})
    void read_shouldSkipTheRow_whenItBreaksTheInvariants(long remainingMillis, long tickPhaseMillis)
            throws SQLException {
        final Optional<StoredEffect> actual = StoredEffectRows.read(row(remainingMillis, tickPhaseMillis), 268_480_001);

        assertThat(actual).isEmpty();
    }

    private static ResultSet row(Long remainingMillis, long tickPhaseMillis) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, (proxy, method, args) -> {
                    final String column = (String) args[0];
                    return switch (method.getName()) {
                        case "getObject" -> "remaining_ms".equals(column) ? remainingMillis : unknown(column);
                        case "getLong" -> "tick_phase_ms".equals(column) ? tickPhaseMillis : unknown(column);
                        case "getInt" -> "skill_id".equals(column) ? 1204 : unknown(column);
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
    }

    private static Object unknown(String column) throws SQLException {
        throw new SQLException("Unknown column " + column);
    }
}
