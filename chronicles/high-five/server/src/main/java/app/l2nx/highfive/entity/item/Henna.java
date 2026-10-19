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
package app.l2nx.highfive.entity.item;

import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.util.StatSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * Class for the Henna object.
 * @author Zoey76
 */
public class Henna {
    @Getter
    private final int dyeId;

    @Getter
    private final int dyeItemId;

    @Getter
    private final int statSTR;

    @Getter
    private final int statCON;

    @Getter
    private final int statDEX;

    @Getter
    private final int statINT;

    @Getter
    private final int statMEN;

    @Getter
    private final int statWIT;

    @Getter
    private final int wearFee;

    @Getter
    private final int wearCount;

    @Getter
    private final int cancelFee;

    @Getter
    private final int cancelCount;

    @Getter
    private final Set<PlayerClass> allowedWearClass;

    public Henna(StatSet set) {
        dyeId = set.getInt("dyeId");
        dyeItemId = set.getInt("dyeItemId");
        statSTR = set.getInt("str", 0);
        statCON = set.getInt("con", 0);
        statDEX = set.getInt("dex", 0);
        statINT = set.getInt("int", 0);
        statMEN = set.getInt("men", 0);
        statWIT = set.getInt("wit", 0);
        wearFee = set.getInt("wear_fee");
        wearCount = set.getInt("wear_count");
        cancelFee = set.getInt("cancel_fee");
        cancelCount = set.getInt("cancel_count");
        allowedWearClass = new HashSet<>();
    }

    /**
     * @param playerClass the class trying to wear this dye.
     * @return {@code true} if the player is allowed to wear this dye, {@code false} otherwise.
     */
    public boolean isAllowedClass(PlayerClass playerClass) {
        return allowedWearClass.contains(playerClass);
    }

    /**
     * @param wearClassIds the list of classes that can wear this dye.
     */
    public void setWearClassIds(List<PlayerClass> wearClassIds) {
        allowedWearClass.addAll(wearClassIds);
    }
}
