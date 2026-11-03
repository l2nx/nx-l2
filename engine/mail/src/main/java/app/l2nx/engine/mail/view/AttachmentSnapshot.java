/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.view;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/**
 * An attachment as it was when the mail was sent; never changes and outlives the item.
 *
 * @param manaLeft {@code null} for an item without mana
 * @param itemExpiresAt {@code null} for an item without a time limit
 * @param augmentation {@code null} for an item without one
 * @param elements {@code null} for an item without attributes
 * @param enchantOptions at most three, in their order on the item
 */
// TODO: compose the common item snapshot of the gs-adapter api (object id, template, count, enchant, attributes,
//  augmentation) plus slot and the fields the mail packets need (l2nx/nx-l2#188).
@Builder(toBuilder = true)
public record AttachmentSnapshot(
        int slot,
        int itemTemplateId,
        long count,
        int enchantLevel,
        int customType1,
        int customType2,
        Integer manaLeft,
        Instant itemExpiresAt,
        Augmentation augmentation,
        Elements elements,
        List<Integer> enchantOptions) {
    public static final int MAX_SLOTS = 8;
    public static final int MAX_ENCHANT_OPTIONS = 3;

    public AttachmentSnapshot {
        if ((slot < 0) || (slot >= MAX_SLOTS)) {
            throw new IllegalArgumentException("Attachment slot out of range: " + slot);
        }
        if (count < 1) {
            throw new IllegalArgumentException("Attachment count must be positive: " + count);
        }
        enchantOptions = List.copyOf(enchantOptions);
        if (enchantOptions.size() > MAX_ENCHANT_OPTIONS) {
            throw new IllegalArgumentException(
                    "At most " + MAX_ENCHANT_OPTIONS + " enchant options: " + enchantOptions);
        }
    }
}
