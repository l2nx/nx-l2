/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import app.l2nx.highfive.partition.WorldLoopExtension;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
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
    class Acquire {
        @Test
        void acquire_shouldHandOutAnotherBuffer_whenNested() {
            final AtomicReference<Object> outer = new AtomicReference<>();
            final AtomicReference<Object> inner = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final ObjectArrayList<Object> outerBuffer = ScratchBuffers.acquire();
                try {
                    final ObjectArrayList<Object> innerBuffer = ScratchBuffers.acquire();
                    ScratchBuffers.release(innerBuffer);
                    inner.set(innerBuffer);
                } finally {
                    ScratchBuffers.release(outerBuffer);
                }
                outer.set(outerBuffer);
            });

            assertThat(inner.get()).isNotNull().isNotSameAs(outer.get());
        }

        @Test
        void acquire_shouldReuseTheBuffer_whenTheDepthWasReleased() {
            final AtomicReference<Object> first = new AtomicReference<>();
            final AtomicReference<Object> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final ObjectArrayList<Object> firstBuffer = ScratchBuffers.acquire();
                ScratchBuffers.release(firstBuffer);
                first.set(firstBuffer);

                final ObjectArrayList<Object> secondBuffer = ScratchBuffers.acquire();
                ScratchBuffers.release(secondBuffer);
                actual.set(secondBuffer);
            });

            assertThat(actual.get()).isNotNull().isSameAs(first.get());
        }

        @Test
        void acquire_shouldThrow_whenCalledOffTheLoop() {
            assertThatThrownBy(ScratchBuffers::acquire).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Copy {
        static Stream<Arguments> sources() {
            return Stream.of(
                    Arguments.of(Named.of("array set", new ObjectArraySet<>(List.of("a", "b", "c")))),
                    Arguments.of(Named.of("hash set", new ObjectOpenHashSet<>(List.of("a", "b", "c")))),
                    Arguments.of(Named.of("jdk set", Set.of("a", "b", "c"))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("sources")
        void copy_shouldReplaceTheContent_whenTheBufferHeldMore(Collection<String> source) {
            final AtomicReference<List<String>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final ObjectArrayList<String> buffer = ScratchBuffers.acquire();
                try {
                    buffer.addAll(List.of("x", "y", "z", "w", "v"));
                    ScratchBuffers.copy(source, buffer);
                    actual.set(List.copyOf(buffer));
                } finally {
                    ScratchBuffers.release(buffer);
                }
            });

            assertThat(actual.get()).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    @Nested
    class Release {
        @Test
        void release_shouldClearTheBuffer() {
            final AtomicReference<List<Object>> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final ObjectArrayList<Object> buffer = ScratchBuffers.acquire();
                buffer.add(new Object());
                ScratchBuffers.release(buffer);
                actual.set(buffer);
            });

            assertThat(actual.get()).isNotNull().isEmpty();
        }

        @Test
        void release_shouldThrow_whenAnOuterBufferIsReleasedFirst() {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final ObjectArrayList<Object> outer = ScratchBuffers.acquire();
                final ObjectArrayList<Object> inner = ScratchBuffers.acquire();
                try {
                    actual.set(catchThrowable(() -> ScratchBuffers.release(outer)));
                } finally {
                    ScratchBuffers.release(inner);
                    ScratchBuffers.release(outer);
                }
            });

            assertThat(actual.get()).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void release_shouldThrow_whenNothingIsAcquired() {
            final AtomicReference<Throwable> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(
                    () -> actual.set(catchThrowable(() -> ScratchBuffers.release(new ObjectArrayList<>()))));

            assertThat(actual.get()).isInstanceOf(IllegalStateException.class);
        }
    }
}
