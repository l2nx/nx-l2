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
import app.l2nx.highfive.network.protocol.highfive.serverpackets.ShowTownMap;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.StaticObjectInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Static Object instance.
 * @author godson
 */
public class StaticObject extends Creature {
    /** The interaction distance of the StaticObject */
    public static final int INTERACTION_DISTANCE = 150;

    @Getter
    private final int id;

    @Getter
    private int meshIndex = 0; // 0 - static objects, alternate static objects

    @Getter
    @Setter
    private int type = -1; // 0 - map signs, 1 - throne , 2 - arena signs

    @Getter
    private ShowTownMap map;

    /**
     * Creates a static object.
     * @param template the static object
     * @param staticId the static ID
     */
    public StaticObject(CreatureTemplate template, int staticId) {
        super(template);
        setInstanceType(InstanceType.StaticObject);
        id = staticId;
    }

    @Override
    protected CreatureAI initAI() {
        return null;
    }

    @Override
    public StaticObjectStat getStat() {
        return (StaticObjectStat) super.getStat();
    }

    @Override
    public void initCharStat() {
        setStat(new StaticObjectStat(this));
    }

    @Override
    public StaticObjectStatus getStatus() {
        return (StaticObjectStatus) super.getStatus();
    }

    @Override
    public void initCharStatus() {
        setStatus(new StaticObjectStatus(this));
    }

    public void setMap(String texture, int x, int y) {
        map = new ShowTownMap("town_map." + texture, x, y);
    }

    @Override
    public int getLevel() {
        return 1;
    }

    @Override
    public Item getActiveWeaponInstance() {
        return null;
    }

    @Override
    public Weapon getActiveWeaponItem() {
        return null;
    }

    @Override
    public Item getSecondaryWeaponInstance() {
        return null;
    }

    @Override
    public Weapon getSecondaryWeaponItem() {
        return null;
    }

    @Override
    public boolean isAutoAttackable(Creature attacker) {
        return false;
    }

    /**
     * Set the meshIndex of the object.<br>
     * <br>
     * <b><u>Values</u>:</b>
     * <ul>
     * <li>default textures : 0</li>
     * <li>alternate textures : 1</li>
     * </ul>
     * @param meshIndex
     */
    public void setMeshIndex(int meshIndex) {
        this.meshIndex = meshIndex;
        broadcastPacket(new StaticObjectInfo(this));
    }

    @Override
    public void updateAbnormalEffect() {}

    @Override
    public void sendInfo(Player player) {
        player.sendPacket(new StaticObjectInfo(this));
    }

    @Override
    public void moveToLocation(int x, int y, int z, int offset) {}

    @Override
    public void stopMove(Location loc) {}

    @Override
    public void doAttack(Creature target) {}

    @Override
    public void doCast(Skill skill) {}
}
