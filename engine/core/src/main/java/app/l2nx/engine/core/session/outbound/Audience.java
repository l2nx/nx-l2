/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session.outbound;

public final class Audience {
    private Audience() {}

    public static void emit(ViewEvent event, Iterable<? extends ClientPort> ports) {
        for (ClientPort port : ports) {
            port.emit(event);
        }
    }
}
