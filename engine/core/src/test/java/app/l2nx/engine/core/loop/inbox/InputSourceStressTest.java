/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.inbox;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class InputSourceStressTest {
    private static final int TOTAL = 200_000;

    private final ManualGameClock clock = new ManualGameClock(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);
    private final Loop loop =
            new Loop("test", clock, 42L, EngineProperties.defaults(), new SimpleMeterRegistry(), PassRecorder.NOOP);

    @Nested
    class SignalProtocol {
        @Test
        @Timeout(60)
        void signal_shouldLoseNoInputToARacingProducer() throws Exception {
            final long now = clock.gameMillis();
            final ConcurrentInput input = new ConcurrentInput();
            final Thread producer = Thread.ofPlatform().name("stress-producer").start(() -> {
                for (int i = 0; i < TOTAL; i++) {
                    input.push(loop, i);
                }
            });

            while (input.drained < TOTAL) {
                loop.runPass(now);
                Thread.onSpinWait();
            }
            producer.join();
            loop.runPass(now);

            assertThat(input.drained).isEqualTo(TOTAL);
            assertThat(input.outOfOrder).isZero();
            assertThat(input.hasInput()).isFalse();
            assertThat(input.isSignalled()).isFalse();
        }
    }
}
