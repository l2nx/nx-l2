/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail;

import lombok.Builder;

/**
 * @param busy trading, a private store or enchanting
 * @param tradeAllowed the access level allows transactions
 * @param mailBlocked the jail rules of the chronicle cut the character off from mail altogether
 * @param transactionsBlocked the jail rules of the chronicle block the character's attachments and cash on delivery
 */
@Builder(toBuilder = true)
public record ParticipantState(
        boolean peaceZone,
        boolean inCombat,
        boolean dead,
        boolean busy,
        boolean tradeAllowed,
        boolean mailBlocked,
        boolean transactionsBlocked,
        boolean gm) {}
