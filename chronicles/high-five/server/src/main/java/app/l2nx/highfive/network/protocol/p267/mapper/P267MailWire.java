/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.Augmentation;
import app.l2nx.engine.mail.view.Elements;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.protocol.game.p267.serverpackets.ItemBlock;
import app.l2nx.protocol.game.p267.serverpackets.PostSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class P267MailWire {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();
    private static final int AUGMENTATION_OPTION_BITS = 16;
    private static final int AUGMENTATION_OPTION_MASK = 0xFFFF;
    /**
     * A full box of 240 mails with 128-character subjects outgrows the 65535-byte frame; 96 characters keep the list of
     * either box inside it with 16-character names.
     */
    static final int LIST_SUBJECT_CHARS = 96;

    private P267MailWire() {}

    static PostSender sender(MailType type) {
        return switch (type) {
            case PLAYER -> PostSender.PLAYER;
            case SYSTEM -> PostSender.SYSTEM;
            case BIRTHDAY -> PostSender.BIRTHDAY;
        };
    }

    static String listSubject(String subject) {
        if ((subject == null) || (subject.length() <= LIST_SUBJECT_CHARS)) {
            return subject;
        }
        final int end = Character.isHighSurrogate(subject.charAt(LIST_SUBJECT_CHARS - 1))
                ? (LIST_SUBJECT_CHARS - 1)
                : LIST_SUBJECT_CHARS;
        return subject.substring(0, end);
    }

    static int unixSeconds(Instant instant) {
        return (int) instant.getEpochSecond();
    }

    static int unixSeconds(long epochMillis) {
        return (int) (epochMillis / 1000);
    }

    /**
     * The snapshot as the mail shows it, never the live item: the object id is the synthetic {@code slot + 1}, below
     * every live handle, and the time left counts from {@code nowMillis}.
     */
    static List<ItemBlock> attachments(List<AttachmentSnapshot> snapshots, long nowMillis) {
        final List<ItemBlock> blocks = new ArrayList<>(snapshots.size());
        for (AttachmentSnapshot snapshot : snapshots) {
            blocks.add(attachment(snapshot, nowMillis));
        }
        return blocks;
    }

    static ItemBlock attachment(AttachmentSnapshot snapshot, long nowMillis) {
        final ItemTemplate template = ItemData.getInstance().getTemplate(snapshot.itemTemplateId());
        if ((template == null) && RATE.tryAcquire("mail-unknown-item")) {
            log.warn("No item template {} for a mail attachment, shown as a bare item id", snapshot.itemTemplateId());
        }
        final Elements elements = snapshot.elements();
        final Integer manaLeft = snapshot.manaLeft();
        final Instant expiresAt = snapshot.itemExpiresAt();
        return ItemBlock.builder()
                .objectId(snapshot.slot() + 1)
                .displayId((template == null) ? snapshot.itemTemplateId() : template.getDisplayId())
                .location(snapshot.slot())
                .count(snapshot.count())
                .type2((template == null) ? ItemTemplate.TYPE2_OTHER : template.getType2())
                .customType1(snapshot.customType1())
                .equipped(false)
                .bodyPart((template == null) ? 0 : template.getBodyPart().getMask())
                .enchantLevel(snapshot.enchantLevel())
                .customType2(snapshot.customType2())
                .augmentationId(augmentationId(snapshot.augmentation()))
                .mana((manaLeft == null) ? ItemBlock.NO_MANA : manaLeft)
                .remainingSeconds(
                        (expiresAt == null)
                                ? ItemBlock.NO_TIME_LIMIT
                                : (int) Math.max(0, (expiresAt.toEpochMilli() - nowMillis) / 1000))
                .attackElementType(
                        ((elements == null) || (elements.attackElement() == null))
                                ? ItemBlock.NO_ATTACK_ELEMENT
                                : elements.attackElement())
                .attackElementPower((elements == null) ? 0 : elements.attackElementPower())
                .elementDefence(
                        (elements == null)
                                ? List.of(0, 0, 0, 0, 0, 0)
                                : List.of(
                                        elements.fire(),
                                        elements.water(),
                                        elements.wind(),
                                        elements.earth(),
                                        elements.holy(),
                                        elements.dark()))
                .enchantOptions(enchantOptions(snapshot.enchantOptions()))
                .build();
    }

    static ItemBlock item(Item item) {
        final ItemInfo info = new ItemInfo(item);
        final List<Integer> defence = new ArrayList<>(ItemBlock.DEFENCE_ELEMENTS);
        for (byte element = 0; element < ItemBlock.DEFENCE_ELEMENTS; element++) {
            defence.add(info.getElementDefAttr(element));
        }
        final List<Integer> options = new ArrayList<>(ItemBlock.ENCHANT_OPTIONS);
        for (int option : info.getEnchantOptions()) {
            options.add(option);
        }
        return ItemBlock.builder()
                .objectId(info.getObjectId())
                .displayId(info.getItem().getDisplayId())
                .location(info.getLocation())
                .count(info.getCount())
                .type2(info.getItem().getType2())
                .customType1(info.getCustomType1())
                .equipped(info.getEquipped() != 0)
                .bodyPart(info.getItem().getBodyPart().getMask())
                .enchantLevel(info.getEnchant())
                .customType2(info.getCustomType2())
                .augmentationId(info.getAugmentationBonus())
                .mana(info.getMana())
                .remainingSeconds(info.getTime())
                .attackElementType(info.getAttackElementType())
                .attackElementPower(info.getAttackElementPower())
                .elementDefence(defence)
                .enchantOptions(enchantOptions(options))
                .build();
    }

    private static int augmentationId(Augmentation augmentation) {
        if (augmentation == null) {
            return 0;
        }
        return (augmentation.option2() << AUGMENTATION_OPTION_BITS)
                | (augmentation.option1() & AUGMENTATION_OPTION_MASK);
    }

    private static List<Integer> enchantOptions(List<Integer> options) {
        final List<Integer> padded = new ArrayList<>(ItemBlock.ENCHANT_OPTIONS);
        for (int i = 0; i < ItemBlock.ENCHANT_OPTIONS; i++) {
            padded.add((i < options.size()) ? options.get(i) : 0);
        }
        return padded;
    }
}
