/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.c;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.h;
import static app.l2nx.protocol.game.p267.WireHex.q;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MailPacketsTest {
    private static final ItemBlock SNAPSHOT = ItemBlock.builder()
            .objectId(1)
            .displayId(6579)
            .location(0)
            .count(1)
            .type2(0)
            .customType1(0)
            .equipped(false)
            .bodyPart(0x4000)
            .enchantLevel(16)
            .customType2(0)
            .augmentationId(0x12345678)
            .mana(ItemBlock.NO_MANA)
            .remainingSeconds(3600)
            .attackElementType(0)
            .attackElementPower(150)
            .elementDefence(List.of(1, 2, 3, 4, 5, 6))
            .enchantOptions(List.of(11, 12, 13))
            .build();

    private static final String SNAPSHOT_BLOCK = d(1)
            + d(6579)
            + d(0)
            + q(1)
            + h(0)
            + h(0)
            + h(0)
            + d(0x4000)
            + h(16)
            + h(0)
            + d(0x12345678)
            + d(-1)
            + d(3600)
            + h(0)
            + h(150)
            + h(1)
            + h(2)
            + h(3)
            + h(4)
            + h(5)
            + h(6)
            + h(11)
            + h(12)
            + h(13);

    private static final ItemBlock INVENTORY_ITEM = SNAPSHOT.toBuilder()
            .objectId(0x10000005)
            .displayId(57)
            .location(3)
            .count(1_000_000L)
            .type2(4)
            .equipped(true)
            .bodyPart(0)
            .enchantLevel(0)
            .augmentationId(0)
            .remainingSeconds(ItemBlock.NO_TIME_LIMIT)
            .attackElementType(ItemBlock.NO_ATTACK_ELEMENT)
            .attackElementPower(0)
            .elementDefence(List.of(0, 0, 0, 0, 0, 0))
            .enchantOptions(List.of(0, 0, 0))
            .build();

    private static String ex(GameServerExOpcode opcode) {
        return c(GameServerExOpcode.PREFIX) + h(opcode.opcode());
    }

    @Nested
    class ItemBlockLayout {
        @Test
        void write_shouldWriteTheFieldsInWireOrder() {
            assertThat(written(SNAPSHOT::write)).isEqualTo(SNAPSHOT_BLOCK);
        }

        @Test
        void read_shouldReadBackNegativeShorts() {
            assertThat(read(written(INVENTORY_ITEM::write), ItemBlock::read)).isEqualTo(INVENTORY_ITEM);
        }

        @Test
        void constructor_shouldFail_whenTheDefenceAttributesAreIncomplete() {
            final ItemBlock.ItemBlockBuilder item = SNAPSHOT.toBuilder().elementDefence(List.of(0, 0, 0));

            assertThatThrownBy(item::build).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void constructor_shouldFail_whenTheEnchantOptionsAreIncomplete() {
            final ItemBlock.ItemBlockBuilder item = SNAPSHOT.toBuilder().enchantOptions(List.of(0));

            assertThatThrownBy(item::build).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class NoticeCodecs {
        @Test
        void write_shouldWriteTheAnimationFlagAsAnInt() {
            assertThat(written(new ExNoticePostArrived(true)::write))
                    .isEqualTo(ex(GameServerExOpcode.EX_NOTICE_POST_ARRIVED) + d(1));
            assertThat(written(new ExNoticePostSent(false)::write))
                    .isEqualTo(ex(GameServerExOpcode.EX_NOTICE_POST_SENT) + d(0));
        }

        @Test
        void read_shouldReadTheAnimationFlag() {
            assertThat(read(d(1), ExNoticePostArrived::read)).isEqualTo(new ExNoticePostArrived(true));
            assertThat(read(d(0), ExNoticePostSent::read)).isEqualTo(new ExNoticePostSent(false));
        }
    }

    @Nested
    class ExShowReceivedPostListCodec {
        private static final ExShowReceivedPostList PACKET = ExShowReceivedPostList.builder()
                .serverTime(1_800_000_000)
                .mails(List.of(
                        ExShowReceivedPostList.Mail.builder()
                                .mailId(1)
                                .subject("Deal")
                                .senderName("Alice")
                                .codPending(true)
                                .expiresAt(1_800_043_200)
                                .unread(true)
                                .returnable(true)
                                .withItems(true)
                                .sentBySystem(PostSender.PLAYER)
                                .build(),
                        ExShowReceivedPostList.Mail.builder()
                                .mailId(2)
                                .subject("Happy birthday")
                                .returned(false)
                                .expiresAt(1_801_000_000)
                                .sentBySystem(PostSender.BIRTHDAY)
                                .build()))
                .build();

        private static final String BODY = d(1_800_000_000)
                + d(2)
                + d(1)
                + s("Deal")
                + s("Alice")
                + d(1)
                + d(1_800_043_200)
                + d(1)
                + d(1)
                + d(1)
                + d(0)
                + d(0)
                + d(0)
                + d(2)
                + s("Happy birthday")
                + s("")
                + d(0)
                + d(1_801_000_000)
                + d(0)
                + d(0)
                + d(0)
                + d(0)
                + d(3)
                + d(0);

        @Test
        void write_shouldWriteEveryMailWithTheTrailingUnknownInt() {
            assertThat(written(PACKET::write)).isEqualTo(ex(GameServerExOpcode.EX_SHOW_RECEIVED_POST_LIST) + BODY);
        }

        @Test
        void write_shouldWriteReturnableAsSet() {
            final ExShowReceivedPostList packet = PACKET.toBuilder()
                    .mails(List.of(PACKET.mails().getFirst().toBuilder()
                            .returnable(false)
                            .build()))
                    .build();

            final String actual = written(packet::write);

            assertThat(actual.substring(actual.length() - (5 * 8), actual.length() - (4 * 8)))
                    .isEqualTo(d(0));
        }

        @Test
        void read_shouldReadBackTheList_whenTheSenderNameIsSet() {
            final ExShowReceivedPostList packet =
                    PACKET.toBuilder().mails(List.of(PACKET.mails().getFirst())).build();

            assertThat(read(written(packet::write).substring(6), ExShowReceivedPostList::read))
                    .isEqualTo(packet);
        }

        @Test
        void read_shouldFail_whenTheSenderIsUnknown() {
            final String body =
                    d(0) + d(1) + d(1) + s("") + s("") + d(0) + d(0) + d(0) + d(0) + d(0) + d(0) + d(2) + d(0);

            assertThatThrownBy(() -> read(body, ExShowReceivedPostList::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class ExShowSentPostListCodec {
        private static final ExShowSentPostList PACKET = ExShowSentPostList.builder()
                .serverTime(1_800_000_000)
                .mails(List.of(ExShowSentPostList.Mail.builder()
                        .mailId(5)
                        .subject("Deal")
                        .receiverName("Bob")
                        .codPending(true)
                        .expiresAt(1_800_043_200)
                        .unread(true)
                        .withItems(true)
                        .build()))
                .build();

        private static final String BODY =
                d(1_800_000_000) + d(1) + d(5) + s("Deal") + s("Bob") + d(1) + d(1_800_043_200) + d(1) + d(0) + d(1);

        @Test
        void write_shouldWriteEveryMail() {
            assertThat(written(PACKET::write)).isEqualTo(ex(GameServerExOpcode.EX_SHOW_SENT_POST_LIST) + BODY);
        }

        @Test
        void read_shouldReadBackTheList() {
            assertThat(read(BODY, ExShowSentPostList::read)).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheCountDoesNotFitTheFrame() {
            assertThatThrownBy(() -> read(d(0) + d(2) + BODY.substring(16), ExShowSentPostList::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class ExReplyReceivedPostCodec {
        private static final ExReplyReceivedPost PACKET = ExReplyReceivedPost.builder()
                .mailId(7)
                .codPending(true)
                .returned(false)
                .senderName("Alice")
                .subject("Deal")
                .body("Pay on delivery")
                .attachments(List.of(SNAPSHOT))
                .codAdena(5_000_000L)
                .returnable(true)
                .sentBySystem(PostSender.PLAYER)
                .build();

        private static final String BODY = d(7)
                + d(1)
                + d(0)
                + s("Alice")
                + s("Deal")
                + s("Pay on delivery")
                + d(1)
                + SNAPSHOT_BLOCK
                + d(1)
                + q(5_000_000L)
                + d(1)
                + d(0);

        @Test
        void write_shouldWriteAFullSnapshotBlockAndRepeatItsObjectId() {
            assertThat(written(PACKET::write)).isEqualTo(ex(GameServerExOpcode.EX_REPLY_RECEIVED_POST) + BODY);
        }

        @Test
        void write_shouldWriteReturnedAsTheThirdField() {
            final ExReplyReceivedPost packet = PACKET.toBuilder()
                    .returned(true)
                    .sentBySystem(PostSender.SYSTEM)
                    .build();

            assertThat(written(packet::write))
                    .startsWith(ex(GameServerExOpcode.EX_REPLY_RECEIVED_POST) + d(7) + d(1) + d(1));
        }

        @Test
        void read_shouldReadBackTheMail() {
            assertThat(read(BODY, ExReplyReceivedPost::read)).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheAttachmentCountDoesNotFitTheFrame() {
            final String body =
                    d(7) + d(0) + d(0) + s("A") + s("B") + s("C") + d(2) + SNAPSHOT_BLOCK + d(1) + q(0) + d(0) + d(0);

            assertThatThrownBy(() -> read(body, ExReplyReceivedPost::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class ExReplySentPostCodec {
        @Test
        void write_shouldEndWithUnread_whenThereAreAttachments() {
            final ExReplySentPost packet = ExReplySentPost.builder()
                    .mailId(7)
                    .codPending(true)
                    .receiverName("Bob")
                    .subject("Deal")
                    .body("Pay on delivery")
                    .attachments(List.of(SNAPSHOT))
                    .codAdena(5_000_000L)
                    .unread(true)
                    .build();

            assertThat(written(packet::write))
                    .isEqualTo(ex(GameServerExOpcode.EX_REPLY_SENT_POST)
                            + d(7)
                            + d(1)
                            + s("Bob")
                            + s("Deal")
                            + s("Pay on delivery")
                            + d(1)
                            + SNAPSHOT_BLOCK
                            + d(1)
                            + q(5_000_000L)
                            + d(1));
        }

        @Test
        void write_shouldEndWithUnread_whenThereAreNoAttachments() {
            final ExReplySentPost packet = ExReplySentPost.builder()
                    .mailId(8)
                    .receiverName("Bob")
                    .subject("Hi")
                    .attachments(List.of())
                    .unread(true)
                    .build();

            assertThat(written(packet::write))
                    .isEqualTo(ex(GameServerExOpcode.EX_REPLY_SENT_POST)
                            + d(8)
                            + d(0)
                            + s("Bob")
                            + s("Hi")
                            + s("")
                            + d(0)
                            + q(0)
                            + d(1));
        }

        @Test
        void read_shouldReadBackTheMail() {
            final ExReplySentPost packet = ExReplySentPost.builder()
                    .mailId(7)
                    .receiverName("Bob")
                    .subject("Deal")
                    .body("Text")
                    .attachments(List.of(SNAPSHOT, INVENTORY_ITEM))
                    .codAdena(0)
                    .unread(false)
                    .build();

            assertThat(read(written(packet::write).substring(6), ExReplySentPost::read))
                    .isEqualTo(packet);
        }
    }

    @Nested
    class ExReplyPostItemListCodec {
        private static final ExReplyPostItemList PACKET = new ExReplyPostItemList(List.of(INVENTORY_ITEM, SNAPSHOT));

        @Test
        void write_shouldWriteEachItemBlockWithoutARepeatedObjectId() {
            assertThat(written(PACKET::write))
                    .isEqualTo(ex(GameServerExOpcode.EX_REPLY_POST_ITEM_LIST)
                            + d(2)
                            + written(INVENTORY_ITEM::write)
                            + SNAPSHOT_BLOCK);
        }

        @Test
        void read_shouldReadBackTheItems() {
            assertThat(read(written(PACKET::write).substring(6), ExReplyPostItemList::read))
                    .isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheCountDoesNotFitTheFrame() {
            assertThatThrownBy(() -> read(d(2) + SNAPSHOT_BLOCK, ExReplyPostItemList::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class ExChangePostStateCodec {
        private static final ExChangePostState PACKET = ExChangePostState.builder()
                .receivedBox(true)
                .changes(List.of(
                        new ExChangePostState.Change(5, PostState.DELETED),
                        new ExChangePostState.Change(6, PostState.ATTACHMENTS_TAKEN)))
                .build();

        private static final String BODY = d(1) + d(2) + d(5) + d(0) + d(6) + d(2);

        @Test
        void write_shouldWriteAStatePerMail() {
            assertThat(written(PACKET::write)).isEqualTo(ex(GameServerExOpcode.EX_CHANGE_POST_STATE) + BODY);
        }

        @Test
        void read_shouldReadBackTheChanges() {
            assertThat(read(BODY, ExChangePostState::read)).isEqualTo(PACKET);
        }

        @Test
        void read_shouldFail_whenTheStateIsUnknown() {
            assertThatThrownBy(() -> read(d(0) + d(1) + d(5) + d(9), ExChangePostState::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }
}
