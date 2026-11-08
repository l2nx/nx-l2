/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import app.l2nx.load.report.model.ServerStats;
import app.l2nx.load.report.model.ServerWindow;
import java.time.Instant;
import java.util.Map;

/**
 * Server meters over the measured phase of one run, read from a metrics store after the fact. Only
 * {@link #checkReady()} may stop a run; when the meters of the window cannot be read, {@link #finish} returns
 * {@code null} and the run keeps its client results.
 */
public interface ServerMeterWindow {
    /**
     * @return the start time of each server process by its name, which the gate checks for stale processes
     * @throws IllegalStateException when the meters could not be read: the run must not start
     */
    Map<String, Instant> checkReady();

    void start();

    void end();

    default ServerStats finish(ServerWindow window) throws InterruptedException {
        end();
        return measure(window);
    }

    /**
     * Reads a window that has already ended: a capacity run reads each level after the last, so no query overlaps the
     * next level.
     */
    ServerStats measure(ServerWindow window) throws InterruptedException;
}
