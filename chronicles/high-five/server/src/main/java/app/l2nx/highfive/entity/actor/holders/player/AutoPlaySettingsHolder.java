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

/**
 * @author Mobius
 */
@SingleWriter
public class AutoPlaySettingsHolder {
    private int options;
    private boolean pickup = true;
    private int nextTargetMode = 1;
    private boolean shortRange;
    private boolean respectfulHunting = true;
    private int autoPotionPercent = 50;

    public AutoPlaySettingsHolder() {}

    public int getOptions() {
        WorldPartition.checkOwner();
        return options;
    }

    public void setOptions(int options) {
        WorldPartition.checkOwner();
        this.options = options;
    }

    public boolean doPickup() {
        WorldPartition.checkOwner();
        return pickup;
    }

    public void setPickup(boolean value) {
        WorldPartition.checkOwner();
        pickup = value;
    }

    public int getNextTargetMode() {
        WorldPartition.checkOwner();
        return nextTargetMode;
    }

    public void setNextTargetMode(int nextTargetMode) {
        WorldPartition.checkOwner();
        this.nextTargetMode = nextTargetMode;
    }

    public boolean isShortRange() {
        WorldPartition.checkOwner();
        return shortRange;
    }

    public void setShortRange(boolean value) {
        WorldPartition.checkOwner();
        shortRange = value;
    }

    public boolean isRespectfulHunting() {
        WorldPartition.checkOwner();
        return respectfulHunting;
    }

    public void setRespectfulHunting(boolean value) {
        WorldPartition.checkOwner();
        respectfulHunting = value;
    }

    public int getAutoPotionPercent() {
        WorldPartition.checkOwner();
        return autoPotionPercent;
    }

    public void setAutoPotionPercent(int value) {
        WorldPartition.checkOwner();
        autoPotionPercent = value;
    }
}
