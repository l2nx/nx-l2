/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import lombok.Builder;

/**
 * The numbers chosen on a lottery ticket, as bit masks stored in the ticket item's enchant level and custom type 2.
 */
@Builder(toBuilder = true)
public record LotteryTicket(int enchantLevel, int customType2) {}
