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
package app.l2nx.highfive.scripts.handlers.skill.effects;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.stat.CreatureStat;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.EffectCalculationType;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.mechanics.stats.functions.FuncAdd;
import app.l2nx.highfive.mechanics.stats.functions.FuncMul;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.SystemMessage;
import app.l2nx.highfive.util.StatSet;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Zealar
 */
@Slf4j
public class MaxCp extends AbstractEffect {
    private final double power;
    private final EffectCalculationType type;
    private final boolean heal;

    public MaxCp(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);

        type = params.getEnum("type", EffectCalculationType.class, EffectCalculationType.DIFF);
        switch (type) {
            case DIFF: {
                power = params.getInt("power", 0);
                break;
            }
            default: {
                power = 1 + (params.getInt("power", 0) / 100.0);
            }
        }

        heal = params.getBoolean("heal", false);
        if (params.isEmpty()) {
            log.warn("Must have parameters.");
        }
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        final CreatureStat charStat = effected.getStat();
        final double currentCp = effected.getCurrentCp();
        double amount = power;

        synchronized (charStat) {
            switch (type) {
                case DIFF: {
                    charStat.getActiveChar().addStatFunc(new FuncAdd(Stat.MAX_CP, 1, this, power, null));
                    if (heal) {
                        effected.setCurrentCp((currentCp + power));
                    }
                    break;
                }
                case PER: {
                    final double maxCp = effected.getMaxCp();
                    charStat.getActiveChar().addStatFunc(new FuncMul(Stat.MAX_CP, 1, this, power, null));
                    if (heal) {
                        amount = (power - 1) * maxCp;
                        effected.setCurrentCp(currentCp + amount);
                    }
                    break;
                }
            }
        }

        if (heal) {
            if ((effector != null) && (effector != effected)) {
                final SystemMessage sm = new SystemMessage(SystemMessageId.S2_CP_HAS_BEEN_RESTORED_BY_C1);
                sm.addString(effector.getName());
                sm.addInt((int) amount);
                effected.sendPacket(sm);
            } else {
                final SystemMessage sm = new SystemMessage(SystemMessageId.S1_CP_HAS_BEEN_RESTORED);
                sm.addInt((int) amount);
                effected.sendPacket(sm);
            }
        }
    }

    @Override
    public void onExit(Creature effector, Creature effected, Skill skill) {
        final CreatureStat charStat = effected.getStat();
        synchronized (charStat) {
            charStat.getActiveChar().removeStatsOwner(this);
        }
    }
}
