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

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.managers.WalkingManager;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.npc.OnNpcMoveRouteFinished;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.Setter;

/**
 * Holds info about current walk progress.<br>
 * Maintains the route cursor and runtime state for an NPC walking task.
 * <ul>
 * <li>Supports sequential and random traversal.</li>
 * <li>Dispatches route-finished event on last node reach.</li>
 * <li>Stores a check task reference and debug timestamp.</li>
 * </ul>
 * @author BazookaRpm
 */
public class WalkInfo {
    // Constants.
    private static final int FLAG_BLOCKED = 0x01;
    private static final int FLAG_SUSPENDED = 0x02;
    private static final int FLAG_STOPPED_BY_ATTACK = 0x04;

    private static final int STEP_FORWARD = 1;

    private static final int NODE_FIRST = 0;
    private static final int NODE_SECOND = 1;

    // Route Data.
    private final String routeName;

    // Task Data.
    @Getter
    @Setter
    private ScheduledFuture<?> walkCheckTask;

    // Runtime State.
    private int stateFlags;

    @Getter
    private int currentNodeId;

    private int step;

    // Debug Data.
    @Getter
    @Setter
    private long lastAction;

    public WalkInfo(String routeName) {
        this.routeName = routeName;
        walkCheckTask = null;

        stateFlags = 0;
        currentNodeId = NODE_FIRST;
        step = STEP_FORWARD;

        lastAction = 0L;
    }

    public boolean isBlocked() {
        return (stateFlags & FLAG_BLOCKED) != 0;
    }

    public void setBlocked(boolean value) {
        int flags = stateFlags;
        if (value) {
            flags |= FLAG_BLOCKED;
        } else {
            flags &= ~FLAG_BLOCKED;
        }
        stateFlags = flags;
    }

    public boolean isSuspended() {
        return (stateFlags & FLAG_SUSPENDED) != 0;
    }

    public void setSuspended(boolean value) {
        int flags = stateFlags;
        if (value) {
            flags |= FLAG_SUSPENDED;
        } else {
            flags &= ~FLAG_SUSPENDED;
        }
        stateFlags = flags;
    }

    public boolean isStoppedByAttack() {
        return (stateFlags & FLAG_STOPPED_BY_ATTACK) != 0;
    }

    public void setStoppedByAttack(boolean value) {
        int flags = stateFlags;
        if (value) {
            flags |= FLAG_STOPPED_BY_ATTACK;
        } else {
            flags &= ~FLAG_STOPPED_BY_ATTACK;
        }
        stateFlags = flags;
    }

    public WalkRoute getRoute() {
        final WalkingManager manager = WalkingManager.getInstance();
        final WalkRoute route = manager.getRoute(routeName);
        return route;
    }

    public WalkNode getCurrentNode() {
        final WalkRoute route = getRoute();
        int cursor = currentNodeId;
        if (cursor < NODE_FIRST) {
            cursor = NODE_FIRST;
        }

        final int size = route.getNodeList().size();
        final int last = size - 1;
        if (cursor > last) {
            cursor = last;
        }

        return route.getNodeList().get(cursor);
    }

    public synchronized void calculateNextNode(Npc npc) {
        final WalkRoute route = getRoute();
        final int nodesCount = route.getNodesCount();
        final byte repeatType = route.getRepeatType();
        if (repeatType == WalkingManager.REPEAT_RANDOM) {
            if (nodesCount <= NODE_SECOND) {
                currentNodeId = NODE_FIRST;
                return;
            }

            final int base = currentNodeId;
            int selected = base;
            while (selected == base) {
                final int candidate = ClockBridge.random().nextInt(nodesCount);
                if (candidate != base) {
                    selected = candidate;
                }
            }

            currentNodeId = selected;
            return;
        }

        int cursor = currentNodeId;
        cursor += step;
        currentNodeId = cursor;

        if (currentNodeId == nodesCount) {
            final EventDispatcher dispatcher = EventDispatcher.getInstance();
            if (dispatcher.hasListener(EventType.ON_NPC_MOVE_ROUTE_FINISHED, npc)) {
                dispatcher.notifyEventAsync(new OnNpcMoveRouteFinished(npc), npc);
            }

            if (!route.repeatWalk()) {
                WalkingManager.getInstance().cancelMoving(npc);
                return;
            }

            switch (repeatType) {
                case WalkingManager.REPEAT_GO_BACK: {
                    step = -STEP_FORWARD;
                    currentNodeId = nodesCount - 2;
                    break;
                }
                case WalkingManager.REPEAT_GO_FIRST: {
                    step = STEP_FORWARD;
                    currentNodeId = NODE_FIRST;
                    break;
                }
                case WalkingManager.REPEAT_TELE_FIRST: {
                    npc.teleToLocation(npc.getSpawn().getLocation());
                    step = STEP_FORWARD;
                    currentNodeId = NODE_FIRST;
                    break;
                }
                default: {
                    break;
                }
            }
            return;
        }

        if (currentNodeId == WalkingManager.NO_REPEAT) {
            currentNodeId = NODE_SECOND;
            step = STEP_FORWARD;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(220);
        sb.append("WalkInfo [routeName=");
        sb.append(routeName);
        sb.append(", walkCheckTask=");
        sb.append(walkCheckTask);
        sb.append(", stateFlags=0x");
        sb.append(Integer.toHexString(stateFlags));
        sb.append(", cursor=");
        sb.append(currentNodeId);
        sb.append(", step=");
        sb.append(step);
        sb.append(", blocked=");
        sb.append(isBlocked());
        sb.append(", suspended=");
        sb.append(isSuspended());
        sb.append(", stoppedByAttack=");
        sb.append(isStoppedByAttack());
        sb.append(", lastActionTime=");
        sb.append(lastAction);
        sb.append(']');
        return sb.toString();
    }
}
