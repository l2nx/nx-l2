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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.holders.player.ArenaParticipantsHolder;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.managers.ItemManager;
import app.l2nx.highfive.managers.games.BlockCheckerManager;
import app.l2nx.highfive.network.serverpackets.AbstractNpcInfo;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.ExCubeGameChangePoints;
import app.l2nx.highfive.network.serverpackets.ExCubeGameExtendedChangePoints;
import lombok.Getter;

/**
 * @author BiggBoss
 */
public class Block extends Monster {
    @Getter
    private int colorEffect;

    /**
     * Creates a block.
     * @param template the block NPC template
     */
    public Block(NpcTemplate template) {
        super(template);
    }

    /**
     * Will change the color of the block and update the appearance in the known players clients
     * @param attacker
     * @param holder
     * @param team
     */
    public void changeColor(Player attacker, ArenaParticipantsHolder holder, int team) {
        // Do not update color while sending old info.
        synchronized (this) {
            final BlockCheckerManager event = holder.getEvent();
            if (colorEffect == 0x53) {
                // Change color
                colorEffect = 0x00;

                // BroadCast to all known players.
                broadcastPacket(new AbstractNpcInfo.NpcInfo(this, attacker));
                increaseTeamPointsAndSend(attacker, team, event);
            } else {
                // Change color
                colorEffect = 0x53;

                // BroadCast to all known players.
                broadcastPacket(new AbstractNpcInfo.NpcInfo(this, attacker));
                increaseTeamPointsAndSend(attacker, team, event);
            }

            // 30% chance to drop the event items
            final int random = ClockBridge.random().nextInt(100);

            // Bond
            if ((random > 69) && (random <= 84)) {
                dropItem(13787, event, attacker);
            } else if (random > 84) {
                dropItem(13788, event, attacker);
            }
        }
    }

    /**
     * Sets if the block is red or blue. Mainly used in block spawn
     * @param isRed
     */
    public void setRed(boolean isRed) {
        colorEffect = isRed ? 0x53 : 0x00;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        if (attacker.isPlayer()) {
            return attacker.isPlayer() && (attacker.asPlayer().getBlockCheckerArena() > -1);
        }

        return true;
    }

    @Override
    public boolean doDie(Creature killer) {
        return false;
    }

    @Override
    public void onAction(Player player, boolean interact) {
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
        final boolean isRed = eng.getHolder().getRedPlayers().contains(player);
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
