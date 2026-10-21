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

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.actor.instance.Decoy;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.CursedWeaponsManager;
import app.l2nx.highfive.mechanics.skill.AbnormalVisualEffect;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import java.util.Map;

public class CharInfo extends ServerPacket {
    private static final int[] PAPERDOLL_ORDER = {
        Inventory.PAPERDOLL_UNDER,
        Inventory.PAPERDOLL_HEAD,
        Inventory.PAPERDOLL_RHAND,
        Inventory.PAPERDOLL_LHAND,
        Inventory.PAPERDOLL_GLOVES,
        Inventory.PAPERDOLL_CHEST,
        Inventory.PAPERDOLL_LEGS,
        Inventory.PAPERDOLL_FEET,
        Inventory.PAPERDOLL_CLOAK,
        Inventory.PAPERDOLL_RHAND,
        Inventory.PAPERDOLL_HAIR,
        Inventory.PAPERDOLL_HAIR2,
        Inventory.PAPERDOLL_RBRACELET,
        Inventory.PAPERDOLL_LBRACELET,
        Inventory.PAPERDOLL_DECO1,
        Inventory.PAPERDOLL_DECO2,
        Inventory.PAPERDOLL_DECO3,
        Inventory.PAPERDOLL_DECO4,
        Inventory.PAPERDOLL_DECO5,
        Inventory.PAPERDOLL_DECO6,
        Inventory.PAPERDOLL_BELT
    };

    private final Player player;
    private final Clan clan;
    private int objId;
    private int x;
    private int y;
    private int z;
    private int heading;
    private final int mAtkSpd;
    private final int pAtkSpd;
    private final int runSpd;
    private final int walkSpd;
    private final int swimRunSpd;
    private final int swimWalkSpd;
    private final int flyRunSpd;
    private final int flyWalkSpd;
    private final double moveMultiplier;
    private int vehicleId = 0;
    private final boolean gmSeeInvis;

    public CharInfo(Player player, boolean gmSeeInvis) {
        this.player = player;
        objId = player.getObjectId();
        clan = player.getClan();
        if ((this.player.getVehicle() != null) && (this.player.getInVehiclePosition() != null)) {
            x = this.player.getInVehiclePosition().getX();
            y = this.player.getInVehiclePosition().getY();
            z = this.player.getInVehiclePosition().getZ();
            vehicleId = this.player.getVehicle().getObjectId();
        } else {
            x = this.player.getX();
            y = this.player.getY();
            z = this.player.getZ();
        }

        heading = this.player.getHeading();
        mAtkSpd = this.player.getMAtkSpd();
        pAtkSpd = (int) this.player.getPAtkSpd();
        moveMultiplier = player.getMovementSpeedMultiplier();
        runSpd = (int) Math.round(player.getRunSpeed() / moveMultiplier);
        walkSpd = (int) Math.round(player.getWalkSpeed() / moveMultiplier);
        swimRunSpd = (int) Math.round(player.getSwimRunSpeed() / moveMultiplier);
        swimWalkSpd = (int) Math.round(player.getSwimWalkSpeed() / moveMultiplier);
        flyRunSpd = player.isFlying() ? runSpd : 0;
        flyWalkSpd = player.isFlying() ? walkSpd : 0;
        this.gmSeeInvis = gmSeeInvis;
    }

    public CharInfo(Decoy decoy, boolean gmSeeInvis) {
        this(decoy.asPlayer(), gmSeeInvis); // init
        objId = decoy.getObjectId();
        x = decoy.getX();
        y = decoy.getY();
        z = decoy.getZ();
        heading = decoy.getHeading();
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.CHAR_INFO.writeId(this, buffer);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(vehicleId);
        buffer.writeInt(objId);
        final PlayerAppearance appearance = player.getAppearance();
        buffer.writeString(appearance.getVisibleName());
        buffer.writeInt(player.getRace().ordinal());
        buffer.writeInt(appearance.isFemale());
        buffer.writeInt(player.getBaseClass());

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollItemDisplayId(slot));
        }

        for (int slot : getPaperdollOrder()) {
            buffer.writeInt(player.getInventory().getPaperdollAugmentationId(slot));
        }

        buffer.writeInt(player.getInventory().getTalismanSlots());
        buffer.writeInt(player.getInventory().canEquipCloak());
        buffer.writeInt(player.getPvpFlag());
        buffer.writeInt(player.getKarma());
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
        buffer.writeDouble(player.getAttackSpeedMultiplier());
        buffer.writeDouble(player.getCollisionRadius());
        buffer.writeDouble(player.getCollisionHeight());
        buffer.writeInt(appearance.getHairStyle());
        buffer.writeInt(appearance.getHairColor());
        buffer.writeInt(appearance.getFace());
        buffer.writeString(gmSeeInvis ? "Invisible" : appearance.getVisibleTitle());
        if (!player.isCursedWeaponEquipped()) {
            buffer.writeInt(player.getClanId());
            buffer.writeInt(player.getClanCrestId());
            buffer.writeInt(player.getAllyId());
            buffer.writeInt(player.getAllyCrestId());
        } else {
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
        }

        buffer.writeByte(!player.isSitting()); // standing = 1 sitting = 0
        buffer.writeByte(player.isRunning()); // running = 1 walking = 0
        buffer.writeByte(player.isInCombat());
        buffer.writeByte(!player.isInOlympiadMode() && player.isAlikeDead());
        buffer.writeByte(!gmSeeInvis && player.isInvisible()); // invisible = 1 visible =0
        buffer.writeByte(player.getMountType().ordinal()); // 1-on Strider, 2-on Wyvern, 3-on Great Wolf, 0-no mount
        buffer.writeByte(player.getPrivateStoreType().getId());

        final Map<Integer, Cubic> cubics = player.getCubics();
        buffer.writeShort(cubics.size());
        for (int cubicId : cubics.keySet()) {
            buffer.writeShort(cubicId);
        }

        buffer.writeByte(player.isInPartyMatchRoom());
        buffer.writeInt(
                gmSeeInvis
                        ? (player.getAbnormalVisualEffects() | AbnormalVisualEffect.STEALTH.getMask())
                        : player.getAbnormalVisualEffects());
        buffer.writeByte(player.isInsideZone(ZoneId.WATER) ? 1 : player.isFlyingMounted() ? 2 : 0);
        buffer.writeShort(player.getRecomHave()); // Blue value for name (0 = white, 255 = pure blue)
        buffer.writeInt(player.getMountNpcId() + 1000000);
        buffer.writeInt(player.getPlayerClass().getId());
        buffer.writeInt(0); // ?
        buffer.writeByte(player.isMounted() ? 0 : player.getEnchantEffect());
        buffer.writeByte(player.getTeam().getId());
        buffer.writeInt(player.getClanCrestLargeId());
        buffer.writeByte(player.isNoble()); // Symbol on char menu ctrl+I
        buffer.writeByte(player.isHero() || (player.isGM() && GeneralConfig.GM_HERO_AURA)); // Hero Aura

        buffer.writeByte(player.isFishing()); // 1: Fishing Mode (Cannot be undone by setting back to 0)
        buffer.writeInt(player.getFishX());
        buffer.writeInt(player.getFishY());
        buffer.writeInt(player.getFishZ());

        buffer.writeInt(appearance.getNameColor());
        buffer.writeInt(heading);
        buffer.writeInt(player.getPledgeClass());
        buffer.writeInt(player.getPledgeType());
        buffer.writeInt(appearance.getTitleColor());
        buffer.writeInt(
                player.isCursedWeaponEquipped()
                        ? CursedWeaponsManager.getInstance().getLevel(player.getCursedWeaponEquippedId())
                        : 0);
        buffer.writeInt(clan != null ? clan.getReputationScore() : 0);

        // T1
        buffer.writeInt(player.getTransformationDisplayId());
        buffer.writeInt(player.getAgathionId());

        // T2
        buffer.writeInt(1);

        // T2.3
        buffer.writeInt(player.getAbnormalVisualEffectSpecial());
    }

    @Override
    public int[] getPaperdollOrder() {
        return PAPERDOLL_ORDER;
    }
}
