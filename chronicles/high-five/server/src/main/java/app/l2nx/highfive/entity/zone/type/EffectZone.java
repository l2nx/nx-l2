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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.network.protocol.p267.serverpackets.EtcStatusUpdate;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Another type of damage zone with skills.
 * @author Kerberos
 */
@Slf4j
public class EffectZone extends ZoneType {
    @Getter
    int chance;

    private int initialDelay;
    private int reuse;
    protected boolean bypassConditions;
    private boolean showDangerIcon;
    private boolean removeEffectsOnExit;
    protected Map<Integer, Integer> skills;
    protected volatile Future<?> task;

    public EffectZone(int id) {
        super(id);
        chance = 100;
        initialDelay = 0;
        reuse = 30000;
        setTargetType(InstanceType.Playable); // default only playable
        bypassConditions = false;
        showDangerIcon = true;
        removeEffectsOnExit = false;
    }

    @Override
    public void setParameter(String name, String value) {
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
                skills = new ConcurrentHashMap<>(Integer.parseInt(value));
                break;
            }
            case "showDangerIcon": {
                showDangerIcon = Boolean.parseBoolean(value);
                break;
            }
            case "skillIdLvl": {
                final String[] propertySplit = value.split(";");
                skills = new ConcurrentHashMap<>(propertySplit.length);
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
        if (skills != null) {
            Future<?> task = this.task;
            if (task == null) {
                synchronized (this) {
                    task = this.task;
                    if (task == null) {
                        this.task = task = WorldPartition.loop()
                                .scheduleAtFixedRate(new ApplySkill(), initialDelay, reuse, TimeUnit.MILLISECONDS);
                    }
                }
            }
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
                for (Entry<Integer, Integer> e : skills.entrySet()) {
                    final Skill skill = SkillData.getInstance()
                            .getSkill(e.getKey().intValue(), e.getValue().intValue());
                    if ((skill != null) && creature.isAffectedBySkill(skill.getId())) {
                        creature.stopSkillEffects(SkillFinishType.REMOVED, skill.getId());
                    }
                }
            }
        }

        if (getCharactersInside().isEmpty() && (task != null)) {
            task.cancel(true);
            task = null;
        }
    }

    public void addSkill(int skillId, int skillLevel) {
        if (skillLevel < 1) // remove skill
        {
            removeSkill(skillId);
            return;
        }

        if (skills == null) {
            synchronized (this) {
                if (skills == null) {
                    skills = new ConcurrentHashMap<>(3);
                }
            }
        }

        skills.put(skillId, skillLevel);
    }

    public void removeSkill(int skillId) {
        if (skills != null) {
            skills.remove(skillId);
        }
    }

    public void clearSkills() {
        if (skills != null) {
            skills.clear();
        }
    }

    public int getSkillLevel(int skillId) {
        if ((skills == null) || !skills.containsKey(skillId)) {
            return 0;
        }

        return skills.get(skillId);
    }

    private class ApplySkill implements Runnable {
        protected ApplySkill() {
            if (skills == null) {
                throw new IllegalStateException("No skills defined.");
            }
        }

        @Override
        public void run() {
            if (getCharactersInside().isEmpty()) {
                if (task != null) {
                    task.cancel(false);
                    task = null;
                }
                return;
            }

            if (!isEnabled()) {
                return;
            }

            for (Creature character : getCharactersInside()) {
                if ((character != null)
                        && character.isPlayer()
                        && !character.isDead()
                        && (ClockBridge.random().nextInt(100) < chance)) {
                    for (Entry<Integer, Integer> e : skills.entrySet()) {
                        final Skill skill = SkillData.getInstance()
                                .getSkill(e.getKey().intValue(), e.getValue().intValue());
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
