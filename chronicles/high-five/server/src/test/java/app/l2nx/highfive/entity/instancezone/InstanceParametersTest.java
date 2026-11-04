/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.instancezone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class InstanceParametersTest {
    private final InstanceParameters parameters = new InstanceParameters();

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<InstanceParameters> call) {
            assertThatThrownBy(() -> call.accept(parameters))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<InstanceParameters>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getBoolean", p -> p.getBoolean("key", false)),
                    Named.of("getInt", p -> p.getInt("key", 0)),
                    Named.of("getLong", p -> p.getLong("key", 0)),
                    Named.of("getObject", p -> p.getObject("key", Object.class)),
                    Named.of("getList", p -> p.getList("key", Integer.class)),
                    Named.of("getMap", p -> p.getMap("key", Integer.class, Integer.class)),
                    Named.of("getLocation", p -> p.getLocation("key")),
                    Named.of("set (object)", p -> p.set("key", (Object) 1)),
                    Named.of("set (int)", p -> p.set("key", 1)),
                    Named.of("remove", p -> p.remove("key")));
        }
    }

    @Nested
    class SetAndRemove {
        @Test
        void set_shouldStoreTheValuesTheGettersRead() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                parameters.set("count", 3);
                parameters.set("done", true);
                parameters.set("marker", (Object) "boss");
                return List.of(
                        parameters.getInt("count", 0),
                        parameters.getBoolean("done", false),
                        parameters.getObject("marker", String.class),
                        parameters.getInt("missing", -1));
            });

            assertThat(actual).containsExactly(3, true, "boss", -1);
        }

        @Test
        void remove_shouldDropTheValue() {
            final int actual = WorldLoopExtension.callInPass(() -> {
                parameters.set("count", 3);
                parameters.remove("count");
                return parameters.getInt("count", -1);
            });

            assertThat(actual).isEqualTo(-1);
        }
    }

    @Nested
    class GetLocation {
        @Test
        void getLocation_shouldReturnACopy_whenTheCallerAdjustsIt() {
            final Location stored = new Location(1, 2, 3);

            final Location actual = WorldLoopExtension.callInPass(() -> {
                parameters.set("spot", stored);
                parameters.getLocation("spot").setXYZ(7, 8, 9);
                return parameters.getLocation("spot");
            });

            assertThat(actual).isNotSameAs(stored).isEqualTo(new Location(1, 2, 3));
        }

        @Test
        void getLocation_shouldReturnNull_whenAbsent() {
            final Location actual = WorldLoopExtension.callInPass(() -> parameters.getLocation("spot"));

            assertThat(actual).isNull();
        }
    }
}
