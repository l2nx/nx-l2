/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.gslink;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.gslink.test.RecordingStreamObserver;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GsLinkStreamWriterTest {
    private static final int THREADS = 8;
    private static final int SENDS_PER_THREAD = 1_000;

    @Test
    void send_shouldNeverEnterTheStreamConcurrently_whenManyThreadsSend() {
        final OverlapDetector stream = new OverlapDetector();
        final GsLinkStreamWriter<Integer> writer = new GsLinkStreamWriter<>(stream);

        try (ExecutorService threads = Executors.newFixedThreadPool(THREADS)) {
            for (int thread = 0; thread < THREADS; thread++) {
                threads.submit(() -> {
                    for (int i = 0; i < SENDS_PER_THREAD; i++) {
                        writer.send(i);
                    }
                });
            }
        }

        assertThat(stream.received).hasValue(THREADS * SENDS_PER_THREAD);
        assertThat(stream.overlapped).isFalse();
    }

    @Test
    void send_shouldBeDropped_afterTheStreamFailed() throws Exception {
        final RecordingStreamObserver<Integer> stream = new RecordingStreamObserver<>();
        final GsLinkStreamWriter<Integer> writer = new GsLinkStreamWriter<>(stream);

        writer.send(1);
        writer.fail(Status.UNAVAILABLE);
        writer.send(2);
        writer.complete();

        assertThat(stream.next()).isEqualTo(1);
        assertThat(stream.awaitEnd().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        assertThat(stream.remaining()).isEmpty();
    }

    @Test
    void abandon_shouldStopEverySend_withoutEndingTheStream() {
        final RecordingStreamObserver<Integer> stream = new RecordingStreamObserver<>();
        final GsLinkStreamWriter<Integer> writer = new GsLinkStreamWriter<>(stream);

        writer.abandon();
        writer.send(1);
        writer.complete();

        assertThat(stream.remaining()).isEmpty();
        assertThat(stream.isEnded()).isFalse();
    }

    private static final class OverlapDetector implements StreamObserver<Integer> {
        private final AtomicBoolean inside = new AtomicBoolean();
        private final AtomicInteger received = new AtomicInteger();
        private volatile boolean overlapped;

        @Override
        public void onNext(Integer value) {
            if (!inside.compareAndSet(false, true)) {
                overlapped = true;
            }
            received.incrementAndGet();
            Thread.onSpinWait();
            inside.set(false);
        }

        @Override
        public void onError(Throwable error) {}

        @Override
        public void onCompleted() {}
    }
}
