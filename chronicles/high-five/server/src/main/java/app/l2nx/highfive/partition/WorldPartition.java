/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;

// TODO: goes once legacy singletons become beans that receive the loop (#41).

public final class WorldPartition {
    private static volatile Loop world;

    private WorldPartition() {}

    static void bind(Loop loop) {
        world = loop;
    }

    static Loop swap(Loop next) {
        final Loop previous = world;
        world = next;
        return previous;
    }

    public static Loop loop() {
        final Loop bound = world;
        if (bound == null) {
            throw new IllegalStateException("The world partition is not running");
        }
        return bound;
    }

    public static void checkOwner() {
        loop().checkOwner();
    }

    public static void offer(Runnable work) {
        loop().execute(work);
    }
}
