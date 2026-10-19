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
package app.l2nx.highfive.entity.item.type;

import lombok.Getter;

/**
 * Crystal Type enumerated.
 * @author Adry_85
 */
public enum CrystalType {
    NONE(0, 0, 0, 0),
    D(1, 1458, 11, 90),
    C(2, 1459, 6, 45),
    B(3, 1460, 11, 67),
    A(4, 1461, 20, 145),
    S(5, 1462, 25, 250),
    S80(6, 1462, 25, 250),
    S84(7, 1462, 25, 250);

    @Getter
    private final int level;

    @Getter
    private final int crystalId;

    @Getter
    private final int crystalEnchantBonusArmor;

    @Getter
    private final int crystalEnchantBonusWeapon;

    CrystalType(int level, int crystalId, int crystalEnchantBonusArmor, int crystalEnchantBonusWeapon) {
        this.level = level;
        this.crystalId = crystalId;
        this.crystalEnchantBonusArmor = crystalEnchantBonusArmor;
        this.crystalEnchantBonusWeapon = crystalEnchantBonusWeapon;
    }

    public boolean isGreater(CrystalType crystalType) {
        return getLevel() > crystalType.getLevel();
    }

    public boolean isLesser(CrystalType crystalType) {
        return getLevel() < crystalType.getLevel();
    }
}
