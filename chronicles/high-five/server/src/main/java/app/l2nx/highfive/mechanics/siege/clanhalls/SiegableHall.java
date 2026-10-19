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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.entity.zone.type.SiegableHallZone;
import app.l2nx.highfive.entity.zone.type.SiegeZone;
import app.l2nx.highfive.mechanics.siege.SiegeClan;
import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import app.l2nx.highfive.network.serverpackets.SiegeInfo;
import app.l2nx.highfive.util.StatSet;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Calendar;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author BiggBoss
 */
@Slf4j
public class SiegableHall extends ClanHall {
    private static final String SQL_SAVE = "UPDATE siegable_clanhall SET ownerId=?, nextSiege=? WHERE clanHallId=?";

    @Getter
    private Calendar siegeDate;

    @Getter
    private final long siegeLength;

    private final int[] scheduleConfig = {7, 0, 0, 12, 0};

    @Getter
    private SiegeStatus siegeStatus = SiegeStatus.REGISTERING;

    @Getter
    @Setter
    private SiegeZone siegeZone;

    @Getter
    private ClanHallSiegeEngine siege;

    public SiegableHall(StatSet set) {
        super(set);
        siegeLength = set.getLong("siegeLength");
        final String[] rawSchConfig = set.getString("scheduleConfig").split(";");
        if (rawSchConfig.length == 5) {
            for (int i = 0; i < 5; i++) {
                try {
                    scheduleConfig[i] = Integer.parseInt(rawSchConfig[i]);
                } catch (Exception e) {
                    log.warn("{}: Wrong schedule_config parameters!", getName());
                }
            }
        } else {
            log.warn("{}: Wrong schedule_config value in siegable_halls table, using default (7 days)", getName());
        }

        siegeDate = Calendar.getInstance();
        final long nextSiege = set.getLong("nextSiege");
        if ((nextSiege - System.currentTimeMillis() - 3600000) < 0) {
            updateNextSiege();
        } else {
            siegeDate.setTimeInMillis(nextSiege);
        }

        if (getOwnerId() != 0) {
            free = false;
            loadFunctions();
        }
    }

    public void spawnDoor() {
        spawnDoor(false);
    }

    public void spawnDoor(boolean isDoorWeak) {
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
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement ps = con.prepareStatement(SQL_SAVE)) {
            ps.setInt(1, getOwnerId());
            ps.setLong(2, getNextSiegeTime());
            ps.setInt(3, getId());
            ps.execute();
        } catch (Exception e) {
            log.warn("Exception: SiegableHall.updateDb(): {}", e.getMessage(), e);
        }
    }

    public void setSiege(ClanHallSiegeEngine siegable) {
        siege = siegable;
        siegeZone.setSiegeInstance(siegable);
    }

    public long getNextSiegeTime() {
        return siegeDate.getTimeInMillis();
    }

    public void setNextSiegeDate(long date) {
        siegeDate.setTimeInMillis(date);
    }

    public void setNextSiegeDate(Calendar c) {
        siegeDate = c;
    }

    public void updateNextSiege() {
        final Calendar callendar = Calendar.getInstance();
        callendar.add(Calendar.DAY_OF_YEAR, scheduleConfig[0]);
        callendar.add(Calendar.MONTH, scheduleConfig[1]);
        callendar.add(Calendar.YEAR, scheduleConfig[2]);
        callendar.set(Calendar.HOUR_OF_DAY, scheduleConfig[3]);
        callendar.set(Calendar.MINUTE, scheduleConfig[4]);
        callendar.set(Calendar.SECOND, 0);
        setNextSiegeDate(callendar);
        updateDb();
    }

    public void addAttacker(Clan clan) {
        if (siege != null) {
            siege.getAttackers().put(clan.getId(), new SiegeClan(clan.getId(), SiegeClanType.ATTACKER));
        }
    }

    public void removeAttacker(Clan clan) {
        if (siege != null) {
            siege.getAttackers().remove(clan.getId());
        }
    }

    public boolean isRegistered(Clan clan) {
        return (siege != null) && siege.checkIsAttacker(clan);
    }

    public boolean isRegistering() {
        return siegeStatus == SiegeStatus.REGISTERING;
    }

    public boolean isInSiege() {
        return siegeStatus == SiegeStatus.RUNNING;
    }

    public boolean isWaitingBattle() {
        return siegeStatus == SiegeStatus.WAITING_BATTLE;
    }

    public void updateSiegeStatus(SiegeStatus status) {
        siegeStatus = status;
    }

    public void updateSiegeZone(boolean active) {
        siegeZone.setActive(active);
    }

    public void showSiegeInfo(Player player) {
        player.sendPacket(new SiegeInfo(this, player));
    }

    @Override
    public boolean isSiegableHall() {
        return true;
    }

    @Override
    public SiegableHallZone getZone() {
        return (SiegableHallZone) super.getZone();
    }
}
