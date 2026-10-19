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

import app.l2nx.highfive.data.xml.CubicData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Cubic;
import app.l2nx.highfive.entity.actor.templates.CubicTemplate;
import app.l2nx.highfive.mechanics.conditions.Condition;
import app.l2nx.highfive.mechanics.effects.AbstractEffect;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.util.StatSet;
import lombok.extern.slf4j.Slf4j;

/**
 * Summons a cubic from XML template data.<br>
 * Keeps High Five skill parameters as runtime overrides.
 * @author Zoey76
 */
@Slf4j
public class SummonCubic extends AbstractEffect {
    private final int cubicId;
    private final int cubicLevel;
    private final int cubicPower;
    private final int cubicDuration;
    private final int cubicDelay;
    private final int cubicMaxCount;
    private final int cubicSkillChance;

    public SummonCubic(Condition attachCond, Condition applyCond, StatSet set, StatSet params) {
        super(attachCond, applyCond, set, params);
        cubicId = params.getInt("cubicId", -1);
        cubicLevel = params.getInt("cubicLvl", params.getInt("cubicSkillLevel", 0));
        cubicPower = params.getInt("cubicPower", 0);
        cubicDuration = params.getInt("cubicDuration", 0);
        cubicDelay = params.getInt("cubicDelay", 0);
        cubicMaxCount = params.getInt("cubicMaxCount", -1);
        cubicSkillChance = params.getInt("cubicSkillChance", 0);
    }

    @Override
    public boolean isInstant() {
        return true;
    }

    @Override
    public void onStart(Creature effector, Creature effected, Skill skill) {
        if ((effected == null) || !effected.isPlayer() || effected.isAlikeDead()) {
            return;
        }

        if (cubicId < 0) {
            log.warn("Invalid cubic id: {} skillId: {}", cubicId, skill.getId());
            return;
        }

        final Player player = effected.asPlayer();
        if (player.inObserverMode() || player.isMounted()) {
            return;
        }

        int cubicTemplateLevel = cubicLevel > 0 ? cubicLevel : skill.getLevel();
        if ((cubicLevel <= 0) && (cubicTemplateLevel > 100)) {
            cubicTemplateLevel = ((skill.getLevel() - 100) / 7) + 8;
        }

        final CubicTemplate template = CubicData.getInstance().getCubicTemplate(cubicId, cubicTemplateLevel);
        if (template == null) {
            log.warn(
                    "Missing cubic template. cubicId: {} level: {} skillId: {}",
                    cubicId,
                    cubicTemplateLevel,
                    skill.getId());
            return;
        }

        final Cubic cubic = player.getCubicById(cubicId);
        if (cubic != null) {
            player.removeCubic(cubicId);
        } else {
            final int allowedCubicCount = Math.max(1, player.getStat().getMaxCubicCount());
            while (player.getCubics().size() >= allowedCubicCount) {
                if (player.removeFirstCubic() == null) {
                    break;
                }
            }
        }

        player.addCubic(
                cubicId,
                template.getLevel(),
                cubicPower,
                cubicDelay,
                cubicSkillChance,
                cubicMaxCount,
                cubicDuration,
                effected != effector);
        player.broadcastUserInfo();
    }
}
