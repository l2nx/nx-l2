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
package app.l2nx.highfive.entity.actor.holders.npc;

import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * Defines an NPC walking route.<br>
 * Stores an ordered node list and repeat policy.
 * <ul>
 * <li>Fail-fast validation for route name and node list.</li>
 * <li>Repeat is enabled only for repeat types 0..2.</li>
 * <li>Provides helpers for route traversal.</li>
 * </ul>
 * @author BazookaRpm
 */
public class WalkRoute {
    // Constants.
    private static final byte REPEAT_MIN = 0;
    private static final byte REPEAT_MID = 1;
    private static final byte REPEAT_MAX = 2;

    // Route Definition.
    @Getter
    private final String name;

    @Getter
    private final List<WalkNode> nodeList;

    // Repeat Policy.
    private final boolean repeatWalk;
    private boolean stopAfterCycle;

    @Getter
    private final byte repeatType;

    public WalkRoute(String name, List<WalkNode> route, boolean repeat, byte repeatType) {
        this.name = Objects.requireNonNull(name, "name");
        nodeList = Objects.requireNonNull(route, "route");

        if (nodeList.isEmpty()) {
            throw new IllegalArgumentException("WalkRoute node list is empty: " + this.name);
        }

        for (int i = 0; i < nodeList.size(); i++) {
            if (nodeList.get(i) == null) {
                throw new IllegalArgumentException("WalkRoute has null node at index " + i + ": " + this.name);
            }
        }

        this.repeatType = repeatType;

        boolean supported = false;
        switch (this.repeatType) {
            case REPEAT_MIN:
            case REPEAT_MID:
            case REPEAT_MAX: {
                supported = true;
                break;
            }
            default: {
                supported = false;
                break;
            }
        }

        if ((this.repeatType < REPEAT_MIN) || (this.repeatType > REPEAT_MAX)) {
            supported = false;
        }

        repeatWalk = repeat && supported;
    }

    public boolean repeatWalk() {
        return repeatWalk;
    }

    public boolean doOnce() {
        return stopAfterCycle;
    }

    public WalkNode getLastNode() {
        final int lastIndex = nodeList.size() - 1;
        return nodeList.get(lastIndex);
    }

    public int getNodesCount() {
        return nodeList.size();
    }
}
