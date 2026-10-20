/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param ownerClanId clan handle, {@code 0} for none
 * @param nextSiege {@code null} when none is stored
 */
@Builder(toBuilder = true)
public record SiegableClanHallState(int clanHallId, int ownerClanId, Instant nextSiege) {}
