/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
package app.l2nx.highfive.mechanics.siege.clanhalls;

import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.zone.type.SiegableHallZone;
import app.l2nx.highfive.entity.zone.type.SiegeZone;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SiegeInfo;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.residence.model.SiegableClanHallState;
import app.l2nx.highfive.util.StatSet;
import java.util.Calendar;

/**
 * @author BiggBoss
 */
@SingleWriter
public class SiegableHall extends ClanHall {
    private Calendar siegeDate;

    private final long siegeLength;

    // Days, months and years to the next siege, then its hour and minute.
    private final int[] scheduleConfig;

    private SiegeStatus siegeStatus = SiegeStatus.REGISTERING;

    private SiegeZone siegeZone;

    private ClanHallSiegeEngine siege;

    public SiegableHall(StatSet set) {
        super(set);
        siegeLength = set.getLong("siegeLength");
        scheduleConfig = new int[] {
            set.getInt("scheduleDays"),
            set.getInt("scheduleMonths"),
            set.getInt("scheduleYears"),
            set.getInt("siegeHour"),
            set.getInt("siegeMinute")
        };

        siegeDate = ClockBridge.calendar();
        final long nextSiege = set.getLong("nextSiege");
        if ((nextSiege - ClockBridge.wallMillis() - 3600000) < 0) {
            updateNextSiege();
        } else {
            siegeDate.setTimeInMillis(nextSiege);
        }

        if (!free) {
            loadFunctions();
        }
    }

    /**
     * @return a copy of the next siege date; {@link #setNextSiegeDate} changes it
     */
    public Calendar getSiegeDate() {
        WorldPartition.checkOwner();
        return (Calendar) siegeDate.clone();
    }

    public long getSiegeLength() {
        WorldPartition.checkOwner();
        return siegeLength;
    }

    public SiegeStatus getSiegeStatus() {
        WorldPartition.checkOwner();
        return siegeStatus;
    }

    public SiegeZone getSiegeZone() {
        WorldPartition.checkOwner();
        return siegeZone;
    }

    public void setSiegeZone(SiegeZone siegeZone) {
        WorldPartition.checkOwner();
        this.siegeZone = siegeZone;
    }

    public ClanHallSiegeEngine getSiege() {
        WorldPartition.checkOwner();
        return siege;
    }

    public void spawnDoor() {
        WorldPartition.checkOwner();
        spawnDoor(false);
    }

    public void spawnDoor(boolean isDoorWeak) {
        WorldPartition.checkOwner();
        for (Door door : getDoors()) {
            if (door.isDead()) {
                door.doRevive();
                if (isDoorWeak) {
                    door.setCurrentHp(door.getMaxHp() / 2);
                } else {
                    door.setCurrentHp(door.getMaxHp());
                }
            }

            if (door.isOpen()) {
                door.closeMe();
            }
        }
    }

    @Override
    public void updateDb() {
        WorldPartition.checkOwner();
        CHSiegeManager.getInstance()
                .saveState(SiegableClanHallState.builder()
                        .clanHallId(getId())
                        .ownerClanId(getOwnerId())
                        .nextSiege(instantOrNull(getNextSiegeTime()))
                        .build());
    }

    public void setSiege(ClanHallSiegeEngine siegable) {
        WorldPartition.checkOwner();
        siege = siegable;
        siegeZone.setSiegeInstance(siegable);
    }

    public long getNextSiegeTime() {
        WorldPartition.checkOwner();
        return siegeDate.getTimeInMillis();
    }

    public void setNextSiegeDate(long date) {
        WorldPartition.checkOwner();
        siegeDate.setTimeInMillis(date);
    }

    public void updateNextSiege() {
        WorldPartition.checkOwner();
        final Calendar callendar = ClockBridge.calendar();
        callendar.add(Calendar.DAY_OF_YEAR, scheduleConfig[0]);
        callendar.add(Calendar.MONTH, scheduleConfig[1]);
        callendar.add(Calendar.YEAR, scheduleConfig[2]);
        callendar.set(Calendar.HOUR_OF_DAY, scheduleConfig[3]);
        callendar.set(Calendar.MINUTE, scheduleConfig[4]);
        callendar.set(Calendar.SECOND, 0);
        siegeDate.setTimeInMillis(callendar.getTimeInMillis());
        updateDb();
    }

    public void addAttacker(Clan clan) {
        WorldPartition.checkOwner();
        if (siege != null) {
            siege.getAttackers().put(clan.getId(), new SiegeClan(clan.getId(), SiegeClanType.ATTACKER));
        }
    }

    public void removeAttacker(Clan clan) {
        WorldPartition.checkOwner();
        if (siege != null) {
            siege.getAttackers().remove(clan.getId());
        }
    }

    public boolean isRegistered(Clan clan) {
        WorldPartition.checkOwner();
        return (siege != null) && siege.checkIsAttacker(clan);
    }

    public boolean isRegistering() {
        WorldPartition.checkOwner();
        return siegeStatus == SiegeStatus.REGISTERING;
    }

    public boolean isInSiege() {
        WorldPartition.checkOwner();
        return siegeStatus == SiegeStatus.RUNNING;
    }

    public boolean isWaitingBattle() {
        WorldPartition.checkOwner();
        return siegeStatus == SiegeStatus.WAITING_BATTLE;
    }

    public void updateSiegeStatus(SiegeStatus status) {
        WorldPartition.checkOwner();
        siegeStatus = status;
    }

    public void updateSiegeZone(boolean active) {
        WorldPartition.checkOwner();
        siegeZone.setActive(active);
    }

    public void showSiegeInfo(Player player) {
        WorldPartition.checkOwner();
        player.sendPacket(new SiegeInfo(this, player));
    }

    @Override
    public boolean isSiegableHall() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public SiegableHallZone getZone() {
        WorldPartition.checkOwner();
        return (SiegableHallZone) super.getZone();
    }
}
