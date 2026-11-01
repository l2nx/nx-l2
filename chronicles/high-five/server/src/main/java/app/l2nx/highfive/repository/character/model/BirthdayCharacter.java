/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import java.util.UUID;
import lombok.Builder;

/**
 * @param age full years since the character was created
 * @param year the year of this anniversary
 */
@Builder(toBuilder = true)
public record BirthdayCharacter(UUID id, String name, int age, int year) {}
