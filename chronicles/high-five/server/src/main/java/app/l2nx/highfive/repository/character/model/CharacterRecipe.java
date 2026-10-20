/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import lombok.Builder;

/** @param classIndex the class index of a dwarven recipe, {@code 0} for a common recipe */
@Builder(toBuilder = true)
public record CharacterRecipe(int recipeId, int classIndex, boolean dwarven) {}
