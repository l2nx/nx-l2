/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.siege.model;

import lombok.Builder;

/**
 * @param allyNpcId {@code 0} until the clan leader picks the allied NPC
 */
@Builder(toBuilder = true)
public record FlagWarAttacker(int clanId, int flagNpcId, int allyNpcId) {}
