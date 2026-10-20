/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import lombok.Builder;

/**
 * @param allyId 0 when the clan has no alliance
 */
@Builder(toBuilder = true)
public record WarOpponent(int clanId, String name, int allyId, String allyName) {}
