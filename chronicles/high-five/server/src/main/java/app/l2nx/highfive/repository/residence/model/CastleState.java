/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param ownerClanId clan handle, {@code 0} for none
 * @param siegeDate {@code null} when no siege is scheduled
 */
@Builder(toBuilder = true)
public record CastleState(
        int castleId,
        int ownerClanId,
        int taxPercent,
        long treasury,
        Instant siegeDate,
        Instant registrationEndDate,
        boolean registrationOver,
        boolean showNpcCrest,
        int ticketBuyCount) {}
