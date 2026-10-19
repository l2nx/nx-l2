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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

/**
 * @author Mobius
 */
public class AutoUseSettingsHolder {
    @Getter
    private final Collection<Integer> autoSupplyItems = ConcurrentHashMap.newKeySet();

    @Getter
    private final Collection<Integer> autoActions = ConcurrentHashMap.newKeySet();

    @Getter
    private final Collection<Integer> autoBuffs = ConcurrentHashMap.newKeySet();

    @Getter
    private final List<Integer> autoSkills = new CopyOnWriteArrayList<>();

    private final AtomicInteger autoPotionItem = new AtomicInteger();
    private int skillIndex = 0;

    public AutoUseSettingsHolder() {}

    public int getAutoPotionItem() {
        return autoPotionItem.get();
    }

    public void setAutoPotionItem(int itemId) {
        autoPotionItem.set(itemId);
    }

    public boolean isAutoSkill(int skillId) {
        return autoSkills.contains(skillId) || autoBuffs.contains(skillId);
    }

    public Integer getNextSkillId() {
        if (skillIndex >= autoSkills.size()) {
            skillIndex = 0;
        }

        Integer skillId = Integer.MIN_VALUE;
        try {
            skillId = autoSkills.get(skillIndex);
        } catch (Exception e) {
            resetSkillOrder();
        }

        return skillId;
    }

    public void incrementSkillOrder() {
        skillIndex++;
    }

    public void resetSkillOrder() {
        skillIndex = 0;
    }

    public boolean isEmpty() {
        return autoSupplyItems.isEmpty()
                && (autoPotionItem.get() == 0)
                && autoSkills.isEmpty()
                && autoBuffs.isEmpty()
                && autoActions.isEmpty();
    }
}
