/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.pet.model;

import lombok.Builder;

/**
 * An effect stored for a pet or servitor that is not summoned, as raw values: the caller builds a
 * {@code StoredEffect} from them and skips a row that breaks its invariants.
 *
 * @param remainingMillis game time left, {@code null} for an effect without a duration
 */
@Builder(toBuilder = true)
public record SummonEffectRow(int skillId, int skillLevel, Long remainingMillis, long tickPhaseMillis) {}
