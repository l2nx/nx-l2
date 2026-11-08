/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import app.l2nx.load.bot.model.Position;
import lombok.Builder;

@Builder(toBuilder = true)
public record Entered(int selfObjectId, Position position, double runSpeed) implements Observation {}
