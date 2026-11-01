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
package app.l2nx.highfive.entity.actor.instance;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.ai.CreatureAI;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.stat.StaticObjectStat;
import app.l2nx.highfive.entity.actor.status.StaticObjectStatus;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShowTownMap;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StaticObjectInfo;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * @author godson
 */
@SingleWriter
public class StaticObject extends Creature {
    public static final int INTERACTION_DISTANCE = 150;

    private final int id;

    private int meshIndex = 0;

    private int type = -1; // 0 - map signs, 1 - throne , 2 - arena signs

    private ShowTownMap map;

    public StaticObject(CreatureTemplate template, int staticId) {
        super(template);
        setInstanceType(InstanceType.StaticObject);
        id = staticId;
    }

    @Override
    public int getId() {
        WorldPartition.checkOwner();
        return id;
    }

    public int getMeshIndex() {
        WorldPartition.checkOwner();
        return meshIndex;
    }

    public int getType() {
        WorldPartition.checkOwner();
        return type;
    }

    public void setType(int type) {
        WorldPartition.checkOwner();
        this.type = type;
    }

    public ShowTownMap getMap() {
        WorldPartition.checkOwner();
        return map;
    }

    @Override
    protected CreatureAI initAI() {
        return null;
    }

    @Override
    public StaticObjectStat getStat() {
        WorldPartition.checkOwner();
        return (StaticObjectStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        WorldPartition.checkOwner();
        setStat(new StaticObjectStat(this));
    }

    @Override
    public StaticObjectStatus getStatus() {
        WorldPartition.checkOwner();
        return (StaticObjectStatus) super.getStatus();
    }

    @Override
    public void initCharStatus() {
        WorldPartition.checkOwner();
        setStatus(new StaticObjectStatus(this));
    }

    public void setMap(String texture, int x, int y) {
        WorldPartition.checkOwner();
        map = new ShowTownMap("town_map." + texture, x, y);
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        return 1;
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
    public boolean isAutoAttackable(Creature attacker) {
        WorldPartition.checkOwner();
        return false;
    }

    /** 0 = default textures, 1 = alternate textures. */
    public void setMeshIndex(int meshIndex) {
        WorldPartition.checkOwner();
        this.meshIndex = meshIndex;
        broadcastPacket(new StaticObjectInfo(this));
    }

    @Override
    public void updateAbnormalEffect() {
        WorldPartition.checkOwner();
    }

    @Override
    public void sendInfo(Player player) {
        WorldPartition.checkOwner();
        player.sendPacket(new StaticObjectInfo(this));
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
}
