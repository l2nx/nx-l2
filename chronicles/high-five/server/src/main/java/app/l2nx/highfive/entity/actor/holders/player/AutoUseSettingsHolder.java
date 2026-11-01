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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;

/**
 * @author Mobius
 */
@SingleWriter
public class AutoUseSettingsHolder {
    // Insertion order is the order the auto-use task uses them in.
    private final IntLinkedOpenHashSet autoSupplyItems = new IntLinkedOpenHashSet();
    private final IntLinkedOpenHashSet autoActions = new IntLinkedOpenHashSet();
    private final IntLinkedOpenHashSet autoBuffs = new IntLinkedOpenHashSet();
    private final IntArrayList autoSkills = new IntArrayList();

    private int autoPotionItem;
    private int skillIndex;

    public AutoUseSettingsHolder() {}

    public IntList getAutoSupplyItems() {
        WorldPartition.checkOwner();
        return snapshotOf(autoSupplyItems);
    }

    public void collectAutoSupplyItems(IntCollection into) {
        WorldPartition.checkOwner();
        into.addAll(autoSupplyItems);
    }

    public boolean isAutoSupplyItem(int itemId) {
        WorldPartition.checkOwner();
        return autoSupplyItems.contains(itemId);
    }

    public void addAutoSupplyItem(int itemId) {
        WorldPartition.checkOwner();
        autoSupplyItems.add(itemId);
    }

    public void removeAutoSupplyItem(int itemId) {
        WorldPartition.checkOwner();
        autoSupplyItems.remove(itemId);
    }

    public IntList getAutoActions() {
        WorldPartition.checkOwner();
        return snapshotOf(autoActions);
    }

    public boolean isAutoAction(int actionId) {
        WorldPartition.checkOwner();
        return autoActions.contains(actionId);
    }

    public void addAutoAction(int actionId) {
        WorldPartition.checkOwner();
        autoActions.add(actionId);
    }

    public void removeAutoAction(int actionId) {
        WorldPartition.checkOwner();
        autoActions.remove(actionId);
    }

    public IntList getAutoBuffs() {
        WorldPartition.checkOwner();
        return snapshotOf(autoBuffs);
    }

    public void collectAutoBuffs(IntCollection into) {
        WorldPartition.checkOwner();
        into.addAll(autoBuffs);
    }

    public boolean isAutoBuff(int skillId) {
        WorldPartition.checkOwner();
        return autoBuffs.contains(skillId);
    }

    public void addAutoBuff(int skillId) {
        WorldPartition.checkOwner();
        autoBuffs.add(skillId);
    }

    public void removeAutoBuff(int skillId) {
        WorldPartition.checkOwner();
        autoBuffs.remove(skillId);
    }

    public void clearAutoBuffs() {
        WorldPartition.checkOwner();
        autoBuffs.clear();
    }

    public IntList getAutoSkills() {
        WorldPartition.checkOwner();
        return snapshotOf(autoSkills);
    }

    public int getAutoSkillCount() {
        WorldPartition.checkOwner();
        return autoSkills.size();
    }

    public boolean isAutoSkill(int skillId) {
        WorldPartition.checkOwner();
        return autoSkills.contains(skillId);
    }

    public void addAutoSkill(int skillId) {
        WorldPartition.checkOwner();
        autoSkills.add(skillId);
    }

    public void removeAutoSkill(int skillId) {
        WorldPartition.checkOwner();
        autoSkills.rem(skillId);
    }

    public void clearAutoSkills() {
        WorldPartition.checkOwner();
        autoSkills.clear();
    }

    public int getAutoPotionItem() {
        WorldPartition.checkOwner();
        return autoPotionItem;
    }

    public void setAutoPotionItem(int itemId) {
        WorldPartition.checkOwner();
        autoPotionItem = itemId;
    }

    /**
     * @return the skill at the rotation cursor, or {@link Integer#MIN_VALUE} when no auto skill is set
     */
    public int getNextSkillId() {
        WorldPartition.checkOwner();
        if (autoSkills.isEmpty()) {
            skillIndex = 0;
            return Integer.MIN_VALUE;
        }

        if (skillIndex >= autoSkills.size()) {
            skillIndex = 0;
        }

        return autoSkills.getInt(skillIndex);
    }

    public void incrementSkillOrder() {
        WorldPartition.checkOwner();
        skillIndex++;
    }

    public void resetSkillOrder() {
        WorldPartition.checkOwner();
        skillIndex = 0;
    }

    public boolean isEmpty() {
        WorldPartition.checkOwner();
        return autoSupplyItems.isEmpty()
                && (autoPotionItem == 0)
                && autoSkills.isEmpty()
                && autoBuffs.isEmpty()
                && autoActions.isEmpty();
    }

    private static IntList snapshotOf(IntCollection ids) {
        return ids.isEmpty() ? IntLists.emptyList() : IntLists.unmodifiable(new IntArrayList(ids));
    }
}
