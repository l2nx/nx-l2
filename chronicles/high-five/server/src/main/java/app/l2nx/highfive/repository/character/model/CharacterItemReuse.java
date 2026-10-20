/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import java.time.Instant;
import lombok.Builder;

/** @param itemObjectId the wire handle of the item instance */
@Builder(toBuilder = true)
public record CharacterItemReuse(int itemObjectId, int itemTemplateId, long reuseDelayMs, Instant reuseEndsAt) {}
