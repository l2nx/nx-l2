/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item.model;

import app.l2nx.highfive.entity.item.enums.ItemLocation;
import java.time.Instant;
import lombok.Builder;

/**
 * A stored item in the legacy terms of {@code Item}; the owner is not part of the row, every query names it.
 *
 * @param locationSlot paperdoll slot (PAPERDOLL, PET_EQUIP), sort order (INVENTORY), 0 elsewhere
 * @param mana shadow item mana, {@code -1} for an ordinary item
 * @param expiresAt {@code null} for an item without a time limit
 */
@Builder(toBuilder = true)
public record ItemRow(
        int objectId,
        int itemId,
        long count,
        ItemLocation location,
        int locationSlot,
        int enchantLevel,
        int customType1,
        int customType2,
        int mana,
        Instant expiresAt) {}
