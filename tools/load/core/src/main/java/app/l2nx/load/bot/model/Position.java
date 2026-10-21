/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.bot.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Position(int x, int y, int z) {}
