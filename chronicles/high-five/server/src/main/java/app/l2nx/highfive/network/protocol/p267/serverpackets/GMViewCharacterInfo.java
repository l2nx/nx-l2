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
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class GMViewCharacterInfo extends ServerPacket {
    private final Player player;
    private final int runSpd;
    private final int walkSpd;
    private final int swimRunSpd;
    private final int swimWalkSpd;
    private final int flyRunSpd;
    private final int flyWalkSpd;
    private final double moveMultiplier;

    public GMViewCharacterInfo(Player player) {
        this.player = player;
        moveMultiplier = player.getMovementSpeedMultiplier();
        runSpd = (int) Math.round(player.getRunSpeed() / moveMultiplier);
        walkSpd = (int) Math.round(player.getWalkSpeed() / moveMultiplier);
        swimRunSpd = (int) Math.round(player.getSwimRunSpeed() / moveMultiplier);
        swimWalkSpd = (int) Math.round(player.getSwimWalkSpeed() / moveMultiplier);
        flyRunSpd = player.isFlying() ? runSpd : 0;
        flyWalkSpd = player.isFlying() ? walkSpd : 0;
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.GM_VIEW_CHARACTER_INFO.writeId(this, buffer);
        buffer.writeInt(player.getX());
        buffer.writeInt(player.getY());
        buffer.writeInt(player.getZ());
        buffer.writeInt(player.getHeading());
        buffer.writeInt(player.getObjectId());
        buffer.writeString(player.getName());
        buffer.writeInt(player.getRace().ordinal());
        final PlayerAppearance appearance = player.getAppearance();
        buffer.writeInt(appearance.isFemale());
        buffer.writeInt(player.getPlayerClass().getId());
        buffer.writeInt(player.getLevel());
        buffer.writeLong(player.getExp());
        buffer.writeDouble(
                (float) (player.getExp() - ExperienceData.getInstance().getExpForLevel(player.getLevel()))
                        / (ExperienceData.getInstance().getExpForLevel(player.getLevel() + 1)
                                - ExperienceData.getInstance().getExpForLevel(player.getLevel()))); // High Five exp %
        buffer.writeInt(player.getSTR());
        buffer.writeInt(player.getDEX());
        buffer.writeInt(player.getCON());
        buffer.writeInt(player.getINT());
        buffer.writeInt(player.getWIT());
        buffer.writeInt(player.getMEN());
        buffer.writeInt(player.getMaxHp());
        buffer.writeInt((int) player.getCurrentHp());
        buffer.writeInt(player.getMaxMp());
        buffer.writeInt((int) player.getCurrentMp());
        buffer.writeInt((int) player.getSp());
        buffer.writeInt(player.getCurrentLoad());
        buffer.writeInt(player.getMaxLoad());
        buffer.writeInt(player.getPkKills());
        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollObjectId(slot));
        }

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollItemDisplayId(slot));
        }

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollAugmentationId(slot));
        }

        buffer.writeInt(player.getInventory().getTalismanSlots()); // CT2.3
        buffer.writeInt(player.getInventory().canEquipCloak()); // CT2.3
        buffer.writeInt((int) player.getPAtk(null));
        buffer.writeInt((int) player.getPAtkSpd());
        buffer.writeInt((int) player.getPDef(null));
        buffer.writeInt(player.getEvasionRate(null));
        buffer.writeInt(player.getAccuracy());
        buffer.writeInt(player.getCriticalHit(null, null));
        buffer.writeInt((int) player.getMAtk(null, null));
        buffer.writeInt(player.getMAtkSpd());
        buffer.writeInt((int) player.getPAtkSpd());
        buffer.writeInt((int) player.getMDef(null, null));
        buffer.writeInt(player.getPvpFlag()); // 0-non-pvp 1-pvp = violett name
        buffer.writeInt(player.getKarma());
        buffer.writeInt(runSpd);
        buffer.writeInt(walkSpd);
        buffer.writeInt(swimRunSpd);
        buffer.writeInt(swimWalkSpd);
        buffer.writeInt(flyRunSpd);
        buffer.writeInt(flyWalkSpd);
        buffer.writeInt(flyRunSpd);
        buffer.writeInt(flyWalkSpd);
        buffer.writeDouble(moveMultiplier);
        buffer.writeDouble(player.getAttackSpeedMultiplier()); // 2.9); //
        buffer.writeDouble(player.getCollisionRadius()); // scale
        buffer.writeDouble(player.getCollisionHeight()); // y offset ??!? fem dwarf 4033
        buffer.writeInt(appearance.getHairStyle());
        buffer.writeInt(appearance.getHairColor());
        buffer.writeInt(appearance.getFace());
        buffer.writeInt(player.isGM()); // builder level
        buffer.writeString(player.getTitle());
        buffer.writeInt(player.getClanId()); // pledge id
        buffer.writeInt(player.getClanCrestId()); // pledge crest id
        buffer.writeInt(player.getAllyId()); // ally id
        buffer.writeByte(player.getMountType().ordinal()); // mount type
        buffer.writeByte(player.getPrivateStoreType().getId());
        buffer.writeByte(player.hasDwarvenCraft());
        buffer.writeInt(player.getPkKills());
        buffer.writeInt(player.getPvpKills());
        buffer.writeShort(player.getRecomLeft());
        buffer.writeShort(player.getRecomHave()); // Blue value for name (0 = white, 255 = pure blue)
        buffer.writeInt(player.getPlayerClass().getId());
        buffer.writeInt(0); // special effects? circles around player...
        buffer.writeInt(player.getMaxCp());
        buffer.writeInt((int) player.getCurrentCp());
        buffer.writeByte(player.isRunning()); // Changes the Speed display on Status Window.
        buffer.writeByte(321);
        buffer.writeInt(player.getPledgeClass()); // Changes the text above CP on Status Window.
        buffer.writeByte(player.isNoble());
        buffer.writeByte(player.isHero());
        buffer.writeInt(appearance.getNameColor());
        buffer.writeInt(appearance.getTitleColor());
        final byte attackAttribute = player.getAttackElement();
        buffer.writeShort(attackAttribute);
        buffer.writeShort(player.getAttackElementValue(attackAttribute));
        for (byte i = 0; i < 6; i++) {
            buffer.writeShort(player.getDefenseElementValue(i));
        }

        buffer.writeInt(player.getFame());
        buffer.writeInt(player.getVitalityPoints());
    }
}
