/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.partition;

import app.l2nx.engine.core.session.outbound.ViewEvent;

/**
 * A fact other slices react to, a record in its slice's {@code event} package; never reaches a client (that is a
 * {@link ViewEvent}).
 */
public interface DomainEvent {}
