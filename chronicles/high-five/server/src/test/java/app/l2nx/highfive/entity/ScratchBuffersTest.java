/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ScratchBuffersTest {

    @Nested
    class Acquire {
        @Test
        void acquire_shouldHandOutAnotherBuffer_whenNested() {
            final ObjectArrayList<Object> outer = ScratchBuffers.acquire();
            try {
                final ObjectArrayList<Object> inner = ScratchBuffers.acquire();
                try {
                    assertThat((Object) inner).isNotSameAs(outer);
                } finally {
                    ScratchBuffers.release(inner);
                }
            } finally {
                ScratchBuffers.release(outer);
            }
        }

        @Test
        void acquire_shouldReuseTheBuffer_whenTheDepthWasReleased() {
            final ObjectArrayList<Object> first = ScratchBuffers.acquire();
            ScratchBuffers.release(first);

            final ObjectArrayList<Object> actual = ScratchBuffers.acquire();
            ScratchBuffers.release(actual);

            assertThat((Object) actual).isSameAs(first);
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
            final ObjectArrayList<String> buffer = ScratchBuffers.acquire();
            try {
                buffer.addAll(List.of("x", "y", "z", "w", "v"));
                ScratchBuffers.copy(source, buffer);

                assertThat((List<String>) buffer).containsExactlyInAnyOrder("a", "b", "c");
            } finally {
                ScratchBuffers.release(buffer);
            }
        }
    }

    @Nested
    class Release {
        @Test
        void release_shouldClearTheBuffer() {
            final ObjectArrayList<Object> buffer = ScratchBuffers.acquire();
            buffer.add(new Object());
            ScratchBuffers.release(buffer);

            assertThat((List<Object>) buffer).isEmpty();
        }

        @Test
        void release_shouldThrow_whenAnOuterBufferIsReleasedFirst() {
            final ObjectArrayList<Object> outer = ScratchBuffers.acquire();
            final ObjectArrayList<Object> inner = ScratchBuffers.acquire();
            try {
                assertThatThrownBy(() -> ScratchBuffers.release(outer)).isInstanceOf(IllegalStateException.class);
            } finally {
                ScratchBuffers.release(inner);
                ScratchBuffers.release(outer);
            }
        }

        @Test
        void release_shouldThrow_whenNothingIsAcquired() {
            final ObjectArrayList<Object> buffer = new ObjectArrayList<>();

            assertThatThrownBy(() -> ScratchBuffers.release(buffer)).isInstanceOf(IllegalStateException.class);
        }
    }
}
