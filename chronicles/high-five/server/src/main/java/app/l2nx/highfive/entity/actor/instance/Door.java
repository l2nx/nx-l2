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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.ai.DoorAI;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.sql.ClanHallTable;
import app.l2nx.highfive.data.xml.DoorData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.stat.DoorStat;
import app.l2nx.highfive.entity.actor.status.DoorStatus;
import app.l2nx.highfive.entity.actor.templates.DoorTemplate;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.instancezone.Instance;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.residences.ClanHall;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.InstanceManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.siege.Castle;
import app.l2nx.highfive.mechanics.siege.Fort;
import app.l2nx.highfive.mechanics.siege.clanhalls.SiegableHall;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.DoorStatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.OnEventTrigger;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StaticObjectInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SingleWriter
public class Door extends Creature {
    public static final byte OPEN_BY_CLICK = 1;
    public static final byte OPEN_BY_TIME = 2;
    public static final byte OPEN_BY_ITEM = 4;
    public static final byte OPEN_BY_SKILL = 8;
    public static final byte OPEN_BY_CYCLE = 16;

    private int castleIndex = -2;
    private int fortIndex = -2;

    private ClanHall clanHall;

    boolean open = false;

    private boolean attackableDoor = false;

    private boolean targetable;

    private int meshIndex = 1;

    private ScheduledFuture<?> autoCloseTask;

    public Door(DoorTemplate template) {
        super(template);
        setInstanceType(InstanceType.Door);
        setInvul(false);
        setLethalable(false);
        open = template.isOpenByDefault();
        attackableDoor = template.isAttackable();
        targetable = template.isTargetable();
        if (getGroupName() != null) {
            DoorData.addDoorGroup(getGroupName(), getId());
        }

        if (isOpenableByTime()) {
            startTimerOpen();
        }

        final int clanhallId = template.getClanHallId();
        if (clanhallId > 0) {
            final ClanHall hall = ClanHallTable.getAllClanHalls().get(clanhallId);
            if (hall != null) {
                setClanHall(hall);
                hall.getDoors().add(this);
            }
        }
    }

    public ClanHall getClanHall() {
        WorldPartition.checkOwner();
        return clanHall;
    }

    public void setClanHall(ClanHall clanHall) {
        WorldPartition.checkOwner();
        this.clanHall = clanHall;
    }

    public boolean isOpen() {
        WorldPartition.checkOwner();
        return open;
    }

    public boolean isAttackableDoor() {
        WorldPartition.checkOwner();
        return attackableDoor;
    }

    @Override
    public boolean isTargetable() {
        WorldPartition.checkOwner();
        return targetable;
    }

    public int getMeshIndex() {
        WorldPartition.checkOwner();
        return meshIndex;
    }

    public void setMeshIndex(int meshIndex) {
        WorldPartition.checkOwner();
        this.meshIndex = meshIndex;
    }

    @Override
    protected CreatureAI initAI() {
        return new DoorAI(this);
    }

    private void startTimerOpen() {
        int delay = open ? getTemplate().getOpenTime() : getTemplate().getCloseTime();
        if (getTemplate().getRandomTime() > 0) {
            delay += ClockBridge.random().nextInt(getTemplate().getRandomTime());
        }

        WorldPartition.loop().schedule(new TimerOpen(), delay * 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public DoorTemplate getTemplate() {
        WorldPartition.checkOwner();
        return (DoorTemplate) uncheckedTemplate();
    }

    @Override
    public DoorStatus getStatus() {
        WorldPartition.checkOwner();
        return (DoorStatus) uncheckedStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new DoorStatus(this));
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new DoorStat(this, statAccess()));
    }

    @Override
    public DoorStat getStat() {
        WorldPartition.checkOwner();
        return (DoorStat) uncheckedStat();
    }

    public boolean isOpenableBySkill() {
        WorldPartition.checkOwner();
        return (getTemplate().getOpenType() & OPEN_BY_SKILL) == OPEN_BY_SKILL;
    }

    public boolean isOpenableByItem() {
        WorldPartition.checkOwner();
        return (getTemplate().getOpenType() & OPEN_BY_ITEM) == OPEN_BY_ITEM;
    }

    public boolean isOpenableByClick() {
        WorldPartition.checkOwner();
        return (getTemplate().getOpenType() & OPEN_BY_CLICK) == OPEN_BY_CLICK;
    }

    public boolean isOpenableByTime() {
        WorldPartition.checkOwner();
        return (getTemplate().getOpenType() & OPEN_BY_TIME) == OPEN_BY_TIME;
    }

    public boolean isOpenableByCycle() {
        WorldPartition.checkOwner();
        return (getTemplate().getOpenType() & OPEN_BY_CYCLE) == OPEN_BY_CYCLE;
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return ((DoorTemplate) uncheckedTemplate()).getLevel();
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return getTemplate().getId();
    }

    public void setOpen(boolean open) {
        WorldPartition.checkOwner();
        this.open = open;
        if (getChildId() > 0) {
            final Door sibling = getSiblingDoor(getChildId());
            if (sibling != null) {
                sibling.notifyChildEvent(open);
            } else {
                log.warn("Cannot find child id: {}", getChildId());
            }
        }
    }

    public boolean isShowHp() {
        WorldPartition.checkOwner();
        return getTemplate().isShowHp();
    }

    public void setIsAttackableDoor(boolean value) {
        WorldPartition.checkOwner();
        attackableDoor = value;
    }

    public int getDamage() {
        WorldPartition.checkOwner();
        final int dmg = 6 - (int) Math.ceil((getCurrentHp() / getMaxHp()) * 6);
        if (dmg > 6) {
            return 6;
        }

        if (dmg < 0) {
            return 0;
        }

        return dmg;
    }

    public Castle getCastle() {
        WorldPartition.checkOwner();
        if (castleIndex < 0) {
            castleIndex = CastleManager.getInstance().getCastleIndex(this);
        }

        if (castleIndex < 0) {
            return null;
        }

        return CastleManager.getInstance().getCastles().get(castleIndex);
    }

    public Fort getFort() {
        WorldPartition.checkOwner();
        if (fortIndex < 0) {
            fortIndex = FortManager.getInstance().getFortIndex(this);
        }

        if (fortIndex < 0) {
            return null;
        }

        return FortManager.getInstance().getForts().get(fortIndex);
    }

    public boolean isEnemy() {
        WorldPartition.checkOwner();
        if ((getCastle() != null)
                && (getCastle().getResidenceId() > 0)
                && getCastle().getZone().isActive()
                && isShowHp()) {
            return true;
        }

        if ((getFort() != null)
                && (getFort().getResidenceId() > 0)
                && getFort().getZone().isActive()
                && isShowHp()) {
            return true;
        }

        if ((clanHall != null)
                && clanHall.isSiegableHall()
                && ((SiegableHall) clanHall).getSiegeZone().isActive()
                && isShowHp()) {
            return true;
        }

        return false;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        if (!attacker.isPlayable()) {
            return false;
        }

        if (attackableDoor) {
            return true;
        }

        if (!isShowHp()) {
            return false;
        }

        final Player actingPlayer = attacker.asPlayer();
        if ((clanHall != null) && clanHall.isSiegableHall()) {
            final SiegableHall hall = (SiegableHall) clanHall;
            if (!hall.isSiegableHall()) {
                return false;
            }

            return hall.isInSiege()
                    && hall.getSiege().doorIsAutoAttackable()
                    && hall.getSiege().checkIsAttacker(actingPlayer.getClan());
        }

        if (TerritoryWarManager.getInstance().isTWInProgress()) {
            final int activeSiegeId = (getFort() != null
                    ? getFort().getResidenceId()
                    : (getCastle() != null ? getCastle().getResidenceId() : 0));
            return !TerritoryWarManager.getInstance().isAllyField(actingPlayer, activeSiegeId);
        }

        final boolean isFort = ((getFort() != null)
                && (getFort().getResidenceId() > 0)
                && getFort().getZone().isActive());
        if (isFort) {
            final Clan clan = actingPlayer.getClan();
            if ((clan != null) && (clan == getFort().getOwnerClan())) {
                return false;
            }
        }

        final boolean isCastle = ((getCastle() != null)
                && (getCastle().getResidenceId() > 0)
                && getCastle().getZone().isActive());
        if (isCastle) {
            final Clan clan = actingPlayer.getClan();
            if ((clan != null) && (clan.getId() == getCastle().getOwnerId())) {
                return false;
            }
        }

        return (isCastle || isFort);
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
    }

    @Override
    public Item getActiveWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        WorldPartition.checkOwner();
        return null;
    }

    @Override
    public void broadcastStatusUpdate() {
        WorldPartition.checkOwner();
        final StaticObjectInfo su = new StaticObjectInfo(this, false);
        final StaticObjectInfo targetableSu = new StaticObjectInfo(this, true);
        final DoorStatusUpdate dsu = new DoorStatusUpdate(this);
        final OnEventTrigger oe = (getEmitter() > 0) ? new OnEventTrigger(this, !open) : null;
        final boolean useTargetable = ((getCastle() != null) && (getCastle().getResidenceId() > 0))
                || ((getFort() != null) && (getFort().getResidenceId() > 0));

        World.forEachVisibleObject(this, Player.class, player -> {
            if (!isVisibleFor(player)) {
                return;
            }

            if (player.isGM() || useTargetable) {
                player.sendPacket(targetableSu);
            } else {
                player.sendPacket(su);
            }

            player.sendPacket(dsu);
            if (oe != null) {
                player.sendPacket(oe);
            }
        });
    }

    public void openMe() {
        WorldPartition.checkOwner();
        if (getGroupName() != null) {
            manageGroupOpen(true, getGroupName());
            return;
        }

        if (!isOpen()) {
            setOpen(true);
            broadcastStatusUpdate();
            startAutoCloseTask();
        }
    }

    public void closeMe() {
        WorldPartition.checkOwner();
        final ScheduledFuture<?> oldTask = autoCloseTask;
        if (oldTask != null) {
            autoCloseTask = null;
            oldTask.cancel(false);
        }

        if (getGroupName() != null) {
            manageGroupOpen(false, getGroupName());
            return;
        }

        if (isOpen()) {
            setOpen(false);
            broadcastStatusUpdate();
        }
    }

    private void manageGroupOpen(boolean open, String groupName) {
        final Set<Integer> set = DoorData.getDoorsByGroup(groupName);
        Door first = null;
        for (Integer id : set) {
            final Door door = getSiblingDoor(id);
            if (first == null) {
                first = door;
            }

            if (door.isOpen() != open) {
                door.setOpen(open);
                door.broadcastStatusUpdate();
            }
        }

        if ((first != null) && open) {
            first.startAutoCloseTask(); // only one from group
        }
    }

    private void notifyChildEvent(boolean open) {
        final byte openThis =
                open ? getTemplate().getMasterDoorOpen() : getTemplate().getMasterDoorClose();
        if (openThis == 1) {
            openMe();
        } else if (openThis == -1) {
            closeMe();
        }
    }

    public String getDoorName() {
        WorldPartition.checkOwner();
        return getTemplate().getName();
    }

    public int getX(int i) {
        WorldPartition.checkOwner();
        return getTemplate().getNodeX()[i];
    }

    public int getY(int i) {
        WorldPartition.checkOwner();
        return getTemplate().getNodeY()[i];
    }

    public int getZMin() {
        WorldPartition.checkOwner();
        return getTemplate().getNodeZ();
    }

    public int getZMax() {
        WorldPartition.checkOwner();
        return getTemplate().getNodeZ() + getTemplate().getHeight();
    }

    public int getEmitter() {
        WorldPartition.checkOwner();
        return getTemplate().getEmmiter();
    }

    public boolean isWall() {
        WorldPartition.checkOwner();
        return getTemplate().isWall();
    }

    public String getGroupName() {
        WorldPartition.checkOwner();
        return getTemplate().getGroupName();
    }

    public int getChildId() {
        WorldPartition.checkOwner();
        return getTemplate().getChildDoorId();
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
        WorldPartition.checkOwner();
        if (isWall() && !isInInstance()) {
            if (!attacker.isServitor()) {
                return;
            }

            if (attacker.getTemplate().getRace() != Race.SIEGE_WEAPON) {
                return;
            }
        }

        super.reduceCurrentHp(damage, attacker, awake, isDOT, skill);
    }

    @Override
    public void reduceCurrentHpByDOT(double i, Creature attacker, Skill skill) {
        WorldPartition.checkOwner();
        // doors can't be damaged by DOTs
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        final boolean isFort = ((getFort() != null)
                && (getFort().getResidenceId() > 0)
                && getFort().getSiege().isInProgress());
        final boolean isCastle = ((getCastle() != null)
                && (getCastle().getResidenceId() > 0)
                && getCastle().getSiege().isInProgress());
        final boolean isHall =
                ((clanHall != null) && clanHall.isSiegableHall() && ((SiegableHall) clanHall).isInSiege());
        if (isFort || isCastle || isHall) {
            broadcastPacket(new SystemMessage(SystemMessageId.THE_CASTLE_GATE_HAS_BEEN_DESTROYED));
        }

        return true;
    }

    @Override
    public void moveToLocation(int x, int y, int z, int offset) {
        WorldPartition.checkOwner();
    }

    @Override
    public void stopMove(Location loc) {
        WorldPartition.checkOwner();
    }

    @Override
    public void doAttack(Creature target) {
        WorldPartition.checkOwner();
    }

    @Override
    public void doCast(Skill skill) {
        WorldPartition.checkOwner();
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        if (isVisibleFor(player)) {
            player.sendPacket(new StaticObjectInfo(this, player.isGM()));
            player.sendPacket(new DoorStatusUpdate(this));
            if (getEmitter() > 0) {
                player.sendPacket(new OnEventTrigger(this, !open));
            }
        }
    }

    public void setTargetable(boolean value) {
        WorldPartition.checkOwner();
        targetable = value;
        broadcastStatusUpdate();
    }

    public boolean checkCollision() {
        WorldPartition.checkOwner();
        return getTemplate().isCheckCollision();
    }

    // Instance doors are not in DoorData.
    private Door getSiblingDoor(int doorId) {
        if (!isInInstance()) {
            return DoorData.getInstance().getDoor(doorId);
        }

        final Instance inst = InstanceManager.getInstance().getInstance(getInstanceId());
        if (inst != null) {
            return inst.getDoor(doorId);
        }

        return null;
    }

    private void startAutoCloseTask() {
        final DoorTemplate template = getTemplate();
        if ((template.getCloseTime() < 0) || isOpenableByTime()) {
            return;
        }

        if (template.getClanHallId() > 0) {
            return;
        }

        final ScheduledFuture<?> oldTask = autoCloseTask;
        if (oldTask != null) {
            autoCloseTask = null;
            oldTask.cancel(false);
        }

        autoCloseTask = WorldPartition.loop()
                .schedule(new AutoClose(), getTemplate().getCloseTime() * 1000, TimeUnit.MILLISECONDS);
    }

    class AutoClose implements Runnable {
        @Override
        public void run() {
            if (open) {
                closeMe();
            }
        }
    }

    class TimerOpen implements Runnable {
        @Override
        public void run() {
            if (open) {
                closeMe();
            } else {
                openMe();
            }

            int delay = open ? getTemplate().getCloseTime() : getTemplate().getOpenTime();
            if (getTemplate().getRandomTime() > 0) {
                delay += ClockBridge.random().nextInt(getTemplate().getRandomTime());
            }

            WorldPartition.loop().schedule(this, delay * 1000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean isDoor() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Door asDoor() {
        WorldPartition.checkOwner();
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append('[');
        sb.append(((DoorTemplate) uncheckedTemplate()).getId());
        sb.append("](");
        sb.append(uncheckedObjectId());
        sb.append(')');
        return sb.toString();
    }
}
