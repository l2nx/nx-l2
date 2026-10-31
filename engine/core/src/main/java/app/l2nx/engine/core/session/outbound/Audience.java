/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

import app.l2nx.engine.core.session.outbound.spi.SharedFrameEmitter;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;

public final class Audience {
    private Audience() {}

    public static void emit(ViewEvent event, Iterable<? extends ClientPort> ports) {
        Map<Object, ByteBuf> frames = null;
        try {
            for (ClientPort port : ports) {
                if (!(port instanceof SharedFrameEmitter shared)) {
                    port.emit(event);
                    continue;
                }
                final Object key = shared.audienceKey(event);
                if (key == null) {
                    port.emit(event);
                    continue;
                }
                if (frames == null) {
                    frames = new HashMap<>(4);
                }
                ByteBuf frame = frames.get(key);
                // A dropped event is remembered as null, so its mapper runs once per key too.
                if ((frame == null) && !frames.containsKey(key)) {
                    frame = shared.serialize(event);
                    frames.put(key, frame);
                }
                if (frame != null) {
                    shared.sendShared(frame);
                }
            }
        } finally {
            if (frames != null) {
                for (ByteBuf frame : frames.values()) {
                    if (frame != null) {
                        frame.release();
                    }
                }
            }
        }
    }
}
