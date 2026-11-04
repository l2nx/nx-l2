/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import app.l2nx.load.bot.model.Position;

/**
 * The server moves a character to the position a client reports when it is over one tick of run speed away, so a stale
 * report would drag it back. Between server positions this is dead reckoning at {@link #RUN_SPEED}, as {@code UserInfo}
 * carries no speed.
 */
final class OwnPosition {
    static final double RUN_SPEED = 120.0;

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private Position origin;
    private Position target;
    private long startedAt;

    OwnPosition(Position start) {
        origin = start;
    }

    void moveTo(Position from, Position to, long now) {
        origin = from;
        target = to;
        startedAt = now;
    }

    void sync(Position position, long now) {
        origin = position;
        startedAt = now;
    }

    void stop(Position position) {
        origin = position;
        target = null;
    }

    Position current(long now) {
        if (target == null) {
            return origin;
        }
        final double dx = target.x() - origin.x();
        final double dy = target.y() - origin.y();
        final double distance = Math.hypot(dx, dy);
        final double walked = RUN_SPEED * (now - startedAt) / NANOS_PER_SECOND;
        if (walked >= distance) {
            stop(target);
            return origin;
        }
        final double share = walked / distance;
        return Position.builder()
                .x(origin.x() + (int) Math.round(dx * share))
                .y(origin.y() + (int) Math.round(dy * share))
                .z(origin.z() + (int) Math.round((target.z() - origin.z()) * share))
                .build();
    }
}
