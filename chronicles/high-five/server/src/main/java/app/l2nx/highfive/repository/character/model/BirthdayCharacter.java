/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import lombok.Builder;

/** @param age full years since the character was created */
@Builder(toBuilder = true)
public record BirthdayCharacter(int objectId, String name, int age) {}
