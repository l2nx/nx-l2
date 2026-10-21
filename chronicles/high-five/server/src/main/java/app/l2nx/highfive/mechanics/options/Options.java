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
package app.l2nx.highfive.mechanics.options;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.functions.AbstractFunction;
import app.l2nx.highfive.mechanics.stats.functions.FuncTemplate;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SkillCoolTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * @author UnAfraid
 */
public class Options {
    @Getter
    private final int id;

    private final List<FuncTemplate> funcs = new ArrayList<>();

    @Setter
    private Skill activeSkill = null;

    @Setter
    private Skill passiveSkill = null;

    private final List<OptionSkillHolder> activationSkills = new ArrayList<>();

    /**
     * @param id
     */
    public Options(int id) {
        this.id = id;
    }

    public boolean hasFuncs() {
        return !funcs.isEmpty();
    }

    public List<AbstractFunction> getStatFuncs(Item item, Creature creature) {
        if (this.funcs.isEmpty()) {
            return Collections.<AbstractFunction>emptyList();
        }

        final List<AbstractFunction> funcs = new ArrayList<>(this.funcs.size());
        for (FuncTemplate fuctionTemplate : this.funcs) {
            final AbstractFunction fuction = fuctionTemplate.getFunc(creature, creature, item, this);
            if (fuction != null) {
                funcs.add(fuction);
            }
        }

        return funcs;
    }

    public void addFunc(FuncTemplate template) {
        funcs.add(template);
    }

    public boolean hasActiveSkill() {
        return activeSkill != null;
    }

    public Skill getActiveSkill() {
        return activeSkill;
    }

    public boolean hasPassiveSkill() {
        return passiveSkill != null;
    }

    public Skill getPassiveSkill() {
        return passiveSkill;
    }

    public boolean hasActivationSkills() {
        return !activationSkills.isEmpty();
    }

    public boolean hasActivationSkills(OptionSkillType type) {
        for (OptionSkillHolder holder : activationSkills) {
            if (holder.skillType() == type) {
                return true;
            }
        }

        return false;
    }

    public List<OptionSkillHolder> getActivationSkills() {
        return activationSkills;
    }

    public List<OptionSkillHolder> getActivationSkills(OptionSkillType type) {
        final List<OptionSkillHolder> temp = new ArrayList<>();
        for (OptionSkillHolder holder : activationSkills) {
            if (holder.skillType() == type) {
                temp.add(holder);
            }
        }

        return temp;
    }

    public void addActivationSkill(OptionSkillHolder holder) {
        activationSkills.add(holder);
    }

    public void apply(Player player) {
        if (!funcs.isEmpty()) {
            player.addStatFuncs(getStatFuncs(null, player));
        }

        if (hasActiveSkill()) {
            addSkill(player, activeSkill);
        }

        if (hasPassiveSkill()) {
            addSkill(player, passiveSkill);
        }

        if (!activationSkills.isEmpty()) {
            for (OptionSkillHolder holder : activationSkills) {
                player.addTriggerSkill(holder);
            }
        }

        player.sendSkillList();
    }

    public void remove(Player player) {
        if (!funcs.isEmpty()) {
            player.removeStatsOwner(this);
        }

        if (hasActiveSkill()) {
            player.removeSkill(activeSkill, false, false);
        }

        if (hasPassiveSkill()) {
            player.removeSkill(passiveSkill, false, true);
        }

        if (!activationSkills.isEmpty()) {
            for (OptionSkillHolder holder : activationSkills) {
                player.removeTriggerSkill(holder);
            }
        }

        player.sendSkillList();
    }

    private void addSkill(Player player, Skill skill) {
        boolean updateTimeStamp = false;
        player.addSkill(skill, false);
        if (skill.isActive()) {
            final long remainingTime = player.getSkillRemainingReuseTime(skill.getReuseHashCode());
            if (remainingTime > 0) {
                player.addTimeStamp(skill, remainingTime);
                player.disableSkill(skill, remainingTime);
            }

            updateTimeStamp = true;
        }

        if (updateTimeStamp) {
            player.sendPacket(new SkillCoolTime(player));
        }
    }
}
