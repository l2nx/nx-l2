/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.residence.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record CastleTrapUpgrade(int towerIndex, int trapLevel) {}
