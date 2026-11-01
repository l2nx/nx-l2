/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.item.ItemRepository;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.repository.item.model.LotteryTicket;
import app.l2nx.highfive.repository.item.model.PaperdollItem;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItemRepositoryTest extends RepositoryTest {
    private static final int SWORD = 1;
    private static final int ADENA = 57;

    private final ItemRepository repository = new ItemRepository(JDBC, registry);

    @Nested
    class FindByOwnerAndLocations {
        @Test
        void findByOwner_shouldReturnBaseAndEquipLocationsInSlotOrder() {
            final UUID owner = insertCharacter();
            final UUID equipped = insertItem(owner, SWORD, ItemLocation.PAPERDOLL, 1, null);
            final UUID carried = insertItem(owner, ADENA, ItemLocation.INVENTORY, 2, null);
            insertItem(owner, ADENA, ItemLocation.WAREHOUSE, null, null);

            final List<ItemRow> actual =
                    repository.findByOwner(handle(owner), ItemLocation.INVENTORY, ItemLocation.PAPERDOLL);

            assertThat(actual).extracting(ItemRow::objectId).containsExactly(handle(equipped), handle(carried));
        }

        @Test
        void findByOwner_shouldListUnslottedPetItemsFirst() {
            final UUID owner = insertCharacter();
            final UUID equipped = insertItem(owner, SWORD, ItemLocation.PET_EQUIP, 0, null);
            final UUID carried = insertItem(owner, ADENA, ItemLocation.PET, null, null);

            final List<ItemRow> actual =
                    repository.findByOwner(handle(owner), ItemLocation.PET, ItemLocation.PET_EQUIP);

            assertThat(actual).extracting(ItemRow::objectId).containsExactly(handle(carried), handle(equipped));
        }

        @Test
        void findByOwner_shouldMapNullsToLegacySentinels() {
            final UUID owner = insertCharacter();
            final UUID item = insertItem(owner, SWORD, ItemLocation.INVENTORY, 4, null);

            final ItemRow actual = repository
                    .findByOwner(handle(owner), ItemLocation.INVENTORY, ItemLocation.PAPERDOLL)
                    .getFirst();

            assertThat(actual)
                    .isEqualTo(ItemRow.builder()
                            .objectId(handle(item))
                            .itemId(SWORD)
                            .count(1)
                            .location(ItemLocation.INVENTORY)
                            .locationSlot(4)
                            .enchantLevel(0)
                            .customType1(0)
                            .customType2(0)
                            .mana(-1)
                            .expiresAt(null)
                            .build());
        }

        @Test
        void findByOwner_shouldReadManaAndExpiry() {
            final UUID owner = insertCharacter();
            final UUID item = insertItem(owner, SWORD, ItemLocation.INVENTORY, 0, null);
            JDBC.sql("UPDATE items SET mana_left = 30, expires_at = ? WHERE id = ?")
                    .param(JdbcTimes.utc(NOW))
                    .param(item)
                    .update();

            final ItemRow actual = repository
                    .findByOwner(handle(owner), ItemLocation.INVENTORY, ItemLocation.PAPERDOLL)
                    .getFirst();

            assertThat(actual).extracting(ItemRow::mana, ItemRow::expiresAt).containsExactly(30, NOW);
        }
    }

    @Nested
    class FindByOwnerAndLocation {
        @Test
        void findByOwner_shouldReturnLocationOnlyWithSlotZero() {
            final UUID clan = insertClan();
            final UUID item = insertItem(clan, ADENA, ItemLocation.CLANWH, null, null);
            insertItem(clan, ADENA, ItemLocation.WAREHOUSE, null, null);

            final List<ItemRow> actual = repository.findByOwner(handle(clan), ItemLocation.CLANWH);

            assertThat(actual)
                    .extracting(ItemRow::objectId, ItemRow::locationSlot)
                    .containsExactly(tuple(handle(item), 0));
        }
    }

    @Nested
    class FindPaperdoll {
        @Test
        void findPaperdoll_shouldReturnEquippedItemsOnly() {
            final UUID owner = insertCharacter();
            final UUID item = insertItem(owner, SWORD, ItemLocation.PAPERDOLL, 7, null);
            insertItem(owner, ADENA, ItemLocation.INVENTORY, 0, null);

            final List<PaperdollItem> actual = repository.findPaperdoll(handle(owner));

            assertThat(actual)
                    .containsExactly(PaperdollItem.builder()
                            .objectId(handle(item))
                            .itemId(SWORD)
                            .slot(7)
                            .enchantLevel(0)
                            .build());
        }
    }

    @Nested
    class HasPetItems {
        @Test
        void hasPetItems_shouldBeTrue_whenPetInventoryHoldsItems() {
            final UUID owner = insertCharacter();
            insertItem(owner, ADENA, ItemLocation.PET, null, null);

            assertThat(repository.hasPetItems(handle(owner))).isTrue();
        }

        @Test
        void hasPetItems_shouldBeFalse_whenOnlyOwnItems() {
            final UUID owner = insertCharacter();
            insertItem(owner, ADENA, ItemLocation.INVENTORY, 0, null);

            assertThat(repository.hasPetItems(handle(owner))).isFalse();
        }
    }

    @Nested
    class FindAnyOwner {
        @Test
        void findAnyOwner_shouldReturnOwnerHandle() {
            final UUID owner = insertCharacter();
            final int template = uniqueTemplate();
            insertItem(owner, template, ItemLocation.INVENTORY, 0, null);

            assertThat(repository.findAnyOwner(template)).contains(handle(owner));
        }

        @Test
        void findAnyOwner_shouldBeEmpty_whenOnlyAttachedToMail() {
            final int template = uniqueTemplate();
            insertItem(null, template, ItemLocation.MAIL, null, insertMail(insertCharacter()));

            assertThat(repository.findAnyOwner(template)).isEmpty();
        }
    }

    @Nested
    class FindLotteryTickets {
        @Test
        void findLotteryTickets_shouldReturnNumbersOfTheLottery() {
            final int ticket = uniqueTemplate();
            final UUID owner = insertCharacter();
            final UUID item = insertItem(owner, ticket, ItemLocation.INVENTORY, 0, null);
            insertItem(owner, ticket, ItemLocation.INVENTORY, 1, null);
            JDBC.sql("UPDATE items SET custom_type1 = 12, enchant_level = 5, custom_type2 = 9 WHERE id = ?")
                    .param(item)
                    .update();

            final List<LotteryTicket> actual = repository.findLotteryTickets(ticket, 12);

            assertThat(actual)
                    .containsExactly(LotteryTicket.builder()
                            .enchantLevel(5)
                            .customType2(9)
                            .build());
        }
    }

    @Nested
    class Insert {
        @Test
        void insert_shouldStoreSentinelsAsNull_whenWarehouseItem() {
            final UUID owner = insertCharacter();
            final ItemRow item = item(ItemLocation.WAREHOUSE, 0).toBuilder()
                    .mana(-1)
                    .expiresAt(null)
                    .build();

            repository.insert(handle(owner), item);

            assertThat(storedItem(item.objectId()))
                    .containsEntry("owner_id", owner)
                    .containsEntry("location", "WAREHOUSE")
                    .containsEntry("location_slot", null)
                    .containsEntry("mail_id", null)
                    .containsEntry("mana_left", null)
                    .containsEntry("expires_at", null);
        }

        @Test
        void insert_shouldStoreManaSlotAndExpiry() {
            final UUID owner = insertCharacter();
            final ItemRow item = item(ItemLocation.PAPERDOLL, 5).toBuilder()
                    .mana(20)
                    .expiresAt(NOW)
                    .build();

            repository.insert(handle(owner), item);

            final Map<String, Object> actual = storedItem(item.objectId());
            assertThat(actual)
                    .containsEntry("location_slot", 5)
                    .containsEntry("mana_left", 20)
                    .containsEntry("item_template_id", SWORD)
                    .containsEntry("count", 3L);
            assertThat(expiresAt(item.objectId())).isEqualTo(NOW);
        }

        @Test
        void insert_shouldStoreClanAsOwner_whenClanWarehouse() {
            final UUID clan = insertClan();
            final ItemRow item = item(ItemLocation.CLANWH, 0);

            repository.insert(handle(clan), item);

            assertThat(storedItem(item.objectId())).containsEntry("owner_id", clan);
        }
    }

    @Nested
    class Update {
        @Test
        void update_shouldLeaveTheRow_whenAMailHoldsIt() {
            final UUID sender = insertCharacter();
            final UUID mail = insertMail(insertCharacter());
            final UUID attached = insertItem(null, ADENA, ItemLocation.MAIL, null, mail);

            repository.update(
                    handle(sender),
                    item(ItemLocation.INVENTORY, 0).toBuilder()
                            .objectId(handle(attached))
                            .build());

            assertThat(storedItem(handle(attached)))
                    .containsEntry("owner_id", null)
                    .containsEntry("mail_id", mail)
                    .containsEntry("location", "MAIL");
        }

        @Test
        void update_shouldMoveItemToNewOwner() {
            final UUID stored = insertItem(insertCharacter(), ADENA, ItemLocation.INVENTORY, 0, null);
            final UUID receiver = insertCharacter();

            repository.update(
                    handle(receiver),
                    item(ItemLocation.INVENTORY, 9).toBuilder()
                            .objectId(handle(stored))
                            .mana(4)
                            .build());

            assertThat(storedItem(handle(stored)))
                    .containsEntry("owner_id", receiver)
                    .containsEntry("location_slot", 9)
                    .containsEntry("mana_left", 4);
        }
    }

    @Nested
    class UnequipAll {
        @Test
        void unequipAll_shouldMoveOnlyEquippedItemsToInventory() {
            final UUID owner = insertCharacter();
            final UUID equipped = insertItem(owner, SWORD, ItemLocation.PAPERDOLL, 6, null);
            final UUID stored = insertItem(owner, ADENA, ItemLocation.WAREHOUSE, null, null);

            repository.unequipAll(handle(owner));

            assertThat(storedItem(handle(equipped)))
                    .containsEntry("location", "INVENTORY")
                    .containsEntry("location_slot", 6);
            assertThat(storedItem(handle(stored))).containsEntry("location", "WAREHOUSE");
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveItemWithItsAugmentation() {
            final UUID item = insertItem(insertCharacter(), SWORD, 1);
            JDBC.sql("INSERT INTO item_augmentations (item_id, augmentation_id) VALUES (?, 5)")
                    .param(item)
                    .update();

            repository.delete(handle(item));

            assertThat(count("SELECT count(*) FROM items WHERE id = ?", item)).isZero();
            assertThat(count("SELECT count(*) FROM item_augmentations WHERE item_id = ?", item))
                    .isZero();
        }

        @Test
        void delete_shouldLeaveTheRow_whenAMailHoldsIt() {
            final UUID attached = insertItem(null, ADENA, ItemLocation.MAIL, null, insertMail(insertCharacter()));

            repository.delete(handle(attached));

            assertThat(count("SELECT count(*) FROM items WHERE id = ?", attached))
                    .isOne();
        }
    }

    @Nested
    class DeleteByOwner {
        @Test
        void deleteByOwner_shouldRemoveEveryItemOfTheOwner() {
            final UUID owner = insertCharacter();
            final UUID other = insertItem(insertCharacter(), ADENA, 1);
            insertItem(owner, ADENA, 1);
            insertItem(owner, ADENA, ItemLocation.WAREHOUSE, null, null);

            repository.deleteByOwner(handle(owner));

            assertThat(count("SELECT count(*) FROM items WHERE owner_id = ?", owner))
                    .isZero();
            assertThat(count("SELECT count(*) FROM items WHERE id = ?", other)).isOne();
        }

        @Test
        void deleteByOwner_shouldReturnHandlesOfDeletedItemsInUse() {
            final UUID owner = insertCharacter();
            final int loaded = handle(insertItem(owner, ADENA, 1));
            final UUID neverLoaded = insertItem(owner, ADENA, 1);

            final List<Integer> actual = repository.deleteByOwner(handle(owner));

            assertThat(actual).containsExactly(loaded);
            assertThat(registry.findHandle(neverLoaded)).isEmpty();
        }

        @Test
        void deleteByOwner_shouldRemoveOnlyTheTemplateAndCount() {
            final UUID owner = insertCharacter();
            final int template = uniqueTemplate();
            insertItem(owner, template, 1);
            final UUID kept = insertItem(owner, ADENA, 1);

            final int actual = repository.deleteByOwner(handle(owner), template);

            assertThat(actual).isOne();
            assertThat(count("SELECT count(*) FROM items WHERE owner_id = ?", owner))
                    .isOne();
            assertThat(count("SELECT count(*) FROM items WHERE id = ?", kept)).isOne();
        }
    }

    @Nested
    class DeleteByItemId {
        @Test
        void deleteByItemId_shouldRemoveTheTemplateForEveryOwner() {
            final int template = uniqueTemplate();
            insertItem(insertCharacter(), template, 1);
            insertItem(insertClan(), template, 1);

            repository.deleteByItemId(template);

            assertThat(count("SELECT count(*) FROM items WHERE item_template_id = ?", template))
                    .isZero();
        }

        @Test
        void deleteByItemIdExcept_shouldKeepOnlyTheGivenItems() {
            final int template = uniqueTemplate();
            final UUID owner = insertCharacter();
            final UUID kept = insertItem(owner, template, 1);
            insertItem(owner, template, 1);
            insertItem(insertClan(), template, 1);

            repository.deleteByItemIdExcept(template, List.of(handle(kept)));

            assertThat(count("SELECT count(*) FROM items WHERE item_template_id = ?", template))
                    .isOne();
            assertThat(count("SELECT count(*) FROM items WHERE id = ?", kept)).isOne();
        }

        @Test
        void deleteByItemIdExcept_shouldRemoveTheTemplate_whenNothingIsKept() {
            final int template = uniqueTemplate();
            insertItem(insertCharacter(), template, 1);

            repository.deleteByItemIdExcept(template, List.of());

            assertThat(count("SELECT count(*) FROM items WHERE item_template_id = ?", template))
                    .isZero();
        }
    }

    @Nested
    class DeleteExceptStaff {
        @Test
        void deleteExceptStaff_shouldKeepItemsOfStaffOnly() {
            final int template = uniqueTemplate();
            final UUID staff = insertCharacter();
            JDBC.sql("UPDATE characters SET access_level = 100 WHERE id = ?")
                    .param(staff)
                    .update();
            final UUID kept = insertItem(staff, template, 1);
            insertItem(insertCharacter(), template, 1);
            insertItem(insertClan(), template, 1);
            insertItem(null, template, ItemLocation.MAIL, null, insertMail(insertCharacter()));
            final UUID otherTemplate = insertItem(insertCharacter(), ADENA, 1);

            repository.deleteExceptStaff(List.of(template, uniqueTemplate()));

            assertThat(JDBC.sql("SELECT id FROM items WHERE item_template_id = ?")
                            .param(template)
                            .query(UUID.class)
                            .list())
                    .containsExactly(kept);
            assertThat(count("SELECT count(*) FROM items WHERE id = ?", otherTemplate))
                    .isOne();
        }

        @Test
        void deleteExceptStaff_shouldDoNothing_whenNoTemplates() {
            final UUID item = insertItem(insertCharacter(), ADENA, 1);

            repository.deleteExceptStaff(List.of());

            assertThat(count("SELECT count(*) FROM items WHERE id = ?", item)).isOne();
        }
    }

    private int handle(UUID id) {
        return registry.handleOf(id);
    }

    private ItemRow item(ItemLocation location, int slot) {
        return ItemRow.builder()
                .objectId(registry.allocateTransient())
                .itemId(location == ItemLocation.PAPERDOLL ? SWORD : ADENA)
                .count(3)
                .location(location)
                .locationSlot(slot)
                .enchantLevel(0)
                .customType1(0)
                .customType2(0)
                .mana(-1)
                .expiresAt(null)
                .build();
    }

    private Map<String, Object> storedItem(int objectId) {
        return JDBC.sql("""
            SELECT owner_id, item_template_id, count, location, location_slot, mail_id, mana_left, expires_at
            FROM items
            WHERE id = ?\
            """).param(registry.idOf(objectId)).query().singleRow();
    }

    private Instant expiresAt(int objectId) {
        return JDBC.sql("SELECT expires_at FROM items WHERE id = ?")
                .param(registry.idOf(objectId))
                .query((rs, rowNum) -> JdbcTimes.instant(rs, "expires_at"))
                .single();
    }

    private static int count(String sql, Object param) {
        return JDBC.sql(sql).param(param).query(Integer.class).single();
    }

    private static int uniqueTemplate() {
        return 100_000 + (int) (EntityIds.next().getLeastSignificantBits() & 0xFFFFF);
    }

    private static UUID insertItem(UUID owner, int template, ItemLocation location, Integer slot, UUID mail) {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO items (
                id, owner_id, item_template_id, count, enchant_level, location, location_slot, mail_id,
                custom_type1, custom_type2)
            VALUES (?, ?, ?, 1, 0, ?, ?, ?, 0, 0)\
            """)
                .param(id)
                .param(owner)
                .param(template)
                .param(location.name())
                .param(slot)
                .param(mail)
                .update();
        return id;
    }

    static UUID insertMail(UUID receiver) {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO mails (id, type, receiver_id, receiver_name, subject, attachments, sent_at, expires_at)
            VALUES (?, 'SYSTEM', ?, 'receiver', 'subject', 'ATTACHED', ?, ?)\
            """)
                .param(id)
                .param(receiver)
                .param(JdbcTimes.utc(NOW))
                .param(JdbcTimes.utc(NOW))
                .update();
        return id;
    }
}
