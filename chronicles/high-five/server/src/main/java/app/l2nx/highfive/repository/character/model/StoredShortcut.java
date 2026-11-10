/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.character.model;

import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import java.util.UUID;
import lombok.Builder;

/** {@code itemId} is set for an item shortcut only; {@code targetId} for the others. */
@Builder(toBuilder = true)
public record StoredShortcut(int slot, int page, ShortcutType type, int targetId, UUID itemId, int level) {
    public CharacterShortcut toShortcut(int itemHandle) {
        return CharacterShortcut.builder()
                .slot(slot)
                .page(page)
                .type(type)
                .id(type == ShortcutType.ITEM ? itemHandle : targetId)
                .level(level)
                .build();
    }
}
