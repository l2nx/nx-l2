/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import lombok.Builder;

/** @param commands the serialized command list as {@code MacroList} writes it */
@Builder(toBuilder = true)
public record CharacterMacro(int macroId, int icon, String name, String description, String acronym, String commands) {}
