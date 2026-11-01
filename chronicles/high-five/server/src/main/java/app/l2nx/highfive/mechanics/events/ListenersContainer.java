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
package app.l2nx.highfive.mechanics.events;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.mechanics.events.listeners.AbstractEventListener;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author UnAfraid, Mobius
 */
@SingleWriter
public class ListenersContainer {
    private EnumMap<EventType, ObjectArrayList<AbstractEventListener>> listeners;

    public AbstractEventListener addListener(AbstractEventListener listener) {
        WorldPartition.checkOwner();
        if (listener == null) {
            throw new NullPointerException("Listener cannot be null!");
        }

        if (listeners == null) {
            listeners = new EnumMap<>(EventType.class);
        }

        final ObjectArrayList<AbstractEventListener> registered =
                listeners.computeIfAbsent(listener.getType(), _ -> new ObjectArrayList<>());
        // Higher priority first; equal priorities keep registration order.
        int index = registered.size();
        while ((index > 0) && (registered.get(index - 1).getPriority() < listener.getPriority())) {
            index--;
        }

        registered.add(index, listener);
        return listener;
    }

    public AbstractEventListener removeListener(AbstractEventListener listener) {
        WorldPartition.checkOwner();
        if (listener == null) {
            throw new NullPointerException("Listener cannot be null!");
        }

        if (listeners == null) {
            throw new NullPointerException("Listeners container is not initialized!");
        }

        final EventType type = listener.getType();
        final ObjectArrayList<AbstractEventListener> registered = listeners.get(type);
        if (registered == null) {
            throw new IllegalAccessError("Listeners container doesn't had " + type + " event type added!");
        }

        registered.remove(listener);
        if (registered.isEmpty()) {
            listeners.remove(type);
        }

        return listener;
    }

    public void removeListenerIf(EventType type, Predicate<? super AbstractEventListener> filter) {
        WorldPartition.checkOwner();
        // Own listeners only: template and global listeners are shared with other creatures.
        try (Snapshot<AbstractEventListener> copy = ScratchBuffers.empty()) {
            collectOwn(type, copy);
            for (AbstractEventListener listener : copy) {
                if (filter.test(listener)) {
                    listener.unregisterMe();
                }
            }
        }
    }

    public void removeListenerIf(Predicate<? super AbstractEventListener> filter) {
        WorldPartition.checkOwner();
        if (listeners == null) {
            return;
        }

        try (Snapshot<AbstractEventListener> copy = ScratchBuffers.empty()) {
            for (ObjectArrayList<AbstractEventListener> registered : listeners.values()) {
                copy.addAll(registered);
            }

            for (AbstractEventListener listener : copy) {
                if (filter.test(listener)) {
                    listener.unregisterMe();
                }
            }
        }
    }

    public boolean hasListener(EventType type) {
        WorldPartition.checkOwner();
        return (listeners != null) && listeners.containsKey(type);
    }

    /** Read-only snapshot in notification order. */
    public List<AbstractEventListener> getListeners(EventType type) {
        WorldPartition.checkOwner();
        if (!hasListener(type)) {
            return List.of();
        }

        final ObjectArrayList<AbstractEventListener> copy = new ObjectArrayList<>();
        collectListeners(type, copy);
        return ObjectLists.unmodifiable(copy);
    }

    public void collectListeners(EventType type, List<? super AbstractEventListener> into) {
        WorldPartition.checkOwner();
        collectOwn(type, into);
    }

    // Unchecked: callers have checked the owner already.
    protected final void collectOwn(EventType type, List<? super AbstractEventListener> into) {
        if (listeners != null) {
            final ObjectArrayList<AbstractEventListener> registered = listeners.get(type);
            if (registered != null) {
                into.addAll(registered);
            }
        }
    }
}
