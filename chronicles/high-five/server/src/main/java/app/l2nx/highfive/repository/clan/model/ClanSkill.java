/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.clan.model;

import lombok.Builder;

/**
 * @param pledgeType the sub-unit the skill belongs to; {@code -2} for a clan-wide skill, {@code 0} for the main clan
 *     squad
 */
@Builder(toBuilder = true)
public record ClanSkill(int skillId, int skillLevel, int pledgeType) {}
