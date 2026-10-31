/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

import app.l2nx.engine.core.session.outbound.spi.SharedFrameEmitter;
import app.l2nx.network.codec.Frames;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Fan-out of one view event: serialized once per audience key versus once per viewer. */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class AudienceBenchmark {
    @Builder(toBuilder = true)
    record Moved(int handle, int x, int y, int z) implements ViewEvent {}

    @Param({"1", "50", "500"})
    private int viewers;

    @Param({"shared", "per-viewer"})
    private String mode;

    private final Moved event =
            Moved.builder().handle(0x10000001).x(1).y(2).z(3).build();
    private List<ClientPort> ports;

    @Setup
    public void setUp() {
        ports = new ArrayList<>(viewers);
        final Object key = "shared".equals(mode) ? new Object() : null;
        for (int i = 0; i < viewers; i++) {
            ports.add(new SinkPort(key));
        }
    }

    @Benchmark
    public void emit() {
        Audience.emit(event, ports);
    }

    private static final class SinkPort implements ClientPort, SharedFrameEmitter {
        private final Object key;

        SinkPort(Object key) {
            this.key = key;
        }

        @Override
        public void emit(ViewEvent event) {
            serialize(event).release();
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
            final Moved moved = (Moved) event;
            return PooledByteBufAllocator.DEFAULT
                    .buffer(64)
                    .writeZero(2)
                    .writeByte(0x2F)
                    .writeIntLE(moved.handle())
                    .writeIntLE(moved.x())
                    .writeIntLE(moved.y())
                    .writeIntLE(moved.z())
                    .writeZero(40);
        }

        @Override
        public void sendShared(ByteBuf frame) {
            Frames.share(frame).release();
        }
    }
}
