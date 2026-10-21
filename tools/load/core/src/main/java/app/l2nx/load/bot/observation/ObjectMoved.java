/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import app.l2nx.load.bot.model.Position;
import lombok.Builder;

/** A world object, the load bot itself included, moves towards {@code position}. */
@Builder(toBuilder = true)
public record ObjectMoved(int objectId, Position position) implements Observation {}
