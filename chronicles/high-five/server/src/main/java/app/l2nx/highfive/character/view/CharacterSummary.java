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
package app.l2nx.highfive.character.view;

import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import java.util.List;
import lombok.Builder;

/**
 * A character as the selection screen shows it.
 *
 * @param deleteTimer the epoch millisecond the character is deleted at, {@code 0} when it is not marked
 * @param lastAccess the epoch millisecond of its last login, {@code 0} for never
 * @param paperdoll indexed by paperdoll slot ({@code Inventory.PAPERDOLL_*}); an empty slot is all zeros
 */
@Builder(toBuilder = true)
public record CharacterSummary(
        int objectId,
        String name,
        int accessLevel,
        int level,
        long exp,
        long sp,
        int maxHp,
        double currentHp,
        int maxMp,
        double currentMp,
        int karma,
        int pkKills,
        int pvpKills,
        int face,
        int hairStyle,
        int hairColor,
        Sex sex,
        Race race,
        int clanId,
        int classId,
        int baseClassId,
        int x,
        int y,
        int z,
        boolean good,
        boolean evil,
        int vitalityPoints,
        int augmentationId,
        long deleteTimer,
        long lastAccess,
        List<PaperdollSlot> paperdoll) {
    public CharacterSummary {
        paperdoll = List.copyOf(paperdoll);
    }

    public int paperdollItemId(int slot) {
        return paperdoll.get(slot).itemId();
    }

    public int enchantEffect() {
        return paperdoll.get(Inventory.PAPERDOLL_RHAND).enchantLevel();
    }

    /** @param itemId the item id the client draws, which transmogrification may change */
    @Builder(toBuilder = true)
    public record PaperdollSlot(int objectId, int itemId, int enchantLevel) {}
}
