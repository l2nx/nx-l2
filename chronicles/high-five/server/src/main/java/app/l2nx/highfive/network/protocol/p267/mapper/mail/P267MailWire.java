/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.Augmentation;
import app.l2nx.engine.mail.view.Elements;
import app.l2nx.engine.mail.view.MailSummary;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.holders.ItemInfo;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.codec.Frames;
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
    // Either list frame opens with its length, the extended opcode, the server time and the mail count.
    private static final int LIST_HEAD_BYTES = Frames.HEADER + 3 + (2 * Integer.BYTES);
    private static final int LIST_BUDGET = Frames.MAX_LENGTH - LIST_HEAD_BYTES;
    static final int RECEIVED_LIST_MAIL_BYTES = 9 * Integer.BYTES;
    static final int SENT_LIST_MAIL_BYTES = 6 * Integer.BYTES;
    static final int MIN_LIST_SUBJECT_CHARS = 16;

    private P267MailWire() {}

    static PostSender sender(MailType type) {
        return switch (type) {
            case PLAYER -> PostSender.PLAYER;
            case SYSTEM -> PostSender.SYSTEM;
            case BIRTHDAY -> PostSender.BIRTHDAY;
        };
    }

    static int listSubjectChars(List<MailSummary> mails, int mailBytes) {
        if (mails.isEmpty()) {
            return 0;
        }
        long left = LIST_BUDGET;
        for (MailSummary mail : mails) {
            final String name = mail.counterpartName();
            left -= mailBytes + stringBytes(0) + stringBytes((name == null) ? 0 : name.length());
        }
        return (int) Math.max(0, left / ((long) Character.BYTES * mails.size()));
    }

    static int maxListMails() {
        return LIST_BUDGET
                / (Math.max(RECEIVED_LIST_MAIL_BYTES, SENT_LIST_MAIL_BYTES)
                        + stringBytes(MIN_LIST_SUBJECT_CHARS)
                        + stringBytes(CharacterService.MAX_NAME_LENGTH));
    }

    /**
     * @return {@code null} when nothing of the subject is left
     */
    static String listSubject(String subject, int chars) {
        if ((subject == null) || (subject.length() <= chars)) {
            return subject;
        }
        final int end = ((chars > 0) && Character.isHighSurrogate(subject.charAt(chars - 1))) ? (chars - 1) : chars;
        return (end == 0) ? null : subject.substring(0, end);
    }

    private static int stringBytes(int chars) {
        return Character.BYTES * (chars + 1);
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
