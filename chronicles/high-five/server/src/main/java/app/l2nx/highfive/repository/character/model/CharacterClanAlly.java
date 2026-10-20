/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import lombok.Builder;

/**
 * @param clanId the clan handle, {@code 0} without a clan
 * @param allyId the handle of the alliance leader clan, {@code 0} outside an alliance
 */
@Builder(toBuilder = true)
public record CharacterClanAlly(int clanId, int allyId) {}
