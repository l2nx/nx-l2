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
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.undergroundColiseum.UCTeam;
import app.l2nx.highfive.partition.WorldPartition;

@SingleWriter
public class UCTower extends Folk {
    private UCTeam team;

    public UCTower(UCTeam team, NpcTemplate template) {
        super(template);
        this.team = team;
    }

    @Override
    public boolean canBeAttacked() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public boolean isAutoAttackable(Creature creature) {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public void reduceCurrentHp(double damage, Creature attacker, boolean awake, boolean isDOT, Skill skill) {
        WorldPartition.checkOwner();
        if (attacker.getTeam() == getTeam()) {
            return;
        }

        if (damage < getStatus().getCurrentHp()) {
            getStatus().setCurrentHp(getStatus().getCurrentHp() - damage);
        } else {
            doDie(attacker);
        }
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        if (team != null) {
            team.deleteTower();
            team = null;
        }

        return true;
    }

    @Override
    public Team getTeam() {
        WorldPartition.checkOwner();
        return team.getIndex() == 1 ? Team.BLUE : Team.RED;
    }
}
