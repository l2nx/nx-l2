/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.territory.model;

import lombok.Builder;

/**
 * @param registrantId clan handle for {@link TerritoryRegistrantType#CLAN}, character handle for a mercenary
 */
@Builder(toBuilder = true)
public record TerritoryRegistration(int castleId, int registrantId, TerritoryRegistrantType registrantType) {}
