/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267.game;

import app.l2nx.load.bot.model.Position;

/**
 * The server moves a character to the position a client reports when it is over one tick of run speed away, so a stale
 * report would drag it back. Between server positions this is dead reckoning at the run speed of the load bot's own
 * {@code UserInfo}.
 */
final class OwnPosition {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private double runSpeed;
    private Position origin;
    private Position target;
    private long startedAt;

    /** @param runSpeed game units per second */
    OwnPosition(Position start, double runSpeed) {
        origin = start;
        this.runSpeed = runSpeed;
    }

    /** The new speed applies from where the move has got to, not to the part already walked. */
    void runSpeed(double runSpeed, long now) {
        origin = current(now);
        startedAt = now;
        this.runSpeed = runSpeed;
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
        if (target == null || runSpeed <= 0) {
            return origin;
        }
        final double dx = target.x() - origin.x();
        final double dy = target.y() - origin.y();
        final double distance = Math.hypot(dx, dy);
        final double walked = runSpeed * (now - startedAt) / NANOS_PER_SECOND;
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
