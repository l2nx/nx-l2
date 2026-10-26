/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import java.time.Duration;

public final class Durations {
    private Durations() {}

    /**
     * The loop works in whole game milliseconds, so a configured fraction would be silently cut off.
     *
     * @param name the full property name the message reports, e.g. {@code l2nx.engine.pass-min}
     * @throws IllegalArgumentException when the value is {@code null}, under 1 ms or not whole milliseconds
     */
    public static void requireWholeMillis(String name, Duration value) {
        if (value == null || value.toMillis() < 1 || value.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    name + " must be a whole number of milliseconds, at least 1 ms: " + value);
        }
    }
}
