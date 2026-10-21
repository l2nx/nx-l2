/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.observation;

import lombok.Builder;

@Builder(toBuilder = true)
public record StatusChanged(int hp, int mp) implements Observation {}
