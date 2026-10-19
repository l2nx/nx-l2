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

import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.zone.type.ResidenceZone;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.skill.holders.SkillHolder;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * @author xban1x
 */
public abstract class AbstractResidence extends ListenersContainer {
    @Getter
    private final int residenceId;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private ResidenceZone residenceZone = null;

    @Getter
    private final List<SkillHolder> residentialSkills = new ArrayList<>();

    public AbstractResidence(int residenceId) {
        this.residenceId = residenceId;
        initResidentialSkills();
    }

    protected abstract void load();

    protected abstract void initResidenceZone();

    protected void initResidentialSkills() {
        for (SkillLearn s : SkillTreeData.getInstance().getAvailableResidentialSkills(getResidenceId())) {
            residentialSkills.add(new SkillHolder(s.getSkillId(), s.getSkillLevel()));
        }
    }

    public void giveResidentialSkills(Player player) {
        if (!residentialSkills.isEmpty()) {
            for (SkillHolder sh : residentialSkills) {
                player.addSkill(sh.getSkill(), false);
            }
        }
    }

    public void removeResidentialSkills(Player player) {
        if (!residentialSkills.isEmpty()) {
            for (SkillHolder sh : residentialSkills) {
                player.removeSkill(sh.getSkill(), false);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof AbstractResidence) && (((AbstractResidence) obj).getResidenceId() == getResidenceId());
    }

    @Override
    public String toString() {
        return name + "(" + residenceId + ")";
    }
}
