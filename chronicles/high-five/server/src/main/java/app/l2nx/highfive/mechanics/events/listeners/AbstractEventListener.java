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
package app.l2nx.highfive.mechanics.events.listeners;

import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.ListenersContainer;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;
import app.l2nx.highfive.mechanics.events.returns.AbstractEventReturn;
import lombok.Getter;

/**
 * @author UnAfraid
 */
public abstract class AbstractEventListener {
    // Fixed at construction: the container orders a listener by priority when it is added.
    @Getter
    private final int priority;

    @Getter
    private final ListenersContainer container;

    @Getter
    private final EventType type;

    @Getter
    private final Object owner;

    public AbstractEventListener(ListenersContainer container, EventType type, Object owner) {
        this(container, type, owner, 0);
    }

    protected AbstractEventListener(ListenersContainer container, EventType type, Object owner, int priority) {
        this.priority = priority;
        this.container = container;
        this.type = type;
        this.owner = owner;
    }

    public abstract <R extends AbstractEventReturn> R executeEvent(IBaseEvent event, Class<R> returnBackClass);

    public void unregisterMe() {
        container.removeListener(this);
    }
}
