/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.periodic;

public interface PeriodicMetrics {
    /** Nanoseconds of the meter registry's clock, so run times follow the registry's notion of time. */
    long monotonicTime();

    SystemMeters system(String name);

    interface SystemMeters {
        void recordRun(long nanos);

        void recordSkipped(long runs);
    }
}
