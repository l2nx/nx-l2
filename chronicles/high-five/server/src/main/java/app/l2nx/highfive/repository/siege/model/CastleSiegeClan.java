/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege.model;

import app.l2nx.highfive.mechanics.siege.SiegeClanType;
import lombok.Builder;

@Builder(toBuilder = true)
public record CastleSiegeClan(int clanId, SiegeClanType side) {}
