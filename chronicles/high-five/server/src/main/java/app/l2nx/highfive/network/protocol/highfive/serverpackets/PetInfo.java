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

import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.instance.Pet;
import app.l2nx.highfive.entity.actor.instance.Servitor;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;

public class PetInfo extends ServerPacket {
    private final Summon summon;
    private final int x;
    private final int y;
    private final int z;
    private final int heading;
    private final boolean summoned;
    private final int value;
    private final int mAtkSpd;
    private final int pAtkSpd;
    private final int runSpd;
    private final int walkSpd;
    private final int swimRunSpd;
    private final int swimWalkSpd;
    private final int flyRunSpd;
    private final int flyWalkSpd;
    private final double moveMultiplier;
    private final int maxHp;
    private final int maxMp;
    private int maxFed;
    private int curFed;

    public PetInfo(Summon summon, int value) {
        this.summon = summon;
        summoned = summon.isShowSummonAnimation();
        x = summon.getX();
        y = summon.getY();
        z = summon.getZ();
        heading = summon.getHeading();
        mAtkSpd = summon.getMAtkSpd();
        pAtkSpd = (int) summon.getPAtkSpd();
        moveMultiplier = summon.getMovementSpeedMultiplier();
        runSpd = (int) Math.round(summon.getRunSpeed() / moveMultiplier);
        walkSpd = (int) Math.round(summon.getWalkSpeed() / moveMultiplier);
        swimRunSpd = (int) Math.round(summon.getSwimRunSpeed() / moveMultiplier);
        swimWalkSpd = (int) Math.round(summon.getSwimWalkSpeed() / moveMultiplier);
        flyRunSpd = summon.isFlying() ? runSpd : 0;
        flyWalkSpd = summon.isFlying() ? walkSpd : 0;
        maxHp = summon.getMaxHp();
        maxMp = summon.getMaxMp();
        this.value = value;
        if (summon.isPet()) {
            final Pet pet = this.summon.asPet();
            curFed = pet.getCurrentFed(); // how fed it is
            maxFed = pet.getMaxFed(); // max fed it can be
        } else if (summon.isServitor()) {
            final Servitor sum = this.summon.asServitor();
            curFed = sum.getLifeTimeRemaining();
            maxFed = sum.getLifeTime();
        }
    }

    @Override
    public void writeImpl(GameClient client, PacketWriter buffer) {
        ServerPackets.PET_INFO.writeId(this, buffer);
        buffer.writeInt(summon.getSummonType());
        buffer.writeInt(summon.getObjectId());
        buffer.writeInt(summon.getTemplate().getDisplayId() + 1000000);
        buffer.writeInt(0); // 1=attackable
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeInt(heading);
        buffer.writeInt(0);
        buffer.writeInt(mAtkSpd);
        buffer.writeInt(pAtkSpd);
        buffer.writeInt(runSpd);
        buffer.writeInt(walkSpd);
        buffer.writeInt(swimRunSpd);
        buffer.writeInt(swimWalkSpd);
        buffer.writeInt(flyRunSpd);
        buffer.writeInt(flyWalkSpd);
        buffer.writeInt(flyRunSpd);
        buffer.writeInt(flyWalkSpd);
        buffer.writeDouble(moveMultiplier);
        buffer.writeDouble(summon.getAttackSpeedMultiplier()); // attack speed multiplier
        buffer.writeDouble(summon.getTemplate().getFCollisionRadius());
        buffer.writeDouble(summon.getTemplate().getFCollisionHeight());
        buffer.writeInt(summon.getWeapon()); // right hand weapon
        buffer.writeInt(summon.getArmor()); // body armor
        buffer.writeInt(0); // left hand weapon
        buffer.writeByte(
                summon.getOwner() != null); // when pet is dead and player exit game, pet doesn't show master name
        buffer.writeByte(summon.isRunning()); // running=1 (it is always 1, walking mode is calculated from multiplier)
        buffer.writeByte(summon.isInCombat()); // attacking 1=true
        buffer.writeByte(summon.isAlikeDead()); // dead 1=true
        buffer.writeByte(summoned ? 2 : value); // 0=teleported 1=default 2=summoned
        buffer.writeInt(-1); // High Five NPCString ID
        if (summon.isPet()) {
            buffer.writeString(summon.getName()); // Pet name.
        } else {
            buffer.writeString(summon.getTemplate().isUsingServerSideName() ? summon.getName() : ""); // Summon name.
        }

        buffer.writeInt(-1); // High Five NPCString ID
        buffer.writeString(summon.getTitle()); // owner name
        buffer.writeInt(1);
        buffer.writeInt(summon.getPvpFlag()); // 0 = white,2= purpleblink, if it is greater then karma = purple
        buffer.writeInt(summon.getKarma()); // karma
        buffer.writeInt(curFed); // how fed it is
        buffer.writeInt(maxFed); // max fed it can be
        buffer.writeInt((int) summon.getCurrentHp()); // current hp
        buffer.writeInt(maxHp); // max hp
        buffer.writeInt((int) summon.getCurrentMp()); // current mp
        buffer.writeInt(maxMp); // max mp
        buffer.writeInt((int) summon.getStat().getSp()); // sp
        buffer.writeInt(summon.getLevel()); // level
        buffer.writeLong(summon.getStat().getExp());
        if (summon.getExpForThisLevel() > summon.getStat().getExp()) {
            buffer.writeLong(summon.getStat().getExp()); // 0% absolute value
        } else {
            buffer.writeLong(summon.getExpForThisLevel()); // 0% absolute value
        }

        buffer.writeLong(summon.getExpForNextLevel()); // 100% absoulte value
        buffer.writeInt(summon.isPet() ? summon.getInventory().getTotalWeight() : 0); // weight
        buffer.writeInt(summon.getMaxLoad()); // max weight it can carry
        buffer.writeInt((int) summon.getPAtk(null)); // patk
        buffer.writeInt((int) summon.getPDef(null)); // pdef
        buffer.writeInt((int) summon.getMAtk(null, null)); // matk
        buffer.writeInt((int) summon.getMDef(null, null)); // mdef
        buffer.writeInt(summon.getAccuracy()); // accuracy
        buffer.writeInt(summon.getEvasionRate(null)); // evasion
        buffer.writeInt(summon.getCriticalHit(null, null)); // critical
        buffer.writeInt((int) summon.getMoveSpeed()); // speed
        buffer.writeInt((int) summon.getPAtkSpd()); // atkspeed
        buffer.writeInt(summon.getMAtkSpd()); // casting speed
        buffer.writeInt(summon.getAbnormalVisualEffects()); // c2 abnormal visual effect... bleed=1; poison=2; poison &
        // bleed=3; flame=4;
        buffer.writeShort(summon.isMountable()); // c2 ride button
        buffer.writeByte(summon.isInsideZone(ZoneId.WATER) ? 1 : summon.isFlying() ? 2 : 0); // c2

        // Following all added in C4.
        buffer.writeShort(0); // ??
        buffer.writeByte(summon.getTeam().getId());
        buffer.writeInt(summon.getSoulShotsPerHit()); // How many soulshots this servitor uses per hit
        buffer.writeInt(summon.getSpiritShotsPerHit()); // How many spiritshots this servitor uses per hit
        buffer.writeInt(summon.getFormId()); // CT1.5 Pet form and skills
        buffer.writeInt(summon.getAbnormalVisualEffectSpecial());
    }
}
