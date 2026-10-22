/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.loop.EngineProperties;
import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.replay.PassRecorder;
import app.l2nx.engine.core.partition.PartitionRuntime;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

/** Users declare {@code @Execution(SAME_THREAD)} and {@code @ResourceLock(LOCK)}: the bridge is static. */
public final class WorldLoopExtension implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
    public static final String LOCK = "app.l2nx.highfive.partition.WorldPartition";
    public static final Instant WALL_START = Instant.parse("2026-01-05T12:00:00Z");
    public static final long SEED = 42L;

    private static final Namespace NAMESPACE = Namespace.create(WorldLoopExtension.class);
    private static final String WORLD = "world";
    private static final String PREVIOUS = "previous";

    private static volatile World current;

    public static void runInPass(Runnable body) {
        final World world = bound();
        world.loop().execute(body);
        world.runPass();
    }

    public static void passAfter(Duration step) {
        final World world = bound();
        world.clock().advance(step);
        world.runPass();
    }

    static Loop newLoop(ManualGameClock clock) {
        return new Loop(
                PartitionRuntime.WORLD,
                clock,
                SEED,
                EngineProperties.defaults(),
                new SimpleMeterRegistry(),
                PassRecorder.NOOP);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        if (boundByEnclosingClass(context)) {
            return;
        }

        final ManualGameClock clock = new ManualGameClock(WALL_START, ZoneOffset.UTC);
        final World world = new World(clock, newLoop(clock));
        final Previous previous =
                new Previous(Optional.ofNullable(current), Optional.ofNullable(WorldPartition.swap(world.loop())));
        current = world;

        final ExtensionContext.Store store = context.getStore(NAMESPACE);
        store.put(WORLD, world);
        store.put(PREVIOUS, previous);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (boundByEnclosingClass(context)) {
            return;
        }

        final Previous previous = context.getStore(NAMESPACE).get(PREVIOUS, Previous.class);
        current = previous.world().orElse(null);
        WorldPartition.swap(previous.loop().orElse(null));
    }

    private static boolean boundByEnclosingClass(ExtensionContext context) {
        return context.getParent()
                .map(parent -> parent.getStore(NAMESPACE).get(WORLD) != null)
                .orElse(false);
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == Loop.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(NAMESPACE).get(WORLD, World.class).loop();
    }

    private static World bound() {
        final World world = current;
        if (world == null) {
            throw new IllegalStateException("No world loop is bound: the test class lacks WorldLoopExtension");
        }
        return world;
    }

    private record World(ManualGameClock clock, Loop loop) {
        // A test may have run a pass of the resolved loop itself, at a later time than the clock reads.
        void runPass() {
            loop.runPass(Math.max(loop.getPassTime(), clock.gameMillis()));
        }
    }

    private record Previous(Optional<World> world, Optional<Loop> loop) {}
}
