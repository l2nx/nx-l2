/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.offline.model;

import app.l2nx.highfive.entity.groups.PartyDistributionType;
import lombok.Builder;

@Builder(toBuilder = true)
public record OfflinePlayPartyMember(int leaderId, int characterId, PartyDistributionType distributionType) {}
