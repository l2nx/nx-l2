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
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;

/**
 * Annotation event listener provides dynamically attached callback to any method operation with or without any return object.
 * @author UnAfraid
 */
@Slf4j
public class AnnotationEventListener extends AbstractEventListener {
    private final Method callback;

    public AnnotationEventListener(
            ListenersContainer container, EventType type, Method callback, Object owner, int priority) {
        super(container, type, owner);
        this.callback = callback;
        setPriority(priority);
    }

    @Override
    public <R extends AbstractEventReturn> R executeEvent(IBaseEvent event, Class<R> returnBackClass) {
        try {
            final Object result = callback.invoke(getOwner(), event);
            if (callback.getReturnType() == returnBackClass) {
                return returnBackClass.cast(result);
            }
        } catch (Exception e) {
            log.warn("Error while invoking {} on {}", callback.getName(), getOwner(), e);
        }

        return null;
    }
}
