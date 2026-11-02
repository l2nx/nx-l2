/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.residences.function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.residences.function.AbstractResidenceFunctionTest.TestFunction;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.Arrays;
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

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class ResidenceFunctionsTest {
    private final ItemContainer warehouse = mock(ItemContainer.class);
    private final ResidenceFunctions<TestFunction> functions = new ResidenceFunctions<>();

    private TestFunction ownedFunction() {
        return new TestFunction(true, warehouse, 0, true);
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ResidenceFunctions<TestFunction>> call) {
            assertThatThrownBy(() -> call.accept(functions))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ResidenceFunctions<TestFunction>>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("get (accessor)", f -> f.get(1)),
                    Named.of("put", f -> f.put(null)),
                    Named.of("remove", f -> f.remove(1)),
                    Named.of("forEachType", f -> f.forEachType(_ -> {})));
        }
    }

    @Nested
    class Put {
        @Test
        void put_shouldKeepOnePendingFee_whenAFunctionIsReplaced(Loop world) {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final int base = world.timers(TimeBase.GAME).size();
                functions.put(ownedFunction());
                final TestFunction replacement = ownedFunction();
                functions.put(replacement);
                return List.of(
                        world.timers(TimeBase.GAME).size() - base, functions.get(replacement.getType()) == replacement);
            });

            assertThat(actual).containsExactly(1, true);
        }
    }

    @Nested
    class Remove {
        @Test
        void remove_shouldCancelThePendingFee(Loop world) {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final int base = world.timers(TimeBase.GAME).size();
                final TestFunction function = ownedFunction();
                functions.put(function);
                functions.remove(function.getType());
                return Arrays.asList(world.timers(TimeBase.GAME).size() - base, functions.get(function.getType()));
            });

            assertThat(actual).containsExactly(0, null);
        }
    }

    @Nested
    class ForEachType {
        @Test
        void forEachType_shouldVisitEveryType_whenTheActionRemovesThem(Loop world) {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                final int base = world.timers(TimeBase.GAME).size();
                functions.put(ownedFunction());
                functions.forEachType(functions::remove);
                return Arrays.asList(world.timers(TimeBase.GAME).size() - base, functions.get(3));
            });

            assertThat(actual).containsExactly(0, null);
        }
    }
}
