/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param ownerClanId clan handle, {@code 0} for none
 * @param lastOwnedTime when the owner took the fort, {@code null} for none
 * @param siegeDate {@code null} when no siege is scheduled
 * @param contractedCastleId {@code 0} for none
 */
@Builder(toBuilder = true)
public record FortState(
        int fortId,
        int ownerClanId,
        Instant lastOwnedTime,
        Instant siegeDate,
        int contractState,
        int contractedCastleId,
        int supplyLevel) {}
