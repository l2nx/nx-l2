/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;

/**
 * Server meters over the measured phase of one run, read from a metrics store after the fact. Only
 * {@link #checkReady()} may stop a run; when the meters of the window cannot be read, {@link #finish} returns
 * {@code null} and the run keeps its client results.
 */
public interface ServerMeterWindow {
    /** @throws IllegalStateException when the meters could not be read: the run must not start */
    void checkReady();

    void start();

    ServerStats finish(ServerWindow window) throws InterruptedException;
}
