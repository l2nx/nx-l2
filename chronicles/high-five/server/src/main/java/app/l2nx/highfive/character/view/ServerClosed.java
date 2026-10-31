/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;

public record ServerClosed() implements ViewEvent {
    public static final ServerClosed INSTANCE = new ServerClosed();
}
