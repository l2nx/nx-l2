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
package app.l2nx.highfive.entity.zone.type;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.IntSnapshot;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EtcStatusUpdate;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Another type of damage zone with skills.
 * @author Kerberos
 */
@Slf4j
@SingleWriter
public class EffectZone extends ZoneType {
    private int chance;

    private int initialDelay;
    private int reuse;
    private boolean bypassConditions;
    private boolean showDangerIcon;
    private boolean removeEffectsOnExit;
    private Int2IntLinkedOpenHashMap skills;
    private ScheduledFuture<?> task;

    public EffectZone(int id) {
        super(id, InstanceType.Playable);
        chance = 100;
        initialDelay = 0;
        reuse = 30000;
        bypassConditions = false;
        showDangerIcon = true;
        removeEffectsOnExit = false;
    }

    public int getChance() {
        WorldPartition.checkOwner();
        return chance;
    }

    @Override
    public void setParameter(String name, String value) {
        WorldPartition.checkOwner();
        switch (name) {
            case "chance": {
                chance = Integer.parseInt(value);
                break;
            }
            case "initialDelay": {
                initialDelay = Integer.parseInt(value);
                break;
            }
            case "reuse": {
                reuse = parsePositiveParameter(name, value, reuse);
                break;
            }
            case "bypassSkillConditions": {
                bypassConditions = Boolean.parseBoolean(value);
                break;
            }
            case "maxDynamicSkillCount": {
                skills = new Int2IntLinkedOpenHashMap(Integer.parseInt(value));
                break;
            }
            case "showDangerIcon": {
                showDangerIcon = Boolean.parseBoolean(value);
                break;
            }
            case "skillIdLvl": {
                final String[] propertySplit = value.split(";");
                skills = new Int2IntLinkedOpenHashMap(propertySplit.length);
                for (String skill : propertySplit) {
                    final String[] skillSplit = skill.split("-");
                    if (skillSplit.length != 2) {
                        log.warn("Invalid config property -> skillsIdLvl \"{}\"", skill);
                    } else {
                        try {
                            skills.put(Integer.parseInt(skillSplit[0]), Integer.parseInt(skillSplit[1]));
                        } catch (NumberFormatException nfe) {
                            if (!skill.isEmpty()) {
                                log.warn(
                                        "Invalid config property -> skillsIdLvl \"{}\"{}",
                                        skillSplit[0],
                                        skillSplit[1]);
                            }
                        }
                    }
                }
                break;
            }
            case "removeEffectsOnExit": {
                removeEffectsOnExit = Boolean.parseBoolean(value);
                break;
            }
            default: {
                super.setParameter(name, value);
            }
        }
    }

    @Override
    protected void onEnter(Creature creature) {
        if ((skills != null) && (task == null)) {
            task = WorldPartition.loop()
                    .scheduleAtFixedRate(new ApplySkill(), initialDelay, reuse, TimeUnit.MILLISECONDS);
        }

        if (creature.isPlayer()) {
            creature.setInsideZone(ZoneId.ALTERED, true);
            if (showDangerIcon) {
                creature.setInsideZone(ZoneId.DANGER_AREA, true);
                creature.sendPacket(new EtcStatusUpdate(creature.asPlayer()));
            }
        }
    }

    @Override
    protected void onExit(Creature creature) {
        if (creature.isPlayer()) {
            creature.setInsideZone(ZoneId.ALTERED, false);
            if (showDangerIcon) {
                creature.setInsideZone(ZoneId.DANGER_AREA, false);
                if (!creature.isInsideZone(ZoneId.DANGER_AREA)) {
                    creature.sendPacket(new EtcStatusUpdate(creature.asPlayer()));
                }
            }

            if (removeEffectsOnExit && (skills != null)) {
                // Stopping an effect calls out, so the walk runs over a copy of the skill ids.
                try (IntSnapshot skillIds = ScratchBuffers.intSnapshot(skills.keySet())) {
                    for (int i = 0; i < skillIds.size(); i++) {
                        final int skillId = skillIds.getInt(i);
                        if (!skills.containsKey(skillId)) {
                            continue;
                        }

                        final Skill skill = SkillData.getInstance().getSkill(skillId, skills.get(skillId));
                        if ((skill != null) && creature.isAffectedBySkill(skill.getId())) {
                            creature.stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
                        }
                    }
                }
            }
        }

        if (!hasCharactersInside() && (task != null)) {
            task.cancel(true);
            task = null;
        }
    }

    public void addSkill(int skillId, int skillLevel) {
        WorldPartition.checkOwner();
        if (skillLevel < 1) {
            removeSkill(skillId);
            return;
        }

        if (skills == null) {
            skills = new Int2IntLinkedOpenHashMap(3);
        }

        skills.put(skillId, skillLevel);
    }

    public void removeSkill(int skillId) {
        WorldPartition.checkOwner();
        if (skills != null) {
            skills.remove(skillId);
        }
    }

    public void clearSkills() {
        WorldPartition.checkOwner();
        if (skills != null) {
            skills.clear();
        }
    }

    /** The level of the skill the zone applies, or 0 when it applies no such skill. */
    public int getSkillLevel(int skillId) {
        WorldPartition.checkOwner();
        return skills == null ? 0 : skills.get(skillId);
    }

    private class ApplySkill implements Runnable {
        protected ApplySkill() {
            if (skills == null) {
                throw new IllegalStateException("No skills defined.");
            }
        }

        @Override
        public void run() {
            if (!hasCharactersInside()) {
                if (task != null) {
                    task.cancel(false);
                    task = null;
                }
                return;
            }

            if (!isEnabled()) {
                return;
            }

            // Applying a skill may change the creatures inside or the skills, so both walks run over copies.
            try (Snapshot<Creature> inside = ScratchBuffers.empty();
                    IntSnapshot skillIds = ScratchBuffers.intSnapshot(skills.keySet())) {
                collectCharactersInside(inside);
                for (Creature character : inside) {
                    if (character.isPlayer()
                            && !character.isDead()
                            && (ClockBridge.random().nextInt(100) < chance)) {
                        for (int i = 0; i < skillIds.size(); i++) {
                            final int skillId = skillIds.getInt(i);
                            if (!skills.containsKey(skillId)) {
                                continue;
                            }

                            final Skill skill = SkillData.getInstance().getSkill(skillId, skills.get(skillId));
                            if ((skill != null)
                                    && (bypassConditions || skill.checkCondition(character, character, false))) {
                                if (!character.isAffectedBySkill(skill.getId())) {
                                    skill.applyEffects(character, character);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
