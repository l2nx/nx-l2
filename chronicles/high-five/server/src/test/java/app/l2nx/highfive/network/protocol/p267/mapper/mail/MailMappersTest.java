/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailAction;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.engine.mail.view.Augmentation;
import app.l2nx.engine.mail.view.Elements;
import app.l2nx.engine.mail.view.MailArrived;
import app.l2nx.engine.mail.view.MailBox;
import app.l2nx.engine.mail.view.MailChange;
import app.l2nx.engine.mail.view.MailChangeType;
import app.l2nx.engine.mail.view.MailDone;
import app.l2nx.engine.mail.view.MailNotice;
import app.l2nx.engine.mail.view.MailRefused;
import app.l2nx.engine.mail.view.MailSent;
import app.l2nx.engine.mail.view.MailStateChanged;
import app.l2nx.engine.mail.view.MailSummary;
import app.l2nx.engine.mail.view.MailView;
import app.l2nx.engine.mail.view.MailableItems;
import app.l2nx.engine.mail.view.ReceivedMailList;
import app.l2nx.engine.mail.view.ReceivedMailOpened;
import app.l2nx.engine.mail.view.SentMailList;
import app.l2nx.engine.mail.view.SentMailOpened;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.BodyPart;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.P267MailNumbers;
import app.l2nx.highfive.network.protocol.p267.mapper.P267Mappers;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.test.GoldenBytes;
import app.l2nx.protocol.game.p267.serverpackets.ExChangePostState;
import app.l2nx.protocol.game.p267.serverpackets.ExNoticePostArrived;
import app.l2nx.protocol.game.p267.serverpackets.ExNoticePostSent;
import app.l2nx.protocol.game.p267.serverpackets.ExReplyPostItemList;
import app.l2nx.protocol.game.p267.serverpackets.ExReplyReceivedPost;
import app.l2nx.protocol.game.p267.serverpackets.ExReplySentPost;
import app.l2nx.protocol.game.p267.serverpackets.ExShowReceivedPostList;
import app.l2nx.protocol.game.p267.serverpackets.ExShowSentPostList;
import app.l2nx.protocol.game.p267.serverpackets.ItemBlock;
import app.l2nx.protocol.game.p267.serverpackets.PostSender;
import app.l2nx.protocol.game.p267.serverpackets.PostState;
import app.l2nx.protocol.game.p267.serverpackets.SystemMessage;
import app.l2nx.protocol.game.p267.serverpackets.SystemMessageParam;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class MailMappersTest {
    private static final Instant NOW = ClockBridgeExtension.WALL_START;
    private static final int SERVER_TIME = (int) NOW.getEpochSecond();
    private static final UUID FIRST = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("018f0000-0000-7000-8000-000000000002");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofHours(360));
    private static final int TEMPLATE_ID = 57;
    private static final int DISPLAY_ID = 58;
    private static final List<Integer> NO_DEFENCE = List.of(0, 0, 0, 0, 0, 0);
    private static final int MAX_FRAME = 0xFFFF;

    private static final MailSummary SUMMARY = MailSummary.builder()
            .mailId(FIRST)
            .type(MailType.PLAYER)
            .counterpartName("Bob")
            .subject("Hi")
            .expiresAt(EXPIRES_AT)
            .unread(true)
            .codPending(true)
            .attachmentsTakeable(true)
            .returnable(true)
            .returned(false)
            .build();
    private static final AttachmentSnapshot SNAPSHOT = AttachmentSnapshot.builder()
            .slot(2)
            .itemTemplateId(TEMPLATE_ID)
            .count(3)
            .enchantLevel(4)
            .customType1(0)
            .customType2(0)
            .enchantOptions(List.of())
            .build();
    private static final MailView VIEW = MailView.builder()
            .mailId(FIRST)
            .type(MailType.PLAYER)
            .senderName("Bob")
            .receiverName("Alice")
            .subject("Hi")
            .body("Text")
            .codAdenaPending(500L)
            .attachments(AttachmentState.ATTACHED)
            .unread(false)
            .returnable(true)
            .returned(false)
            .snapshots(List.of())
            .build();

    private final EmbeddedChannel channel = new EmbeddedChannel();
    private final GameClient viewer = mock(GameClient.class);

    @BeforeEach
    void bindViewer() {
        final GameConnection connection = mock(GameConnection.class);
        when(connection.channel()).thenReturn(channel);
        when(viewer.connection()).thenReturn(connection);
    }

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class ReceivedMailListMapping {
        private static final ExShowReceivedPostList.Mail MAIL = ExShowReceivedPostList.Mail.builder()
                .mailId(1)
                .subject("Hi")
                .senderName("Bob")
                .codPending(true)
                .expiresAt((int) EXPIRES_AT.getEpochSecond())
                .unread(true)
                .returnable(true)
                .withItems(true)
                .returned(false)
                .sentBySystem(PostSender.PLAYER)
                .build();

        @Test
        void map_shouldNumberTheMailsUnderTheServerTime() {
            final ReceivedMailList event = new ReceivedMailList(
                    List.of(SUMMARY, SUMMARY.toBuilder().mailId(SECOND).build()));

            assertThat(new ReceivedMailListMapper().map(event, viewer))
                    .isEqualTo(new ExShowReceivedPostList(
                            SERVER_TIME,
                            List.of(MAIL, MAIL.toBuilder().mailId(2).build())));
            assertThat(P267MailNumbers.of(viewer).mail(2)).isEqualTo(SECOND);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"PLAYER, PLAYER", "SYSTEM, SYSTEM", "BIRTHDAY, BIRTHDAY"})
        void map_shouldShowTheSenderKindOfTheMailType(MailType type, PostSender expected) {
            final ReceivedMailList event = new ReceivedMailList(
                    List.of(SUMMARY.toBuilder().type(type).counterpartName(null).build()));

            final ExShowReceivedPostList actual =
                    (ExShowReceivedPostList) new ReceivedMailListMapper().map(event, viewer);

            assertThat(actual.mails()).singleElement().satisfies(mail -> {
                assertThat(mail.sentBySystem()).isEqualTo(expected);
                assertThat(mail.senderName()).isNull();
            });
        }

        @Test
        void serialize_shouldWriteTheRecordThroughTheAdapterTable() {
            final ReceivedMailList event = new ReceivedMailList(List.of(SUMMARY));

            assertThat(payload(P267Mappers.serialize(event, viewer, UnpooledByteBufAllocator.DEFAULT)))
                    .isEqualTo(GoldenBytes.written(new ExShowReceivedPostList(SERVER_TIME, List.of(MAIL))::write));
        }
    }

    @Nested
    class SentMailListMapping {
        @Test
        void map_shouldNumberTheMailsOfTheSentBox() {
            final SentMailList event = new SentMailList(List.of(SUMMARY.toBuilder()
                    .counterpartName("Alice")
                    .codPending(false)
                    .attachmentsTakeable(false)
                    .returnable(false)
                    .build()));

            assertThat(new SentMailListMapper().map(event, viewer))
                    .isEqualTo(new ExShowSentPostList(
                            SERVER_TIME,
                            List.of(ExShowSentPostList.Mail.builder()
                                    .mailId(1)
                                    .subject("Hi")
                                    .receiverName("Alice")
                                    .expiresAt((int) EXPIRES_AT.getEpochSecond())
                                    .unread(true)
                                    .build())));
        }
    }

    @Nested
    class FullBox {
        private static final int RETAIL_LIMIT = 240;
        private static final String SUBJECT = "s".repeat(128);
        private static final String NAME = "n".repeat(16);

        static IntStream boxSizes() {
            return IntStream.of(RETAIL_LIMIT, P267MailWire.maxListMails());
        }

        @ParameterizedTest(name = "{0} mails")
        @MethodSource("boxSizes")
        void serialize_shouldFitAFullReceivedBoxInOneFrame_whenEverySubjectIsAtItsLimit(int size) {
            final ReceivedMailList event = new ReceivedMailList(fullBox(size));

            assertThat(frameBytes(P267Mappers.serialize(event, viewer, UnpooledByteBufAllocator.DEFAULT)))
                    .isLessThanOrEqualTo(MAX_FRAME);
        }

        @ParameterizedTest(name = "{0} mails")
        @MethodSource("boxSizes")
        void serialize_shouldFitAFullSentBoxInOneFrame_whenEverySubjectIsAtItsLimit(int size) {
            final SentMailList event = new SentMailList(fullBox(size));

            assertThat(frameBytes(P267Mappers.serialize(event, viewer, UnpooledByteBufAllocator.DEFAULT)))
                    .isLessThanOrEqualTo(MAX_FRAME);
        }

        @Test
        void map_shouldCutEverySubjectAlike_whenTheRetailBoxIsFull() {
            final ReceivedMailList event = new ReceivedMailList(fullBox(RETAIL_LIMIT));

            assertThat(((ExShowReceivedPostList) new ReceivedMailListMapper().map(event, viewer)).mails())
                    .allSatisfy(mail -> assertThat(mail.subject()).hasSize(100));
        }

        @Test
        void map_shouldKeepTheWholeSubject_whenTheListHasRoomForIt() {
            final ReceivedMailList event = new ReceivedMailList(fullBox(10));

            assertThat(((ExShowReceivedPostList) new ReceivedMailListMapper().map(event, viewer)).mails())
                    .allSatisfy(mail -> assertThat(mail.subject()).isEqualTo(SUBJECT));
        }

        @Test
        void listSubject_shouldNotSplitASurrogatePair() {
            final String head = "a".repeat(9);
            final String subject = head + new String(Character.toChars(0x1F600)) + "tail";

            assertThat(P267MailWire.listSubject(subject, 10)).isEqualTo(head);
        }

        @Test
        void listSubject_shouldBeNull_whenNothingOfTheSubjectIsLeft() {
            assertThat(P267MailWire.listSubject(SUBJECT, 0)).isNull();
        }

        private static List<MailSummary> fullBox(int size) {
            final List<MailSummary> mails = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                mails.add(SUMMARY.toBuilder()
                        .mailId(new UUID(FIRST.getMostSignificantBits(), i))
                        .subject(SUBJECT)
                        .counterpartName(NAME)
                        .build());
            }
            return mails;
        }

        private static int frameBytes(ByteBuf frame) {
            try {
                return frame.readableBytes();
            } finally {
                frame.release();
            }
        }
    }

    @Nested
    class ReceivedMailOpenedMapping {
        @Test
        void map_shouldKeepTheNumberTheListGave() {
            P267MailNumbers.of(viewer).show(MailBox.RECEIVED, List.of(SECOND, FIRST));

            assertThat(new ReceivedMailOpenedMapper().map(new ReceivedMailOpened(VIEW), viewer))
                    .isEqualTo(ExReplyReceivedPost.builder()
                            .mailId(2)
                            .codPending(true)
                            .senderName("Bob")
                            .subject("Hi")
                            .body("Text")
                            .attachments(List.of())
                            .codAdena(500)
                            .returnable(true)
                            .sentBySystem(PostSender.PLAYER)
                            .build());
        }

        @Test
        void map_shouldShowNoPayment_whenTheMailSettled() {
            final MailView settled = VIEW.toBuilder()
                    .codAdenaPending(null)
                    .attachments(AttachmentState.RECEIVED)
                    .returnable(false)
                    .build();

            final ExReplyReceivedPost actual =
                    (ExReplyReceivedPost) new ReceivedMailOpenedMapper().map(new ReceivedMailOpened(settled), viewer);

            assertThat(actual.codPending()).isFalse();
            assertThat(actual.codAdena()).isZero();
            assertThat(actual.returnable()).isFalse();
        }

        @Test
        void map_shouldListTheSnapshotsWithSyntheticHandles_whenTheItemsWereTaken() {
            final MailView settled = VIEW.toBuilder()
                    .codAdenaPending(null)
                    .attachments(AttachmentState.RECEIVED)
                    .snapshots(List.of(SNAPSHOT))
                    .build();

            try (MockedStatic<ItemData> data = templates()) {
                final ExReplyReceivedPost actual = (ExReplyReceivedPost)
                        new ReceivedMailOpenedMapper().map(new ReceivedMailOpened(settled), viewer);

                assertThat(actual.attachments()).containsExactly(block().build());
            }
        }
    }

    @Nested
    class SentMailOpenedMapping {
        @Test
        void map_shouldShowTheReceiverAndWhetherTheMailIsUnread() {
            final MailView sent =
                    VIEW.toBuilder().unread(true).snapshots(List.of(SNAPSHOT)).build();

            try (MockedStatic<ItemData> data = templates()) {
                assertThat(new SentMailOpenedMapper().map(new SentMailOpened(sent), viewer))
                        .isEqualTo(ExReplySentPost.builder()
                                .mailId(1)
                                .codPending(true)
                                .receiverName("Alice")
                                .subject("Hi")
                                .body("Text")
                                .attachments(List.of(block().build()))
                                .codAdena(500)
                                .unread(true)
                                .build());
            }
        }
    }

    @Nested
    class AttachmentBlock {
        @Test
        void attachment_shouldCarryTheSnapshotValues() {
            final AttachmentSnapshot snapshot = SNAPSHOT.toBuilder()
                    .slot(0)
                    .manaLeft(30)
                    .itemExpiresAt(NOW.plusSeconds(90))
                    .augmentation(new Augmentation(10, 20))
                    .elements(Elements.builder()
                            .attackElement(2)
                            .attackElementPower(150)
                            .fire(1)
                            .water(2)
                            .wind(3)
                            .earth(4)
                            .holy(5)
                            .dark(6)
                            .build())
                    .enchantOptions(List.of(7))
                    .build();

            try (MockedStatic<ItemData> data = templates()) {
                assertThat(P267MailWire.attachment(snapshot, NOW.toEpochMilli()))
                        .isEqualTo(block().objectId(1)
                                .location(0)
                                .mana(30)
                                .remainingSeconds(90)
                                .augmentationId((20 << 16) | 10)
                                .attackElementType(2)
                                .attackElementPower(150)
                                .elementDefence(List.of(1, 2, 3, 4, 5, 6))
                                .enchantOptions(List.of(7, 0, 0))
                                .build());
            }
        }

        @Test
        void attachment_shouldShowNoTimeLeft_whenTheItemExpiredSinceTheSend() {
            final AttachmentSnapshot snapshot =
                    SNAPSHOT.toBuilder().itemExpiresAt(NOW.minusSeconds(5)).build();

            try (MockedStatic<ItemData> data = templates()) {
                assertThat(P267MailWire.attachment(snapshot, NOW.toEpochMilli()).remainingSeconds())
                        .isZero();
            }
        }

        @Test
        void attachment_shouldShowTheBareItemId_whenTheTemplateIsUnknown() {
            try (MockedStatic<ItemData> data = mockStatic(ItemData.class)) {
                data.when(ItemData::getInstance).thenReturn(mock(ItemData.class));

                assertThat(P267MailWire.attachment(SNAPSHOT, NOW.toEpochMilli()))
                        .isEqualTo(block().displayId(TEMPLATE_ID)
                                .type2(ItemTemplate.TYPE2_OTHER)
                                .bodyPart(0)
                                .build());
            }
        }
    }

    @Nested
    class MailableItemsMapping {
        private static final int HANDLE = 0x10000005;

        @Test
        void map_shouldListTheLiveItemsOfTheHandles_whenTheyAreStillInTheInventory() {
            final Item item = item();
            final PlayerInventory inventory = mock(PlayerInventory.class);
            when(inventory.getItemByObjectId(HANDLE)).thenReturn(item);
            final Player player = mock(Player.class);
            when(player.getInventory()).thenReturn(inventory);
            when(viewer.getPlayer()).thenReturn(player);

            assertThat(new MailableItemsMapper().map(new MailableItems(List.of(HANDLE, HANDLE + 1)), viewer))
                    .isEqualTo(new ExReplyPostItemList(List.of(ItemBlock.builder()
                            .objectId(HANDLE)
                            .displayId(DISPLAY_ID)
                            .location(6)
                            .count(10)
                            .type2(ItemTemplate.TYPE2_ACCESSORY)
                            .bodyPart(BodyPart.NECK.getMask())
                            .enchantLevel(4)
                            .mana(ItemBlock.NO_MANA)
                            .remainingSeconds(ItemBlock.NO_TIME_LIMIT)
                            .attackElementType(ItemBlock.NO_ATTACK_ELEMENT)
                            .elementDefence(NO_DEFENCE)
                            .enchantOptions(List.of(0, 0, 0))
                            .build())));
        }

        @Test
        void map_shouldSendNothing_whenTheViewerHasNoCharacter() {
            assertThat(new MailableItemsMapper().map(new MailableItems(List.of(HANDLE)), viewer))
                    .isNull();
        }

        private static Item item() {
            final ItemTemplate template = template();
            final Item item = mock(Item.class);
            when(item.getObjectId()).thenReturn(HANDLE);
            when(item.getTemplate()).thenReturn(template);
            when(item.getCount()).thenReturn(10L);
            when(item.getEnchantLevel()).thenReturn(4);
            when(item.getLocationSlot()).thenReturn(6);
            when(item.getMana()).thenReturn(ItemBlock.NO_MANA);
            when(item.getAttackElementType()).thenReturn((byte) ItemBlock.NO_ATTACK_ELEMENT);
            when(item.getEnchantOptions()).thenReturn(Item.DEFAULT_ENCHANT_OPTIONS);
            return item;
        }
    }

    @Nested
    class Notices {
        @ParameterizedTest(name = "fresh {0}")
        @CsvSource({"true", "false"})
        void map_shouldAnimateOnlyAFreshArrival(boolean fresh) {
            assertThat(new MailArrivedMapper().map(new MailArrived(fresh), viewer))
                    .isEqualTo(new ExNoticePostArrived(fresh));
        }

        @Test
        void map_shouldAnimateTheSend() {
            assertThat(new MailSentMapper().map(MailSent.INSTANCE, viewer)).isEqualTo(new ExNoticePostSent(true));
        }
    }

    @Nested
    class MailDoneMapping {
        @ParameterizedTest(name = "{0}")
        @CsvSource({"SENT, 3009", "RECEIVED, 3012", "RETURNED, 3010", "CANCELLED, 3011", "EXPIRED_RETURNED, 3068"})
        void map_shouldBeTheSuccessMessage_whenTheNoticeNamesNobody(MailNotice notice, int expected) {
            assertThat(new MailDoneMapper().map(MailDone.of(notice), null))
                    .isEqualTo(new SystemMessage(expected, List.of()));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"ATTACHMENTS_TAKEN, 3072", "RETURNED_TO_YOU, 3029", "CANCELLED_BY_SENDER, 3067"})
        void map_shouldNameTheOtherCharacter(MailNotice notice, int expected) {
            assertThat(new MailDoneMapper().map(MailDone.of(notice, "Bob"), null))
                    .isEqualTo(new SystemMessage(expected, List.of(new SystemMessageParam.Text("Bob"))));
        }

        @Test
        void map_shouldNameTheAmountBeforeThePayer_whenTheCashOnDeliveryWasPaid() {
            assertThat(new MailDoneMapper().map(new MailDone(MailNotice.COD_PAID, "Bob", 700), null))
                    .isEqualTo(new SystemMessage(
                            3025, List.of(new SystemMessageParam.LongNumber(700), new SystemMessageParam.Text("Bob"))));
        }
    }

    @Nested
    class MailStateChangedMapping {
        @Test
        void map_shouldNameTheMailsByTheirNumbersInTheirBox() {
            P267MailNumbers.of(viewer).show(MailBox.SENT, List.of(FIRST, SECOND));
            final MailStateChanged event = new MailStateChanged(
                    MailBox.SENT,
                    List.of(
                            new MailChange(SECOND, MailChangeType.READ),
                            new MailChange(FIRST, MailChangeType.ATTACHMENTS_TAKEN)));

            assertThat(new MailStateChangedMapper().map(event, viewer))
                    .isEqualTo(new ExChangePostState(
                            false,
                            List.of(
                                    new ExChangePostState.Change(2, PostState.READ),
                                    new ExChangePostState.Change(1, PostState.ATTACHMENTS_TAKEN))));
        }

        @Test
        void map_shouldLeaveOutAMailTheClientWasNeverShown() {
            P267MailNumbers.of(viewer).show(MailBox.RECEIVED, List.of(FIRST));
            final MailStateChanged event = new MailStateChanged(
                    MailBox.RECEIVED,
                    List.of(
                            new MailChange(SECOND, MailChangeType.DELETED),
                            new MailChange(FIRST, MailChangeType.DELETED)));

            assertThat(new MailStateChangedMapper().map(event, viewer))
                    .isEqualTo(
                            new ExChangePostState(true, List.of(new ExChangePostState.Change(1, PostState.DELETED))));
        }

        @Test
        void map_shouldSendNothing_whenNoMailWasShown() {
            final MailStateChanged event = MailStateChanged.of(MailBox.RECEIVED, FIRST, MailChangeType.READ);

            assertThat(new MailStateChangedMapper().map(event, viewer)).isNull();
        }
    }

    @Nested
    class MailRefusedMapping {
        @Test
        void id_shouldGiveEveryRefusalOfEveryActionAHighFiveMessage() {
            for (MailAction action : MailAction.values()) {
                for (MailRefusal refusal : MailRefusal.values()) {
                    assertThat(P267MailRefusals.id(action, refusal))
                            .as("%s %s", action, refusal)
                            .isNotNull();
                }
            }
        }

        @ParameterizedTest(name = "{0} {1}")
        @CsvSource({
            "RECEIVE, ALREADY_RECEIVED, 3382",
            "CANCEL, ALREADY_RECEIVED, 3030",
            "SEND, COD_AMOUNT_INVALID, 3020",
            "SEND, COD_WITHOUT_ATTACHMENTS, 2966",
            "SEND, RECEIVER_SELF, 3019",
            "SEND, NOT_IN_PEACE_ZONE, 3066",
            "RECEIVE, NOT_IN_PEACE_ZONE, 3066",
            "CANCEL, NOT_IN_PEACE_ZONE, 3066",
            "SEND, BUSY, 2971",
            "RECEIVE, BUSY, 2977",
            "SEND, NOT_ENOUGH_ADENA, 2975",
            "RECEIVE, NOT_ENOUGH_ADENA, 2980",
            "RECEIVE, INVENTORY_FULL, 2981",
            "CANCEL, INVENTORY_FULL, 2988",
            "RECEIVE, SENDER_ADENA_LIMIT, 3081",
            "CANCEL, ADENA_LIMIT, 403",
            "READ, DISABLED, 483"
        })
        void map_shouldBeTheSystemMessageOfTheActionAndTheRefusal(
                MailAction action, MailRefusal refusal, int expected) {
            assertThat(new MailRefusedMapper().map(MailRefused.of(action, refusal), null))
                    .isEqualTo(new SystemMessage(expected, List.of()));
        }

        @Test
        void map_shouldNameTheReceiver_whenTheReceiverBlockedTheSender() {
            final MailRefused event = new MailRefused(MailAction.SEND, MailRefusal.RECEIVER_BLOCKED, "Bob");

            assertThat(new MailRefusedMapper().map(event, null))
                    .isEqualTo(new SystemMessage(
                            SystemMessageId.C1_HAS_BLOCKED_YOU_YOU_CANNOT_SEND_MAIL_TO_C1.getId(),
                            List.of(new SystemMessageParam.PlayerName("Bob"))));
        }
    }

    private static MockedStatic<ItemData> templates() {
        final ItemTemplate template = template();
        final ItemData data = mock(ItemData.class);
        when(data.getTemplate(TEMPLATE_ID)).thenReturn(template);
        final MockedStatic<ItemData> mocked = mockStatic(ItemData.class);
        mocked.when(ItemData::getInstance).thenReturn(data);
        return mocked;
    }

    private static ItemTemplate template() {
        final ItemTemplate template = mock(ItemTemplate.class);
        when(template.getDisplayId()).thenReturn(DISPLAY_ID);
        when(template.getType2()).thenReturn(ItemTemplate.TYPE2_ACCESSORY);
        when(template.getBodyPart()).thenReturn(BodyPart.NECK);
        return template;
    }

    private static ItemBlock.ItemBlockBuilder block() {
        return ItemBlock.builder()
                .objectId(3)
                .displayId(DISPLAY_ID)
                .location(2)
                .count(3)
                .type2(ItemTemplate.TYPE2_ACCESSORY)
                .bodyPart(BodyPart.NECK.getMask())
                .enchantLevel(4)
                .mana(ItemBlock.NO_MANA)
                .remainingSeconds(ItemBlock.NO_TIME_LIMIT)
                .attackElementType(ItemBlock.NO_ATTACK_ELEMENT)
                .elementDefence(NO_DEFENCE)
                .enchantOptions(List.of(0, 0, 0));
    }

    private static String payload(ByteBuf frame) {
        try {
            return HexFormat.of()
                    .formatHex(ByteBufUtil.getBytes(frame, Frames.HEADER, frame.readableBytes() - Frames.HEADER));
        } finally {
            frame.release();
        }
    }
}
