/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.repository.item;

import app.l2nx.database.JdbcTimes;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.repository.References;
import app.l2nx.highfive.repository.item.model.ItemRow;
import app.l2nx.highfive.repository.item.model.LotteryTicket;
import app.l2nx.highfive.repository.item.model.PaperdollItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Items owned by a character (inventory, paperdoll, warehouse, freight, pet) or a clan (clan warehouse). Rows attached
 * to a mail belong to the mail module: no write by handle here touches one.
 */
@Repository
@RequiredArgsConstructor
public class ItemRepository {
    // NULLS FIRST: the pet inventory has no slot, and MySQL sorted its 0 before the equipped slots.
    private static final String SELECT_BY_OWNER_AND_LOCATIONS = """
        SELECT id, item_template_id, count, location, location_slot, enchant_level,
               custom_type1, custom_type2, mana_left, expires_at
        FROM items
        WHERE owner_id = ? AND location IN (?, ?)
        ORDER BY location_slot NULLS FIRST\
        """;
    private static final String SELECT_BY_OWNER_AND_LOCATION = """
        SELECT id, item_template_id, count, location, location_slot, enchant_level,
               custom_type1, custom_type2, mana_left, expires_at
        FROM items
        WHERE owner_id = ? AND location = ?\
        """;
    private static final String SELECT_PAPERDOLL = """
        SELECT id, item_template_id, location_slot, enchant_level
        FROM items
        WHERE owner_id = ? AND location = 'PAPERDOLL'\
        """;
    private static final String EXISTS_PET_ITEMS =
            "SELECT EXISTS (SELECT 1 FROM items WHERE owner_id = ? AND location IN ('PET', 'PET_EQUIP'))";
    private static final String SELECT_OWNER_BY_TEMPLATE =
            "SELECT owner_id FROM items WHERE item_template_id = ? AND owner_id IS NOT NULL LIMIT 1";
    private static final String SELECT_LOTTERY_TICKETS = """
        SELECT enchant_level, custom_type2
        FROM items
        WHERE item_template_id = ? AND custom_type1 = ?\
        """;
    private static final String INSERT_ITEM = """
        INSERT INTO items (
            id, owner_id, item_template_id, count, location, location_slot, enchant_level,
            custom_type1, custom_type2, mana_left, expires_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\
        """;
    // A late save cannot pull back a row a value transaction moved into a mail.
    private static final String UPDATE_ITEM = """
        UPDATE items
        SET owner_id = ?, count = ?, location = ?, location_slot = ?, enchant_level = ?,
            custom_type1 = ?, custom_type2 = ?, mana_left = ?, expires_at = ?
        WHERE id = ? AND mail_id IS NULL\
        """;
    // Moves equipped items only: other locations have no slot to keep, and items in transit belong to no one.
    private static final String UNEQUIP_ALL =
            "UPDATE items SET location = 'INVENTORY' WHERE owner_id = ? AND location = 'PAPERDOLL'";
    private static final String DELETE_ITEM = "DELETE FROM items WHERE id = ? AND mail_id IS NULL";
    private static final String DELETE_BY_OWNER = "DELETE FROM items WHERE owner_id = ? RETURNING id";
    private static final String DELETE_BY_OWNER_AND_TEMPLATE =
            "DELETE FROM items WHERE owner_id = ? AND item_template_id = ?";
    private static final String DELETE_BY_TEMPLATE = "DELETE FROM items WHERE item_template_id = ?";
    private static final String DELETE_BY_TEMPLATE_EXCEPT =
            "DELETE FROM items WHERE item_template_id = :templateId AND id NOT IN (:kept)";
    // Items in mail transit have no owner and are removed too.
    private static final String DELETE_BY_TEMPLATES_EXCEPT_STAFF = """
        DELETE FROM items i
        WHERE i.item_template_id IN (:templateIds)
          AND NOT EXISTS (SELECT 1 FROM characters c WHERE c.id = i.owner_id AND c.access_level > 0)\
        """;

    private final JdbcClient jdbcClient;
    private final IdentityRegistry registry;

    public List<ItemRow> findByOwner(int ownerId, ItemLocation baseLocation, ItemLocation equipLocation) {
        return registry.findId(ownerId)
                .map(id -> jdbcClient
                        .sql(SELECT_BY_OWNER_AND_LOCATIONS)
                        .param(id)
                        .param(baseLocation.name())
                        .param(equipLocation.name())
                        .query((rs, rowNum) -> toItem(rs))
                        .list())
                .orElse(List.of());
    }

    public List<ItemRow> findByOwner(int ownerId, ItemLocation location) {
        return registry.findId(ownerId)
                .map(id -> jdbcClient
                        .sql(SELECT_BY_OWNER_AND_LOCATION)
                        .param(id)
                        .param(location.name())
                        .query((rs, rowNum) -> toItem(rs))
                        .list())
                .orElse(List.of());
    }

    public List<PaperdollItem> findPaperdoll(int characterId) {
        return registry.findId(characterId)
                .map(refId -> jdbcClient
                        .sql(SELECT_PAPERDOLL)
                        .param(refId)
                        .query((rs, rowNum) -> PaperdollItem.builder()
                                .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                                .itemId(rs.getInt("item_template_id"))
                                .slot(rs.getInt("location_slot"))
                                .enchantLevel(rs.getInt("enchant_level"))
                                .build())
                        .list())
                .orElse(List.of());
    }

    public boolean hasPetItems(int characterId) {
        return registry.findId(characterId)
                .map(id -> jdbcClient
                        .sql(EXISTS_PET_ITEMS)
                        .param(id)
                        .query(Boolean.class)
                        .single())
                .orElse(false);
    }

    public Optional<Integer> findAnyOwner(int itemId) {
        return jdbcClient
                .sql(SELECT_OWNER_BY_TEMPLATE)
                .param(itemId)
                .query((rs, rowNum) -> registry.handleOf(rs.getObject("owner_id", UUID.class)))
                .optional();
    }

    public List<LotteryTicket> findLotteryTickets(int ticketItemId, int lotteryId) {
        return jdbcClient
                .sql(SELECT_LOTTERY_TICKETS)
                .param(ticketItemId)
                .param(lotteryId)
                .query((rs, rowNum) -> LotteryTicket.builder()
                        .enchantLevel(rs.getInt("enchant_level"))
                        .customType2(rs.getInt("custom_type2"))
                        .build())
                .list();
    }

    /**
     * @param ownerId the character or clan handle
     */
    public void insert(int ownerId, ItemRow item) {
        jdbcClient
                .sql(INSERT_ITEM)
                .param(registry.idOf(item.objectId()))
                .param(References.required(registry, ownerId))
                .param(item.itemId())
                .param(item.count())
                .param(item.location().name())
                .param(slot(item))
                .param(item.enchantLevel())
                .param(item.customType1())
                .param(item.customType2())
                .param(mana(item))
                .param(JdbcTimes.utc(item.expiresAt()))
                .update();
    }

    /**
     * @param ownerId the character or clan handle
     */
    public void update(int ownerId, ItemRow item) {
        registry.findId(item.objectId())
                .ifPresent(id -> jdbcClient
                        .sql(UPDATE_ITEM)
                        .param(References.required(registry, ownerId))
                        .param(item.count())
                        .param(item.location().name())
                        .param(slot(item))
                        .param(item.enchantLevel())
                        .param(item.customType1())
                        .param(item.customType2())
                        .param(mana(item))
                        .param(JdbcTimes.utc(item.expiresAt()))
                        .param(id)
                        .update());
    }

    public void unequipAll(int characterId) {
        registry.findId(characterId)
                .ifPresent(id -> jdbcClient.sql(UNEQUIP_ALL).param(id).update());
    }

    /**
     * Its augmentation, elementals, variables, pet and pet effects go with it; a row attached to a mail stays.
     */
    public void delete(int objectId) {
        registry.findId(objectId)
                .ifPresent(id -> jdbcClient.sql(DELETE_ITEM).param(id).update());
    }

    /**
     * @return the handles of the deleted items that are in use, for the caller to release once the deletion commits
     */
    public List<Integer> deleteByOwner(int ownerId) {
        return deletedHandles(ownerId, DELETE_BY_OWNER);
    }

    public int deleteByOwner(int ownerId, int itemId) {
        return registry.findId(ownerId)
                .map(id -> jdbcClient
                        .sql(DELETE_BY_OWNER_AND_TEMPLATE)
                        .param(id)
                        .param(itemId)
                        .update())
                .orElse(0);
    }

    public void deleteByItemId(int itemId) {
        jdbcClient.sql(DELETE_BY_TEMPLATE).param(itemId).update();
    }

    public void deleteByItemIdExcept(int itemId, Collection<Integer> keptObjectIds) {
        final List<UUID> kept = keptObjectIds.stream()
                .map(registry::findId)
                .flatMap(Optional::stream)
                .toList();
        if (kept.isEmpty()) {
            deleteByItemId(itemId);
            return;
        }

        jdbcClient
                .sql(DELETE_BY_TEMPLATE_EXCEPT)
                .param("templateId", itemId)
                .param("kept", kept)
                .update();
    }

    public void deleteExceptStaff(Collection<Integer> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }

        jdbcClient
                .sql(DELETE_BY_TEMPLATES_EXCEPT_STAFF)
                .param("templateIds", itemIds)
                .update();
    }

    private List<Integer> deletedHandles(int handle, String sql) {
        return registry
                .findId(handle)
                .map(id -> jdbcClient
                        .sql(sql)
                        .param(id)
                        .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                        .list())
                .orElse(List.of())
                .stream()
                .flatMap(itemId -> registry.findHandle(itemId).stream().boxed())
                .toList();
    }

    private ItemRow toItem(ResultSet rs) throws SQLException {
        final Integer manaLeft = rs.getObject("mana_left", Integer.class);
        return ItemRow.builder()
                .objectId(registry.handleOf(rs.getObject("id", UUID.class)))
                .itemId(rs.getInt("item_template_id"))
                .count(rs.getLong("count"))
                .location(ItemLocation.valueOf(rs.getString("location")))
                .locationSlot(rs.getInt("location_slot"))
                .enchantLevel(rs.getInt("enchant_level"))
                .customType1(rs.getInt("custom_type1"))
                .customType2(rs.getInt("custom_type2"))
                .mana(manaLeft == null ? -1 : manaLeft)
                .expiresAt(JdbcTimes.instant(rs, "expires_at"))
                .build();
    }

    private static Integer slot(ItemRow item) {
        return switch (item.location()) {
            case INVENTORY, PAPERDOLL, PET_EQUIP -> item.locationSlot();
            default -> null;
        };
    }

    private static Integer mana(ItemRow item) {
        return item.mana() < 0 ? null : item.mana();
    }
}
