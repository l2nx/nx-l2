/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mechanics.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;
import app.l2nx.highfive.mechanics.events.holders.OnDailyReset;
import app.l2nx.highfive.mechanics.events.listeners.AbstractEventListener;
import app.l2nx.highfive.mechanics.events.returns.AbstractEventReturn;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
class ListenersContainerTest {

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ListenersContainer> call) {
            final ListenersContainer container = new ListenersContainer();

            assertThatThrownBy(() -> call.accept(container))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ListenersContainer>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("addListener", container -> container.addListener(listener(container, 0, null))),
                    Named.of("hasListener", container -> container.hasListener(EventType.ON_DAILY_RESET)),
                    Named.of("getListeners", container -> container.getListeners(EventType.ON_DAILY_RESET)),
                    Named.of(
                            "collectListeners",
                            container -> container.collectListeners(EventType.ON_DAILY_RESET, new ArrayList<>())),
                    Named.of("removeListenerIf", container -> container.removeListenerIf(_ -> true)));
        }
    }

    @Nested
    class Dispatch {
        @Test
        void notifyEvent_shouldCallHigherPriorityFirst_andKeepRegistrationOrder_whenPrioritiesTie() {
            final List<String> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final ListenersContainer container = new ListenersContainer();
                container.addListener(listener(container, 0, () -> actual.add("low-first")));
                container.addListener(listener(container, 5, () -> actual.add("high")));
                container.addListener(listener(container, 0, () -> actual.add("low-second")));
                EventDispatcher.getInstance().notifyEvent(new OnDailyReset(), container);
            });

            assertThat(actual).containsExactly("high", "low-first", "low-second");
        }

        @Test
        void notifyEvent_shouldReachEveryListener_whenOneUnregistersItselfWhileNotified() {
            final List<String> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final ListenersContainer container = new ListenersContainer();
                final AtomicReference<AbstractEventListener> once = new AtomicReference<>();
                once.set(container.addListener(listener(container, 0, () -> {
                    actual.add("once");
                    once.get().unregisterMe();
                })));
                container.addListener(listener(container, 0, () -> actual.add("always")));
                EventDispatcher.getInstance().notifyEvent(new OnDailyReset(), container);
                EventDispatcher.getInstance().notifyEvent(new OnDailyReset(), container);
            });

            assertThat(actual).containsExactly("once", "always", "always");
        }
    }

    @Nested
    class GetListeners {
        @Test
        void getListeners_shouldReturnReadOnlySnapshot() {

            final List<AbstractEventListener> actual = WorldLoopExtension.callInPass(() -> {
                final ListenersContainer container = new ListenersContainer();
                container.addListener(listener(container, 0, null));
                final List<AbstractEventListener> listeners = container.getListeners(EventType.ON_DAILY_RESET);
                container.addListener(listener(container, 0, null));
                return listeners;
            });

            assertThat(actual).hasSize(1);
            assertThatThrownBy(() -> actual.clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private static AbstractEventListener listener(ListenersContainer container, int priority, Runnable onEvent) {
        return new AbstractEventListener(container, EventType.ON_DAILY_RESET, null, priority) {
            @Override
            public <R extends AbstractEventReturn> R executeEvent(IBaseEvent event, Class<R> returnBackClass) {
                if (onEvent != null) {
                    onEvent.run();
                }

                return null;
            }
        };
    }
}
