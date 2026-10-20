/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.minigame.model;

import java.time.Instant;
import lombok.Builder;

@Builder(toBuilder = true)
public record Lottery(int id, long prize, long newPrize, Instant endDate, boolean finished) {}
