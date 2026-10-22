/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.l2nx.engine.core.clock.ManualGameClock;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class ClockBridgeExtensionTest {
    private final ClockBridgeExtension extension = new ClockBridgeExtension();
    private final ExtensionContext context = context(null);

    private ClockBridge.TimeSource outer;

    @BeforeEach
    void unbind() {
        outer = ClockBridge.swap(null);
    }

    @AfterEach
    void restore() {
        ClockBridge.swap(outer);
    }

    @Nested
    class BeforeAll {
        @Test
        void beforeAll_shouldKeepTheEnclosingBinding_whenTheClassIsNested() {
            final ExtensionContext nested = context(context);
            extension.beforeAll(context);
            final ManualGameClock outerClock = (ManualGameClock) extension.resolveParameter(null, context);

            extension.beforeAll(nested);

            assertThat(ClockBridge.clock()).isSameAs(outerClock);
            assertThat(extension.resolveParameter(null, nested)).isSameAs(outerClock);
        }

        @Test
        void beforeAll_shouldSeedEachThreadByItsName() throws InterruptedException {
            extension.beforeAll(context);

            // Started in reverse name order: the sequence of a thread must not depend on which thread drew first.
            final long second = firstDraw("worker-2");
            final long first = firstDraw("worker-1");

            assertThat(first).isEqualTo(expectedFirstDraw("worker-1"));
            assertThat(second).isEqualTo(expectedFirstDraw("worker-2"));
        }

        private static long firstDraw(String threadName) throws InterruptedException {
            final AtomicLong draw = new AtomicLong();
            final Thread thread = new Thread(() -> draw.set(ClockBridge.random().nextLong()), threadName);
            thread.start();
            thread.join();
            return draw.get();
        }

        private static long expectedFirstDraw(String threadName) {
            return RandomGeneratorFactory.of("L64X128MixRandom")
                    .create(ClockBridgeExtension.SEED ^ threadName.hashCode())
                    .nextLong();
        }
    }

    @Nested
    class AfterAll {
        @Test
        void afterAll_shouldUnbind_whenNothingWasBoundBefore() {
            extension.beforeAll(context);

            extension.afterAll(context);

            assertThatIllegalStateException().isThrownBy(ClockBridge::clock).withMessageContaining("not bound");
        }

        @Test
        void afterAll_shouldRestoreThePreviousBinding() {
            final ManualGameClock previous = new ManualGameClock(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC);
            ClockBridge.bind(previous, ThreadLocalRandom::current);
            extension.beforeAll(context);

            extension.afterAll(context);

            assertThat(ClockBridge.clock()).isSameAs(previous);
        }

        @Test
        void afterAll_shouldKeepTheEnclosingBinding_whenTheClassIsNested() {
            final ExtensionContext nested = context(context);
            extension.beforeAll(context);
            final ManualGameClock outerClock = (ManualGameClock) extension.resolveParameter(null, context);
            extension.beforeAll(nested);

            extension.afterAll(nested);

            assertThat(ClockBridge.clock()).isSameAs(outerClock);
        }
    }

    /**
     * A context whose store, like JUnit's, falls back to the parent's store on a missing key.
     */
    private static ExtensionContext context(ExtensionContext parent) {
        final Map<Object, Object> values = new HashMap<>();
        final ExtensionContext.Store store = (ExtensionContext.Store) Proxy.newProxyInstance(
                ExtensionContext.Store.class.getClassLoader(),
                new Class<?>[] {ExtensionContext.Store.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "put" -> values.put(args[0], args[1]);
                    case "get" -> {
                        if (values.containsKey(args[0]) || (parent == null)) {
                            yield values.get(args[0]);
                        }

                        yield parent.getStore((ExtensionContext.Namespace) null).get(args[0]);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class<?>[] {ExtensionContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getStore" -> store;
                    case "getParent" -> Optional.ofNullable(parent);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
