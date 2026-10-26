/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.movement.system.MovementSystems;

/**
 * TODO: the bridge for legacy callers that are not beans disappears once its last caller is handed the movement
 * systems of its partition.
 */
public final class Movement {
    private static volatile MovementSystems systems;

    private Movement() {}

    public static void bind(MovementSystems installed) {
        systems = installed;
    }

    static MovementSystems swap(MovementSystems next) {
        final MovementSystems previous = systems;
        systems = next;
        return previous;
    }

    public static void register(Creature creature) {
        current().registerMovingObject(creature);
    }

    private static MovementSystems current() {
        final MovementSystems bound = systems;
        if (bound == null) {
            throw new IllegalStateException("The movement systems are not installed on a running world");
        }
        return bound;
    }
}
