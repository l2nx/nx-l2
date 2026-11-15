/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.persistence.value.InFlightCharacters;
import app.l2nx.engine.core.persistence.value.PendingItems;
import lombok.Builder;

// TODO: goes once legacy singletons become beans that receive the loop (#41).

public final class WorldPartition {
    private static volatile Bound world;

    private WorldPartition() {}

    static void bind(Loop loop) {
        world = new Bound(loop);
    }

    static Loop swap(Loop next) {
        final Bound previous = world;
        world = next == null ? null : new Bound(next);
        return previous == null ? null : previous.loop();
    }

    public static Loop loop() {
        return bound().loop();
    }

    public static void checkOwner() {
        loop().checkOwner();
    }

    public static void offer(Runnable work) {
        loop().execute(work);
    }

    /** World loop only. */
    public static InFlightCharacters inFlightCharacters() {
        return bound().inFlight();
    }

    /** World loop only. */
    public static LoadedCharacters loadedCharacters() {
        return bound().loaded();
    }

    /** World loop only. */
    public static LoadedGameAccounts loadedGameAccounts() {
        return bound().gameAccounts();
    }

    /** World loop only. */
    public static PendingItems pendingItems() {
        return bound().pending();
    }

    /** World loop only. */
    public static AwaitedStacks awaitedStacks() {
        return bound().awaited();
    }

    private static Bound bound() {
        final Bound bound = world;
        if (bound == null) {
            throw new IllegalStateException("The world partition is not running");
        }
        return bound;
    }

    @Builder(toBuilder = true)
    private record Bound(
            Loop loop,
            InFlightCharacters inFlight,
            LoadedCharacters loaded,
            LoadedGameAccounts gameAccounts,
            PendingItems pending,
            AwaitedStacks awaited) {
        Bound(Loop loop) {
            this(
                    loop,
                    new InFlightCharacters(),
                    new LoadedCharacters(),
                    new LoadedGameAccounts(),
                    new PendingItems(),
                    new AwaitedStacks());
        }
    }
}
