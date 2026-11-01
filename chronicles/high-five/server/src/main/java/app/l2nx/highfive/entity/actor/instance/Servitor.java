/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.sql.CharSummonTable;
import app.l2nx.highfive.data.sql.SummonEffectTable;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.enums.creature.InstanceType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemHolder;
import app.l2nx.highfive.mechanics.skill.EffectScope;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.skill.StoredEffect;
import app.l2nx.highfive.mechanics.skill.enums.SkillFinishType;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SetSummonRemainTime;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.pet.model.SummonEffectRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * @author UnAfraid
 */
@Slf4j
@SingleWriter
public class Servitor extends Summon implements Runnable {

    private float expMultiplier = 0;

    private ItemHolder itemConsume;

    private int lifeTime;

    private int lifeTimeRemaining;

    private int itemConsumeInterval;

    private int consumeItemIntervalRemaining;
    protected ScheduledFuture<?> summonLifeTask;

    private int referenceSkill;

    public Servitor(NpcTemplate template, Player owner) {
        super(template, owner);
        setInstanceType(InstanceType.Servitor);
        setShowSummonAnimation(true);
    }

    public float getExpMultiplier() {
        WorldPartition.checkOwner();
        return expMultiplier;
    }

    public void setExpMultiplier(float expMultiplier) {
        WorldPartition.checkOwner();
        this.expMultiplier = expMultiplier;
    }

    public ItemHolder getItemConsume() {
        WorldPartition.checkOwner();
        return itemConsume;
    }

    public void setItemConsume(ItemHolder itemConsume) {
        WorldPartition.checkOwner();
        this.itemConsume = itemConsume;
    }

    public int getLifeTime() {
        WorldPartition.checkOwner();
        return lifeTime;
    }

    public int getLifeTimeRemaining() {
        WorldPartition.checkOwner();
        return lifeTimeRemaining;
    }

    public void setLifeTimeRemaining(int lifeTimeRemaining) {
        WorldPartition.checkOwner();
        this.lifeTimeRemaining = lifeTimeRemaining;
    }

    public int getItemConsumeInterval() {
        WorldPartition.checkOwner();
        return itemConsumeInterval;
    }

    public int getReferenceSkill() {
        WorldPartition.checkOwner();
        return referenceSkill;
    }

    public void setReferenceSkill(int referenceSkill) {
        WorldPartition.checkOwner();
        this.referenceSkill = referenceSkill;
    }

    @Override
    public void onSpawn() {
        WorldPartition.checkOwner();
        super.onSpawn();
        if (summonLifeTask == null) {
            summonLifeTask = WorldPartition.loop().scheduleAtFixedRate(this, 0, 5000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public int getLevel() {
        WorldPartition.checkOwner();
        final NpcTemplate template = (NpcTemplate) uncheckedTemplate();
        return (template != null) ? template.getLevel() : 0;
    }

    @Override
    public int getSummonType() {
        WorldPartition.checkOwner();
        return 1;
    }

    public void setItemConsumeInterval(int interval) {
        WorldPartition.checkOwner();
        itemConsumeInterval = interval;
        consumeItemIntervalRemaining = interval;
    }

    public void setLifeTime(int lifeTime) {
        WorldPartition.checkOwner();
        this.lifeTime = lifeTime;
        lifeTimeRemaining = lifeTime;
    }

    @Override
    public boolean doDie(Creature killer) {
        WorldPartition.checkOwner();
        if (!super.doDie(killer)) {
            return false;
        }

        if (summonLifeTask != null) {
            summonLifeTask.cancel(false);
        }

        CharSummonTable.getInstance().removeServitor(getOwner());
        return true;
    }

    @Override
    public void doPickupItem(WorldObject object) {
        WorldPartition.checkOwner();
    }

    @Override
    public void setRestoreSummon(boolean value) {
        WorldPartition.checkOwner();
        restoreSummon = value;
    }

    @Override
    public void stopSkillEffects(SkillFinishType type, int skillId) {
        WorldPartition.checkOwner();
        super.stopSkillEffects(type, skillId);
        SummonEffectTable.getInstance().removeServitorEffects(getOwner(), getReferenceSkill(), skillId);
    }

    @Override
    public void storeMe() {
        WorldPartition.checkOwner();
        if ((referenceSkill == 0) || isDead()) {
            return;
        }

        if (PlayerConfig.RESTORE_SERVITOR_ON_RECONNECT) {
            CharSummonTable.getInstance().saveSummon(this);
        }
    }

    @Override
    public void storeEffect(boolean storeEffects) {
        WorldPartition.checkOwner();
        if (!PlayerConfig.SUMMON_STORE_SKILL_COOLTIME
                || (getOwner() == null)
                || getOwner().isInOlympiadMode()) {
            return;
        }

        SummonEffectTable.getInstance().clearServitorEffects(getOwner(), getReferenceSkill());

        final List<SummonEffectRow> rows = new ArrayList<>();
        if (storeEffects) {
            for (Entry<Skill, StoredEffect> entry : effectsToStore().entrySet()) {
                final Skill skill = entry.getKey();
                final StoredEffect stored = entry.getValue();
                rows.add(SummonEffectRow.builder()
                        .skillId(skill.getId())
                        .skillLevel(skill.getLevel())
                        .remainingMillis(stored.remainingMillis())
                        .tickPhaseMillis(stored.tickPhaseMillis())
                        .build());

                SummonEffectTable.getInstance().addServitorEffect(getOwner(), getReferenceSkill(), skill, stored);
            }
        }

        try {
            CharSummonTable.getInstance()
                    .saveServitorEffects(getOwner().getObjectId(), getOwner().getClassIndex(), referenceSkill, rows);
        } catch (DataAccessException e) {
            log.warn("Could not store summon effect data: ", e);
        }
    }

    @Override
    public void restoreEffects() {
        WorldPartition.checkOwner();
        if (getOwner().isInOlympiadMode()) {
            return;
        }

        final int ownerId = getOwner().getObjectId();
        final int classIndex = getOwner().getClassIndex();
        try {
            if (!SummonEffectTable.getInstance().containsSkill(getOwner(), getReferenceSkill())) {
                for (SummonEffectRow row :
                        CharSummonTable.getInstance().findServitorEffects(ownerId, classIndex, referenceSkill)) {
                    final Skill skill = SkillData.getInstance().getSkill(row.skillId(), row.skillLevel());
                    if (skill == null) {
                        continue;
                    }

                    if (skill.hasEffects(EffectScope.GENERAL)) {
                        CharSummonTable.toStoredEffect(row, ownerId)
                                .ifPresent(stored -> SummonEffectTable.getInstance()
                                        .addServitorEffect(getOwner(), getReferenceSkill(), skill, stored));
                    }
                }
            }

            CharSummonTable.getInstance().deleteServitorEffects(ownerId, classIndex, referenceSkill);
        } catch (DataAccessException e) {
            log.warn("Could not restore {} active effect data: {}", this, e.getMessage(), e);
        } finally {
            SummonEffectTable.getInstance().applyServitorEffects(this, getOwner(), getReferenceSkill());
        }
    }

    @Override
    public void unSummon(Player owner) {
        WorldPartition.checkOwner();
        if (summonLifeTask != null) {
            summonLifeTask.cancel(false);
        }

        super.unSummon(owner);

        if (!restoreSummon) {
            CharSummonTable.getInstance().removeServitor(owner);
        }
    }

    @Override
    public boolean destroyItem(
            ItemProcessType process, int objectId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        return getOwner().destroyItem(process, objectId, count, reference, sendMessage);
    }

    @Override
    public boolean destroyItemByItemId(
            ItemProcessType process, int itemId, long count, WorldObject reference, boolean sendMessage) {
        WorldPartition.checkOwner();
        return getOwner().destroyItemByItemId(process, itemId, count, reference, sendMessage);
    }

    @Override
    public byte getAttackElement() {
        WorldPartition.checkOwner();
        return getOwner() != null ? getOwner().getAttackElement() : super.getAttackElement();
    }

    @Override
    public int getAttackElementValue(byte attackAttribute) {
        WorldPartition.checkOwner();
        return getOwner() != null
                ? getOwner().getAttackElementValue(attackAttribute)
                : super.getAttackElementValue(attackAttribute);
    }

    @Override
    public int getDefenseElementValue(byte defenseAttribute) {
        WorldPartition.checkOwner();
        return getOwner() != null
                ? getOwner().getDefenseElementValue(defenseAttribute)
                : super.getDefenseElementValue(defenseAttribute);
    }

    @Override
    public boolean isServitor() {
        WorldPartition.checkOwner();
        return true;
    }

    @Override
    public Servitor asServitor() {
        WorldPartition.checkOwner();
        return this;
    }

    @Override
    public void run() {
        WorldPartition.checkOwner();
        final int usedtime = 5000;
        lifeTimeRemaining -= usedtime;
        if (isDead() || !isSpawned()) {
            if (summonLifeTask != null) {
                summonLifeTask.cancel(false);
            }
            return;
        }

        if (lifeTimeRemaining < 0) {
            sendPacket(SystemMessageId.YOUR_SERVITOR_PASSED_AWAY);
            unSummon(getOwner());
            return;
        }

        if (itemConsumeInterval > 0) {
            consumeItemIntervalRemaining -= usedtime;

            if ((consumeItemIntervalRemaining <= 0)
                    && (itemConsume.getCount() > 0)
                    && (itemConsume.getId() > 0)
                    && !isDead()) {
                if (destroyItemByItemId(null, itemConsume.getId(), itemConsume.getCount(), this, false)) {
                    final SystemMessage msg = new SystemMessage(SystemMessageId.A_SUMMONED_MONSTER_USES_S1);
                    msg.addItemName(itemConsume.getId());
                    sendPacket(msg);

                    consumeItemIntervalRemaining = itemConsumeInterval;
                } else {
                    sendPacket(
                            SystemMessageId
                                    .SINCE_YOU_DO_NOT_HAVE_ENOUGH_ITEMS_TO_MAINTAIN_THE_SERVITOR_S_STAY_THE_SERVITOR_HAS_DISAPPEARED);
                    unSummon(getOwner());
                }
            }
        }

        sendPacket(new SetSummonRemainTime(lifeTime, lifeTimeRemaining));
        updateEffectIcons();
    }

    @Override
    public double getMAtk(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getMAtk(target, skill);
        }

        return super.getMAtk(target, skill)
                + (player.getMAtk(target, skill) * (player.getServitorShareBonus(Stat.MAGIC_ATTACK) - 1.0));
    }

    @Override
    public double getMDef(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getMDef(target, skill);
        }

        return super.getMDef(target, skill)
                + (player.getMDef(target, skill) * (player.getServitorShareBonus(Stat.MAGIC_DEFENCE) - 1.0));
    }

    @Override
    public double getPAtk(Creature target) {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getPAtk(target);
        }

        return super.getPAtk(target)
                + (player.getPAtk(target) * (player.getServitorShareBonus(Stat.POWER_ATTACK) - 1.0));
    }

    @Override
    public double getPDef(Creature target) {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getPDef(target);
        }

        return super.getPDef(target)
                + (player.getPDef(target) * (player.getServitorShareBonus(Stat.POWER_DEFENCE) - 1.0));
    }

    @Override
    public int getMAtkSpd() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getMAtkSpd();
        }

        return (int) (super.getMAtkSpd()
                + (player.getMAtkSpd() * (player.getServitorShareBonus(Stat.MAGIC_ATTACK_SPEED) - 1.0)));
    }

    @Override
    public int getMaxHp() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getMaxHp();
        }

        return (int) (super.getMaxHp() + (player.getMaxHp() * (player.getServitorShareBonus(Stat.MAX_HP) - 1.0)));
    }

    @Override
    public int getMaxMp() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getMaxMp();
        }

        return (int) (super.getMaxMp() + (player.getMaxMp() * (player.getServitorShareBonus(Stat.MAX_MP) - 1.0)));
    }

    @Override
    public int getCriticalHit(Creature target, Skill skill) {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getCriticalHit(target, skill);
        }

        return (int) (super.getCriticalHit(target, skill)
                + ((player.getCriticalHit(target, skill)) * (player.getServitorShareBonus(Stat.CRITICAL_RATE) - 1.0)));
    }

    @Override
    public double getPAtkSpd() {
        WorldPartition.checkOwner();
        final Player player = asPlayer();
        if (player == null) {
            return super.getPAtkSpd();
        }

        return super.getPAtkSpd()
                + (player.getPAtkSpd() * (player.getServitorShareBonus(Stat.POWER_ATTACK_SPEED) - 1.0));
    }
}
