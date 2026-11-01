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
package app.l2nx.highfive.entity.actor.stat;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.data.xml.PetDataTable;
import app.l2nx.highfive.entity.actor.Playable;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.type.SwampZone;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.playable.OnPlayableExpChanged;
import app.l2nx.highfive.mechanics.events.returns.TerminateReturn;
import app.l2nx.highfive.partition.WorldPartition;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class PlayableStat extends CreatureStat {

    public PlayableStat(Playable activeChar, CreatureAccess access) {
        super(activeChar, access);
    }

    public boolean addExp(long amount) {
        WorldPartition.checkOwner();
        final Playable playable = playable();
        if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYABLE_EXP_CHANGED, playable)) {
            final TerminateReturn term = EventDispatcher.getInstance()
                    .notifyEvent(
                            OnPlayableExpChanged.builder()
                                    .activeChar(playable)
                                    .oldExp(getExp())
                                    .newExp(getExp() + amount)
                                    .build(),
                            playable,
                            TerminateReturn.class);
            if ((term != null) && term.terminate()) {
                return false;
            }
        }

        if (((getExp() + amount) < 0) || ((amount > 0) && (getExp() == (getExpForLevel(getMaxLevel()) - 1)))) {
            return true;
        }

        long value = amount;
        if ((getExp() + value) >= getExpForLevel(getMaxLevel())) {
            value = getExpForLevel(getMaxLevel()) - 1 - getExp();
        }

        setExp(getExp() + value);
        byte minimumLevel = 1;
        if (playable.isPet()) {
            minimumLevel = (byte) PetDataTable.getInstance()
                    .getPetMinLevel(playable.asPet().getTemplate().getId());
        }

        byte level = minimumLevel;
        for (byte tmp = level; tmp <= getMaxLevel(); tmp++) {
            if (getExp() >= getExpForLevel(tmp)) {
                continue;
            }

            level = --tmp;
            break;
        }

        if ((level != getLevel()) && (level >= minimumLevel)) {
            addLevel((byte) (level - getLevel()));
        }

        return true;
    }

    public boolean removeExp(long amount) {
        WorldPartition.checkOwner();
        long value = amount;
        if ((getExp() - value) < 0) {
            value = getExp() - 1;
        }

        setExp(getExp() - value);
        byte minimumLevel = 1;
        final Playable playable = playable();
        if (playable.isPet()) {
            minimumLevel = (byte) PetDataTable.getInstance()
                    .getPetMinLevel(playable.asPet().getTemplate().getId());
        }

        byte level = minimumLevel;
        for (byte tmp = level; tmp <= getMaxLevel(); tmp++) {
            if (getExp() >= getExpForLevel(tmp)) {
                continue;
            }

            level = --tmp;
            break;
        }

        if ((level != getLevel()) && (level >= minimumLevel)) {
            addLevel((byte) (level - getLevel()));
        }

        return true;
    }

    public boolean removeExpAndSp(long removeExp, long removeSp) {
        WorldPartition.checkOwner();
        boolean expRemoved = false;
        boolean spRemoved = false;
        if (removeExp > 0) {
            expRemoved = removeExp(removeExp);
        }

        if (removeSp > 0) {
            spRemoved = removeSp(removeSp);
        }

        return expRemoved || spRemoved;
    }

    public boolean addLevel(byte amount) {
        WorldPartition.checkOwner();
        byte value = amount;
        if ((getLevel() + value) > (getMaxLevel() - 1)) {
            if (getLevel() < (getMaxLevel() - 1)) {
                value = (byte) (getMaxLevel() - 1 - getLevel());
            } else {
                return false;
            }
        }

        final boolean levelIncreased = (getLevel() + value) > getLevel();
        value += getLevel();
        setLevel(value);

        if ((getExp() >= getExpForLevel(getLevel() + 1)) || (getExpForLevel(getLevel()) > getExp())) {
            setExp(getExpForLevel(getLevel()));
        }

        final Playable playable = playable();
        if (!levelIncreased && playable.isPlayer() && !playable.isGM() && PlayerConfig.DECREASE_SKILL_LEVEL) {
            playable.asPlayer().checkPlayerSkills();
        }

        if (!levelIncreased) {
            return false;
        }

        playable.getStatus().setCurrentHp(playable.getStat().getMaxHp());
        playable.getStatus().setCurrentMp(playable.getStat().getMaxMp());

        return true;
    }

    public boolean addSp(long amount) {
        WorldPartition.checkOwner();
        if (amount < 0) {
            log.warn("wrong usage");
            return false;
        }

        final long currentSp = getSp();
        if (currentSp >= PlayerConfig.MAX_SP) {
            return false;
        }

        long value = amount;
        if (currentSp > (PlayerConfig.MAX_SP - value)) {
            value = PlayerConfig.MAX_SP - currentSp;
        }

        setSp(currentSp + value);
        return true;
    }

    public boolean removeSp(long amount) {
        WorldPartition.checkOwner();
        final long currentSp = getSp();
        if (currentSp < amount) {
            setSp(getSp() - currentSp);
            return true;
        }

        setSp(getSp() - amount);
        return true;
    }

    public long getExpForLevel(int level) {
        WorldPartition.checkOwner();
        return ExperienceData.getInstance().getExpForLevel(level);
    }

    @Override
    protected double runSpeed() {
        final Playable playable = playable();
        if (playable.isInsideZone(ZoneId.SWAMP)) {
            final SwampZone zone = ZoneManager.getInstance().getZone(playable, SwampZone.class);
            if (zone != null) {
                return super.runSpeed() * zone.getMoveBonus();
            }
        }

        return super.runSpeed();
    }

    @Override
    protected double walkSpeed() {
        final Playable playable = playable();
        if (playable.isInsideZone(ZoneId.SWAMP)) {
            final SwampZone zone = ZoneManager.getInstance().getZone(playable, SwampZone.class);
            if (zone != null) {
                return super.walkSpeed() * zone.getMoveBonus();
            }
        }

        return super.walkSpeed();
    }

    @Override
    public Playable getActiveChar() {
        WorldPartition.checkOwner();
        return playable();
    }

    public int getMaxLevel() {
        WorldPartition.checkOwner();
        return ExperienceData.getInstance().getMaxLevel();
    }

    @Override
    protected int physicalAttackAngle() {
        final Weapon weapon = playable().getActiveWeaponItem();
        return weapon != null ? weapon.getBaseAttackAngle() : super.physicalAttackAngle();
    }

    private Playable playable() {
        return (Playable) creature();
    }
}
