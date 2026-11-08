/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import lombok.Builder;

/** @param runSpeed game units per second, as the client moves the character */
@Builder(toBuilder = true)
public record RunSpeedChanged(double runSpeed) implements Observation {}
