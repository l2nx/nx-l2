/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.partition.WorldLoopExtension;
import it.unimi.dsi.fastutil.ints.AbstractIntCollection;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ScratchBuffersTest {

    @Nested
    class SnapshotLease {
        static Stream<Arguments> sources() {
            return Stream.of(
                    Arguments.of(Named.of("array set", new ObjectArraySet<>(List.of("a", "b", "c")))),
                    Arguments.of(Named.of("hash set", new ObjectOpenHashSet<>(List.of("a", "b", "c")))),
                    Arguments.of(Named.of("jdk set", Set.of("a", "b", "c"))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sources")
        void snapshot_shouldHoldExactlyTheSource_whenTheDepthHeldMoreBefore(Collection<String> source) {
            final AtomicReference<List<String>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<String> earlier = ScratchBuffers.empty()) {
                    earlier.addAll(List.of("x", "y", "z", "w", "v"));
                }

                try (Snapshot<String> snapshot = ScratchBuffers.snapshot(source)) {
                    actual.set(List.copyOf(snapshot));
                }
            });

            assertThat(actual.get()).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        void snapshot_shouldLeaseAnotherBuffer_whenNested() {
            final AtomicReference<Object> outer = new AtomicReference<>();
            final AtomicReference<Object> inner = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<String> outerSnapshot = ScratchBuffers.snapshot(List.of("a"));
                        Snapshot<String> innerSnapshot = ScratchBuffers.snapshot(List.of("b"))) {
                    outer.set(outerSnapshot);
                    inner.set(innerSnapshot);
                }
            });

            assertThat(inner.get()).isNotNull().isNotSameAs(outer.get());
        }

        @Test
        void snapshot_shouldReuseTheBufferOfEachDepth_whenLeasedAgain() {
            final AtomicReference<List<Object>> first = new AtomicReference<>();
            final AtomicReference<List<Object>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                first.set(leaseTwoDepths());
                actual.set(leaseTwoDepths());
            });

            assertThat(actual.get())
                    .hasSize(2)
                    .zipSatisfy(
                            first.get(),
                            (again, firstLease) -> assertThat(again).isSameAs(firstLease));
        }

        @Test
        void snapshot_shouldFreeTheDepth_whenTheCopyThrows() {
            final AtomicReference<Throwable> thrown = new AtomicReference<>();
            final AtomicReference<Object> before = new AtomicReference<>();
            final AtomicReference<Object> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<Object> lease = ScratchBuffers.empty()) {
                    before.set(lease);
                }

                thrown.set(catchThrowable(() -> ScratchBuffers.snapshot(new FailingCollection())));
                try (Snapshot<Object> lease = ScratchBuffers.empty()) {
                    actual.set(lease);
                }
            });

            assertThat(thrown.get()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(actual.get()).isNotNull().isSameAs(before.get());
        }

        @Test
        void snapshot_shouldThrow_whenCalledOffTheLoop() {
            assertThatThrownBy(() -> ScratchBuffers.snapshot(List.of("a"))).isInstanceOf(IllegalStateException.class);
        }

        private static List<Object> leaseTwoDepths() {
            try (Snapshot<String> outer = ScratchBuffers.snapshot(List.of("a"));
                    Snapshot<String> inner = ScratchBuffers.snapshot(List.of("b"))) {
                return List.of(outer, inner);
            }
        }
    }

    @Nested
    class Empty {
        @Test
        void empty_shouldBeEmpty_whenTheDepthHeldElementsBefore() {
            final AtomicReference<List<Object>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<String> earlier = ScratchBuffers.snapshot(List.of("a", "b"))) {
                    earlier.add("c");
                }

                try (Snapshot<Object> lease = ScratchBuffers.empty()) {
                    actual.set(List.copyOf(lease));
                }
            });

            assertThat(actual.get()).isNotNull().isEmpty();
        }

        @Test
        void empty_shouldThrow_whenCalledOffTheLoop() {
            assertThatThrownBy(ScratchBuffers::empty).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class IntEmpty {
        @Test
        void intEmpty_shouldBeEmpty_whenTheDepthHeldElementsBefore() {
            final AtomicReference<int[]> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (IntSnapshot earlier = ScratchBuffers.intSnapshot(IntArrayList.of(1, 2))) {
                    earlier.add(3);
                }

                try (IntSnapshot lease = ScratchBuffers.intEmpty()) {
                    actual.set(lease.toIntArray());
                }
            });

            assertThat(actual.get()).isNotNull().isEmpty();
        }

        @Test
        void intEmpty_shouldThrow_whenCalledOffTheLoop() {
            assertThatThrownBy(ScratchBuffers::intEmpty).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Refill {
        @Test
        void refill_shouldHoldExactlyTheNewSource_whenTheLeaseHeldMore() {
            final AtomicReference<List<String>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<String> lease = ScratchBuffers.snapshot(List.of("a", "b", "c"))) {
                    actual.set(List.copyOf(lease.refill(List.of("d"))));
                }
            });

            assertThat(actual.get()).containsExactly("d");
        }

        @Test
        void refill_shouldThrow_whenCalledOffTheLoop() {
            final AtomicReference<Snapshot<String>> lease = new AtomicReference<>();
            WorldLoopExtension.runInPass(() -> lease.set(ScratchBuffers.empty()));

            assertThatThrownBy(() -> lease.get().refill(List.of("a"))).isInstanceOf(IllegalStateException.class);

            WorldLoopExtension.runInPass(() -> lease.get().close());
        }
    }

    @Nested
    class Close {
        @Test
        void close_shouldClearTheBuffer() {
            final AtomicReference<List<Object>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final Snapshot<Object> lease = ScratchBuffers.snapshot(List.of("a", "b"));
                lease.close();
                actual.set(lease);
            });

            assertThat(actual.get()).isNotNull().isEmpty();
        }

        @Test
        void close_shouldFreeTheDepth_whenTheBlockExitsEarly() {
            final AtomicReference<Object> exited = new AtomicReference<>();
            final AtomicReference<Object> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                exited.set(returnFromInsideTheLease(List.of("a", "b", "c")));
                try (Snapshot<Object> next = ScratchBuffers.empty()) {
                    actual.set(next);
                }
            });

            assertThat(actual.get()).isNotNull().isSameAs(exited.get());
        }

        @Test
        void close_shouldThrow_whenAnOuterLeaseIsClosedFirst() {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<Object> outer = ScratchBuffers.empty();
                        Snapshot<Object> inner = ScratchBuffers.empty()) {
                    actual.set(catchThrowable(outer::close));
                }
            });

            assertThat(actual.get()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void close_shouldThrow_whenClosedTwice() {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (Snapshot<Object> outer = ScratchBuffers.empty()) {
                    final Snapshot<Object> inner = ScratchBuffers.empty();
                    inner.close();
                    actual.set(catchThrowable(inner::close));
                }
            });

            assertThat(actual.get()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void close_shouldThrow_whenCalledOffTheLoop() {
            final AtomicReference<Snapshot<Object>> lease = new AtomicReference<>();
            WorldLoopExtension.runInPass(() -> lease.set(ScratchBuffers.empty()));

            final Throwable actual = catchThrowable(() -> lease.get().close());
            WorldLoopExtension.runInPass(() -> lease.get().close());

            assertThat(actual).isInstanceOf(IllegalStateException.class);
        }

        private static Object returnFromInsideTheLease(Collection<String> source) {
            try (Snapshot<String> snapshot = ScratchBuffers.snapshot(source)) {
                for (String element : snapshot) {
                    if (element.equals("b")) {
                        return snapshot;
                    }
                }

                return null;
            }
        }
    }

    @Nested
    class IntSnapshotLease {
        @Test
        void intSnapshot_shouldHoldExactlyTheSourceInItsOrder_whenTheDepthHeldMoreBefore() {
            final AtomicReference<int[]> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (IntSnapshot earlier = ScratchBuffers.intSnapshot(IntArrayList.of(9, 8, 7, 6, 5))) {
                    earlier.add(4);
                }

                try (IntSnapshot snapshot = ScratchBuffers.intSnapshot(IntLinkedOpenHashSet.of(3, 1, 2))) {
                    actual.set(snapshot.toIntArray());
                }
            });

            assertThat(actual.get()).containsExactly(3, 1, 2);
        }

        @Test
        void intSnapshot_shouldLeaseAnotherBuffer_whenNested() {
            final AtomicReference<Object> outer = new AtomicReference<>();
            final AtomicReference<Object> inner = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (IntSnapshot outerSnapshot = ScratchBuffers.intSnapshot(IntArrayList.of(1));
                        IntSnapshot innerSnapshot = ScratchBuffers.intSnapshot(IntArrayList.of(2))) {
                    outer.set(outerSnapshot);
                    inner.set(innerSnapshot);
                }
            });

            assertThat(inner.get()).isNotNull().isNotSameAs(outer.get());
        }

        @Test
        void intSnapshot_shouldReuseTheBufferOfEachDepth_whenLeasedAgain() {
            final AtomicReference<List<Object>> first = new AtomicReference<>();
            final AtomicReference<List<Object>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                first.set(leaseTwoIntDepths());
                actual.set(leaseTwoIntDepths());
            });

            assertThat(actual.get())
                    .hasSize(2)
                    .zipSatisfy(
                            first.get(),
                            (again, firstLease) -> assertThat(again).isSameAs(firstLease));
        }

        @Test
        void intSnapshot_shouldFreeTheDepth_whenTheCopyThrows() {
            final AtomicReference<Throwable> thrown = new AtomicReference<>();
            final AtomicReference<Object> before = new AtomicReference<>();
            final AtomicReference<Object> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (IntSnapshot lease = ScratchBuffers.intSnapshot(IntArrayList.of())) {
                    before.set(lease);
                }

                thrown.set(catchThrowable(() -> ScratchBuffers.intSnapshot(new FailingIntCollection())));
                try (IntSnapshot lease = ScratchBuffers.intSnapshot(IntArrayList.of())) {
                    actual.set(lease);
                }
            });

            assertThat(thrown.get()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(actual.get()).isNotNull().isSameAs(before.get());
        }

        @Test
        void intSnapshot_shouldKeepItsOwnDepth_whenAnObjectLeaseIsOpen() {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final IntSnapshot ints = ScratchBuffers.intSnapshot(IntArrayList.of(1));
                final Snapshot<Object> objects = ScratchBuffers.empty();
                actual.set(catchThrowable(ints::close));
                objects.close();
            });

            assertThat(actual.get()).isNull();
        }

        @Test
        void intSnapshot_shouldThrow_whenCalledOffTheLoop() {
            assertThatThrownBy(() -> ScratchBuffers.intSnapshot(IntArrayList.of(1)))
                    .isInstanceOf(IllegalStateException.class);
        }

        private static List<Object> leaseTwoIntDepths() {
            try (IntSnapshot outer = ScratchBuffers.intSnapshot(IntArrayList.of(1));
                    IntSnapshot inner = ScratchBuffers.intSnapshot(IntArrayList.of(2))) {
                return List.of(outer, inner);
            }
        }
    }

    @Nested
    class IntClose {
        @Test
        void close_shouldClearTheIntBuffer() {
            final AtomicReference<int[]> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final IntSnapshot lease = ScratchBuffers.intSnapshot(IntArrayList.of(1, 2));
                lease.close();
                actual.set(lease.toIntArray());
            });

            assertThat(actual.get()).isNotNull().isEmpty();
        }

        @Test
        void close_shouldThrow_whenAnOuterIntLeaseIsClosedFirst() {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                try (IntSnapshot outer = ScratchBuffers.intSnapshot(IntArrayList.of(1));
                        IntSnapshot inner = ScratchBuffers.intSnapshot(IntArrayList.of(2))) {
                    actual.set(catchThrowable(outer::close));
                }
            });

            assertThat(actual.get()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void close_shouldThrow_whenTheIntLeaseIsClosedOffTheLoop() {
            final AtomicReference<IntSnapshot> lease = new AtomicReference<>();
            WorldLoopExtension.runInPass(() -> lease.set(ScratchBuffers.intSnapshot(IntArrayList.of(1))));

            final Throwable actual = catchThrowable(() -> lease.get().close());
            WorldLoopExtension.runInPass(() -> lease.get().close());

            assertThat(actual).isInstanceOf(IllegalStateException.class);
        }
    }

    private static final class FailingIntCollection extends AbstractIntCollection {
        @Override
        public IntIterator iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public int[] toArray(int[] a) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailingCollection extends AbstractCollection<Object> {
        @Override
        public Iterator<Object> iterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public <T> T[] toArray(T[] a) {
            throw new UnsupportedOperationException();
        }
    }
}
