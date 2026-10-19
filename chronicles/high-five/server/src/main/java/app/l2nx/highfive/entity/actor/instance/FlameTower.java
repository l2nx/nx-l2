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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Tower;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.managers.ZoneManager;
import java.util.List;

/**
 * Class for Flame Control Tower instance.
 * @author JIV
 */
public class FlameTower extends Tower {
    private int _upgradeLevel = 0;
    private List<Integer> _zoneList;

    /**
     * Creates a flame tower.
     * @param template the flame tower NPC template
     */
    public FlameTower(NpcTemplate template) {
        super(template);
        setInstanceType(InstanceType.FlameTower);
    }

    @Override
    public boolean doDie(Creature killer) {
        enableZones(false);
        return super.doDie(killer);
    }

    @Override
    public boolean deleteMe() {
        enableZones(false);
        return super.deleteMe();
    }

    public void enableZones(boolean value) {
        if ((_zoneList != null) && (_upgradeLevel != 0)) {
            final int maxIndex = _upgradeLevel * 2;
            for (int i = 0; i < maxIndex; i++) {
                final ZoneType zone = ZoneManager.getInstance().getZoneById(_zoneList.get(i));
                if (zone != null) {
                    zone.setEnabled(value);
                }
            }
        }
    }

    public void setUpgradeLevel(int level) {
        _upgradeLevel = level;
    }

    public void setZoneList(List<Integer> list) {
        _zoneList = list;
        enableZones(true);
    }
}
