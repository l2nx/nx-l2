/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

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
    };

    void emit(ViewEvent event);

    void emitAndClose(ViewEvent event);

    void legacy(LegacyPacket packet);

    void legacyAndClose(LegacyPacket packet);
}
