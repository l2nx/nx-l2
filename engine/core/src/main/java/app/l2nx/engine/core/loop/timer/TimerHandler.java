/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

import app.l2nx.engine.core.loop.Loop;

@FunctionalInterface
public interface TimerHandler {
    /**
     * Lazy cancellation: {@code false} when the entry's target is gone or its generation changed. The entry is then
     * dropped unfired and a periodic entry is not armed again.
     */
    default boolean isCurrent(Loop loop, TimerEntry entry) {
        return true;
    }

    void onTimer(Loop loop, TimerEntry entry);
}
