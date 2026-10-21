/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.architecture.fixture.broken.login.view;

import app.l2nx.engine.core.partition.DomainEvent;

public record AccountLeft(String login) implements DomainEvent {}
