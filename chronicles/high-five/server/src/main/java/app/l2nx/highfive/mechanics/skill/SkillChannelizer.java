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
package app.l2nx.highfive.mechanics.skill;

import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.item.enums.ShotType;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.serverpackets.MagicSkillLaunched;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.LocationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Skill Channelizer implementation.
 * @author UnAfraid
 */
@Slf4j
public class SkillChannelizer implements Runnable {

    @Getter
    private final Creature channelizer;

    private List<Creature> channelized;

    @Getter
    private Skill skill;

    private ScheduledFuture<?> task = null;

    public SkillChannelizer(Creature channelizer) {
        this.channelizer = channelizer;
    }

    public List<Creature> getChannelized() {
        return channelized;
    }

    public boolean hasChannelized() {
        return channelized != null;
    }

    public void startChanneling(Skill skill) {
        // Verify for same status.
        if (isChanneling()) {
            log.warn("Character: {} is attempting to channel skill but he already does!", channelizer);
            return;
        }

        // Start channeling.
        this.skill = skill;
        final long tickInterval = skill.getChannelingTickInterval();
        if (tickInterval > 0) {
            task = ThreadPool.scheduleAtFixedRate(this, skill.getChannelingTickInitialDelay(), tickInterval);
        } else {
            // A skill without a tick interval channels once, a period of zero would be rejected by the pool.
            task = ThreadPool.schedule(this, skill.getChannelingTickInitialDelay());
        }
    }

    public void stopChanneling() {
        // Verify for same status.
        if (!isChanneling()) {
            log.warn("Character: {} is attempting to stop channel skill but he does not!", channelizer);
            return;
        }

        // Cancel the task and unset it.
        task.cancel(true);
        task = null;

        // Cancel target channelization and unset it.
        if (channelized != null) {
            for (Creature creature : channelized) {
                creature.getSkillChannelized().removeChannelizer(skill.getChannelingSkillId(), channelizer);
            }

            channelized = null;
        }

        // unset skill.
        skill = null;
    }

    public boolean isChanneling() {
        return task != null;
    }

    @Override
    public void run() {
        if (!isChanneling()) {
            return;
        }

        final Skill skill = this.skill;
        if (skill == null) {
            return;
        }

        try {
            if (skill.getMpPerChanneling() > 0) {
                // Validate mana per tick.
                if (channelizer.getCurrentMp() < skill.getMpPerChanneling()) {
                    if (channelizer.isPlayer()) {
                        channelizer.sendPacket(SystemMessageId.YOUR_SKILL_WAS_DEACTIVATED_DUE_TO_LACK_OF_MP);
                    }

                    channelizer.abortCast();
                    return;
                }

                // Reduce mana per tick.
                channelizer.reduceCurrentMp(skill.getMpPerChanneling());
            }

            // Apply channeling skills on the targets.
            if (skill.getChannelingSkillId() > 0) {
                final Skill baseSkill = SkillData.getInstance().getSkill(skill.getChannelingSkillId(), 1);
                if (baseSkill == null) {
                    log.warn("Skill {} couldn't find effect id skill: {} !", skill, skill.getChannelingSkillId());
                    channelizer.abortCast();
                    return;
                }

                final List<Creature> targetList = new ArrayList<>();
                for (WorldObject chars : skill.getTargetList(channelizer)) {
                    if (chars.isCreature()) {
                        targetList.add(chars.asCreature());
                        chars.asCreature()
                                .getSkillChannelized()
                                .addChannelizer(skill.getChannelingSkillId(), channelizer);
                    }
                }

                if (targetList.isEmpty()) {
                    return;
                }

                channelized = targetList;

                for (Creature creature : channelized) {
                    if (!LocationUtil.checkIfInRange(skill.getEffectRange(), channelizer, creature, true)) {
                        continue;
                    } else if (!GeoEngine.getInstance().canSeeTarget(channelizer, creature)) {
                        continue;
                    } else {
                        final int maxSkillLevel = SkillData.getInstance().getMaxLevel(skill.getChannelingSkillId());
                        final int skillLevel = Math.min(
                                creature.getSkillChannelized().getChannerlizersSize(skill.getChannelingSkillId()),
                                maxSkillLevel);
                        if (skillLevel == 0) {
                            continue;
                        }

                        final BuffInfo info =
                                creature.getEffectList().getBuffInfoBySkillId(skill.getChannelingSkillId());
                        if ((info == null) || (info.getSkill().getLevel() < skillLevel)) {
                            final Skill channelingSkill =
                                    SkillData.getInstance().getSkill(skill.getChannelingSkillId(), skillLevel);
                            if (channelingSkill == null) {
                                log.warn("Non existent channeling skill requested: {}", skill);
                                channelizer.abortCast();
                                return;
                            }

                            // Update PvP status
                            if (creature.isPlayable()
                                    && channelizer.isPlayer()
                                    && channelingSkill.hasNegativeEffect()) {
                                channelizer.asPlayer().updatePvPStatus(creature);
                            }

                            channelingSkill.applyEffects(channelizer, creature);

                            // Reduce shots.
                            if (skill.useSpiritShot()) {
                                channelizer.setChargedShot(
                                        channelizer.isChargedShot(ShotType.BLESSED_SPIRITSHOTS)
                                                ? ShotType.BLESSED_SPIRITSHOTS
                                                : ShotType.SPIRITSHOTS,
                                        false);
                            } else {
                                channelizer.setChargedShot(ShotType.SOULSHOTS, false);
                            }

                            // Shots are re-charged every cast.
                            channelizer.rechargeShots(skill.useSoulShot(), skill.useSpiritShot());
                        }

                        channelizer.broadcastSkillPacket(
                                new MagicSkillLaunched(channelizer, skill.getId(), skill.getLevel(), creature),
                                creature);
                    }
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Error while channelizing skill: {} channelizer: {} channelized: {}; ",
                    skill,
                    channelizer,
                    channelized,
                    e);
        }
    }
}
