/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail.repository;

import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;

/**
 * An item row as a value transaction committed it, or as memory holds an item created since its last save, for the
 * transaction to insert.
 *
 * @param count {@code 0} when the transaction deleted the row: all of it left
 * @param locationSlot {@code null} outside the inventory and the paperdoll
 * @param manaLeft {@code null} for an item without mana
 * @param expiresAt {@code null} for an item without a time limit
 * @param augmentationId {@code null} for an item without one
 * @param arrived the signed count the transaction moved into the row; {@code null} for a row read back outside the
 *     transaction (a replay), where only its state is known, and for a row still to insert
 */
@Builder(toBuilder = true)
public record MailItemRow(
        UUID itemId,
        UUID ownerId,
        ItemLocation location,
        Integer locationSlot,
        int itemTemplateId,
        long count,
        int enchantLevel,
        int customType1,
        int customType2,
        Integer manaLeft,
        Instant expiresAt,
        Integer augmentationId,
        List<ItemElemental> elementals,
        Long arrived)
        implements CommittedItemRow {
    public MailItemRow {
        elementals = List.copyOf(elementals);
    }

    public boolean deleted() {
        return count == 0;
    }
}
