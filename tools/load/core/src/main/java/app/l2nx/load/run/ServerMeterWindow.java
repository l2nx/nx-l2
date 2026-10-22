/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.report.model.ServerStats;

/**
 * Server meters over the measured phase of one run. Never aborts the run: when the meters
 * cannot be read, {@code finish} returns {@code null} and the run keeps its client results.
 */
public interface ServerMeterWindow {
    void start();

    void sample();

    ServerStats finish();
}
