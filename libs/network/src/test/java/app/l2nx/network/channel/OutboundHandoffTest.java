/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.channel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.test.RecordingExecutor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutboundHandoffTest {
    private final OutboundHandoff handoff = new OutboundHandoff();
    private final RecordingExecutor first = new RecordingExecutor();
    private final RecordingExecutor second = new RecordingExecutor();

    @Nested
    class Wake {
        @Test
        void wake_shouldCloseTheHandoff() {
            handoff.open();

            handoff.wake();

            assertThat(handoff.isOpen()).isFalse();
        }

        @Test
        void wake_shouldWakeEachRecordedEventLoopOnce() {
            handoff.open();
            handoff.defer(first);
            handoff.defer(second);
            handoff.defer(first);

            handoff.wake();

            assertThat(first.immediate()).isEqualTo(1);
            assertThat(second.immediate()).isEqualTo(1);
        }

        @Test
        void wake_shouldForgetTheEventLoops_whenWokenOnce() {
            handoff.open();
            handoff.defer(first);

            handoff.wake();
            handoff.wake();

            assertThat(first.immediate()).isEqualTo(1);
        }

        @Test
        void wake_shouldWakeTheOthersAndRethrow_whenOneEventLoopFails() {
            final WakeFailure failure = new WakeFailure();
            first.failExecuteWith(failure);
            handoff.open();
            handoff.defer(first);
            handoff.defer(second);

            assertThatThrownBy(handoff::wake).isSameAs(failure);
            assertThat(second.immediate()).isEqualTo(1);
            assertThat(handoff.isOpen()).isFalse();
        }

        @Test
        void wake_shouldWakeTheOthers_whenOneEventLoopRejects() {
            first.reject();
            handoff.open();
            handoff.defer(first);
            handoff.defer(second);

            assertThatCode(handoff::wake).doesNotThrowAnyException();
            assertThat(second.immediate()).isEqualTo(1);
        }
    }

    private static final class WakeFailure extends Error {
        WakeFailure() {
            super("wake-up failure");
        }
    }
}
