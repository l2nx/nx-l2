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

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.partition.WorldPartition;

/**
 * A set of {@link ClanAccess} stored as a bitmask, the bit position being the ordinal.
 * @author Mobius
 */
@SingleWriter
public class ClanPrivileges {
    private static final int COMPLETE_MASK = completeMask();

    private int mask;

    public ClanPrivileges() {
        mask = 0;
    }

    public ClanPrivileges(int mask) {
        this.mask = mask;
    }

    public int getMask() {
        WorldPartition.checkOwner();
        return mask;
    }

    public void setMask(int mask) {
        WorldPartition.checkOwner();
        this.mask = mask;
    }

    public void disableAll() {
        WorldPartition.checkOwner();
        mask = 0;
    }

    public void enableAll() {
        WorldPartition.checkOwner();
        mask = COMPLETE_MASK;
    }

    public static int getCompleteMask() {
        WorldPartition.checkOwner();
        return COMPLETE_MASK;
    }

    private static int completeMask() {
        int mask = 0;
        for (ClanAccess access : ClanAccess.values()) {
            mask |= 1 << access.ordinal();
        }

        return mask;
    }

    public void setPrivileges(ClanAccess... privileges) {
        WorldPartition.checkOwner();
        mask = 0;
        for (ClanAccess access : privileges) {
            mask |= 1 << access.ordinal();
        }
    }

    public void setMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        WorldPartition.checkOwner();
        mask = 0;
        addMinimumPrivileges(minimum, privileges);
    }

    public void addMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        WorldPartition.checkOwner();
        mask |= 1 << minimum.ordinal();
        for (ClanAccess access : privileges) {
            mask |= 1 << access.ordinal();
        }
    }

    public void removeMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        WorldPartition.checkOwner();
        mask &= ~(1 << minimum.ordinal());
        for (ClanAccess access : privileges) {
            mask &= ~(1 << access.ordinal());
        }
    }

    public boolean hasMinimumPrivileges(ClanAccess minimum, ClanAccess... privileges) {
        WorldPartition.checkOwner();
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

    @Override
    public ClanPrivileges clone() {
        WorldPartition.checkOwner();
        return new ClanPrivileges(mask);
    }
}
