/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.clock;

import app.l2nx.engine.core.clock.ManualGameClock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Binds a {@link ManualGameClock} and a seeded generator for a test class and its {@code @Nested} classes. Users also
 * declare {@code @Execution(SAME_THREAD)} and {@code @ResourceLock(LOCK)}: the bridge is static.
 */
public final class ClockBridgeExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    public static final String LOCK = "app.l2nx.highfive.clock.ClockBridge";
    public static final Instant WALL_START = Instant.parse("2026-01-05T12:00:00Z");
    public static final long SEED = 42L;

    private static final Namespace NAMESPACE = Namespace.create(ClockBridgeExtension.class);
    private static final String CLOCK = "clock";
    private static final String PREVIOUS = "previous";

    @Override
    public void beforeAll(ExtensionContext context) {
        if (boundByEnclosingClass(context)) {
            return;
        }

        final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);
        // Generators are not thread-safe: one per thread, seeded from SEED and the thread name, so a sequence does
        // not depend on which thread drew first.
        final ThreadLocal<RandomGenerator> random =
                ThreadLocal.withInitial(() -> RandomGeneratorFactory.of("L64X128MixRandom")
                        .create(SEED ^ Thread.currentThread().getName().hashCode()));
        final ClockBridge.Binding previous = ClockBridge.swap(new ClockBridge.Binding(clock, random::get));

        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(CLOCK, clock);
        store.put(PREVIOUS, Optional.ofNullable(previous));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterAll(ExtensionContext context) {
        if (boundByEnclosingClass(context)) {
            return;
        }

        final Optional<ClockBridge.Binding> previous =
                context.getStore(NAMESPACE).get(PREVIOUS, Optional.class);
        ClockBridge.swap(previous.orElse(null));
    }

    /** A store looks a key up in the enclosing contexts too, so a nested class resolves the outer class's clock. */
    private static boolean boundByEnclosingClass(ExtensionContext context) {
        return context.getParent()
                .map(parent -> parent.getStore(NAMESPACE).get(CLOCK) != null)
                .orElse(false);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == ManualGameClock.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(CLOCK, ManualGameClock.class);
    }
}
