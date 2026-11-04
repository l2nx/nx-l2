/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import app.l2nx.load.bot.model.Position;
import lombok.Builder;

/** @param dead a repeated sighting never revives an object: {@link Revived} does */
@Builder(toBuilder = true)
public record ObjectSeen(int objectId, Kind kind, Position position, int team, boolean dead) implements Observation {
    public enum Kind {
        CHARACTER,
        NPC,
        MONSTER
    }
}
