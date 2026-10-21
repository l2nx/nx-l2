/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import app.l2nx.engine.core.loop.FailureHandler;
import java.util.concurrent.RejectedExecutionException;

/**
 * Where a signalled session is drained: a pool executor or a partition loop, which must clear the signal when it
 * refuses the session.
 */
@FunctionalInterface
public interface DrainScheduler {
    void schedule(Session session);

    static DrainScheduler pool(DrainExecutor executor, FailureHandler failures) {
        return session -> {
            try {
                executor.execute(() -> session.drainOnPool(failures), session::drainAbandoned);
            } catch (RejectedExecutionException e) {
                // The executor closed: the session is closed before it, so nothing it held is left to run.
                session.clearSignal();
            }
        };
    }

    @FunctionalInterface
    interface DrainExecutor {
        /**
         * @param abandoned runs instead of the drain when the pool accepted it but closes before the drain starts
         * @throws RejectedExecutionException once the pool is closed
         */
        void execute(Runnable drain, Runnable abandoned);
    }
}
