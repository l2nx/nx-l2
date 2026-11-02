/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.mechanics.siege;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.FeatureConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.data.xml.CastleData;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.instance.Artefact;
import app.l2nx.highfive.entity.actor.instance.Door;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.residences.AbstractResidence;
import app.l2nx.highfive.entity.residences.function.AbstractResidenceFunction;
import app.l2nx.highfive.entity.residences.function.ResidenceFunctions;
import app.l2nx.highfive.entity.zone.type.CastleZone;
import app.l2nx.highfive.entity.zone.type.ResidenceTeleportZone;
import app.l2nx.highfive.entity.zone.type.SiegeZone;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.CastleManorManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.SiegeManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.managers.TerritoryWarManager.Territory;
import app.l2nx.highfive.managers.ZoneManager;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PlaySound;
import app.l2nx.highfive.network.protocol.p267.serverpackets.PledgeShowInfoUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.residence.model.CastleDoorUpgrade;
import app.l2nx.highfive.repository.residence.model.CastleState;
import app.l2nx.highfive.repository.residence.model.ResidenceFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.Calendar;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class Castle extends AbstractResidence {

    // Load order of the doors, which respawn and lose their upgrades in it.
    private final List<Door> doors = new ObjectArrayList<>();

    int ownerId = 0;

    private Siege siege = null;

    private Calendar siegeDate;

    private boolean timeRegistrationOver =
            true; // True if Castle Lords set the time, or 24h is elapsed after the siege.

    private Calendar siegeTimeRegistrationEndDate; // Last siege end date + 1 day.

    private int taxPercent = 0;

    private double taxRate = 0;

    private long treasury = 0;

    private boolean showNpcCrest = false;
    private SiegeZone zone = null;
    private ResidenceTeleportZone teleZone;
    private Clan formerOwner = null;

    private final ObjectLinkedOpenHashSet<Artefact> artefacts = new ObjectLinkedOpenHashSet<>(1);

    private final ResidenceFunctions<CastleFunction> function = new ResidenceFunctions<>();

    private int ticketBuyCount = 0;

    private boolean firstMidVictory = false;

    public static final int FUNC_TELEPORT = 1;

    public static final int FUNC_RESTORE_HP = 2;
    public static final int FUNC_RESTORE_MP = 3;
    public static final int FUNC_RESTORE_EXP = 4;
    public static final int FUNC_SUPPORT = 5;

    @SingleWriter
    public class CastleFunction extends AbstractResidenceFunction {
        public CastleFunction(int type, int lvl, int lease, int tempLease, long rate, long time, boolean cwh) {
            super(type, lvl, lease, tempLease, rate, time);
            startFee(cwh);
        }

        @Override
        protected boolean isOwned() {
            return ownerId > 0;
        }

        @Override
        protected ItemContainer ownerWarehouse() {
            return ClanTable.getInstance().getClan(ownerId).getWarehouse();
        }

        @Override
        protected void save(ResidenceFunction function) {
            CastleManager.getInstance().saveFunction(getResidenceId(), function);
        }

        @Override
        protected void removeUnpaid() {
            removeFunction(getType());
        }
    }

    public Castle(int castleId) {
        super(castleId);
        load();
        initResidenceZone();
        if (ownerId != 0) {
            loadFunctions();
            loadDoorUpgrade();
        }
    }

    public CastleFunction getFunction(int type) {
        WorldPartition.checkOwner();
        return function.get(type);
    }

    public int getOwnerId() {
        WorldPartition.checkOwner();
        return ownerId;
    }

    /**
     * @return a read-only snapshot of the castle doors in load order
     */
    public List<Door> getDoors() {
        WorldPartition.checkOwner();
        return ObjectLists.unmodifiable(new ObjectArrayList<>(doors));
    }

    /**
     * @return a copy of the siege date; {@link #setSiegeDate} changes it
     */
    public Calendar getSiegeDate() {
        WorldPartition.checkOwner();
        return (Calendar) siegeDate.clone();
    }

    public void setSiegeDate(long millis) {
        WorldPartition.checkOwner();
        siegeDate.setTimeInMillis(millis);
    }

    public boolean isTimeRegistrationOver() {
        WorldPartition.checkOwner();
        return timeRegistrationOver;
    }

    public void setTimeRegistrationOver(boolean timeRegistrationOver) {
        WorldPartition.checkOwner();
        this.timeRegistrationOver = timeRegistrationOver;
    }

    public int getTaxPercent() {
        WorldPartition.checkOwner();
        return taxPercent;
    }

    public double getTaxRate() {
        WorldPartition.checkOwner();
        return taxRate;
    }

    public long getTreasury() {
        WorldPartition.checkOwner();
        return treasury;
    }

    public boolean isArtefact(WorldObject object) {
        WorldPartition.checkOwner();
        return artefacts.contains(object);
    }

    public int getTicketBuyCount() {
        WorldPartition.checkOwner();
        return ticketBuyCount;
    }

    public boolean isFirstMidVictory() {
        WorldPartition.checkOwner();
        return firstMidVictory;
    }

    public void setFirstMidVictory(boolean firstMidVictory) {
        WorldPartition.checkOwner();
        this.firstMidVictory = firstMidVictory;
    }

    public void engrave(Clan clan, WorldObject target) {
        WorldPartition.checkOwner();
        if (!artefacts.contains(target)) {
            return;
        }

        setOwner(clan);
        final SystemMessage msg =
                new SystemMessage(SystemMessageId.CLAN_S1_HAS_SUCCESSFULLY_ENGRAVED_THE_HOLY_ARTIFACT);
        msg.addString(clan.getName());
        World.broadcastToAllOnlinePlayers(msg);
    }

    public void addToTreasury(long amountValue) {
        WorldPartition.checkOwner();
        if (ownerId <= 0) {
            return;
        }

        long amount = amountValue;
        if (getName().equalsIgnoreCase("Schuttgart") || getName().equalsIgnoreCase("Goddard")) {
            final Castle rune = CastleManager.getInstance().getCastle("rune");
            if (rune != null) {
                final long runeTax = (long) (amount * rune.getTaxRate());
                if (rune.getOwnerId() > 0) {
                    rune.addToTreasury(runeTax);
                }

                amount -= runeTax;
            }
        }

        if (!getName().equalsIgnoreCase("aden")
                && !getName().equalsIgnoreCase("Rune")
                && !getName().equalsIgnoreCase("Schuttgart")
                && !getName().equalsIgnoreCase("Goddard")) {
            final Castle aden = CastleManager.getInstance().getCastle("aden");
            if (aden != null) {
                final long adenTax = (long) (amount * aden.getTaxRate());
                if (aden.getOwnerId() > 0) {
                    aden.addToTreasury(adenTax);
                }

                amount -= adenTax;
            }
        }

        addToTreasuryNoTax(amount);
    }

    public boolean addToTreasuryNoTax(long amountValue) {
        WorldPartition.checkOwner();
        if (ownerId <= 0) {
            return false;
        }

        long amount = amountValue;
        if (amount < 0) {
            amount *= -1;
            if (treasury < amount) {
                return false;
            }

            treasury -= amount;
        } else if ((treasury + amount) > PlayerConfig.MAX_ADENA) {
            treasury = PlayerConfig.MAX_ADENA;
        } else {
            treasury += amount;
        }

        CastleManager.getInstance().saveTreasury(getResidenceId(), treasury);
        return true;
    }

    public void banishForeigners() {
        WorldPartition.checkOwner();
        getResidenceZone().banishForeigners(ownerId);
    }

    public boolean checkIfInZone(int x, int y, int z) {
        WorldPartition.checkOwner();
        final SiegeZone zone = getZone();
        return (zone != null) && zone.isInsideZone(x, y, z);
    }

    public SiegeZone getZone() {
        WorldPartition.checkOwner();
        if (this.zone == null) {
            for (SiegeZone zone : ZoneManager.getInstance().getAllZones(SiegeZone.class)) {
                if (zone.getSiegeObjectId() == getResidenceId()) {
                    this.zone = zone;
                    break;
                }
            }
        }

        return this.zone;
    }

    @Override
    public CastleZone getResidenceZone() {
        WorldPartition.checkOwner();
        return (CastleZone) super.getResidenceZone();
    }

    public ResidenceTeleportZone getTeleZone() {
        WorldPartition.checkOwner();
        if (teleZone == null) {
            for (ResidenceTeleportZone zone : ZoneManager.getInstance().getAllZones(ResidenceTeleportZone.class)) {
                if (zone.getResidenceId() == getResidenceId()) {
                    teleZone = zone;
                    break;
                }
            }
        }

        return teleZone;
    }

    public void oustAllPlayers() {
        WorldPartition.checkOwner();
        getTeleZone().oustAllPlayers();
    }

    public double getDistance(WorldObject obj) {
        WorldPartition.checkOwner();
        return getZone().getDistanceToZone(obj);
    }

    public void closeDoor(Player player, int doorId) {
        WorldPartition.checkOwner();
        openCloseDoor(player, doorId, false);
    }

    public void openDoor(Player player, int doorId) {
        WorldPartition.checkOwner();
        openCloseDoor(player, doorId, true);
    }

    public void openCloseDoor(Player player, int doorId, boolean open) {
        WorldPartition.checkOwner();
        if (player.getClanId() != ownerId) {
            return;
        }

        final Door door = getDoor(doorId);
        if (door != null) {
            if (open) {
                door.openMe();
            } else {
                door.closeMe();
            }
        }
    }

    public void removeUpgrade() {
        WorldPartition.checkOwner();
        removeDoorUpgrade();
        removeTrapUpgrade();
        removeAllFunctions();
    }

    private void removeAllFunctions() {
        function.forEachType(this::removeFunction);
    }

    public void setOwner(Clan clan) {
        WorldPartition.checkOwner();
        if ((ownerId > 0) && ((clan == null) || (clan.getId() != ownerId))) {
            final Clan oldOwner = ClanTable.getInstance().getClan(getOwnerId());
            if (oldOwner != null) {
                if (formerOwner == null) {
                    formerOwner = oldOwner;
                    if (PlayerConfig.REMOVE_CASTLE_CIRCLETS) {
                        CastleManager.getInstance().removeCirclet(formerOwner, getResidenceId());
                    }
                }

                try {
                    final Player oldleader = oldOwner.getLeader().getPlayer();
                    if ((oldleader != null) && (oldleader.getMountType() == MountType.WYVERN)) {
                        oldleader.dismount();
                    }
                } catch (Exception e) {
                    log.warn("Exception in setOwner: {}", e.getMessage(), e);
                }

                oldOwner.setCastleId(0);
                for (Player member : oldOwner.getOnlineMembers(0)) {
                    removeResidentialSkills(member);
                    member.sendSkillList();
                }
            }
        }

        updateOwnerInDB(clan);
        setShowNpcCrest(false);

        if ((clan != null) && (clan.getFortId() > 0)) {
            FortManager.getInstance().getFortByOwner(clan).removeOwner(true);
        }

        if (getSiege().isInProgress()) {
            getSiege().midVictory();
        }

        TerritoryWarManager.getInstance().getTerritory(getResidenceId()).setOwnerClan(clan);

        if (clan != null) {
            for (Player member : clan.getOnlineMembers(0)) {
                giveResidentialSkills(member);
                member.sendSkillList();
            }
        }
    }

    public void removeOwner(Clan clan) {
        WorldPartition.checkOwner();
        if (clan != null) {
            formerOwner = clan;
            if (PlayerConfig.REMOVE_CASTLE_CIRCLETS) {
                CastleManager.getInstance().removeCirclet(formerOwner, getResidenceId());
            }

            for (Player member : clan.getOnlineMembers(0)) {
                removeResidentialSkills(member);
                member.sendSkillList();
            }

            clan.setCastleId(0);
            clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
        }

        updateOwnerInDB(null);
        if (getSiege().isInProgress()) {
            getSiege().midVictory();
        }

        final Territory territory = TerritoryWarManager.getInstance().getTerritory(getResidenceId());
        if (territory != null) {
            territory.setOwnerClan(null);
        }

        removeAllFunctions();
    }

    public void setTaxPercent(int taxPercent) {
        WorldPartition.checkOwner();
        this.taxPercent = taxPercent;
        taxRate = this.taxPercent / 100.0;
        CastleManager.getInstance().saveTaxPercent(getResidenceId(), taxPercent);
    }

    public void spawnDoor() {
        WorldPartition.checkOwner();
        spawnDoor(false);
    }

    public void spawnDoor(boolean isDoorWeak) {
        WorldPartition.checkOwner();
        try (Snapshot<Door> walk = ScratchBuffers.snapshot(doors)) {
            for (Door door : walk) {
                if (door.isDead()) {
                    door.doRevive();
                    door.setCurrentHp((isDoorWeak) ? (door.getMaxHp() / 2) : (door.getMaxHp()));
                }

                if (door.isOpen()) {
                    door.closeMe();
                }
            }
        }
    }

    @Override
    protected void load() {
        setName(CastleData.getInstance().getCastle(getResidenceId()).name());
        final CastleState state = CastleManager.getInstance().findState(getResidenceId());
        // No scheduled siege reads as a date in the past, which makes the siege schedule the next one.
        siegeDate = ClockBridge.calendar();
        siegeDate.setTimeInMillis(epochMillisOrZero(state.siegeDate()));
        siegeTimeRegistrationEndDate = ClockBridge.calendar();
        siegeTimeRegistrationEndDate.setTimeInMillis(epochMillisOrZero(state.registrationEndDate()));
        timeRegistrationOver = state.registrationOver();
        taxPercent = state.taxPercent();
        treasury = state.treasury();
        showNpcCrest = state.showNpcCrest();
        ticketBuyCount = state.ticketBuyCount();
        taxRate = taxPercent / 100.0;
        ownerId = state.ownerClanId();
    }

    private void loadFunctions() {
        for (ResidenceFunction stored : CastleManager.getInstance().findFunctions(getResidenceId())) {
            function.put(new CastleFunction(
                    stored.type(),
                    stored.level(),
                    stored.lease(),
                    0,
                    stored.rate(),
                    epochMillisOrZero(stored.endTime()),
                    true));
        }
    }

    public void removeFunction(int functionType) {
        WorldPartition.checkOwner();
        function.remove(functionType);
        CastleManager.getInstance().deleteFunction(getResidenceId(), functionType);
    }

    public boolean updateFunctions(Player player, int type, int lvl, int lease, long rate, boolean addNew) {
        WorldPartition.checkOwner();
        if (player == null) {
            return false;
        }

        if ((lease > 0) && !player.destroyItemByItemId(null, Inventory.ADENA_ID, lease, null, true)) {
            return false;
        }

        if (addNew) {
            function.put(new CastleFunction(type, lvl, lease, 0, rate, 0, false));
        } else if ((lvl == 0) && (lease == 0)) {
            removeFunction(type);
        } else if ((lease - function.get(type).getLease()) > 0) {
            function.put(new CastleFunction(type, lvl, lease, 0, rate, -1, false));
        } else {
            function.get(type).change(lvl, lease);
        }

        return true;
    }

    public void activateInstance() {
        WorldPartition.checkOwner();
        loadDoor();
    }

    private void loadDoor() {
        for (Door door : DoorData.getInstance().getDoors()) {
            if ((door.getCastle() != null) && (door.getCastle().getResidenceId() == getResidenceId())) {
                doors.add(door);
            }
        }
    }

    private void loadDoorUpgrade() {
        for (CastleDoorUpgrade upgrade : CastleManager.getInstance().findDoorUpgrades(getResidenceId())) {
            setDoorUpgrade(upgrade.doorId(), upgrade.hpRatio(), false);
        }
    }

    private void removeDoorUpgrade() {
        try (Snapshot<Door> walk = ScratchBuffers.snapshot(doors)) {
            for (Door door : walk) {
                door.getStat().setUpgradeHpRatio(1);
                door.setCurrentHp(door.getCurrentHp());
            }
        }

        CastleManager.getInstance().deleteDoorUpgrades(getResidenceId());
    }

    public void setDoorUpgrade(int doorId, int ratio, boolean save) {
        WorldPartition.checkOwner();
        final Door door = doors.isEmpty() ? DoorData.getInstance().getDoor(doorId) : getDoor(doorId);
        if (door == null) {
            return;
        }

        door.getStat().setUpgradeHpRatio(ratio);
        door.setCurrentHp(door.getMaxHp());

        if (save) {
            CastleManager.getInstance()
                    .saveDoorUpgrade(
                            getResidenceId(),
                            CastleDoorUpgrade.builder()
                                    .doorId(doorId)
                                    .hpRatio(ratio)
                                    .build());
        }
    }

    private void updateOwnerInDB(Clan clan) {
        if (clan != null) {
            ownerId = clan.getId();
        } else {
            ownerId = 0;
            CastleManorManager.getInstance().resetManorData(getResidenceId());
        }

        CastleManager.getInstance().saveOwner(getResidenceId(), ownerId);

        if (clan != null) {
            clan.setCastleId(getResidenceId());
            clan.broadcastToOnlineMembers(new PledgeShowInfoUpdate(clan));
            clan.broadcastToOnlineMembers(new PlaySound(1, "Siege_Victory", 0, 0, 0, 0, 0));
        }
    }

    public Door getDoor(int doorId) {
        WorldPartition.checkOwner();
        if (doorId <= 0) {
            return null;
        }

        for (Door door : doors) {
            if (door.getId() == doorId) {
                return door;
            }
        }

        return null;
    }

    public Clan getOwner() {
        WorldPartition.checkOwner();
        return (ownerId != 0) ? ClanTable.getInstance().getClan(ownerId) : null;
    }

    public Siege getSiege() {
        WorldPartition.checkOwner();
        if (siege == null) {
            siege = new Siege(this);
        }

        return siege;
    }

    /**
     * @return a copy of the end of the siege time registration; {@link #setTimeRegistrationOverDate} changes it
     */
    public Calendar getTimeRegistrationOverDate() {
        WorldPartition.checkOwner();
        return (Calendar) registrationEndDate().clone();
    }

    public void setTimeRegistrationOverDate(long millis) {
        WorldPartition.checkOwner();
        registrationEndDate().setTimeInMillis(millis);
    }

    private Calendar registrationEndDate() {
        if (siegeTimeRegistrationEndDate == null) {
            siegeTimeRegistrationEndDate = ClockBridge.calendar();
        }

        return siegeTimeRegistrationEndDate;
    }

    public boolean getShowNpcCrest() {
        WorldPartition.checkOwner();
        return showNpcCrest;
    }

    public void setShowNpcCrest(boolean showNpcCrest) {
        WorldPartition.checkOwner();
        if (this.showNpcCrest != showNpcCrest) {
            this.showNpcCrest = showNpcCrest;
            updateShowNpcCrest();
        }
    }

    public void updateClansReputation() {
        WorldPartition.checkOwner();
        final Clan owner = ClanTable.getInstance().getClan(getOwnerId());
        // A dissolved former owner has nothing to lose and does not cap the winner's reward.
        final Clan former = (formerOwner != null) && (ClanTable.getInstance().getClan(formerOwner.getId()) != null)
                ? formerOwner
                : null;
        if (former != null) {
            if (former != owner) {
                final int maxreward = Math.max(0, former.getReputationScore());
                former.takeReputationScore(FeatureConfig.LOOSE_CASTLE_POINTS);
                if (owner != null) {
                    owner.addReputationScore(Math.min(FeatureConfig.TAKE_CASTLE_POINTS, maxreward));
                    owner.broadcastToOnlineMembers(new SystemMessage(
                                    SystemMessageId
                                            .SINCE_YOUR_CLAN_EMERGED_VICTORIOUS_FROM_THE_SIEGE_S1_POINTS_HAVE_BEEN_ADDED_TO_YOUR_CLAN_S_REPUTATION_SCORE)
                            .addInt(Math.min(FeatureConfig.TAKE_CASTLE_POINTS, maxreward)));
                }

                former.broadcastToOnlineMembers(new SystemMessage(
                                SystemMessageId
                                        .SINCE_YOUR_CLAN_WAS_DEFEATED_IN_A_SIEGE_S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_YOUR_CLAN_S_REPUTATION_SCORE_AND_GIVEN_TO_THE_OPPOSING_CLAN)
                        .addInt(FeatureConfig.LOOSE_CASTLE_POINTS));
            } else {
                former.addReputationScore(FeatureConfig.CASTLE_DEFENDED_POINTS);
                former.broadcastToOnlineMembers(new SystemMessage(
                                SystemMessageId
                                        .SINCE_YOUR_CLAN_EMERGED_VICTORIOUS_FROM_THE_SIEGE_S1_POINTS_HAVE_BEEN_ADDED_TO_YOUR_CLAN_S_REPUTATION_SCORE)
                        .addInt(FeatureConfig.CASTLE_DEFENDED_POINTS));
            }
        } else if (owner != null) {
            owner.addReputationScore(FeatureConfig.TAKE_CASTLE_POINTS);
            owner.broadcastToOnlineMembers(new SystemMessage(
                            SystemMessageId
                                    .SINCE_YOUR_CLAN_EMERGED_VICTORIOUS_FROM_THE_SIEGE_S1_POINTS_HAVE_BEEN_ADDED_TO_YOUR_CLAN_S_REPUTATION_SCORE)
                    .addInt(FeatureConfig.TAKE_CASTLE_POINTS));
        }
    }

    public void updateShowNpcCrest() {
        WorldPartition.checkOwner();
        CastleManager.getInstance().saveShowNpcCrest(getResidenceId(), showNpcCrest);
    }

    @Override
    public void giveResidentialSkills(Player player) {
        WorldPartition.checkOwner();
        final Territory territory = TerritoryWarManager.getInstance().getTerritory(getResidenceId());
        if ((territory != null) && territory.getOwnedWardIds().contains(getResidenceId() + 80)) {
            for (int wardId : territory.getOwnedWardIds()) {
                final List<SkillLearn> territorySkills =
                        SkillTreeData.getInstance().getAvailableResidentialSkills(wardId);
                for (SkillLearn s : territorySkills) {
                    final Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
                    if (sk != null) {
                        player.addSkill(sk, false);
                    } else {
                        log.warn(
                                "Trying to add a null skill for Territory Ward Id: {}, skill Id: {} level: {}!",
                                wardId,
                                s.getSkillId(),
                                s.getSkillLevel());
                    }
                }
            }
        }

        super.giveResidentialSkills(player);
    }

    @Override
    public void removeResidentialSkills(Player player) {
        WorldPartition.checkOwner();
        if (TerritoryWarManager.getInstance().getTerritory(getResidenceId()) != null) {
            for (int wardId : TerritoryWarManager.getInstance()
                    .getTerritory(getResidenceId())
                    .getOwnedWardIds()) {
                final List<SkillLearn> territorySkills =
                        SkillTreeData.getInstance().getAvailableResidentialSkills(wardId);
                for (SkillLearn s : territorySkills) {
                    final Skill sk = SkillData.getInstance().getSkill(s.getSkillId(), s.getSkillLevel());
                    if (sk != null) {
                        player.removeSkill(sk, false, true);
                    } else {
                        log.warn(
                                "Trying to remove a null skill for Territory Ward Id: {}, skill Id: {} level: {}!",
                                wardId,
                                s.getSkillId(),
                                s.getSkillLevel());
                    }
                }
            }
        }

        super.removeResidentialSkills(player);
    }

    public void registerArtefact(Artefact artefact) {
        WorldPartition.checkOwner();
        artefacts.add(artefact);
    }

    public void setTicketBuyCount(int count) {
        WorldPartition.checkOwner();
        ticketBuyCount = count;
        CastleManager.getInstance().saveTicketBuyCount(getResidenceId(), ticketBuyCount);
    }

    public int getTrapUpgradeLevel(int towerIndex) {
        WorldPartition.checkOwner();
        final TowerSpawn spawn =
                SiegeManager.getInstance().getFlameTowers(getResidenceId()).get(towerIndex);
        return (spawn != null) ? spawn.getUpgradeLevel() : 0;
    }

    public void setTrapUpgrade(int towerIndex, int level, boolean save) {
        WorldPartition.checkOwner();
        if (save) {
            SiegeManager.getInstance().saveTrapUpgrade(getResidenceId(), towerIndex, level);
        }

        final TowerSpawn spawn =
                SiegeManager.getInstance().getFlameTowers(getResidenceId()).get(towerIndex);
        if (spawn != null) {
            spawn.setUpgradeLevel(level);
        }
    }

    private void removeTrapUpgrade() {
        for (TowerSpawn ts : SiegeManager.getInstance().getFlameTowers(getResidenceId())) {
            ts.setUpgradeLevel(0);
        }

        SiegeManager.getInstance().deleteTrapUpgrades(getResidenceId());
    }

    @Override
    protected void initResidenceZone() {
        for (CastleZone zone : ZoneManager.getInstance().getAllZones(CastleZone.class)) {
            if (zone.getResidenceId() == getResidenceId()) {
                setResidenceZone(zone);
                break;
            }
        }
    }
}
