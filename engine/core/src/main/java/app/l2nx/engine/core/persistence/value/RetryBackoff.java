/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.persistence.value;

import java.time.Duration;
import java.util.List;

/**
 * Pauses before asking the database again what a commit did, after it gave no answer; the last pause repeats until it
 * answers.
 */
public final class RetryBackoff {
    private static final List<Duration> PAUSES =
            List.of(Duration.ofSeconds(1), Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(60));

    private RetryBackoff() {}

    /** @param attempt the attempt that gave no answer, counted from zero */
    public static Duration pause(int attempt) {
        return PAUSES.get(Math.min(attempt, PAUSES.size() - 1));
    }
}
