/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop;

import app.l2nx.engine.core.loop.inbox.Wakeup;

public final class LoopBinding {
    private LoopBinding() {}

    public static void bindToCurrentThread(Loop loop) {
        loop.bind(Thread.currentThread(), Wakeup.NONE);
    }
}
