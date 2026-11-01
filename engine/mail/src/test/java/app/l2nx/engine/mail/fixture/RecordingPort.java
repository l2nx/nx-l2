/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.engine.core.session.outbound.LegacyPacket;
import app.l2nx.engine.core.session.outbound.ViewEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingPort implements ClientPort {
    private final List<ViewEvent> events = new CopyOnWriteArrayList<>();

    public List<ViewEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public void emit(ViewEvent event) {
        events.add(event);
    }

    @Override
    public void emitAndClose(ViewEvent event) {
        events.add(event);
    }

    @Override
    public void legacy(LegacyPacket packet) {
        throw new UnsupportedOperationException("Mail sends no legacy packets");
    }

    @Override
    public void legacyAndClose(LegacyPacket packet) {
        throw new UnsupportedOperationException("Mail sends no legacy packets");
    }
}
