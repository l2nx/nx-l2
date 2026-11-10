/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import app.l2nx.highfive.entity.item.enums.ItemLocation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;

/**
 * An item row by UUID; holds no wire handle until the item is built on the loop.
 * {@code augmentationId} is {@code null} when the item has none.
 */
@Builder(toBuilder = true)
public record StoredItem(
        UUID id,
        int itemTemplateId,
        long count,
        ItemLocation location,
        int locationSlot,
        int enchantLevel,
        int customType1,
        int customType2,
        int mana,
        Instant expiresAt,
        Integer augmentationId,
        List<ItemElemental> elementals,
        Map<String, String> variables) {
    public StoredItem {
        elementals = elementals == null ? List.of() : List.copyOf(elementals);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public ItemRow toRow(int objectId) {
        return ItemRow.builder()
                .objectId(objectId)
                .itemId(itemTemplateId)
                .count(count)
                .location(location)
                .locationSlot(locationSlot)
                .enchantLevel(enchantLevel)
                .customType1(customType1)
                .customType2(customType2)
                .mana(mana)
                .expiresAt(expiresAt)
                .build();
    }
}
