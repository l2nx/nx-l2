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
package app.l2nx.highfive.entity.residences;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.type.ResidenceZone;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.List;

/**
 * @author xban1x
 */
@SingleWriter
public abstract class AbstractResidence extends ListenersContainer {
    private final int residenceId;

    private String name;

    private ResidenceZone residenceZone = null;

    // Fixed by the skill tree at construction, so granting walks it without a copy.
    private final List<SkillHolder> residentialSkills;

    public AbstractResidence(int residenceId) {
        this.residenceId = residenceId;
        residentialSkills = SkillTreeData.getInstance().getAvailableResidentialSkills(residenceId).stream()
                .map(skill -> new SkillHolder(skill.getSkillId(), skill.getSkillLevel()))
                .toList();
    }

    protected abstract void load();

    protected abstract void initResidenceZone();

    public int getResidenceId() {
        WorldPartition.checkOwner();
        return residenceId;
    }

    public String getName() {
        WorldPartition.checkOwner();
        return name;
    }

    public void setName(String name) {
        WorldPartition.checkOwner();
        this.name = name;
    }

    public ResidenceZone getResidenceZone() {
        WorldPartition.checkOwner();
        return residenceZone;
    }

    protected void setResidenceZone(ResidenceZone residenceZone) {
        this.residenceZone = residenceZone;
    }

    public void giveResidentialSkills(Player player) {
        WorldPartition.checkOwner();
        for (SkillHolder sh : residentialSkills) {
            player.addSkill(sh.getSkill(), false);
        }
    }

    public void removeResidentialSkills(Player player) {
        WorldPartition.checkOwner();
        for (SkillHolder sh : residentialSkills) {
            player.removeSkill(sh.getSkill(), false);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof AbstractResidence other) && (other.residenceId == residenceId);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(residenceId);
    }

    @Override
    public String toString() {
        return name + "(" + residenceId + ")";
    }
}
