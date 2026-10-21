/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import app.l2nx.load.bot.model.Position;
import lombok.Builder;

/**
 * A world object came into view, or was sent again with fresh state.
 *
 * @param team PvP team of a character as the server shows it, 0 for none
 */
@Builder(toBuilder = true)
public record ObjectSeen(int objectId, Kind kind, Position position, int team, boolean dead) implements Observation {
    public enum Kind {
        CHARACTER,
        NPC,
        ITEM
    }
}
