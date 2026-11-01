/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.Augmentation;
import app.l2nx.engine.mail.view.Elements;
import app.l2nx.highfive.data.xml.EnchantItemOptionsData;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.options.EnchantOptions;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class ItemSnapshots {
    private static final int AUGMENTATION_OPTION_BITS = 16;
    private static final int AUGMENTATION_OPTION_MASK = 0xFFFF;
    private static final byte NO_ATTACK_ELEMENT = -2;

    private ItemSnapshots() {}

    public static List<Integer> enchantOptions(int itemTemplateId, int enchantLevel) {
        final EnchantOptions options = EnchantItemOptionsData.getInstance().getOptions(itemTemplateId, enchantLevel);
        if (options == null) {
            return List.of();
        }
        return Arrays.stream(options.getOptions()).boxed().toList();
    }

    public static AttachmentSnapshot of(Item item, int slot, long count) {
        final int mana = item.getMana();
        return AttachmentSnapshot.builder()
                .slot(slot)
                .itemTemplateId(item.getId())
                .count(count)
                .enchantLevel(item.getEnchantLevel())
                .customType1(item.getCustomType1())
                .customType2(item.getCustomType2())
                .manaLeft(mana < 0 ? null : mana)
                .itemExpiresAt(item.isTimeLimitedItem() ? Instant.ofEpochMilli(item.getTime()) : null)
                .augmentation(augmentation(item))
                .elements(elements(item))
                .enchantOptions(enchantOptions(item.getId(), item.getEnchantLevel()))
                .build();
    }

    // High Five packs both options into one id, the first in the low half.
    private static Augmentation augmentation(Item item) {
        if (!item.isAugmented()) {
            return null;
        }
        final int id = item.getAugmentation().getAugmentationId();
        return new Augmentation(id & AUGMENTATION_OPTION_MASK, id >>> AUGMENTATION_OPTION_BITS);
    }

    private static Elements elements(Item item) {
        final byte attackElement = item.getAttackElementType();
        final Elements elements = Elements.builder()
                .attackElement(attackElement == NO_ATTACK_ELEMENT ? null : (int) attackElement)
                .attackElementPower(item.getAttackElementPower())
                .fire(item.getElementDefAttr(Elementals.FIRE))
                .water(item.getElementDefAttr(Elementals.WATER))
                .wind(item.getElementDefAttr(Elementals.WIND))
                .earth(item.getElementDefAttr(Elementals.EARTH))
                .holy(item.getElementDefAttr(Elementals.HOLY))
                .dark(item.getElementDefAttr(Elementals.DARK))
                .build();
        return elements.equals(Elements.builder().build()) ? null : elements;
    }
}
