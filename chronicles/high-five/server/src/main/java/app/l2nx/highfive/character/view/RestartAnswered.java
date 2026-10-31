/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;

public record RestartAnswered(boolean accepted) implements ViewEvent {
    public static final RestartAnswered ACCEPTED = new RestartAnswered(true);
    public static final RestartAnswered REFUSED = new RestartAnswered(false);
}
