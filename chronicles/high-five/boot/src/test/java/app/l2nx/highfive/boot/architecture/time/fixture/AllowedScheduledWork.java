/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time.fixture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

public final class AllowedScheduledWork {
    ScheduledFuture<?> timer;

    Object run(List<String> values, CompletableFuture<String> future) {
        return List.of(
                values.stream().count(),
                future.thenApply(String::length),
                future.getNow("pending"),
                timer.isDone(),
                Thread.currentThread().getName());
    }
}
