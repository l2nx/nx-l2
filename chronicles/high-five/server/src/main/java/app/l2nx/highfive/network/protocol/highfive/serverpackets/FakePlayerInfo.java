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
package app.l2nx.highfive.network.protocol.highfive.serverpackets;

import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.entity.actor.holders.npc.FakePlayerHolder;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

/**
 * @author Mobius
 */
public class FakePlayerInfo extends ServerPacket {
    private final Npc npc;
    private final int objId;
    private final int x;
    private final int y;
    private final int z;
    private final int heading;
    private final int mAtkSpd;
    private final int pAtkSpd;
    private final int runSpd;
    private final int walkSpd;
    private final int swimRunSpd;
    private final int swimWalkSpd;
    private final int flyRunSpd;
    private final int flyWalkSpd;
    private final double moveMultiplier;
    private final float attackSpeedMultiplier;
    private final FakePlayerHolder fpcHolder;
    private final Clan clan;

    public FakePlayerInfo(Npc npc) {
        this.npc = npc;
        objId = npc.getObjectId();
        x = npc.getX();
        y = npc.getY();
        z = npc.getZ();
        heading = npc.getHeading();
        mAtkSpd = npc.getMAtkSpd();
        pAtkSpd = (int) npc.getPAtkSpd();
        attackSpeedMultiplier = npc.getAttackSpeedMultiplier();
        moveMultiplier = npc.getMovementSpeedMultiplier();
        runSpd = (int) Math.round(npc.getRunSpeed() / moveMultiplier);
        walkSpd = (int) Math.round(npc.getWalkSpeed() / moveMultiplier);
        swimRunSpd = (int) Math.round(npc.getSwimRunSpeed() / moveMultiplier);
        swimWalkSpd = (int) Math.round(npc.getSwimWalkSpeed() / moveMultiplier);
        flyRunSpd = npc.isFlying() ? runSpd : 0;
        flyWalkSpd = npc.isFlying() ? walkSpd : 0;
        fpcHolder = npc.getTemplate().getFakePlayerInfo();
        clan = ClanTable.getInstance().getClan(fpcHolder.getClanId());
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.CHAR_INFO.writeId(this, buffer);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(0); // vehicleId
        buffer.writeInt(objId);
        buffer.writeString(npc.getName());
        buffer.writeInt(npc.getRace().ordinal());
        buffer.writeInt(npc.getTemplate().getSex() == Sex.FEMALE);
        buffer.writeInt(fpcHolder.getPlayerClass().getId());
        buffer.writeInt(0); // Inventory.PAPERDOLL_UNDER
        buffer.writeInt(fpcHolder.getEquipHead());
        buffer.writeInt(fpcHolder.getEquipRHand());
        buffer.writeInt(fpcHolder.getEquipLHand());
        buffer.writeInt(fpcHolder.getEquipGloves());
        buffer.writeInt(fpcHolder.getEquipChest());
        buffer.writeInt(fpcHolder.getEquipLegs());
        buffer.writeInt(fpcHolder.getEquipFeet());
        buffer.writeInt(fpcHolder.getEquipCloak());
        buffer.writeInt(fpcHolder.getEquipRHand()); // dual hand
        buffer.writeInt(fpcHolder.getEquipHair());
        buffer.writeInt(fpcHolder.getEquipHair2());
        buffer.writeInt(0); // Inventory.PAPERDOLL_RBRACELET
        buffer.writeInt(0); // Inventory.PAPERDOLL_LBRACELET
        buffer.writeInt(0); // Inventory.PAPERDOLL_DECO1
        buffer.writeInt(0); // Inventory.PAPERDOLL_DECO2
        buffer.writeInt(0); // Inventory.PAPERDOLL_DECO3
        buffer.writeInt(0); // Inventory.PAPERDOLL_DECO4
        buffer.writeInt(0); // Inventory.PAPERDOLL_DECO5
        buffer.writeInt(0); // Inventory.PAPERDOLL_DECO6
        buffer.writeInt(0); // Inventory.PAPERDOLL_BELT
        for (int i = 0; i < 21; i++) {
            buffer.writeInt(0);
        }

        buffer.writeInt(0); // getTalismanSlots
        buffer.writeInt(1); // canEquipCloak
        buffer.writeInt(npc.getScriptValue()); // getPvpFlag()
        buffer.writeInt(npc.getKarma());
        buffer.writeInt(mAtkSpd);
        buffer.writeInt(pAtkSpd);
        buffer.writeInt(0); // ?
        buffer.writeInt(runSpd);
        buffer.writeInt(walkSpd);
        buffer.writeInt(swimRunSpd);
        buffer.writeInt(swimWalkSpd);
        buffer.writeInt(flyRunSpd);
        buffer.writeInt(flyWalkSpd);
        buffer.writeInt(flyRunSpd);
        buffer.writeInt(flyWalkSpd);
        buffer.writeDouble(moveMultiplier);
        buffer.writeDouble(attackSpeedMultiplier);
        buffer.writeDouble(npc.getCollisionRadius());
        buffer.writeDouble(npc.getCollisionHeight());
        buffer.writeInt(fpcHolder.getHair());
        buffer.writeInt(fpcHolder.getHairColor());
        buffer.writeInt(fpcHolder.getFace());
        buffer.writeString(npc.getTemplate().getTitle());
        if (clan != null) {
            buffer.writeInt(clan.getId());
            buffer.writeInt(clan.getCrestId());
            buffer.writeInt(clan.getAllyId());
            buffer.writeInt(clan.getAllyCrestId());
        } else {
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
        }

        buffer.writeByte(!fpcHolder.isSitting());
        buffer.writeByte(npc.isRunning());
        buffer.writeByte(npc.isInCombat());
        buffer.writeByte(npc.isAlikeDead());
        buffer.writeByte(npc.isInvisible());
        buffer.writeByte(0); // 1-on Strider, 2-on Wyvern, 3-on Great Wolf, 0-no mount
        buffer.writeByte(fpcHolder.getPrivateStoreType());
        buffer.writeShort(0); // getCubics().size()

        // getCubics().keySet().forEach(packet::writeH);
        buffer.writeByte(0); // isInPartyMatchRoom
        buffer.writeInt(npc.getAbnormalVisualEffects());
        buffer.writeByte(npc.isInsideZone(ZoneId.WATER));
        buffer.writeShort(fpcHolder.getRecommends()); // Blue value for name (0 = white, 255 = pure blue)
        buffer.writeInt(0); // getMountNpcId() == 0 ? 0 : getMountNpcId() + 1000000
        buffer.writeInt(fpcHolder.getPlayerClass().getId());
        buffer.writeInt(0); // ?
        buffer.writeByte(fpcHolder.getWeaponEnchantLevel()); // isMounted() ? 0 : _enchantLevel
        buffer.writeByte(npc.getTeam().getId());
        buffer.writeInt(clan != null ? clan.getCrestLargeId() : 0);
        buffer.writeByte(fpcHolder.getNobleLevel());
        buffer.writeByte(fpcHolder.isHero());
        buffer.writeByte(fpcHolder.isFishing());
        buffer.writeInt(fpcHolder.getBaitLocationX());
        buffer.writeInt(fpcHolder.getBaitLocationY());
        buffer.writeInt(fpcHolder.getBaitLocationZ());
        buffer.writeInt(fpcHolder.getNameColor());
        buffer.writeInt(heading);
        buffer.writeInt(fpcHolder.getPledgeStatus());
        buffer.writeInt(0); // getPledgeType()
        buffer.writeInt(fpcHolder.getTitleColor());
        buffer.writeInt(0); // isCursedWeaponEquipped
        buffer.writeInt(0); // getClanId() > 0 ? getClan().getReputationScore() : 0

        // T1
        buffer.writeInt(0); // getTransformationDisplayId()
        buffer.writeInt(fpcHolder.getAgathionId());

        // T2
        buffer.writeInt(1);

        // T2.3
        buffer.writeInt(npc.getAbnormalVisualEffectSpecial());
    }
}
