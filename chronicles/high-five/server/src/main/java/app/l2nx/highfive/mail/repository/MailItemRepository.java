/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail.repository;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.ItemMovement;
import app.l2nx.engine.core.persistence.value.ValueRefusal;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.CreatedItems;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.ItemTransfer;
import app.l2nx.engine.mail.MailItemStore;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.NewItem;
import app.l2nx.engine.mail.Recipient;
import app.l2nx.engine.mail.ReservedItem;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mail.ItemSnapshots;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.JdbcUpdateAffectedIncorrectNumberOfRowsException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Runs on the shared {@link JdbcClient}, so inside a value transaction every statement joins its transaction. */
@Repository
@RequiredArgsConstructor
public class MailItemRepository implements MailItemStore {
    private static final String SELECT_RECIPIENT_BY_NAME =
            "SELECT id, name, access_level FROM characters WHERE name = ?";
    private static final String SELECT_RECIPIENT_BY_ID = "SELECT id, name, access_level FROM characters WHERE id = ?";
    private static final String EXISTS_BLOCK = """
        SELECT EXISTS (
            SELECT 1 FROM character_relations
            WHERE character_id = ? AND target_character_id = ? AND relation = 'BLOCK')\
        """;
    private static final String MOVE_TO_MAIL = """
        UPDATE items
        SET location = 'MAIL', location_slot = NULL, owner_id = NULL, mail_id = :mailId, count = :count
        WHERE id = :id AND owner_id = :ownerId AND location = 'INVENTORY'\
        """;
    private static final String SET_COUNT = "UPDATE items SET count = :count WHERE id = :id AND owner_id = :ownerId";
    // No adena stack grows past the most a character may hold; the row is left as it is when it would.
    private static final String ADD_COUNT = """
        UPDATE items SET count = count + :delta
        WHERE id = :id AND (item_template_id <> :adena OR count + :delta <= :maxAdena)
        RETURNING count\
        """;
    private static final String SPLIT_INTO_MAIL = """
        INSERT INTO items (
            id, owner_id, item_template_id, count, enchant_level, location, location_slot, mail_id,
            custom_type1, custom_type2, mana_left, expires_at)
        SELECT :newId, NULL, item_template_id, :count, enchant_level, 'MAIL', NULL, :mailId,
               custom_type1, custom_type2, mana_left, expires_at
        FROM items
        WHERE id = :id\
        """;
    private static final String INSERT_ITEM = """
        INSERT INTO items (
            id, owner_id, item_template_id, count, enchant_level, location, location_slot, mail_id,
            custom_type1, custom_type2, mana_left, expires_at)
        VALUES (:id, :ownerId, :itemTemplateId, :count, :enchantLevel, :location, :locationSlot, :mailId,
                0, 0, :manaLeft, :expiresAt)\
        """;
    // A row stored meanwhile is left as it is: the move that follows writes its count.
    private static final String INSERT_UNSTORED = """
        INSERT INTO items (
            id, owner_id, item_template_id, count, enchant_level, location, location_slot, mail_id,
            custom_type1, custom_type2, mana_left, expires_at)
        VALUES (:id, :ownerId, :itemTemplateId, :count, :enchantLevel, :location, :locationSlot, NULL,
                :customType1, :customType2, :manaLeft, :expiresAt)
        ON CONFLICT (id) DO NOTHING\
        """;
    private static final String INSERT_AUGMENTATION =
            "INSERT INTO item_augmentations (item_id, augmentation_id) VALUES (?, ?)";
    private static final String INSERT_ELEMENTAL =
            "INSERT INTO item_elementals (item_id, element, element_value) VALUES (?, ?, ?)";
    private static final String SELECT_MAIL_ITEMS_FOR_UPDATE =
            "SELECT id, item_template_id, count FROM items WHERE mail_id = ? ORDER BY id FOR UPDATE";
    private static final String MOVE_FROM_MAIL = """
        UPDATE items
        SET location = :location, location_slot = :locationSlot, owner_id = :ownerId, mail_id = NULL
        WHERE id = :id AND mail_id = :mailId\
        """;
    private static final String DELETE_ITEM = "DELETE FROM items WHERE id = ?";
    private static final String DELETE_OWNED_ITEM = "DELETE FROM items WHERE id = ? AND owner_id = ?";
    // Serializes the offline writes to one character, so two of them cannot both find no stack and both insert one.
    private static final String LOCK_CHARACTER = "SELECT id FROM characters WHERE id = ? FOR NO KEY UPDATE";
    private static final String SELECT_STACK = """
        SELECT id, count FROM items
        WHERE owner_id = :ownerId AND location IN (:locations) AND item_template_id = :itemTemplateId
        ORDER BY id
        LIMIT 1
        FOR UPDATE\
        """;
    private static final String MOVE_TO_OTHER_MAIL =
            "UPDATE items SET mail_id = ? WHERE mail_id = ? RETURNING id, item_template_id, count";
    private static final String COUNT_MAIL_ITEMS = "SELECT count(*) FROM items WHERE mail_id = ?";
    private static final String DELETE_MAIL_ITEMS =
            "DELETE FROM items WHERE mail_id = ? RETURNING id, item_template_id, count";
    private static final String SELECT_ROWS = """
        SELECT i.id, i.owner_id, i.location, i.location_slot, i.item_template_id, i.count, i.enchant_level,
               i.custom_type1, i.custom_type2, i.mana_left, i.expires_at, a.augmentation_id
        FROM items i
        LEFT JOIN item_augmentations a ON a.item_id = i.id
        WHERE i.id IN (:ids)\
        """;
    private static final String SELECT_ELEMENTALS = """
        SELECT item_id, element, element_value FROM item_elementals
        WHERE item_id IN (:ids)
        ORDER BY item_id, element\
        """;

    private final JdbcClient jdbcClient;
    private final GameClock clock;

    @Override
    public Optional<Recipient> findRecipient(String name) {
        return jdbcClient
                .sql(SELECT_RECIPIENT_BY_NAME)
                .param(name)
                .query((rs, rowNum) -> toRecipient(rs))
                .optional();
    }

    @Override
    public Optional<Recipient> findRecipient(UUID id) {
        return jdbcClient
                .sql(SELECT_RECIPIENT_BY_ID)
                .param(id)
                .query((rs, rowNum) -> toRecipient(rs))
                .optional();
    }

    @Override
    public boolean isBlocked(UUID receiverId, UUID senderId) {
        return jdbcClient
                .sql(EXISTS_BLOCK)
                .param(receiverId)
                .param(senderId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public List<ItemMovement> toMail(UUID mailId, List<ReservedItem> items) {
        final List<ItemMovement> movements = new ArrayList<>();
        for (ReservedItem item : items) {
            final ItemRowState row = item.row();
            insertUnstored(row);
            UUID mailItemId = row.itemId();
            if (item.wholeRow()) {
                require(
                        MOVE_TO_MAIL,
                        jdbcClient
                                .sql(MOVE_TO_MAIL)
                                .param("mailId", mailId)
                                .param("count", item.count())
                                .param("id", row.itemId())
                                .param("ownerId", row.ownerId())
                                .update());
            } else {
                setCount(row.itemId(), row.ownerId(), row.count() - item.count());
                mailItemId = EntityIds.next();
                require(
                        SPLIT_INTO_MAIL,
                        jdbcClient
                                .sql(SPLIT_INTO_MAIL)
                                .param("newId", mailItemId)
                                .param("count", item.count())
                                .param("mailId", mailId)
                                .param("id", row.itemId())
                                .update());
            }
            movements.add(
                    movement(HolderType.INVENTORY, row.ownerId(), row.itemTemplateId(), row.itemId(), -item.count()));
            movements.add(movement(HolderType.MAIL, mailId, row.itemTemplateId(), mailItemId, item.count()));
        }
        return movements;
    }

    @Override
    public void checkCreatable(List<NewItem> items) {
        for (NewItem item : items) {
            if (!template(item.itemTemplateId()).isStackable() && (item.count() > 1)) {
                throw new IllegalArgumentException("A mail item of template " + item.itemTemplateId()
                        + " is not stackable and cannot hold " + item.count());
            }
        }
    }

    @Override
    public CreatedItems createInMail(UUID mailId, List<NewItem> items) {
        final Instant now = clock.wallClock().instant().truncatedTo(ChronoUnit.MILLIS);
        final List<ItemMovement> movements = new ArrayList<>();
        final List<AttachmentSnapshot> snapshots = new ArrayList<>();
        checkCreatable(items);
        for (NewItem item : items) {
            final ItemTemplate template = template(item.itemTemplateId());
            final Integer manaLeft = template.getDuration() < 0 ? null : template.getDuration();
            final Instant expiresAt = template.getTime() < 0 ? null : now.plus(Duration.ofMinutes(template.getTime()));
            final UUID id = EntityIds.next();
            insert(NewRow.builder()
                    .id(id)
                    .itemTemplateId(item.itemTemplateId())
                    .count(item.count())
                    .enchantLevel(item.enchantLevel())
                    .location(ItemLocation.MAIL)
                    .mailId(mailId)
                    .manaLeft(manaLeft)
                    .expiresAt(expiresAt)
                    .build());
            movements.add(movement(HolderType.MAIL, mailId, item.itemTemplateId(), id, item.count()));
            snapshots.add(AttachmentSnapshot.builder()
                    .slot(snapshots.size())
                    .itemTemplateId(item.itemTemplateId())
                    .count(item.count())
                    .enchantLevel(item.enchantLevel())
                    .manaLeft(manaLeft)
                    .itemExpiresAt(expiresAt)
                    .enchantOptions(ItemSnapshots.enchantOptions(item.itemTemplateId(), item.enchantLevel()))
                    .build());
        }
        return new CreatedItems(movements, snapshots);
    }

    @Override
    public ItemTransfer toCharacter(UUID mailId, Holder target, List<ItemRowState> online) throws ValueRefusal {
        final ItemLocation location = location(target);
        final Map<Integer, Stack> stacks = new HashMap<>();
        if (online != null) {
            // A stack the same transaction paid away to nothing lost its row; the item moves in as a row of its own.
            online.stream().filter(stack -> stack.count() > 0).forEach(stack -> {
                insertUnstored(stack);
                stacks.put(stack.itemTemplateId(), new Stack(stack.itemId(), stack.count()));
            });
        } else {
            lockCharacter(target.id());
        }
        final List<ItemMovement> movements = new ArrayList<>();
        final Map<UUID, Long> arrived = new LinkedHashMap<>();
        for (MailItem item : mailItems(mailId)) {
            movements.add(movement(HolderType.MAIL, mailId, item.itemTemplateId(), item.id(), -item.count()));
            final boolean stackable = stackable(item.itemTemplateId());
            Stack stack = stackable ? stacks.get(item.itemTemplateId()) : null;
            if (stackable && (stack == null) && (online == null)) {
                stack = offlineStack(target, item.itemTemplateId());
            }
            if (stack != null) {
                final long count;
                if (online != null) {
                    count = checkAdenaLimit(item.itemTemplateId(), stack.count() + item.count());
                    setCount(stack.itemId(), target.id(), count);
                } else {
                    count = addCount(stack.itemId(), item.count());
                }
                jdbcClient.sql(DELETE_ITEM).param(item.id()).update();
                stacks.put(item.itemTemplateId(), new Stack(stack.itemId(), count));
                movements.add(
                        movement(target.type(), target.id(), item.itemTemplateId(), stack.itemId(), item.count()));
                arrived.merge(stack.itemId(), item.count(), Long::sum);
                continue;
            }
            require(
                    MOVE_FROM_MAIL,
                    jdbcClient
                            .sql(MOVE_FROM_MAIL)
                            .param("location", location.name())
                            .param("locationSlot", slot(location))
                            .param("ownerId", target.id())
                            .param("id", item.id())
                            .param("mailId", mailId)
                            .update());
            if (stackable) {
                stacks.put(item.itemTemplateId(), new Stack(item.id(), item.count()));
            }
            movements.add(movement(target.type(), target.id(), item.itemTemplateId(), item.id(), item.count()));
            arrived.merge(item.id(), item.count(), Long::sum);
        }
        return new ItemTransfer(movements, rows(arrived));
    }

    @Override
    public List<ItemMovement> toMailOf(UUID fromMailId, UUID toMailId) {
        final List<ItemMovement> movements = new ArrayList<>();
        for (MailItem item : jdbcClient
                .sql(MOVE_TO_OTHER_MAIL)
                .param(toMailId)
                .param(fromMailId)
                .query((rs, rowNum) -> toMailItem(rs))
                .list()) {
            movements.add(movement(HolderType.MAIL, fromMailId, item.itemTemplateId(), item.id(), -item.count()));
            movements.add(movement(HolderType.MAIL, toMailId, item.itemTemplateId(), item.id(), item.count()));
        }
        return movements;
    }

    @Override
    public ItemTransfer adena(Holder holder, long delta, ItemRowState online) throws ValueRefusal {
        final ItemLocation location = location(holder);
        final UUID itemId;
        final long count;
        if (online != null) {
            insertUnstored(online);
            itemId = online.itemId();
            count = checkAdenaLimit(Inventory.ADENA_ID, online.count() + delta);
            checkNotNegative(holder, count);
            if (count == 0) {
                require(
                        DELETE_OWNED_ITEM,
                        jdbcClient
                                .sql(DELETE_OWNED_ITEM)
                                .param(itemId)
                                .param(holder.id())
                                .update());
            } else {
                setCount(itemId, holder.id(), count);
            }
        } else {
            lockCharacter(holder.id());
            final Stack stack = offlineStack(holder, Inventory.ADENA_ID);
            if (stack != null) {
                itemId = stack.itemId();
                count = stack.count() + delta;
                checkNotNegative(holder, count);
                if (count == 0) {
                    jdbcClient.sql(DELETE_ITEM).param(itemId).update();
                } else {
                    addCount(itemId, delta);
                }
            } else if (delta > 0) {
                itemId = EntityIds.next();
                count = checkAdenaLimit(Inventory.ADENA_ID, delta);
                insert(NewRow.builder()
                        .id(itemId)
                        .ownerId(holder.id())
                        .itemTemplateId(Inventory.ADENA_ID)
                        .count(delta)
                        .location(location)
                        .build());
            } else {
                throw new IllegalStateException(holder + " has no adena to pay " + -delta);
            }
        }
        final List<CommittedItemRow> rows = count == 0
                ? List.of(MailItemRow.builder()
                        .itemId(itemId)
                        .ownerId(holder.id())
                        .location(location)
                        .itemTemplateId(Inventory.ADENA_ID)
                        .elementals(List.of())
                        .arrived(delta)
                        .build())
                : rows(Map.of(itemId, delta));
        return new ItemTransfer(List.of(movement(holder.type(), holder.id(), Inventory.ADENA_ID, itemId, delta)), rows);
    }

    @Override
    public int liveItemsInMail(UUID mailId) {
        return jdbcClient
                .sql(COUNT_MAIL_ITEMS)
                .param(mailId)
                .query(Integer.class)
                .single();
    }

    @Override
    public List<ItemMovement> destroyInMail(UUID mailId) {
        return jdbcClient.sql(DELETE_MAIL_ITEMS).param(mailId).query((rs, rowNum) -> toMailItem(rs)).list().stream()
                .map(item -> movement(HolderType.MAIL, mailId, item.itemTemplateId(), item.id(), -item.count()))
                .toList();
    }

    @Override
    public List<CommittedItemRow> rows(List<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        final Map<UUID, List<ItemElemental>> elementals = new HashMap<>();
        jdbcClient.sql(SELECT_ELEMENTALS).param("ids", itemIds).query(rs -> {
            elementals
                    .computeIfAbsent(rs.getObject("item_id", UUID.class), id -> new ArrayList<>())
                    .add(ItemElemental.builder()
                            .element(rs.getByte("element"))
                            .value(rs.getInt("element_value"))
                            .build());
        });
        final Map<UUID, MailItemRow> rows = new HashMap<>();
        jdbcClient.sql(SELECT_ROWS).param("ids", itemIds).query(rs -> {
            final UUID id = rs.getObject("id", UUID.class);
            rows.put(id, toRow(rs, elementals.getOrDefault(id, List.of())));
        });
        return itemIds.stream()
                .distinct()
                .map(rows::get)
                .filter(Objects::nonNull)
                .map(CommittedItemRow.class::cast)
                .toList();
    }

    private List<CommittedItemRow> rows(Map<UUID, Long> arrived) {
        return rows(List.copyOf(arrived.keySet())).stream()
                .map(MailItemRow.class::cast)
                .map(row -> row.toBuilder().arrived(arrived.get(row.itemId())).build())
                .map(CommittedItemRow.class::cast)
                .toList();
    }

    private void insertUnstored(ItemRowState state) {
        if (state.unstoredRow() == null) {
            return;
        }
        final MailItemRow row = (MailItemRow) state.unstoredRow();
        final int inserted = jdbcClient
                .sql(INSERT_UNSTORED)
                .param("id", row.itemId())
                .param("ownerId", row.ownerId())
                .param("itemTemplateId", row.itemTemplateId())
                .param("count", row.count())
                .param("enchantLevel", row.enchantLevel())
                .param("location", row.location().name())
                .param("locationSlot", row.locationSlot())
                .param("customType1", row.customType1())
                .param("customType2", row.customType2())
                .param("manaLeft", row.manaLeft())
                .param("expiresAt", JdbcTimes.utc(row.expiresAt()))
                .update();
        if (inserted == 0) {
            return;
        }
        if (row.augmentationId() != null) {
            jdbcClient
                    .sql(INSERT_AUGMENTATION)
                    .param(row.itemId())
                    .param(row.augmentationId())
                    .update();
        }
        for (ItemElemental elemental : row.elementals()) {
            jdbcClient
                    .sql(INSERT_ELEMENTAL)
                    .param(row.itemId())
                    .param(elemental.element())
                    .param(elemental.value())
                    .update();
        }
    }

    private Stack offlineStack(Holder holder, int itemTemplateId) {
        final List<String> locations = location(holder) == ItemLocation.INVENTORY
                // An equipped stack (arrows, bolts) is the one the inventory merges into.
                ? List.of(ItemLocation.INVENTORY.name(), ItemLocation.PAPERDOLL.name())
                : List.of(location(holder).name());
        return jdbcClient
                .sql(SELECT_STACK)
                .param("ownerId", holder.id())
                .param("locations", locations)
                .param("itemTemplateId", itemTemplateId)
                .query((rs, rowNum) -> new Stack(rs.getObject("id", UUID.class), rs.getLong("count")))
                .optional()
                .orElse(null);
    }

    private List<MailItem> mailItems(UUID mailId) {
        return jdbcClient
                .sql(SELECT_MAIL_ITEMS_FOR_UPDATE)
                .param(mailId)
                .query((rs, rowNum) -> toMailItem(rs))
                .list();
    }

    private void lockCharacter(UUID characterId) {
        if (jdbcClient
                .sql(LOCK_CHARACTER)
                .param(characterId)
                .query(UUID.class)
                .optional()
                .isEmpty()) {
            throw new IllegalStateException("No character " + characterId + " to hold the items");
        }
    }

    private void setCount(UUID itemId, UUID ownerId, long count) {
        require(
                SET_COUNT,
                jdbcClient
                        .sql(SET_COUNT)
                        .param("count", count)
                        .param("id", itemId)
                        .param("ownerId", ownerId)
                        .update());
    }

    private long addCount(UUID itemId, long delta) throws ValueRefusal {
        final Optional<Long> count = jdbcClient
                .sql(ADD_COUNT)
                .param("delta", delta)
                .param("id", itemId)
                .param("adena", Inventory.ADENA_ID)
                .param("maxAdena", PlayerConfig.MAX_ADENA)
                .query(Long.class)
                .optional();
        if (count.isEmpty()) {
            throw new ValueRefusal(MailRefusal.ADENA_LIMIT);
        }
        return count.get();
    }

    private static long checkAdenaLimit(int itemTemplateId, long count) throws ValueRefusal {
        if ((itemTemplateId == Inventory.ADENA_ID) && (count > PlayerConfig.MAX_ADENA)) {
            throw new ValueRefusal(MailRefusal.ADENA_LIMIT);
        }
        return count;
    }

    private void insert(NewRow row) {
        jdbcClient
                .sql(INSERT_ITEM)
                .param("id", row.id())
                .param("ownerId", row.ownerId())
                .param("itemTemplateId", row.itemTemplateId())
                .param("count", row.count())
                .param("enchantLevel", row.enchantLevel())
                .param("location", row.location().name())
                .param("locationSlot", slot(row.location()))
                .param("mailId", row.mailId())
                .param("manaLeft", row.manaLeft())
                .param("expiresAt", JdbcTimes.utc(row.expiresAt()))
                .update();
    }

    private static Recipient toRecipient(ResultSet rs) throws SQLException {
        return Recipient.builder()
                .id(rs.getObject("id", UUID.class))
                .name(rs.getString("name"))
                .gm(gm(rs.getInt("access_level")))
                .build();
    }

    // Asks for a defined level only: getAccessLevel would add an undefined one to a map the world loop writes.
    private static boolean gm(int accessLevel) {
        final AdminData levels = AdminData.getInstance();
        return levels.hasAccessLevel(accessLevel)
                && levels.getAccessLevel(accessLevel).isGm();
    }

    private static MailItem toMailItem(ResultSet rs) throws SQLException {
        return MailItem.builder()
                .id(rs.getObject("id", UUID.class))
                .itemTemplateId(rs.getInt("item_template_id"))
                .count(rs.getLong("count"))
                .build();
    }

    private static MailItemRow toRow(ResultSet rs, List<ItemElemental> elementals) throws SQLException {
        return MailItemRow.builder()
                .itemId(rs.getObject("id", UUID.class))
                .ownerId(rs.getObject("owner_id", UUID.class))
                .location(ItemLocation.valueOf(rs.getString("location")))
                .locationSlot(rs.getObject("location_slot", Integer.class))
                .itemTemplateId(rs.getInt("item_template_id"))
                .count(rs.getLong("count"))
                .enchantLevel(rs.getInt("enchant_level"))
                .customType1(rs.getInt("custom_type1"))
                .customType2(rs.getInt("custom_type2"))
                .manaLeft(rs.getObject("mana_left", Integer.class))
                .expiresAt(JdbcTimes.instant(rs, "expires_at"))
                .augmentationId(rs.getObject("augmentation_id", Integer.class))
                .elementals(elementals)
                .build();
    }

    private static ItemTemplate template(int itemTemplateId) {
        final ItemTemplate template = ItemData.getInstance().getTemplate(itemTemplateId);
        if (template == null) {
            throw new IllegalArgumentException("No item template " + itemTemplateId);
        }
        return template;
    }

    private static boolean stackable(int itemTemplateId) {
        final ItemTemplate template = ItemData.getInstance().getTemplate(itemTemplateId);
        return (template != null) && template.isStackable();
    }

    private static ItemLocation location(Holder holder) {
        return switch (holder.type()) {
            case INVENTORY -> ItemLocation.INVENTORY;
            case WAREHOUSE -> ItemLocation.WAREHOUSE;
            case MAIL -> throw new IllegalArgumentException("A mail holds no character items: " + holder);
        };
    }

    private static Integer slot(ItemLocation location) {
        return location == ItemLocation.INVENTORY ? 0 : null;
    }

    private static void checkNotNegative(Holder holder, long count) {
        if (count < 0) {
            throw new IllegalStateException(holder + " cannot pay: its adena would be " + count);
        }
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

    // An owner or location condition that fails throws, which rolls the value transaction back.
    private static void require(String sql, int updated) {
        if (updated != 1) {
            throw new JdbcUpdateAffectedIncorrectNumberOfRowsException(sql, 1, updated);
        }
    }

    private record Stack(UUID itemId, long count) {}

    @Builder(toBuilder = true)
    private record MailItem(UUID id, int itemTemplateId, long count) {}

    @Builder(toBuilder = true)
    private record NewRow(
            UUID id,
            UUID ownerId,
            int itemTemplateId,
            long count,
            int enchantLevel,
            ItemLocation location,
            UUID mailId,
            Integer manaLeft,
            Instant expiresAt) {}
}
