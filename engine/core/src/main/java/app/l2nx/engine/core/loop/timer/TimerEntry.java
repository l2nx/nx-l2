/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public final class TimerEntry {
    @Getter
    @ToString.Include
    private final TimerSpec spec;

    @Getter
    @ToString.Include
    private final long sequence;

    @Getter(AccessLevel.PACKAGE)
    private final long insertion;

    @Getter
    @ToString.Include
    long deadline;

    // Ticks this periodic entry has run in the current pass.
    int owed;

    TimerEntry(TimerSpec spec, long sequence, long insertion, long deadline) {
        this.spec = spec;
        this.sequence = sequence;
        this.insertion = insertion;
        this.deadline = deadline;
    }

    boolean isPeriodic() {
        return spec.period() > 0;
    }
}
