/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.minigame.model;

import lombok.Builder;

/**
 * The draw of a finished lottery.
 *
 * @param number1 bit mask of the drawn numbers, encoded like a ticket's enchant level
 * @param number2 bit mask of the drawn numbers, encoded like a ticket's custom type 2
 */
@Builder(toBuilder = true)
public record LotteryResult(int number1, int number2, long prize1, long prize2, long prize3) {}
