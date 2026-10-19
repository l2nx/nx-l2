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
package app.l2nx.highfive.data.holders;

import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.util.StatSet;
import lombok.Getter;

/**
 * @author HorridoJoho
 */
public class AccessLevel {
    /** The access level. */
    @Getter
    private int level = 0;
    /** The access level name. */
    @Getter
    private String name = null;
    /** Child access levels. */
    AccessLevel childsAccessLevel = null;
    /** Child access levels. */
    private int child = 0;
    /** The name color for the access level. */
    @Getter
    private int nameColor = 0;
    /** The title color for the access level. */
    @Getter
    private int titleColor = 0;
    /** Flag to determine if the access level has GM access. */
    @Getter
    private boolean gm = false;
    /** Flag for peace zone attack */
    private boolean allowPeaceAttack = false;
    /** Flag for fixed res */
    private boolean allowFixedRes = false;
    /** Flag for transactions */
    private boolean allowTransaction = false;
    /** Flag for AltG commands */
    private boolean allowAltG = false;
    /** Flag to give damage */
    private boolean giveDamage = false;
    /** Flag to take aggro */
    private boolean takeAggro = false;
    /** Flag to gain exp in party */
    private boolean gainExp = false;

    public AccessLevel(StatSet set) {
        level = set.getInt("level");
        name = set.getString("name");
        nameColor = Integer.decode("0x" + set.getString("nameColor", "FFFFFF"));
        titleColor = Integer.decode("0x" + set.getString("titleColor", "FFFFFF"));
        child = set.getInt("childAccess", 0);
        gm = set.getBoolean("isGM", false);
        allowPeaceAttack = set.getBoolean("allowPeaceAttack", false);
        allowFixedRes = set.getBoolean("allowFixedRes", false);
        allowTransaction = set.getBoolean("allowTransaction", true);
        allowAltG = set.getBoolean("allowAltg", false);
        giveDamage = set.getBoolean("giveDamage", true);
        takeAggro = set.getBoolean("takeAggro", true);
        gainExp = set.getBoolean("gainExp", true);
    }

    public AccessLevel() {
        level = 0;
        name = "User";
        nameColor = Integer.decode("0xFFFFFF");
        titleColor = Integer.decode("0xFFFFFF");
        child = 0;
        gm = false;
        allowPeaceAttack = false;
        allowFixedRes = false;
        allowTransaction = true;
        allowAltG = false;
        giveDamage = true;
        takeAggro = true;
        gainExp = true;
    }

    /**
     * Returns if the access level is allowed to attack in peace zone or not
     * @return boolean: true if the access level is allowed to attack in peace zone, otherwise false
     */
    public boolean allowPeaceAttack() {
        return allowPeaceAttack;
    }

    /**
     * Retruns if the access level is allowed to use fixed res or not
     * @return true if the access level is allowed to use fixed res, otherwise false
     */
    public boolean allowFixedRes() {
        return allowFixedRes;
    }

    /**
     * Returns if the access level is allowed to perform transactions or not
     * @return boolean: true if access level is allowed to perform transactions, otherwise false
     */
    public boolean allowTransaction() {
        return allowTransaction;
    }

    /**
     * Returns if the access level is allowed to use AltG commands or not
     * @return boolean: true if access level is allowed to use AltG commands, otherwise false
     */
    public boolean allowAltG() {
        return allowAltG;
    }

    /**
     * Returns if the access level can give damage or not
     * @return boolean: true if the access level can give damage, otherwise false
     */
    public boolean canGiveDamage() {
        return giveDamage;
    }

    /**
     * Returns if the access level can take aggro or not
     * @return boolean: true if the access level can take aggro, otherwise false
     */
    public boolean canTakeAggro() {
        return takeAggro;
    }

    /**
     * Returns if the access level can gain exp or not
     * @return boolean: true if the access level can gain exp, otherwise false
     */
    public boolean canGainExp() {
        return gainExp;
    }

    /**
     * Returns if the access level contains allowedAccess as child
     * @param accessLevel as AccessLevel
     * @return boolean: true if a child access level is equals to allowedAccess, otherwise false
     */
    public boolean hasChildAccess(AccessLevel accessLevel) {
        if (childsAccessLevel == null) {
            if (child <= 0) {
                return false;
            }

            childsAccessLevel = AdminData.getInstance().getAccessLevel(child);
        }

        return (childsAccessLevel != null)
                && ((childsAccessLevel.getLevel() == accessLevel.getLevel())
                        || childsAccessLevel.hasChildAccess(accessLevel));
    }
}
