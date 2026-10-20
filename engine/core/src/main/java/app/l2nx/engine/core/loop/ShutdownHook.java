/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

/** Runs once on the owner thread when the loop stops, after its input stopped and its inbox drained. */
@FunctionalInterface
public interface ShutdownHook {
    void onShutdown(Loop loop);
}
