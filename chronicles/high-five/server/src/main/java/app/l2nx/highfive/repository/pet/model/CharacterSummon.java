/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.pet.model;

import lombok.Builder;

/** @param lifeRemainingMs game time left, always above 0 */
@Builder(toBuilder = true)
public record CharacterSummon(int ownerId, int summonSkillId, int currentHp, int currentMp, long lifeRemainingMs) {}
