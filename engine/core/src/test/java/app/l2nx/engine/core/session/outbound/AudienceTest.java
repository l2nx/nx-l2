/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudienceTest {
    record Said(String text) implements ViewEvent {}

    @Test
    void emit_shouldEmitOncePerEntry_whenAPortIsListedTwice() {
        final List<ViewEvent> emitted = new ArrayList<>();
        final ClientPort plain = new RecordingPort(emitted);
        final Said said = new Said("hi");

        Audience.emit(said, List.of(plain, plain));

        assertThat(emitted).containsExactly(said, said);
    }

    @Test
    void emit_shouldDeliverTheEventToEveryPort() {
        final List<ViewEvent> first = new ArrayList<>();
        final List<ViewEvent> second = new ArrayList<>();
        final List<ViewEvent> third = new ArrayList<>();
        final Said said = new Said("hi");

        Audience.emit(said, List.of(new RecordingPort(first), new RecordingPort(second), new RecordingPort(third)));

        assertThat(List.of(first, second, third))
                .allSatisfy(events -> assertThat(events).containsExactly(said));
    }

    private record RecordingPort(List<ViewEvent> emitted) implements ClientPort {
        @Override
        public void emit(ViewEvent event) {
            emitted.add(event);
        }

        @Override
        public void emitAndClose(ViewEvent event) {}

        @Override
        public void legacy(LegacyPacket packet) {}

        @Override
        public void legacyAndClose(LegacyPacket packet) {}
    }
}
