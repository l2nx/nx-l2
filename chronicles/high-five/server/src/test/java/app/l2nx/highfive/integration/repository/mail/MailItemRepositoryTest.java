/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.CreatedItems;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.ItemTransfer;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.NewItem;
import app.l2nx.engine.mail.Recipient;
import app.l2nx.engine.mail.ReservedItem;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.data.xml.EnchantItemOptionsData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.mail.repository.MailItemRepository;
import app.l2nx.highfive.mail.repository.MailItemRow;
import app.l2nx.highfive.mechanics.options.EnchantOptions;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.dao.DataAccessException;

@ResourceLock("PlayerConfig")
class MailItemRepositoryTest extends RepositoryTest {
    private static final int SWORD = 1;
    private static final int ARROW = 17;
    private static final int ADENA = 57;
    private static final int TIMED_RING = 13_000;
    private static final int GM_LEVEL = 8;

    private final ManualGameClock clock = new ManualGameClock(NOW, ZoneOffset.UTC);
    private final MailItemRepository repository = new MailItemRepository(JDBC, clock);
    private final List<MockedStatic<?>> bridges = new ArrayList<>();
    private final ItemData items = mock(ItemData.class);
    private final AdminData admins = mock(AdminData.class);
    private final EnchantItemOptionsData enchantOptions = mock(EnchantItemOptionsData.class);

    private long maxAdena;

    @BeforeEach
    void bindStaticData() {
        maxAdena = PlayerConfig.MAX_ADENA;
        PlayerConfig.MAX_ADENA = 1_000_000;
        bind(ItemData.class, ItemData::getInstance, items);
        bind(AdminData.class, AdminData::getInstance, admins);
        bind(EnchantItemOptionsData.class, EnchantItemOptionsData::getInstance, enchantOptions);
        when(admins.hasAccessLevel(anyInt())).thenReturn(false);
        template(SWORD, false, -1, -1);
        template(ARROW, true, -1, -1);
        template(ADENA, true, -1, -1);
        template(TIMED_RING, false, 60, 120);
        final AccessLevel gm = mock(AccessLevel.class);
        when(gm.isGm()).thenReturn(true);
        when(admins.hasAccessLevel(GM_LEVEL)).thenReturn(true);
        when(admins.getAccessLevel(GM_LEVEL)).thenReturn(gm);
    }

    @AfterEach
    void releaseStaticData() {
        bridges.forEach(MockedStatic::close);
        PlayerConfig.MAX_ADENA = maxAdena;
    }

    @Nested
    class FindRecipient {
        @Test
        void findRecipient_shouldFindTheCharacter_whenTheNameDiffersInCase() {
            final UUID id = insertCharacter();
            final String name = name(id);

            final Recipient actual =
                    repository.findRecipient(name.toUpperCase()).orElseThrow();

            assertThat(actual).isEqualTo(Recipient.builder().id(id).name(name).build());
        }

        @Test
        void findRecipient_shouldBeEmpty_whenNoCharacterHasTheName() {
            assertThat(repository.findRecipient("nobody")).isEmpty();
        }

        @Test
        void findRecipient_shouldMarkAGm_byTheAccessLevel() {
            final UUID id = insertCharacter();
            JDBC.sql("UPDATE characters SET access_level = ? WHERE id = ?")
                    .param(GM_LEVEL)
                    .param(id)
                    .update();

            assertThat(repository.findRecipient(id).orElseThrow().gm()).isTrue();
        }
    }

    @Nested
    class IsBlocked {
        @Test
        void isBlocked_shouldFollowTheReceiversBlockList() {
            final UUID receiver = insertCharacter();
            final UUID sender = insertCharacter();
            JDBC.sql("INSERT INTO character_relations VALUES (?, ?, 'BLOCK')")
                    .param(receiver)
                    .param(sender)
                    .update();

            assertThat(repository.isBlocked(receiver, sender)).isTrue();
            assertThat(repository.isBlocked(sender, receiver)).isFalse();
        }
    }

    @Nested
    class ToMail {
        @Test
        void toMail_shouldMoveAWholeRow_withItsCountFromMemory() {
            final UUID owner = insertCharacter();
            final UUID sword = insertItem(owner, SWORD, 1);
            final UUID arrows = insertItem(owner, ARROW, 40);
            final UUID mail = insertMail();

            final List<ItemMovement> actual = repository.toMail(
                    mail,
                    List.of(
                            new ReservedItem(state(sword, owner, SWORD, 1), 1),
                            new ReservedItem(state(arrows, owner, ARROW, 55), 55)));

            assertThat(actual)
                    .containsExactly(
                            movement(HolderType.INVENTORY, owner, SWORD, sword, -1),
                            movement(HolderType.MAIL, mail, SWORD, sword, 1),
                            movement(HolderType.INVENTORY, owner, ARROW, arrows, -55),
                            movement(HolderType.MAIL, mail, ARROW, arrows, 55));
            assertThat(itemsOfMail(mail)).containsExactlyInAnyOrder(tuple(sword, 1L), tuple(arrows, 55L));
        }

        @Test
        void toMail_shouldSplitAStack_intoANewRowOfTheMail() {
            final UUID owner = insertCharacter();
            final UUID arrows = insertItem(owner, ARROW, 100);
            final UUID mail = insertMail();

            final List<ItemMovement> actual =
                    repository.toMail(mail, List.of(new ReservedItem(state(arrows, owner, ARROW, 120), 30)));

            final UUID split = actual.get(1).itemId();
            assertThat(split).isNotEqualTo(arrows);
            assertThat(count(arrows)).isEqualTo(90);
            assertThat(itemsOfMail(mail)).containsExactly(tuple(split, 30L));
        }

        @Test
        void toMail_shouldInsertAndMoveTheRow_whenTheItemHadNoneYet() {
            final UUID owner = insertCharacter();
            final UUID sword = EntityIds.next();
            final UUID mail = insertMail();

            repository.toMail(mail, List.of(new ReservedItem(unstored(sword, owner, SWORD, 1), 1)));

            assertThat(itemsOfMail(mail)).containsExactly(tuple(sword, 1L));
            assertThat(augmentation(sword)).isEqualTo(5);
            assertThat(elementals(sword)).containsExactly(tuple((short) 2, 40));
        }

        @Test
        void toMail_shouldInsertAndSplitTheRow_whenThePartOfAStackHadNoneYet() {
            final UUID owner = insertCharacter();
            final UUID arrows = EntityIds.next();
            final UUID mail = insertMail();

            final List<ItemMovement> actual =
                    repository.toMail(mail, List.of(new ReservedItem(unstored(arrows, owner, ARROW, 50), 20)));

            assertThat(count(arrows)).isEqualTo(30);
            assertThat(location(arrows)).isEqualTo(tuple(owner, "INVENTORY", null));
            assertThat(itemsOfMail(mail)).containsExactly(tuple(actual.get(1).itemId(), 20L));
        }

        @Test
        void toMail_shouldThrow_whenTheOwnerNoLongerHoldsTheItem() {
            final UUID owner = insertCharacter();
            final UUID sword = insertItem(insertCharacter(), SWORD, 1);
            final UUID mail = insertMail();

            assertThatThrownBy(() ->
                            repository.toMail(mail, List.of(new ReservedItem(state(sword, owner, SWORD, 1), 1))))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Nested
    class CreateInMail {
        @Test
        void createInMail_shouldInsertTheItems_andSnapshotThemFromTheTemplates() {
            final EnchantOptions options = new EnchantOptions(3);
            options.setOption((byte) 0, 7);
            when(enchantOptions.getOptions(TIMED_RING, 3)).thenReturn(options);
            final UUID mail = insertMail();

            final CreatedItems actual = repository.createInMail(
                    mail,
                    List.of(
                            NewItem.builder().itemTemplateId(ARROW).count(500).build(),
                            NewItem.builder()
                                    .itemTemplateId(TIMED_RING)
                                    .count(1)
                                    .enchantLevel(3)
                                    .build()));

            assertThat(actual.snapshots())
                    .containsExactly(
                            AttachmentSnapshot.builder()
                                    .slot(0)
                                    .itemTemplateId(ARROW)
                                    .count(500)
                                    .enchantOptions(List.of())
                                    .build(),
                            AttachmentSnapshot.builder()
                                    .slot(1)
                                    .itemTemplateId(TIMED_RING)
                                    .count(1)
                                    .enchantLevel(3)
                                    .manaLeft(60)
                                    .itemExpiresAt(NOW.plus(Duration.ofMinutes(120)))
                                    .enchantOptions(List.of(7, 0, 0))
                                    .build());
            assertThat(actual.movements())
                    .extracting(ItemMovement::holderType, ItemMovement::holderId, ItemMovement::delta)
                    .containsExactly(tuple(HolderType.MAIL, mail, 500L), tuple(HolderType.MAIL, mail, 1L));
            final MailItemRow ring = (MailItemRow)
                    repository.rows(List.of(actual.movements().get(1).itemId())).getFirst();
            assertThat(ring)
                    .extracting(
                            MailItemRow::location,
                            MailItemRow::enchantLevel,
                            MailItemRow::manaLeft,
                            MailItemRow::expiresAt)
                    .containsExactly(ItemLocation.MAIL, 3, 60, NOW.plus(Duration.ofMinutes(120)));
        }

        @ParameterizedTest(name = "template {0}, count {1}")
        @CsvSource({"1, 2", "99999, 1"})
        void checkCreatable_shouldThrow_whenAnItemCannotBeCreated(int itemTemplateId, long count) {
            final List<NewItem> items = List.of(NewItem.builder()
                    .itemTemplateId(itemTemplateId)
                    .count(count)
                    .build());

            assertThatThrownBy(() -> repository.checkCreatable(items)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void createInMail_shouldRefuse_whenANonStackableItemHasACountAboveOne() {
            final UUID mail = insertMail();

            assertThatThrownBy(() -> repository.createInMail(
                            mail,
                            List.of(NewItem.builder()
                                    .itemTemplateId(SWORD)
                                    .count(2)
                                    .build())))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ToCharacter {
        @Test
        void toCharacter_shouldMergeIntoAnOnlineStack_withAnAbsoluteCount() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID stack = insertItem(owner, ARROW, 10);
            final UUID mail = insertMail();
            final UUID mailed = insertMailItem(mail, ARROW, 25);
            final UUID sword = insertMailItem(mail, SWORD, 1);

            final ItemTransfer actual =
                    repository.toCharacter(mail, Holder.inventory(owner), List.of(state(stack, owner, ARROW, 70)));

            assertThat(count(stack)).isEqualTo(95);
            assertThat(exists(mailed)).isFalse();
            assertThat(location(sword)).isEqualTo(tuple(owner, "INVENTORY", null));
            assertThat(actual.movements())
                    .containsExactlyInAnyOrder(
                            movement(HolderType.MAIL, mail, ARROW, mailed, -25),
                            movement(HolderType.INVENTORY, owner, ARROW, stack, 25),
                            movement(HolderType.MAIL, mail, SWORD, sword, -1),
                            movement(HolderType.INVENTORY, owner, SWORD, sword, 1));
            assertThat(actual.rows())
                    .map(MailItemRow.class::cast)
                    .extracting(MailItemRow::itemId, MailItemRow::count, MailItemRow::arrived)
                    .containsExactlyInAnyOrder(tuple(stack, 95L, 25L), tuple(sword, 1L, 1L));
        }

        @Test
        void toCharacter_shouldMoveTheRow_whenThePaymentLeftTheOnlineStackEmpty() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID adena = insertItem(owner, ADENA, 300);
            final UUID mail = insertMail();
            final UUID mailed = insertMailItem(mail, ADENA, 50);
            repository.adena(Holder.inventory(owner), -300, state(adena, owner, ADENA, 300));

            repository.toCharacter(mail, Holder.inventory(owner), List.of(state(adena, owner, ADENA, 0)));

            assertThat(exists(adena)).isFalse();
            assertThat(count(mailed)).isEqualTo(50);
            assertThat(location(mailed)).isEqualTo(tuple(owner, "INVENTORY", null));
        }

        @Test
        void toCharacter_shouldRefuseTheLimit_whenOfflineAdenaWouldMergePastIt() {
            final UUID owner = insertCharacter();
            final UUID stored = insertItem(owner, ADENA, ItemLocation.WAREHOUSE, null, 999_950);
            final UUID mail = insertMail();
            insertMailItem(mail, ADENA, 100);

            assertThatThrownBy(() -> repository.toCharacter(mail, Holder.warehouse(owner), null))
                    .isInstanceOfSatisfying(
                            ValueRefusal.class,
                            refusal -> assertThat(refusal.reason()).isEqualTo(MailRefusal.ADENA_LIMIT));
            assertThat(count(stored)).isEqualTo(999_950);
        }

        @Test
        void toCharacter_shouldMoveAStack_whenTheOnlineTargetHasNone() throws ValueRefusal {
            final UUID owner = insertCharacter();
            insertItem(owner, ARROW, 10);
            final UUID mail = insertMail();
            final UUID mailed = insertMailItem(mail, ARROW, 25);

            repository.toCharacter(mail, Holder.inventory(owner), List.of());

            assertThat(count(mailed)).isEqualTo(25);
            assertThat(location(mailed)).isEqualTo(tuple(owner, "INVENTORY", null));
        }

        @Test
        void toCharacter_shouldMergeIntoAnEquippedStack_byDelta_whenTheTargetIsOffline() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID equipped = insertItem(owner, ARROW, ItemLocation.PAPERDOLL, 8, 10);
            final UUID mail = insertMail();
            insertMailItem(mail, ARROW, 25);
            insertMailItem(mail, ARROW, 5);

            final ItemTransfer actual = repository.toCharacter(mail, Holder.inventory(owner), null);

            assertThat(count(equipped)).isEqualTo(40);
            assertThat(itemsOfMail(mail)).isEmpty();
            assertThat(actual.rows())
                    .map(MailItemRow.class::cast)
                    .extracting(MailItemRow::itemId, MailItemRow::location, MailItemRow::count, MailItemRow::arrived)
                    .containsExactly(tuple(equipped, ItemLocation.PAPERDOLL, 40L, 30L));
        }

        @Test
        void toCharacter_shouldMergeTwoMailStacksIntoOne_whenTheOfflineWarehouseHasNone() throws ValueRefusal {
            final UUID owner = insertCharacter();
            insertItem(owner, ARROW, 10);
            final UUID mail = insertMail();
            final UUID first = insertMailItem(mail, ARROW, 25);
            final UUID second = insertMailItem(mail, ARROW, 5);

            final ItemTransfer actual = repository.toCharacter(mail, Holder.warehouse(owner), null);

            assertThat(location(first)).isEqualTo(tuple(owner, "WAREHOUSE", null));
            assertThat(count(first)).isEqualTo(30);
            assertThat(exists(second)).isFalse();
            assertThat(actual.movements())
                    .filteredOn(movement -> movement.holderType() == HolderType.WAREHOUSE)
                    .extracting(ItemMovement::itemId, ItemMovement::delta)
                    .containsExactly(tuple(first, 25L), tuple(first, 5L));
        }

        @Test
        void toCharacter_shouldThrow_whenTheOnlineStackChangedOwner() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID stack = insertItem(insertCharacter(), ARROW, 10);
            final UUID mail = insertMail();
            insertMailItem(mail, ARROW, 25);

            assertThatThrownBy(() -> repository.toCharacter(
                            mail, Holder.inventory(owner), List.of(state(stack, owner, ARROW, 10))))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Nested
    class ToMailOf {
        @Test
        void toMailOf_shouldMoveEveryLiveItem_withAMovementOnEachSide() {
            final UUID from = insertMail();
            final UUID to = insertMail();
            final UUID sword = insertMailItem(from, SWORD, 1);

            final List<ItemMovement> actual = repository.toMailOf(from, to);

            assertThat(actual)
                    .containsExactly(
                            movement(HolderType.MAIL, from, SWORD, sword, -1),
                            movement(HolderType.MAIL, to, SWORD, sword, 1));
            assertThat(itemsOfMail(to)).containsExactly(tuple(sword, 1L));
        }
    }

    @Nested
    class Adena {
        @Test
        void adena_shouldRefuseTheLimit_whenTheOfflineRowWouldGrowPastIt() {
            final UUID owner = insertCharacter();
            final UUID adena = insertItem(owner, ADENA, 999_000);

            assertThatThrownBy(() -> repository.adena(Holder.inventory(owner), 1_001, null))
                    .isInstanceOfSatisfying(
                            ValueRefusal.class,
                            refusal -> assertThat(refusal.reason()).isEqualTo(MailRefusal.ADENA_LIMIT));
            assertThat(count(adena)).isEqualTo(999_000);
        }

        @Test
        void adena_shouldRefuseTheLimit_whenTheOnlineRowWouldGrowPastIt() {
            final UUID owner = insertCharacter();
            final UUID adena = insertItem(owner, ADENA, 999_000);

            assertThatThrownBy(
                            () -> repository.adena(Holder.inventory(owner), 1_001, state(adena, owner, ADENA, 999_000)))
                    .isInstanceOfSatisfying(
                            ValueRefusal.class,
                            refusal -> assertThat(refusal.reason()).isEqualTo(MailRefusal.ADENA_LIMIT));
        }

        @Test
        void adena_shouldInsertTheOnlineRowFirst_whenItHadNoneYet() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID adena = EntityIds.next();

            repository.adena(Holder.inventory(owner), -300, unstored(adena, owner, ADENA, 1000));

            assertThat(count(adena)).isEqualTo(700);
            assertThat(location(adena)).isEqualTo(tuple(owner, "INVENTORY", null));
        }

        @Test
        void adena_shouldWriteTheOnlineRowAbsolutely() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID adena = insertItem(owner, ADENA, 100);

            final ItemTransfer actual =
                    repository.adena(Holder.inventory(owner), -300, state(adena, owner, ADENA, 1000));

            assertThat(count(adena)).isEqualTo(700);
            assertThat(actual.movements()).containsExactly(movement(HolderType.INVENTORY, owner, ADENA, adena, -300));
        }

        @Test
        void adena_shouldDeleteTheOnlineRow_whenAllOfItIsPaid() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID adena = insertItem(owner, ADENA, 100);

            final ItemTransfer actual =
                    repository.adena(Holder.inventory(owner), -1000, state(adena, owner, ADENA, 1000));

            assertThat(exists(adena)).isFalse();
            assertThat(actual.rows())
                    .map(MailItemRow.class::cast)
                    .extracting(MailItemRow::itemId, MailItemRow::deleted)
                    .containsExactly(tuple(adena, true));
        }

        @Test
        void adena_shouldChangeTheOfflineRowByDelta() throws ValueRefusal {
            final UUID owner = insertCharacter();
            final UUID adena = insertItem(owner, ADENA, 100);

            repository.adena(Holder.inventory(owner), 250, null);

            assertThat(count(adena)).isEqualTo(350);
        }

        @Test
        void adena_shouldInsertARow_whenTheOfflineHolderHasNoAdena() throws ValueRefusal {
            final UUID owner = insertCharacter();

            final ItemTransfer actual = repository.adena(Holder.inventory(owner), 250, null);

            final UUID adena = actual.movements().getFirst().itemId();
            assertThat(location(adena)).isEqualTo(tuple(owner, "INVENTORY", null));
            assertThat(count(adena)).isEqualTo(250);
        }

        @Test
        void adena_shouldThrow_whenTheOfflineHolderCannotPay() throws ValueRefusal {
            final UUID owner = insertCharacter();
            insertItem(owner, ADENA, 100);

            assertThatThrownBy(() -> repository.adena(Holder.inventory(owner), -101, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class LiveItems {
        @Test
        void liveItemsInMail_shouldCountTheRowsOfTheMail() {
            final UUID mail = insertMail();
            insertMailItem(mail, SWORD, 1);
            insertMailItem(mail, ARROW, 10);

            assertThat(repository.liveItemsInMail(mail)).isEqualTo(2);
            assertThat(repository.liveItemsInMail(insertMail())).isZero();
        }

        @Test
        void destroyInMail_shouldDeleteTheRows_withANegativeMovementEach() {
            final UUID mail = insertMail();
            final UUID arrows = insertMailItem(mail, ARROW, 10);

            final List<ItemMovement> actual = repository.destroyInMail(mail);

            assertThat(actual).containsExactly(movement(HolderType.MAIL, mail, ARROW, arrows, -10));
            assertThat(exists(arrows)).isFalse();
        }
    }

    @Nested
    class Rows {
        @Test
        void rows_shouldCarryTheAugmentationAndElementals_andLeaveOutMissingRows() {
            final UUID owner = insertCharacter();
            final UUID sword = insertItem(owner, SWORD, 1);
            JDBC.sql("INSERT INTO item_augmentations VALUES (?, 12345)")
                    .param(sword)
                    .update();
            JDBC.sql("INSERT INTO item_elementals VALUES (?, 0, 150), (?, 2, 30)")
                    .param(sword)
                    .param(sword)
                    .update();

            final List<CommittedItemRow> actual = repository.rows(List.of(EntityIds.next(), sword));

            assertThat(actual)
                    .map(MailItemRow.class::cast)
                    .extracting(
                            MailItemRow::itemId,
                            MailItemRow::ownerId,
                            MailItemRow::augmentationId,
                            MailItemRow::elementals)
                    .containsExactly(tuple(
                            sword,
                            owner,
                            12345,
                            List.of(
                                    ItemElemental.builder()
                                            .element((byte) 0)
                                            .value(150)
                                            .build(),
                                    ItemElemental.builder()
                                            .element((byte) 2)
                                            .value(30)
                                            .build())));
        }
    }

    private <T> void bind(Class<T> type, MockedStatic.Verification getInstance, T instance) {
        final MockedStatic<T> bridge = mockStatic(type);
        bridge.when(getInstance).thenReturn(instance);
        bridges.add(bridge);
    }

    private void template(int id, boolean stackable, int duration, long minutes) {
        final ItemTemplate template = mock(ItemTemplate.class);
        when(template.isStackable()).thenReturn(stackable);
        when(template.getDuration()).thenReturn(duration);
        when(template.getTime()).thenReturn(minutes);
        when(items.getTemplate(id)).thenReturn(template);
    }

    private static ItemRowState unstored(UUID itemId, UUID ownerId, int itemTemplateId, long count) {
        return state(itemId, ownerId, itemTemplateId, count).toBuilder()
                .unstoredRow(MailItemRow.builder()
                        .itemId(itemId)
                        .ownerId(ownerId)
                        .location(ItemLocation.INVENTORY)
                        .locationSlot(0)
                        .itemTemplateId(itemTemplateId)
                        .count(count)
                        .augmentationId(itemTemplateId == SWORD ? 5 : null)
                        .elementals(
                                itemTemplateId == SWORD
                                        ? List.of(ItemElemental.builder()
                                                .element((byte) 2)
                                                .value(40)
                                                .build())
                                        : List.of())
                        .build())
                .build();
    }

    private static Integer augmentation(UUID itemId) {
        return JDBC.sql("SELECT augmentation_id FROM item_augmentations WHERE item_id = ?")
                .param(itemId)
                .query(Integer.class)
                .single();
    }

    private static List<Tuple> elementals(UUID itemId) {
        return JDBC.sql("SELECT element, element_value FROM item_elementals WHERE item_id = ?")
                .param(itemId)
                .query((rs, rowNum) -> tuple(rs.getShort("element"), rs.getInt("element_value")))
                .list();
    }

    private static ItemRowState state(UUID itemId, UUID ownerId, int itemTemplateId, long count) {
        return ItemRowState.builder()
                .itemHandle(1)
                .itemId(itemId)
                .ownerId(ownerId)
                .itemTemplateId(itemTemplateId)
                .count(count)
                .build();
    }

    private static ItemMovement movement(HolderType type, UUID holderId, int itemTemplateId, UUID itemId, long delta) {
        return ItemMovement.builder()
                .holderType(type)
                .holderId(holderId)
                .itemTemplateId(itemTemplateId)
                .itemId(itemId)
                .delta(delta)
                .build();
    }

    private static UUID insertItem(UUID ownerId, int itemTemplateId, ItemLocation location, Integer slot, long count) {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO items (
                id, owner_id, item_template_id, count, enchant_level, location, location_slot,
                custom_type1, custom_type2)
            VALUES (?, ?, ?, ?, 0, ?, ?, 0, 0)\
            """)
                .param(id)
                .param(ownerId)
                .param(itemTemplateId)
                .param(count)
                .param(location.name())
                .param(slot)
                .update();
        return id;
    }

    private static UUID insertMailItem(UUID mailId, int itemTemplateId, long count) {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO items (
                id, item_template_id, count, enchant_level, location, mail_id, custom_type1, custom_type2)
            VALUES (?, ?, ?, 0, 'MAIL', ?, 0, 0)\
            """).param(id).param(itemTemplateId).param(count).param(mailId).update();
        return id;
    }

    private static UUID insertMail() {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO mails (id, type, receiver_name, subject, attachments, sent_at, expires_at)
            VALUES (?, 'SYSTEM', 'receiver', 'subject', 'ATTACHED', ?, ?)\
            """)
                .param(id)
                .param(JdbcTimes.utc(NOW))
                .param(JdbcTimes.utc(NOW.plus(Duration.ofDays(15))))
                .update();
        return id;
    }

    private static String name(UUID characterId) {
        return JDBC.sql("SELECT name FROM characters WHERE id = ?")
                .param(characterId)
                .query(String.class)
                .single();
    }

    private static long count(UUID itemId) {
        return JDBC.sql("SELECT count FROM items WHERE id = ?")
                .param(itemId)
                .query(Long.class)
                .single();
    }

    private static boolean exists(UUID itemId) {
        return JDBC.sql("SELECT EXISTS (SELECT 1 FROM items WHERE id = ?)")
                .param(itemId)
                .query(Boolean.class)
                .single();
    }

    private static Tuple location(UUID itemId) {
        return JDBC.sql("SELECT owner_id, location, mail_id FROM items WHERE id = ?")
                .param(itemId)
                .query((rs, rowNum) ->
                        tuple(rs.getObject("owner_id", UUID.class), rs.getString("location"), rs.getObject("mail_id")))
                .single();
    }

    private static List<Tuple> itemsOfMail(UUID mailId) {
        return JDBC.sql("SELECT id, count FROM items WHERE mail_id = ? AND location = 'MAIL'")
                .param(mailId)
                .query((rs, rowNum) -> tuple(rs.getObject("id", UUID.class), rs.getLong("count")))
                .list();
    }
}
