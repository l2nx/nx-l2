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

import app.l2nx.highfive.entity.ScratchBuffers;
import app.l2nx.highfive.entity.ScratchBuffers.Snapshot;
import app.l2nx.highfive.mechanics.events.holders.IBaseEvent;
import app.l2nx.highfive.mechanics.events.listeners.AbstractEventListener;
import app.l2nx.highfive.mechanics.events.returns.AbstractEventReturn;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * @author UnAfraid, Mobius
 */
@Slf4j
public class EventDispatcher {

    protected EventDispatcher() {}

    public boolean hasListener(EventType type) {
        return Containers.Global().hasListener(type);
    }

    public boolean hasListener(EventType type, ListenersContainer container) {
        return Containers.Global().hasListener(type) || ((container != null) && container.hasListener(type));
    }

    public boolean hasListener(EventType type, ListenersContainer... containers) {
        boolean hasListeners = Containers.Global().hasListener(type);
        if (!hasListeners) {
            for (ListenersContainer container : containers) {
                if (container.hasListener(type)) {
                    hasListeners = true;
                    break;
                }
            }
        }

        return hasListeners;
    }

    public <T extends AbstractEventReturn> T notifyEvent(IBaseEvent event) {
        return notifyEvent(event, null, null);
    }

    public <T extends AbstractEventReturn> T notifyEvent(IBaseEvent event, Class<T> callbackClass) {
        return notifyEvent(event, null, callbackClass);
    }

    public <T extends AbstractEventReturn> T notifyEvent(IBaseEvent event, ListenersContainer container) {
        return notifyEvent(event, container, null);
    }

    public <T extends AbstractEventReturn> T notifyEvent(
            IBaseEvent event, ListenersContainer container, Class<T> callbackClass) {
        // Outside the try: an off-loop call must fail at its caller, not as a lost event in the log.
        WorldPartition.checkOwner();
        try {
            return notifyEventImpl(event, container, callbackClass);
        } catch (Exception e) {
            log.warn("Couldn't notify event {}", event.getClass().getSimpleName(), e);
        }

        return null;
    }

    public void notifyEventAsyncDelayed(IBaseEvent event, ListenersContainer container, long delay) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null!");
        }

        WorldPartition.loop().schedule(() -> notifyEvent(event, container, null), delay, TimeUnit.MILLISECONDS);
    }

    public void notifyEventAsync(IBaseEvent event, ListenersContainer container) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null!");
        }

        WorldPartition.loop().execute(() -> notifyEventToSingleContainer(event, container, null));
    }

    public void notifyEventAsync(IBaseEvent event, ListenersContainer... containers) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null!");
        }

        WorldPartition.loop().execute(() -> notifyEventToMultipleContainers(event, containers, null));
    }

    private <T extends AbstractEventReturn> T notifyEventToSingleContainer(
            IBaseEvent event, ListenersContainer container, Class<T> callbackClass) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null!");
        }

        try {
            return notifyEventImpl(event, container, callbackClass);
        } catch (Exception e) {
            log.warn("Couldn't notify event {}", event.getClass().getSimpleName(), e);
        }

        return null;
    }

    private <T extends AbstractEventReturn> T notifyEventToMultipleContainers(
            IBaseEvent event, ListenersContainer[] containers, Class<T> callbackClass) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null!");
        }

        try {
            T callback = null;
            if (containers != null) {
                for (ListenersContainer container : containers) {
                    if ((callback == null) || !callback.abort()) {
                        callback = notifyContainer(container, event, callbackClass, callback);
                    }
                }
            }

            if ((callback == null) || !callback.abort()) {
                callback = notifyContainer(Containers.Global(), event, callbackClass, callback);
            }

            return callback;
        } catch (Exception e) {
            log.warn("Couldn't notify event {}", event.getClass().getSimpleName(), e);
        }

        return null;
    }

    private <T extends AbstractEventReturn> T notifyEventImpl(
            IBaseEvent event, ListenersContainer container, Class<T> callbackClass) {
        if (event == null) {
            throw new NullPointerException("Event cannot be null!");
        }

        T callback = null;
        if (container != null) {
            callback = notifyContainer(container, event, callbackClass, callback);
        }

        if ((callback == null) || !callback.abort()) {
            callback = notifyContainer(Containers.Global(), event, callbackClass, callback);
        }

        return callback;
    }

    // A listener may unregister itself or others while it runs, so dispatch walks a copy.
    private <T extends AbstractEventReturn> T notifyContainer(
            ListenersContainer container, IBaseEvent event, Class<T> returnBackClass, T callbackValue) {
        try (Snapshot<AbstractEventListener> copy = ScratchBuffers.empty()) {
            container.collectListeners(event.getType(), copy);
            return copy.isEmpty() ? callbackValue : notifyToListeners(copy, event, returnBackClass, callbackValue);
        }
    }

    private <T extends AbstractEventReturn> T notifyToListeners(
            Collection<AbstractEventListener> listeners, IBaseEvent event, Class<T> returnBackClass, T callbackValue) {
        T callback = callbackValue;
        for (AbstractEventListener listener : listeners) {
            try {
                final T rb = listener.executeEvent(event, returnBackClass);
                if (rb == null) {
                    continue;
                } else if ((callback == null) || rb.override()) {
                    callback = rb;
                } else if (rb.abort()) {
                    break;
                }
            } catch (Exception e) {
                log.warn(
                        "Exception during notification of event: {} listener: {}",
                        event.getClass().getSimpleName(),
                        listener.getClass().getSimpleName(),
                        e);
            }
        }

        return callback;
    }

    public static EventDispatcher getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final EventDispatcher INSTANCE = new EventDispatcher();
    }
}
