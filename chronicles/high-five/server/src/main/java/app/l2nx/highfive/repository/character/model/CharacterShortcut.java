/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import lombok.Builder;

/**
 * @param id the item handle for an {@code ITEM} shortcut, else the skill, action, macro, recipe or bookmark id
 * @param level the skill level of a {@code SKILL} shortcut, {@code 0} for the other types
 */
@Builder(toBuilder = true)
public record CharacterShortcut(int slot, int page, ShortcutType type, int id, int level) {}
