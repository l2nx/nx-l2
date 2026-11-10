/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.mechanics.events.holders.actor.player;

import app.l2nx.highfive.character.StoredCharacter;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;
import lombok.Builder;

/**
 * The listeners run after the world entry, when the player no longer keeps its rows, so the event carries them.
 *
 * @param stored the rows of the world entry; {@code null} for a character created in this session
 * @author UnAfraid
 */
@Builder(toBuilder = true)
public record OnPlayerLogin(Player player, StoredCharacter.WorldEntry stored) implements IBaseEvent {
    @Override
    public EventType getType() {
        return EventType.ON_PLAYER_LOGIN;
    }
}
