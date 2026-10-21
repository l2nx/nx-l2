/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.codec.Frames;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AudienceTest {
    record Said(String text) implements ViewEvent {}

    @Test
    void emit_shouldSerializeOncePerKeyAndShareDuplicates() {
        final List<ByteBuf> serialized = new ArrayList<>();
        final RecordingPort a = new RecordingPort("v1", serialized);
        final RecordingPort b = new RecordingPort("v1", serialized);
        final RecordingPort c = new RecordingPort("v2", serialized);

        Audience.emit(new Said("hi"), List.of(a, b, c));

        assertThat(serialized).hasSize(2);
        assertThat(a.shared).hasSize(1);
        assertThat(b.shared.getFirst().unwrap()).isSameAs(a.shared.getFirst().unwrap());
        assertThat(serialized.get(0).refCnt()).as("v1 frame, held by a and b").isEqualTo(2);
        assertThat(serialized.get(1).refCnt()).as("v2 frame, held by c").isEqualTo(1);
        a.release();
        b.release();
        c.release();
        assertThat(serialized).allSatisfy(frame -> assertThat(frame.refCnt()).isZero());
    }

    @Test
    void emit_shouldFallBackToEmit_whenPortDoesNotShare() {
        final RecordingPort port = new RecordingPort(null, new ArrayList<>());

        Audience.emit(new Said("hi"), List.of(port));

        assertThat(port.emitted).hasSize(1);
        assertThat(port.shared).isEmpty();
    }

    @Test
    void emit_shouldSendNothing_whenTheMapperDropsTheEvent() {
        final RecordingPort port = new RecordingPort("v1", new ArrayList<>()) {
            @Override
            public ByteBuf serialize(ViewEvent event) {
                return null;
            }
        };

        Audience.emit(new Said("hi"), List.of(port));

        assertThat(port.shared).isEmpty();
        assertThat(port.emitted).isEmpty();
    }

    private static class RecordingPort implements ClientPort {
        private final Object key;
        private final List<ByteBuf> serialized;
        private final List<ByteBuf> shared = new ArrayList<>();
        private final List<ViewEvent> emitted = new ArrayList<>();

        RecordingPort(Object key, List<ByteBuf> serialized) {
            this.key = key;
            this.serialized = serialized;
        }

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

        @Override
        public Object audienceKey(ViewEvent event) {
            return key;
        }

        @Override
        public ByteBuf serialize(ViewEvent event) {
            final ByteBuf frame = Unpooled.buffer(4).writeInt(1);
            serialized.add(frame);
            return frame;
        }

        @Override
        public void sendShared(ByteBuf frame) {
            shared.add(Frames.share(frame));
        }

        void release() {
            shared.forEach(ByteBuf::release);
        }
    }
}
