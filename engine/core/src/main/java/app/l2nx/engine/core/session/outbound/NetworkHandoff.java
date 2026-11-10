/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

import app.l2nx.network.channel.OutboundHandoff;

public final class NetworkHandoff {
    private final OutboundHandoff outbound = new OutboundHandoff();

    public void open() {
        outbound.open();
    }

    public void wake() {
        outbound.wake();
    }

    public OutboundHandoff outbound() {
        return outbound;
    }
}
