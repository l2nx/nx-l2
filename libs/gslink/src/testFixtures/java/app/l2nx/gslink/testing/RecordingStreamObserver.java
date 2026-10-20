/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink.testing;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Records a gRPC stream for tests that wait for single messages and for the end of the stream. */
public final class RecordingStreamObserver<T> implements StreamObserver<T> {
    private static final long TIMEOUT_SECONDS = 5;

    private final BlockingQueue<T> values = new LinkedBlockingQueue<>();
    private final CompletableFuture<Status> end = new CompletableFuture<>();

    @Override
    public void onNext(T value) {
        values.add(value);
    }

    @Override
    public void onError(Throwable error) {
        end.complete(Status.fromThrowable(error));
    }

    @Override
    public void onCompleted() {
        end.complete(Status.OK);
    }

    /** The next message; fails the test when none arrives within five seconds. */
    public T next() throws InterruptedException {
        final T value = values.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (value == null) {
            throw new AssertionError("No message within " + TIMEOUT_SECONDS + " s.");
        }
        return value;
    }

    /** The status the stream ended with, {@code OK} for a completion; fails the test after five seconds. */
    public Status awaitEnd() throws Exception {
        return end.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public boolean isEnded() {
        return end.isDone();
    }

    /** The messages received and not taken by {@link #next()} yet. */
    public List<T> remaining() {
        final List<T> rest = new ArrayList<>();
        values.drainTo(rest);
        return rest;
    }
}
