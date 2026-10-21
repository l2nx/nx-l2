/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

import io.netty.buffer.ByteBuf;

public interface ClientPort {
    ClientPort DETACHED = new ClientPort() {
        @Override
        public void emit(ViewEvent event) {}

        @Override
        public void emitAndClose(ViewEvent event) {}

        @Override
        public void legacy(LegacyPacket packet) {}

        @Override
        public void legacyAndClose(LegacyPacket packet) {}

        @Override
        public Object audienceKey(ViewEvent event) {
            return null;
        }

        @Override
        public ByteBuf serialize(ViewEvent event) {
            return null;
        }

        @Override
        public void sendShared(ByteBuf frame) {}
    };

    void emit(ViewEvent event);

    void emitAndClose(ViewEvent event);

    void legacy(LegacyPacket packet);

    void legacyAndClose(LegacyPacket packet);

    /** Ports with equal keys get identical bytes for this event; {@code null} means no sharing. */
    Object audienceKey(ViewEvent event);

    /** The frame for this port's audience, owned by the caller; {@code null} when the event is dropped. */
    ByteBuf serialize(ViewEvent event);

    void sendShared(ByteBuf frame);
}
