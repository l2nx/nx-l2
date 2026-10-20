/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.periodic;

import app.l2nx.engine.core.loop.Loop;
import java.time.Duration;

public interface PeriodicSystem {
    String name();

    Duration cadence();

    void run(Loop loop, long now);
}
