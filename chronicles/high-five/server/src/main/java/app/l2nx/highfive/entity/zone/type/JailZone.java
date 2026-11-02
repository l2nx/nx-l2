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
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.tasks.player.TeleportTask;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.concurrent.TimeUnit;

/**
 * A jail zone
 * @author durgus
 */
@SingleWriter
public class JailZone extends ZoneType {
    private static final Location JAIL_IN_LOC = new Location(-114356, -249645, -2984);
    private static final Location JAIL_OUT_LOC = new Location(17836, 170178, -3507);

    public JailZone(int id) {
        super(id);
    }

    @Override
    protected void onEnter(Creature creature) {
        if (creature.isPlayer()) {
            creature.setInsideZone(ZoneId.JAIL, true);
            creature.setInsideZone(ZoneId.NO_SUMMON_FRIEND, true);
            if (GeneralConfig.JAIL_IS_PVP) {
                creature.setInsideZone(ZoneId.PVP, true);
                creature.sendPacket(SystemMessageId.YOU_HAVE_ENTERED_A_COMBAT_ZONE);
            }

            if (GeneralConfig.JAIL_DISABLE_TRANSACTION) {
                creature.setInsideZone(ZoneId.NO_STORE, true);
            }
        }
    }

    @Override
    protected void onExit(Creature creature) {
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            player.setInsideZone(ZoneId.JAIL, false);
            player.setInsideZone(ZoneId.NO_SUMMON_FRIEND, false);

            if (GeneralConfig.JAIL_IS_PVP) {
                creature.setInsideZone(ZoneId.PVP, false);
                creature.sendPacket(SystemMessageId.YOU_HAVE_LEFT_A_COMBAT_ZONE);
            }

            if (player.isJailed()) {
                WorldPartition.loop()
                        .schedule(new TeleportTask(player, JAIL_IN_LOC.clone()), 2000, TimeUnit.MILLISECONDS);
                creature.sendMessage(
                        "You cannot cheat your way out of here. You must wait until your jail time is over.");
            }

            if (GeneralConfig.JAIL_DISABLE_TRANSACTION) {
                creature.setInsideZone(ZoneId.NO_STORE, false);
            }
        }
    }

    public static Location getLocationIn() {
        WorldPartition.checkOwner();
        return JAIL_IN_LOC.clone();
    }

    public static Location getLocationOut() {
        WorldPartition.checkOwner();
        return JAIL_OUT_LOC.clone();
    }
}
