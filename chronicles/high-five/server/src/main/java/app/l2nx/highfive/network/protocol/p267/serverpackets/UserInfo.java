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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.skill.AbnormalVisualEffect;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Map;

public class UserInfo extends ServerPacket {
    private final Player player;
    private int relation;
    private int airShipHelm;
    private final int runSpd;
    private final int walkSpd;
    private final int swimRunSpd;
    private final int swimWalkSpd;
    private final int flyRunSpd;
    private final int flyWalkSpd;
    private final double moveMultiplier;

    public UserInfo(Player player) {
        this.player = player;
        final int territoryId = TerritoryWarManager.getInstance().getRegisteredTerritoryId(player);
        relation = this.player.isClanLeader() ? 0x40 : 0;
        if (this.player.getSiegeState() == 1) {
            if (territoryId == 0) {
                relation |= 0x180;
            } else {
                relation |= 0x1000;
            }
        }

        if (this.player.getSiegeState() == 2) {
            relation |= 0x80;
        }
        // _isDisguised = TerritoryWarManager.getInstance().isDisguised(character.getObjectId());
        if (this.player.isInAirShip() && this.player.getAirShip().isCaptain(this.player)) {
            airShipHelm = this.player.getAirShip().getHelmItemId();
        } else {
            airShipHelm = 0;
        }

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
        ServerPackets.USER_INFO.writeId(this, buffer);
        buffer.writeInt(player.getX());
        buffer.writeInt(player.getY());
        buffer.writeInt(player.getZ());
        buffer.writeInt(player.getVehicle() != null ? player.getVehicle().getObjectId() : 0);
        buffer.writeInt(player.getObjectId());
        final PlayerAppearance appearance = player.getAppearance();
        buffer.writeString(appearance.getVisibleName());
        buffer.writeInt(player.getRace().ordinal());
        buffer.writeInt(appearance.isFemale());
        buffer.writeInt(player.getBaseClass());
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
        buffer.writeInt((int) Math.round(player.getCurrentHp()));
        buffer.writeInt(player.getMaxMp());
        buffer.writeInt((int) Math.round(player.getCurrentMp()));
        buffer.writeInt((int) player.getSp());
        buffer.writeInt(player.getCurrentLoad());
        buffer.writeInt(player.getMaxLoad());
        buffer.writeInt(player.getActiveWeaponItem() != null ? 40 : 20); // 20 no weapon, 40 weapon equipped

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollObjectId(slot));
        }

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollItemDisplayId(slot));
        }

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollAugmentationId(slot));
        }

        buffer.writeInt(player.getInventory().getTalismanSlots());
        buffer.writeInt(player.getInventory().canEquipCloak());
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
        buffer.writeInt(player.getPvpFlag());
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
        buffer.writeDouble(player.getAttackSpeedMultiplier());
        buffer.writeDouble(player.getCollisionRadius());
        buffer.writeDouble(player.getCollisionHeight());
        buffer.writeInt(appearance.getHairStyle());
        buffer.writeInt(appearance.getHairColor());
        buffer.writeInt(appearance.getFace());
        buffer.writeInt(player.isGM()); // builder level

        String title = player.getTitle();
        if (player.isGM() && player.isInvisible()) {
            title = "[Invisible]";
        }

        buffer.writeString(title);

        buffer.writeInt(player.getClanId());
        buffer.writeInt(player.getClanCrestId());
        buffer.writeInt(player.getAllyId());
        buffer.writeInt(player.getAllyCrestId()); // ally crest id

        // 0x40 leader rights
        // siege flags: attacker - 0x180 sword over name, defender - 0x80 shield, 0xC0 crown (|leader), 0x1C0 flag
        // (|leader)
        buffer.writeInt(relation);
        buffer.writeByte(player.getMountType().ordinal()); // mount type
        buffer.writeByte(player.getPrivateStoreType().getId());
        buffer.writeByte(player.hasDwarvenCraft());
        buffer.writeInt(player.getPkKills());
        buffer.writeInt(player.getPvpKills());

        final Map<Integer, Cubic> cubics = player.getCubics();
        buffer.writeShort(cubics.size());
        for (int cubicId : cubics.keySet()) {
            buffer.writeShort(cubicId);
        }

        buffer.writeByte(player.isInPartyMatchRoom());
        buffer.writeInt(
                player.isInvisible()
                        ? player.getAbnormalVisualEffects() | AbnormalVisualEffect.STEALTH.getMask()
                        : player.getAbnormalVisualEffects());
        buffer.writeByte(player.isInsideZone(ZoneId.WATER) ? 1 : player.isFlyingMounted() ? 2 : 0);
        buffer.writeInt(player.getClanPrivileges().getMask());
        buffer.writeShort(player.getRecomLeft()); // c2 recommendations remaining
        buffer.writeShort(player.getRecomHave()); // c2 recommendations received
        buffer.writeInt(player.getMountNpcId() > 0 ? player.getMountNpcId() + 1000000 : 0);
        buffer.writeShort(player.getInventoryLimit());
        buffer.writeInt(player.getPlayerClass().getId());
        buffer.writeInt(0); // special effects? circles around player...
        buffer.writeInt(player.getMaxCp());
        buffer.writeInt((int) player.getCurrentCp());
        buffer.writeByte(player.isMounted() || (airShipHelm != 0) ? 0 : player.getEnchantEffect());
        buffer.writeByte(player.getTeam().getId());
        buffer.writeInt(player.getClanCrestLargeId());
        buffer.writeByte(player.isNoble()); // 1: symbol on char menu ctrl+I
        buffer.writeByte(player.isHero() || (player.isGM() && GeneralConfig.GM_HERO_AURA)); // 1: Hero Aura

        buffer.writeByte(player.isFishing()); // Fishing Mode
        buffer.writeInt(player.getFishX()); // fishing x
        buffer.writeInt(player.getFishY()); // fishing y
        buffer.writeInt(player.getFishZ()); // fishing z

        buffer.writeInt(appearance.getNameColor());

        // new c5
        buffer.writeByte(player.isRunning()); // changes the Speed display on Status Window
        buffer.writeInt(player.getPledgeClass()); // changes the text above CP on Status Window
        buffer.writeInt(player.getPledgeType());
        buffer.writeInt(appearance.getTitleColor());
        buffer.writeInt(
                player.isCursedWeaponEquipped()
                        ? CursedWeaponsManager.getInstance().getLevel(player.getCursedWeaponEquippedId())
                        : 0);

        // T1 Starts
        buffer.writeInt(player.getTransformationDisplayId());
        final byte attackAttribute = player.getAttackElement();
        buffer.writeShort(attackAttribute);
        buffer.writeShort(player.getAttackElementValue(attackAttribute));
        buffer.writeShort(player.getDefenseElementValue(Elementals.FIRE));
        buffer.writeShort(player.getDefenseElementValue(Elementals.WATER));
        buffer.writeShort(player.getDefenseElementValue(Elementals.WIND));
        buffer.writeShort(player.getDefenseElementValue(Elementals.EARTH));
        buffer.writeShort(player.getDefenseElementValue(Elementals.HOLY));
        buffer.writeShort(player.getDefenseElementValue(Elementals.DARK));
        buffer.writeInt(player.getAgathionId());

        // T2 Starts
        buffer.writeInt(player.getFame()); // Fame
        buffer.writeInt(player.isMinimapAllowed()); // Minimap on Hellbound
        buffer.writeInt(player.getVitalityPoints()); // Vitality Points
        buffer.writeInt(player.getAbnormalVisualEffectSpecial());
        // buffer.writeInt(territoryId); // CT2.3
        // buffer.writeInt((_isDisguised ? 1: 0)); // CT2.3
        // buffer.writeInt(territoryId); // CT2.3
    }
}
