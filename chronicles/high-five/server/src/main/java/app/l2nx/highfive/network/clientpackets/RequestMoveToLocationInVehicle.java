/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.network.clientpackets;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Boat;
import app.l2nx.highfive.entity.item.type.WeaponType;
import app.l2nx.highfive.managers.BoatManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.ActionFailed;
import app.l2nx.highfive.network.serverpackets.MoveToLocationInVehicle;
import app.l2nx.highfive.network.serverpackets.StopMoveInVehicle;

public class RequestMoveToLocationInVehicle extends ClientPacket {
    private int boatId;
    private int targetX;
    private int targetY;
    private int targetZ;
    private int originX;
    private int originY;
    private int originZ;

    @Override
    protected void readImpl() {
        boatId = readInt(); // objectId of boat
        targetX = readInt();
        targetY = readInt();
        targetZ = readInt();
        originX = readInt();
        originY = readInt();
        originZ = readInt();
    }

    @Override
    protected void runImpl() {
        final Player player = getPlayer();
        if (player == null) {
            return;
        }

        if ((PlayerConfig.PLAYER_MOVEMENT_BLOCK_TIME > 0)
                && !player.isGM()
                && (player.getNotMoveUntil() > System.currentTimeMillis())) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_MOVE_WHILE_SPEAKING_TO_AN_NPC_ONE_MOMENT_PLEASE);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if ((targetX == originX) && (targetY == originY) && (targetZ == originZ)) {
            player.sendPacket(new StopMoveInVehicle(player, boatId));
            return;
        }

        if (player.isAttackingNow()
                && (player.getActiveWeaponItem() != null)
                && (player.getActiveWeaponItem().getItemType() == WeaponType.BOW)) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isSitting() || player.isMovementDisabled()) {
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.hasSummon()) {
            player.sendPacket(
                    SystemMessageId
                            .YOU_SHOULD_RELEASE_YOUR_PET_OR_SERVITOR_SO_THAT_IT_DOES_NOT_FALL_OFF_OF_THE_BOAT_AND_DROWN);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        if (player.isTransformed()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_POLYMORPH_WHILE_RIDING_A_BOAT);
            player.sendPacket(ActionFailed.STATIC_PACKET);
            return;
        }

        Boat boat;
        if (player.isInBoat()) {
            boat = player.getBoat();
            if (boat.getObjectId() != boatId) {
                boat = BoatManager.getInstance().getBoat(boatId);
                player.setVehicle(boat);
            }
        } else {
            boat = BoatManager.getInstance().getBoat(boatId);
            player.setVehicle(boat);
        }

        final Location pos = new Location(targetX, targetY, targetZ);
        final Location originPos = new Location(originX, originY, originZ);
        player.setInVehiclePosition(pos);
        player.broadcastPacket(new MoveToLocationInVehicle(player, pos, originPos));
    }
}
