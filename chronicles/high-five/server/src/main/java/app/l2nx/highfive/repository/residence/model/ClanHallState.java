/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param ownerClanId clan handle, {@code 0} for none
 * @param paidUntil {@code null} for none
 */
@Builder(toBuilder = true)
public record ClanHallState(int clanHallId, int ownerClanId, Instant paidUntil, boolean paid) {}
