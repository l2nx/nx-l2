/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import java.util.Objects;
import lombok.Builder;

/**
 * @param target wire handle of the entity the entry acts on, 0 for none
 * @param key what of the target the entry belongs to, such as an effect id
 * @param period 0 for a one-shot entry; otherwise the entry is armed again at {@code deadline + period}
 */
@Builder(toBuilder = true)
public record TimerSpec(TimerHandler handler, int target, int key, int generation, TimerKind kind, long period) {
    public TimerSpec {
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(kind, "kind");
        if (period < 0) {
            throw new IllegalArgumentException("Timer period must not be negative: " + period);
        }
    }
}
