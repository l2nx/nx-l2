/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import java.time.Instant;
import lombok.Builder;

/**
 * @param remainingMs game time left on the effect, {@code null} for a {@code REUSE} row and for an effect without a
 *     duration
 * @param tickPhaseMs game time elapsed on the effect tick schedule, {@code null} for a {@code REUSE} row
 * @param reuseEndsAt wall-clock end of the skill reuse, {@code null} when the skill is not under reuse
 */
@Builder(toBuilder = true)
public record CharacterSkillSave(
        int skillId,
        int skillLevel,
        SkillRestoreType restoreType,
        Long remainingMs,
        Long tickPhaseMs,
        long reuseDelayMs,
        Instant reuseEndsAt) {}
