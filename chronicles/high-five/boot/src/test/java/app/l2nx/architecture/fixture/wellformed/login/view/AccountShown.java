/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.wellformed.login.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;

public record AccountShown(String login) implements ViewEvent {}
