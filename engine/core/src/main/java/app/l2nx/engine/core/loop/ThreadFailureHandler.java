/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

/**
 * Told on a loop thread that is dying, before it stops its loops: the partitions they ran cannot go on. Must not
 * block on that thread's end.
 */
@FunctionalInterface
public interface ThreadFailureHandler {
    void onThreadFailure(Thread thread, Throwable failure);
}
