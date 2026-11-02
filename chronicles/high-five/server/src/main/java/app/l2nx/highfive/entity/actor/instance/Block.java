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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.ArenaParticipantsHolder;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.games.BlockCheckerManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCubeGameChangePoints;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExCubeGameExtendedChangePoints;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author BiggBoss
 */
@SingleWriter
public class Block extends Monster {
    private int colorEffect;

    public Block(NpcTemplate template) {
        super(template);
    }

    @Override
    public int getColorEffect() {
        WorldPartition.checkOwner();
        return colorEffect;
    }

    public void changeColor(Player attacker, ArenaParticipantsHolder holder, int team) {
        WorldPartition.checkOwner();
        final BlockCheckerManager event = holder.getEvent();
        if (colorEffect == 0x53) {
            colorEffect = 0x00;

            broadcastPacket(new AbstractNpcInfo.NpcInfo(this, attacker));
            increaseTeamPointsAndSend(attacker, team, event);
        } else {
            colorEffect = 0x53;

            broadcastPacket(new AbstractNpcInfo.NpcInfo(this, attacker));
            increaseTeamPointsAndSend(attacker, team, event);
        }

        final int random = ClockBridge.random().nextInt(100);

        if ((random > 69) && (random <= 84)) {
            dropItem(13787, event, attacker);
        } else if (random > 84) {
            dropItem(13788, event, attacker);
        }
    }

    public void setRed(boolean isRed) {
        WorldPartition.checkOwner();
        colorEffect = isRed ? 0x53 : 0x00;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        if (attacker.isPlayer()) {
            return attacker.isPlayer() && (attacker.asPlayer().getBlockCheckerArena() > -1);
        }

        return true;
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        return false;
    }

    @Override
    public void onAction(Player player, boolean interact) {
        WorldPartition.checkOwner();
        if (!canTarget(player)) {
            return;
        }

        player.setLastFolkNPC(this);

        if (player.getTarget() != this) {
            player.setTarget(this);
            getAI(); // wake up ai
        } else if (interact) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
        }
    }

    private void increaseTeamPointsAndSend(Player player, int team, BlockCheckerManager eng) {
        eng.increasePlayerPoints(player, team);

        final int timeLeft = (int) ((eng.getStarterTime() - ClockBridge.gameMillis()) / 1000);
        final boolean isRed = eng.getHolder().getPlayerTeam(player) == 0;
        final ExCubeGameChangePoints changePoints =
                new ExCubeGameChangePoints(timeLeft, eng.getBluePoints(), eng.getRedPoints());
        final ExCubeGameExtendedChangePoints secretPoints = new ExCubeGameExtendedChangePoints(
                timeLeft, eng.getBluePoints(), eng.getRedPoints(), isRed, player, eng.getPlayerPoints(player, isRed));
        eng.getHolder().broadCastPacketToTeam(changePoints);
        eng.getHolder().broadCastPacketToTeam(secretPoints);
    }

    private void dropItem(int id, BlockCheckerManager eng, Player player) {
        final Item drop = ItemManager.createItem(ItemProcessType.LOOT, id, 1, player, this);
        final int x = getX() + ClockBridge.random().nextInt(50);
        final int y = getY() + ClockBridge.random().nextInt(50);
        final int z = getZ();
        drop.dropMe(this, x, y, z);
        eng.addNewDrop(drop);
    }
}
