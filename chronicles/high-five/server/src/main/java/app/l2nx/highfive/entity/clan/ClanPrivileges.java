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
package app.l2nx.highfive.entity.clan;

import lombok.Getter;
import lombok.Setter;

/**
 * The {@link ClanPrivileges} class manages a set of {@link ClanAccess} for clan members using a mask representation.
 * @author Mobius
 */
public class ClanPrivileges {
    @Getter
    @Setter
    private int mask;

    /**
     * Initializes a new instance of ClanPrivileges with all privileges disabled.
     */
    public ClanPrivileges() {
        mask = 0;
    }

    /**
     * Initializes a new instance of ClanPrivileges with the specified {@link ClanAccess} mask.
     * @param mask The mask representing the privileges to be set.
     */
    public ClanPrivileges(int mask) {
        this.mask = mask;
    }

    /**
     * Disables all privileges by setting the mask to 0.
     */
    public void disableAll() {
        mask = 0;
    }

    /**
     * Enables all possible privileges by setting the mask to the complete mask.
     */
    public void enableAll() {
        mask = getCompleteMask();
    }

    /**
     * Calculates and returns a mask with all privileges enabled.
     * @return The integer mask with all bits corresponding to {@link ClanAccess} set to 1.
     */
    public static int getCompleteMask() {
        int mask = 0;
        for (ClanAccess access : ClanAccess.values()) {
            mask |= 1 << access.ordinal();
        }

        return mask;
    }

    /**
     * Sets the specified privileges, disabling any previously set privileges.
     * @param privileges The array of {@link ClanAccess} to enable.
     */
    public void setPrivileges(ClanAccess... privileges) {
        disableAll();
        for (ClanAccess access : privileges) {
            mask |= 1 << access.ordinal();
        }
    }

    /**
     * Sets the specified minimum privilege and additional privileges, disabling any others.
     * @param minimum The minimum {@link ClanAccess} to enable.
     * @param privileges Additional {@link ClanAccess} to enable.
     */
    public void setMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        disableAll();
        addMinimumPrivileges(minimum, privileges);
    }

    /**
     * Adds the specified minimum privilege and additional privileges to the current mask.
     * @param minimum The minimum {@link ClanAccess} to add.
     * @param privileges Additional {@link ClanAccess} to add.
     */
    public void addMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        mask |= 1 << minimum.ordinal();
        for (ClanAccess access : privileges) {
            mask |= 1 << access.ordinal();
        }
    }

    /**
     * Removes the specified minimum privilege and additional privileges from the current mask.
     * @param minimum The minimum {@link ClanAccess} to remove.
     * @param privileges Additional {@link ClanAccess} to remove.
     */
    public void removeMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        mask &= ~(1 << minimum.ordinal());
        for (ClanAccess access : privileges) {
            mask &= ~(1 << access.ordinal());
        }
    }

    /**
     * Checks if the specified minimum privilege and additional privileges are enabled in the mask.
     * @param minimum The minimum {@link ClanAccess} to check.
     * @param privileges Additional {@link ClanAccess} to check.
     * @return true if all specified privileges are enabled; false otherwise.
     */
    public boolean hasMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        if ((mask & (1 << minimum.ordinal())) == 0) {
            return false;
        }

        for (ClanAccess access : privileges) {
            if ((mask & (1 << access.ordinal())) == 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Creates a clone of this ClanPrivileges instance, preserving the current mask.
     * @return A new instance of ClanPrivileges with the same {@link ClanAccess} mask.
     */
    @Override
    public ClanPrivileges clone() {
        return new ClanPrivileges(mask);
    }
}
