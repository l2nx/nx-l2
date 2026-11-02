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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.transform.Transform;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.zone.AbstractZoneSettings;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.FortSiegeManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.FortSiege;
import app.l2nx.highfive.mechanics.siege.Siegable;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.mechanics.skill.BuffInfo;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.partition.WorldPartition;
import lombok.extern.slf4j.Slf4j;

/**
 * A siege zone
 * @author durgus, Skache
 */
@Slf4j
@SingleWriter
public class SiegeZone extends ZoneType {
    private static final int DISMOUNT_DELAY = 5;

    public SiegeZone(int id) {
        super(id, new Settings());
    }

    @SingleWriter
    public static final class Settings extends AbstractZoneSettings {
        private int siegeableId = -1;

        private Siegable siege = null;

        private boolean activeSiege = false;

        Settings() {}

        @Override
        public void clear() {
            WorldPartition.checkOwner();
            siegeableId = -1;
            siege = null;
            activeSiege = false;
        }
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
        if (name.equals("castleId")) {
            if (settings().siegeableId != -1) {
                throw new IllegalArgumentException("Siege object already defined!");
            }

            settings().siegeableId = Integer.parseInt(value);
        } else if (name.equals("fortId")) {
            if (settings().siegeableId != -1) {
                throw new IllegalArgumentException("Siege object already defined!");
            }

            settings().siegeableId = Integer.parseInt(value);
        } else if (name.equals("clanHallId")) {
            if (settings().siegeableId != -1) {
                throw new IllegalArgumentException("Siege object already defined!");
            }

            settings().siegeableId = Integer.parseInt(value);
            final SiegableHall hall =
                    CHSiegeManager.getInstance().getConquerableHalls().get(settings().siegeableId);
            if (hall != null) {
                hall.setSiegeZone(this);
            } else {
                log.warn("Siegable clan hall with id {} does not exist!", value);
            }
        } else {
            super.setParameter(name, value);
        }
    }

    @Override
    protected void onEnter(Creature creature) {
        if (!settings().activeSiege) {
            return;
        }

        creature.setInsideZone(ZoneId.PVP, true);
        creature.setInsideZone(ZoneId.SIEGE, true);
        creature.setInsideZone(ZoneId.NO_SUMMON_FRIEND, true);

        if (!creature.isPlayer()) {
            return;
        }

        final Player player = creature.asPlayer();
        if (player.isRegisteredOnThisSiegeField(settings().siegeableId)) {
            player.setInSiege(true);
            if (settings().siege.giveFame() && (settings().siege.getFameFrequency() > 0)) {
                player.startFameTask(
                        settings().siege.getFameFrequency() * 1000,
                        settings().siege.getFameAmount());
            }
        }

        creature.sendPacket(SystemMessageId.YOU_HAVE_ENTERED_A_COMBAT_ZONE);

        if (FeatureConfig.ALLOW_MOUNTS_DURING_SIEGE) {
            return;
        }

        if (player.isGM()) {
            player.sendMessage("You have entered a siege zone. GM dismount restrictions are ignored.");
            return;
        }

        final Castle castle = CastleManager.getInstance().getCastleById(settings().siegeableId);
        final boolean isCastleLord =
                (castle != null) && player.isClanLeader() && (player.getClanId() == castle.getOwnerId());
        if (player.getMountType() == MountType.WYVERN) {
            if (!isCastleLord) {
                player.sendPacket(
                        SystemMessageId
                                .THIS_AREA_CANNOT_BE_ENTERED_WHILE_MOUNTED_ATOP_OF_A_WYVERN_YOU_WILL_BE_DISMOUNTED_FROM_YOUR_WYVERN_IF_YOU_DO_NOT_LEAVE);
                player.enteredNoLanding(DISMOUNT_DELAY);
            }
        } else if (player.isMounted() && (player.getMountType() != MountType.WYVERN)) {
            // The lord keeps a strider: summoning a wyvern requires being mounted on one first.
            final boolean isCastleLordOnStrider = (player.getMountType() == MountType.STRIDER) && isCastleLord;
            if (!isCastleLordOnStrider) {
                player.dismount();
            }
        }

        final Transform transform = player.getTransformation();
        if ((transform != null) && transform.isRiding()) {
            player.untransform();
        }
    }

    @Override
    protected void onExit(Creature creature) {
        creature.setInsideZone(ZoneId.PVP, false);
        creature.setInsideZone(ZoneId.SIEGE, false);
        creature.setInsideZone(ZoneId.NO_SUMMON_FRIEND, false);
        if (settings().activeSiege && creature.isPlayer()) {
            final Player player = creature.asPlayer();
            creature.sendPacket(SystemMessageId.YOU_HAVE_LEFT_A_COMBAT_ZONE);
            if (player.getMountType() == MountType.WYVERN) {
                player.exitedNoLanding();
            }

            if (player.getPvpFlag() == 0) {
                player.startPvPFlag();
            }
        }

        if (!creature.isPlayer()) {
            return;
        }

        final Player player = creature.asPlayer();
        player.stopFameTask();
        player.setInSiege(false);

        if (!(settings().siege instanceof FortSiege) || (player.getInventory().getItemByItemId(9819) == null)) {
            return;
        }

        final Fort fort = FortManager.getInstance().getFortById(settings().siegeableId);
        if (fort != null) {
            FortSiegeManager.getInstance().dropCombatFlag(player, fort.getResidenceId());
        } else {
            final BodyPart bodyPart = BodyPart.fromItem(player.getInventory().getItemByItemId(9819));
            player.getInventory().unEquipItemInBodySlot(bodyPart);
            player.destroyItem(ItemProcessType.DESTROY, player.getInventory().getItemByItemId(9819), null, true);
        }

        final Summon summon = player.getSummon();
        if (summon != null) {
            summon.abortAttack();
            summon.abortCast();
            summon.stopAllEffects();
            summon.unSummon(player);
        }
    }

    @Override
    public void onDieInside(Creature creature) {
        WorldPartition.checkOwner();
        if (!settings().activeSiege
                || !creature.isPlayer()
                || !creature.asPlayer().isRegisteredOnThisSiegeField(settings().siegeableId)) {
            return;
        }

        int level = 1;
        final BuffInfo info = creature.getEffectList().getBuffInfoBySkillId(5660);
        if (info != null) {
            level = Math.min(level + info.getSkill().getLevel(), 5);
        }

        final Skill skill = SkillData.getInstance().getSkill(5660, level);
        if (skill != null) {
            skill.applyEffects(creature, creature);
        }
    }

    @Override
    public void onPlayerLogoutInside(Player player) {
        WorldPartition.checkOwner();
        if (player.getClanId() != settings().siegeableId) {
            player.teleToLocation(TeleportWhereType.TOWN);
        }
    }

    public void updateZoneStatusForCharactersInside() {
        WorldPartition.checkOwner();
        // Entering may dismount or teleport, so the walk runs over a copy.
        try (Snapshot<Creature> inside = ScratchBuffers.empty()) {
            collectCharactersInside(inside);
            if (settings().activeSiege) {
                for (Creature creature : inside) {
                    onEnter(creature);
                }
            } else {
                for (Creature creature : inside) {
                    creature.setInsideZone(ZoneId.PVP, false);
                    creature.setInsideZone(ZoneId.SIEGE, false);
                    creature.setInsideZone(ZoneId.NO_SUMMON_FRIEND, false);

                    if (creature.isPlayer()) {
                        final Player player = creature.asPlayer();
                        creature.sendPacket(SystemMessageId.YOU_HAVE_LEFT_A_COMBAT_ZONE);
                        player.stopFameTask();
                        if (player.getMountType() == MountType.WYVERN) {
                            player.exitedNoLanding();
                        }
                    }
                }
            }
        }
    }

    public void announceToPlayers(String message) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player player : players) {
                player.sendMessage(message);
            }
        }
    }

    public int getSiegeObjectId() {
        WorldPartition.checkOwner();
        return settings().siegeableId;
    }

    public boolean isActive() {
        WorldPartition.checkOwner();
        return settings().activeSiege;
    }

    public void setActive(boolean value) {
        WorldPartition.checkOwner();
        settings().activeSiege = value;
    }

    public void setSiegeInstance(Siegable siege) {
        WorldPartition.checkOwner();
        settings().siege = siege;
    }

    public void banishForeigners(int owningClanId) {
        WorldPartition.checkOwner();
        try (Snapshot<Player> players = ScratchBuffers.empty()) {
            collectPlayersInside(players);
            for (Player temp : players) {
                if (temp.getClanId() == owningClanId) {
                    continue;
                }

                temp.teleToLocation(TeleportWhereType.TOWN);
            }
        }
    }
}
