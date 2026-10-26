/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InboxTest {
    @Nested
    class Poll {
        @Test
        void poll_shouldReturnMessagesInOfferOrder() {
            final Inbox<String> inbox = new Inbox<>();

            inbox.offer("a");
            inbox.offer("b");

            assertThat(inbox.size()).isEqualTo(2);
            assertThat(inbox.poll()).isEqualTo("a");
            assertThat(inbox.poll()).isEqualTo("b");
            assertThat(inbox.poll()).isNull();
            assertThat(inbox.isEmpty()).isTrue();
        }

        @Test
        void poll_shouldKeepTheOrderOfEachProducerUnderContention() {
            final Inbox<long[]> inbox = new Inbox<>();
            final int producers = 4;
            final int perProducer = 10_000;

            try (ExecutorService pool = Executors.newFixedThreadPool(producers)) {
                for (int p = 0; p < producers; p++) {
                    final int producer = p;
                    pool.submit(() -> {
                        for (int i = 0; i < perProducer; i++) {
                            inbox.offer(new long[] {producer, i});
                        }
                    });
                }
            }

            final long[] next = new long[producers];
            int actual = 0;
            long[] item;
            while ((item = inbox.poll()) != null) {
                assertThat(item[1]).isEqualTo(next[(int) item[0]]);
                next[(int) item[0]]++;
                actual++;
            }
            assertThat(actual).isEqualTo(producers * perProducer);
        }
    }

    @Nested
    class Offer {
        @Test
        void offer_shouldWakeTheOwner() {
            final Inbox<String> inbox = new Inbox<>();
            final AtomicInteger actual = new AtomicInteger();
            inbox.wakeup(actual::incrementAndGet);

            inbox.offer("a");
            inbox.offer("b");

            assertThat(actual).hasValue(2);
        }

        @Test
        void offer_shouldBeRejectedAfterTermination() {
            final Inbox<String> inbox = new Inbox<>();
            inbox.offer("a");

            inbox.terminate();

            assertThat(inbox.offer("b")).isFalse();
            assertThat(inbox.poll()).isEqualTo("a");
            assertThat(inbox.poll()).isNull();
        }

        // The producer and the terminating consumer start together; a varying spin moves the race over the offer.
        @Test
        void offer_shouldBeDrainedAfterTermination_whenItReturnedTrue() throws Exception {
            try (ExecutorService producer = Executors.newSingleThreadExecutor()) {
                for (int i = 0; i < 10_000; i++) {
                    final Inbox<Integer> inbox = new Inbox<>();
                    final AtomicBoolean go = new AtomicBoolean();
                    final int message = i;
                    final Future<Boolean> offered = producer.submit(() -> {
                        while (!go.get()) {
                            Thread.onSpinWait();
                        }
                        return inbox.offer(message);
                    });
                    go.set(true);
                    for (int spin = 0; spin < i % 64; spin++) {
                        Thread.onSpinWait();
                    }
                    inbox.terminate();
                    final List<Integer> actual = new ArrayList<>();
                    while (!inbox.isEmpty()) {
                        actual.add(inbox.poll());
                    }

                    if (offered.get()) {
                        assertThat(actual).as("iteration %d", i).containsExactly(message);
                    }
                }
            }
        }

        @Test
        void offer_shouldNotWake_whenTheInboxHasTerminated() {
            final Inbox<String> inbox = new Inbox<>();
            final AtomicInteger actual = new AtomicInteger();
            inbox.wakeup(actual::incrementAndGet);
            inbox.terminate();

            inbox.offer("a");

            assertThat(actual).hasValue(0);
        }

        @Test
        void offer_shouldRejectNull() {
            assertThatNullPointerException().isThrownBy(() -> new Inbox<String>().offer(null));
        }
    }
}
