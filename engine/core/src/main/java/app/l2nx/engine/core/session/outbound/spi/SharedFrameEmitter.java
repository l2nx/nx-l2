/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound.spi;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import io.netty.buffer.ByteBuf;

/** Implemented by a protocol adapter's port so a broadcast is serialized once per audience; game code never calls it. */
public interface SharedFrameEmitter {
    /** Ports with equal keys get identical bytes for this event; {@code null} means no sharing. */
    Object audienceKey(ViewEvent event);

    /** The frame for this port's audience, owned by the caller; {@code null} when the event is dropped. */
    ByteBuf serialize(ViewEvent event);

    void sendShared(ByteBuf frame);
}
