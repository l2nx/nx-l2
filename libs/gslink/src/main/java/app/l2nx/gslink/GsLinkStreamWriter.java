/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import io.grpc.Status;
import io.grpc.SynchronizationContext;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

/**
 * The one way to send on a GS-link stream. A {@link StreamObserver} is not thread-safe, so every call runs in a
 * {@link SynchronizationContext}: in the caller's thread, or queued behind the send already running. Nothing is sent
 * after the stream ended.
 */
@Slf4j
public final class GsLinkStreamWriter<T> {
    private final StreamObserver<T> stream;
    private final SynchronizationContext context =
            new SynchronizationContext((thread, error) -> log.warn("A GS-link send failed.", error));

    // Read and written only inside the context.
    private boolean ended;

    public GsLinkStreamWriter(StreamObserver<T> stream) {
        this.stream = stream;
    }

    public void send(T message) {
        context.execute(() -> {
            if (!ended) {
                stream.onNext(message);
            }
        });
    }

    public void complete() {
        context.execute(() -> {
            if (!ended) {
                ended = true;
                stream.onCompleted();
            }
        });
    }

    public void fail(Status status) {
        context.execute(() -> {
            if (!ended) {
                ended = true;
                stream.onError(status.asRuntimeException());
            }
        });
    }

    /** The peer ended the stream: drops every later send instead of writing to a closed call. */
    public void abandon() {
        context.execute(() -> ended = true);
    }
}
