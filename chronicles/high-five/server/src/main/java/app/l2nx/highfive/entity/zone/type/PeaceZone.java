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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.TerritoryWarManager;

/**
 * A Peace Zone
 * @author durgus
 */
@SingleWriter
public class PeaceZone extends ZoneType {
    public PeaceZone(int id) {
        super(id);
    }

    @Override
    protected void onEnter(Creature creature) {
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            if (player.isCombatFlagEquipped()
                    && TerritoryWarManager.getInstance().isTWInProgress()) {
                TerritoryWarManager.getInstance().dropCombatFlag(player, false, true);
            }

            // Siege participants skip the peace-zone effects in mode 1.
            if ((player.getSiegeState() != 0) && (GeneralConfig.PEACE_ZONE_MODE == 1)) {
                return;
            }

            if (PlayerConfig.NEVIT_ENABLED) {
                player.getNevitSystem().stopAdventTask(true);

                player.storeRecommendations(true);
                player.getStat().setPausedNevitHourglassStatus(true);
            }
        }

        if (GeneralConfig.PEACE_ZONE_MODE != 2) {
            creature.setInsideZone(ZoneId.PEACE, true);
        }

        if (!getAllowStore()) {
            creature.setInsideZone(ZoneId.NO_STORE, true);
        }
    }

    @Override
    protected void onExit(Creature creature) {
        if (GeneralConfig.PEACE_ZONE_MODE != 2) {
            creature.setInsideZone(ZoneId.PEACE, false);
        }

        if (!getAllowStore()) {
            creature.setInsideZone(ZoneId.NO_STORE, false);
        }
    }
}
