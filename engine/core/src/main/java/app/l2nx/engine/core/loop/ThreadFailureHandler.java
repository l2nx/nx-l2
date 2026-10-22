/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

/**
 * Called on a dying loop thread or on the watchdog thread; the partitions they ran cannot go on. Must not block on
 * that thread's end.
 */
@FunctionalInterface
public interface ThreadFailureHandler {
    void onThreadFailure(Thread thread, Throwable failure);
}
