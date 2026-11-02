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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.WorldLoader;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Attackable;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.zone.AbstractZoneSettings;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.GrandBossManager;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.List;

/**
 * @author DaRkRaGe
 */
@SingleWriter
public class BossZone extends ZoneType {
    private int timeInvade;

    private final int[] oustLoc = new int[3];

    @SingleWriter
    public static final class Settings extends AbstractZoneSettings {
        // Entry deadline in game millis per player objectId, set on logout or by allowPlayerEntry.
        private final Int2LongOpenHashMap playerAllowedReEntryTimes = new Int2LongOpenHashMap();

        // Admitted players in admission order; they may also re-enter after a server restart within timeInvade.
        private final IntLinkedOpenHashSet playersAllowed = new IntLinkedOpenHashSet();

        Settings() {}

        @Override
        public void clear() {
            WorldPartition.checkOwner();
            playerAllowedReEntryTimes.clear();
            playersAllowed.clear();
        }
    }

    public BossZone(int id) {
        super(id, new Settings());
        GrandBossManager.getInstance().addZone(this);
    }

    public int getTimeInvade() {
        WorldPartition.checkOwner();
        return timeInvade;
    }

    @Override
    public Settings getSettings() {
        WorldPartition.checkOwner();
        return settings();
    }

    private Settings settings() {
        return (Settings) currentSettings();
    }

    @Override
    public void setParameter(String name, String value) {
        WorldPartition.checkOwner();
        if (name.equals("InvadeTime")) {
            timeInvade = Integer.parseInt(value);
        } else if (name.equals("oustX")) {
            oustLoc[0] = Integer.parseInt(value);
        } else if (name.equals("oustY")) {
            oustLoc[1] = Integer.parseInt(value);
        } else if (name.equals("oustZ")) {
            oustLoc[2] = Integer.parseInt(value);
        } else {
            super.setParameter(name, value);
        }
    }

    /**
     * Players without an unexpired entry time are ousted; it is set on logout (logoutTime + timeInvade) or by
     * {@link #allowPlayerEntry}. A player admitted before a restart may re-enter within timeInvade of boot.
     */
    @Override
    protected void onEnter(Creature creature) {
        final Settings settings = settings();
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            if (player.isGM()) {
                return;
            }

            final int objectId = player.getObjectId();
            if (settings.playersAllowed.contains(objectId)) {
                if (!settings.playerAllowedReEntryTimes.containsKey(objectId)) {
                    if (WorldLoader.getStartTime() > (ClockBridge.gameMillis() - timeInvade)) {
                        return;
                    }
                } else {
                    final long expirationTime = settings.playerAllowedReEntryTimes.remove(objectId);
                    if (expirationTime > ClockBridge.gameMillis()) {
                        return;
                    }
                }

                settings.playersAllowed.remove(objectId);
            }

            oust(player);
        } else if (creature.isSummon()) {
            final Player player = creature.asPlayer();
            if (player != null) {
                if (settings.playersAllowed.contains(player.getObjectId()) || player.isGM()) {
                    return;
                }

                oust(player);
            }

            creature.asSummon().unSummon(player);
        }
    }

    @Override
    protected void onExit(Creature creature) {
        final Settings settings = settings();
        if (creature.isPlayer()) {
            final Player player = creature.asPlayer();
            if (player.isGM()) {
                return;
            }

            if (!player.isOnline() && settings.playersAllowed.contains(player.getObjectId())) {
                settings.playerAllowedReEntryTimes.put(player.getObjectId(), ClockBridge.gameMillis() + timeInvade);
            } else {
                settings.playersAllowed.remove(player.getObjectId());
                settings.playerAllowedReEntryTimes.remove(player.getObjectId());
            }
        }

        if (creature.isPlayable() && hasCharactersInside()) {
            // Sending a raid home moves it, so the walk runs over a copy.
            try (Snapshot<Creature> inside = ScratchBuffers.empty()) {
                collectCharactersInside(inside);
                if (!containsPlayable(inside)) {
                    for (Creature obj : inside) {
                        if (!obj.isAttackable() || !obj.isRaid()) {
                            continue;
                        }

                        final Attackable raid = obj.asAttackable();
                        if ((raid.getSpawn() == null) || raid.isDead()) {
                            continue;
                        }

                        if (!raid.isInsideRadius2D(raid.getSpawn(), 150)) {
                            raid.returnHome();
                        }
                    }
                }
            }
        }

        if (creature.isAttackable() && creature.isRaid() && !creature.isDead()) {
            creature.asAttackable().returnHome();
        }
    }

    private static boolean containsPlayable(List<Creature> creatures) {
        for (Creature creature : creatures) {
            if (creature.isPlayable()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void setEnabled(boolean flag) {
        WorldPartition.checkOwner();
        if (isEnabled() != flag) {
            oustAllPlayers();
        }

        super.setEnabled(flag);
    }

    public void setAllowedPlayers(List<Integer> players) {
        WorldPartition.checkOwner();
        if (players != null) {
            final Settings settings = settings();
            settings.playersAllowed.clear();
            settings.playersAllowed.addAll(players);
        }
    }

    /** Read-only snapshot in admission order. */
    public List<Integer> getAllowedPlayers() {
        WorldPartition.checkOwner();
        return IntLists.unmodifiable(new IntArrayList(settings().playersAllowed));
    }

    public boolean isPlayerAllowed(Player player) {
        WorldPartition.checkOwner();
        if (player.isGM() || settings().playersAllowed.contains(player.getObjectId())) {
            return true;
        }

        oust(player);
        return false;
    }

    private void oust(Player player) {
        if ((oustLoc[0] != 0) && (oustLoc[1] != 0) && (oustLoc[2] != 0)) {
            player.teleToLocation(oustLoc[0], oustLoc[1], oustLoc[2]);
        } else {
            player.teleToLocation(TeleportWhereType.TOWN);
        }
    }

    public void movePlayersTo(Location loc) {
        WorldPartition.checkOwner();
        // A teleport may take the player out of this zone, so the walk runs over a copy.
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player player : players) {
                if (player.isOnline()) {
                    player.teleToLocation(loc);
                }
            }
        }
    }

    /** Clearing the entry times makes offline players fail re-entry on login, so they need no teleport. */
    @Override
    public void oustAllPlayers() {
        WorldPartition.checkOwner();
        if (!hasCharactersInside()) {
            return;
        }

        // A teleport takes the player out of this zone, so the walk runs over a copy.
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player player : players) {
                if (player.isOnline()) {
                    oust(player);
                }
            }
        }

        final Settings settings = settings();
        settings.playerAllowedReEntryTimes.clear();
        settings.playersAllowed.clear();
    }

    /** Entry is valid for {@code durationInSec} seconds; later entry ousts the player again. */
    public void allowPlayerEntry(Player player, int durationInSec) {
        WorldPartition.checkOwner();
        if (player.isGM()) {
            return;
        }

        final Settings settings = settings();
        settings.playersAllowed.add(player.getObjectId());
        settings.playerAllowedReEntryTimes.put(player.getObjectId(), ClockBridge.gameMillis() + (durationInSec * 1000));
    }

    public void removePlayer(Player player) {
        WorldPartition.checkOwner();
        if (player.isGM()) {
            return;
        }

        final Settings settings = settings();
        settings.playersAllowed.remove(player.getObjectId());
        settings.playerAllowedReEntryTimes.remove(player.getObjectId());
    }
}
