/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.action.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;

/** The client's pending action (a move, a restart request) was not accepted; the client stops waiting for it. */
public record ActionCancelled() implements ViewEvent {
    public static final ActionCancelled INSTANCE = new ActionCancelled();
}
