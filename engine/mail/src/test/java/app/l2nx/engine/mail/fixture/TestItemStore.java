/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A chronicle store over the test {@code items} and {@code characters} tables, written as the SPI asks: absolute counts
 * under an owner condition for an online side, deltas merged into the rows of an offline one.
 */
public final class TestItemStore implements MailItemStore {
    private final JdbcClient jdbc;
    private final Set<List<UUID>> blocks = ConcurrentHashMap.newKeySet();

    public record TestRow(UUID itemId, HolderType holder, UUID ownerId, int itemTemplateId, long count)
            implements CommittedItemRow {}

    public TestItemStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void block(UUID receiverId, UUID senderId) {
        blocks.add(List.of(receiverId, senderId));
    }

    @Override
    public Optional<Recipient> findRecipient(String name) {
        return jdbc.sql("SELECT id, name, gm FROM characters WHERE name = ?")
                .param(name)
                .query((rs, rowNum) ->
                        new Recipient(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("gm")))
                .optional();
    }

    @Override
    public Optional<Recipient> findRecipient(UUID id) {
        return jdbc.sql("SELECT id, name, gm FROM characters WHERE id = ?")
                .param(id)
                .query((rs, rowNum) ->
                        new Recipient(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("gm")))
                .optional();
    }

    @Override
    public boolean isBlocked(UUID receiverId, UUID senderId) {
        return blocks.contains(List.of(receiverId, senderId));
    }

    @Override
    public List<ItemMovement> toMail(UUID mailId, List<ReservedItem> items) {
        final List<ItemMovement> movements = new ArrayList<>();
        for (ReservedItem item : items) {
            final ItemRowState row = item.row();
            UUID mailItemId = row.itemId();
            if (item.wholeRow()) {
                require(jdbc.sql("""
                    UPDATE items SET location = 'MAIL', owner_id = NULL, mail_id = ?
                    WHERE id = ? AND owner_id = ? AND location = 'INVENTORY'
                    """)
                        .param(mailId)
                        .param(row.itemId())
                        .param(row.ownerId())
                        .update());
            } else {
                require(jdbc.sql("UPDATE items SET count = ? WHERE id = ? AND owner_id = ?")
                        .param(row.count() - item.count())
                        .param(row.itemId())
                        .param(row.ownerId())
                        .update());
                mailItemId = EntityIds.next();
                insert(mailItemId, null, "MAIL", mailId, row.itemTemplateId(), item.count());
            }
            movements.add(
                    movement(HolderType.INVENTORY, row.ownerId(), row.itemTemplateId(), row.itemId(), -item.count()));
            movements.add(movement(HolderType.MAIL, mailId, row.itemTemplateId(), mailItemId, item.count()));
        }
        return movements;
    }

    /** Template ids above zero exist; {@link FakeItems#stackable} tells the stackable ones. */
    @Override
    public void checkCreatable(List<NewItem> items) {
        for (NewItem item : items) {
            if (item.itemTemplateId() <= 0) {
                throw new IllegalArgumentException("No item template " + item.itemTemplateId());
            }
            if (!FakeItems.stackable(item.itemTemplateId()) && (item.count() > 1)) {
                throw new IllegalArgumentException("Item template " + item.itemTemplateId() + " is not stackable");
            }
        }
    }

    @Override
    public CreatedItems createInMail(UUID mailId, List<NewItem> items) {
        checkCreatable(items);
        final List<ItemMovement> movements = new ArrayList<>();
        final List<AttachmentSnapshot> snapshots = new ArrayList<>();
        for (NewItem item : items) {
            final UUID id = EntityIds.next();
            insert(id, null, "MAIL", mailId, item.itemTemplateId(), item.count());
            movements.add(movement(HolderType.MAIL, mailId, item.itemTemplateId(), id, item.count()));
            snapshots.add(AttachmentSnapshot.builder()
                    .slot(snapshots.size())
                    .itemTemplateId(item.itemTemplateId())
                    .count(item.count())
                    .enchantLevel(item.enchantLevel())
                    .enchantOptions(List.of())
                    .build());
        }
        return new CreatedItems(movements, snapshots);
    }

    @Override
    public ItemTransfer toCharacter(UUID mailId, Holder target, List<ItemRowState> online) throws ValueRefusal {
        final Map<Integer, ItemRowState> stacks = new HashMap<>();
        if (online != null) {
            online.forEach(stack -> stacks.put(stack.itemTemplateId(), stack));
        }
        final List<ItemMovement> movements = new ArrayList<>();
        final List<CommittedItemRow> rows = new ArrayList<>();
        for (TestRow item : itemsInMail(mailId)) {
            movements.add(movement(HolderType.MAIL, mailId, item.itemTemplateId(), item.itemId(), -item.count()));
            final boolean stackable = FakeItems.stackable(item.itemTemplateId());
            final ItemRowState stack = stackable ? stacks.get(item.itemTemplateId()) : null;
            final TestRow offlineStack = stackable && (online == null) ? stackOf(target, item.itemTemplateId()) : null;
            if (stack != null) {
                final long count = checkLimit(item.itemTemplateId(), stack.count() + item.count());
                require(jdbc.sql("UPDATE items SET count = ? WHERE id = ? AND owner_id = ?")
                        .param(count)
                        .param(stack.itemId())
                        .param(target.id())
                        .update());
                deleteItem(item.itemId());
                stacks.put(item.itemTemplateId(), stack.withCount(count));
                movements.add(
                        movement(target.type(), target.id(), item.itemTemplateId(), stack.itemId(), item.count()));
                rows.add(new TestRow(stack.itemId(), target.type(), target.id(), item.itemTemplateId(), count));
            } else if (offlineStack != null) {
                final long count = jdbc.sql("UPDATE items SET count = count + ? WHERE id = ? RETURNING count")
                        .param(item.count())
                        .param(offlineStack.itemId())
                        .query(Long.class)
                        .single();
                checkLimit(item.itemTemplateId(), count);
                deleteItem(item.itemId());
                movements.add(movement(
                        target.type(), target.id(), item.itemTemplateId(), offlineStack.itemId(), item.count()));
                rows.add(new TestRow(offlineStack.itemId(), target.type(), target.id(), item.itemTemplateId(), count));
            } else {
                require(jdbc.sql("UPDATE items SET location = ?, owner_id = ?, mail_id = NULL WHERE id = ?")
                        .param(target.type().name())
                        .param(target.id())
                        .param(item.itemId())
                        .update());
                movements.add(movement(target.type(), target.id(), item.itemTemplateId(), item.itemId(), item.count()));
                rows.add(new TestRow(item.itemId(), target.type(), target.id(), item.itemTemplateId(), item.count()));
            }
        }
        return new ItemTransfer(movements, rows);
    }

    // A refusal rolls back what the transaction wrote so far.
    private static long checkLimit(int itemTemplateId, long count) throws ValueRefusal {
        if ((itemTemplateId == FakeItems.ADENA) && (count > FakeItems.ADENA_LIMIT)) {
            throw new ValueRefusal(MailRefusal.ADENA_LIMIT);
        }
        return count;
    }

    @Override
    public List<ItemMovement> toMailOf(UUID fromMailId, UUID toMailId) {
        final List<ItemMovement> movements = new ArrayList<>();
        for (TestRow item : itemsInMail(fromMailId)) {
            require(jdbc.sql("UPDATE items SET mail_id = ? WHERE id = ?")
                    .param(toMailId)
                    .param(item.itemId())
                    .update());
            movements.add(movement(HolderType.MAIL, fromMailId, item.itemTemplateId(), item.itemId(), -item.count()));
            movements.add(movement(HolderType.MAIL, toMailId, item.itemTemplateId(), item.itemId(), item.count()));
        }
        return movements;
    }

    @Override
    public ItemTransfer adena(Holder holder, long delta, ItemRowState online) throws ValueRefusal {
        final UUID itemId;
        final long count;
        if (online != null) {
            itemId = online.itemId();
            count = online.count() + delta;
            require(jdbc.sql("UPDATE items SET count = ? WHERE id = ? AND owner_id = ?")
                    .param(count)
                    .param(itemId)
                    .param(holder.id())
                    .update());
        } else {
            final TestRow stack = stackOf(holder, FakeItems.ADENA);
            if (stack != null) {
                itemId = stack.itemId();
                count = jdbc.sql("UPDATE items SET count = count + ? WHERE id = ? RETURNING count")
                        .param(delta)
                        .param(itemId)
                        .query(Long.class)
                        .single();
            } else if (delta > 0) {
                itemId = EntityIds.next();
                count = delta;
                insert(itemId, holder.id(), holder.type().name(), null, FakeItems.ADENA, delta);
            } else {
                throw new IllegalStateException(holder + " has no adena to pay " + -delta);
            }
        }
        checkLimit(FakeItems.ADENA, count);
        return new ItemTransfer(
                List.of(movement(holder.type(), holder.id(), FakeItems.ADENA, itemId, delta)),
                List.of(new TestRow(itemId, holder.type(), holder.id(), FakeItems.ADENA, count)));
    }

    @Override
    public int liveItemsInMail(UUID mailId) {
        return itemsInMail(mailId).size();
    }

    @Override
    public List<ItemMovement> destroyInMail(UUID mailId) {
        final List<ItemMovement> movements = new ArrayList<>();
        for (TestRow item : itemsInMail(mailId)) {
            deleteItem(item.itemId());
            movements.add(movement(HolderType.MAIL, mailId, item.itemTemplateId(), item.itemId(), -item.count()));
        }
        return movements;
    }

    @Override
    public List<CommittedItemRow> rows(List<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(
                        "SELECT id, owner_id, location, item_template_id, count FROM items WHERE id IN (:ids) ORDER BY id")
                .param("ids", itemIds)
                .query((rs, rowNum) -> (CommittedItemRow) new TestRow(
                        rs.getObject("id", UUID.class),
                        HolderType.valueOf(rs.getString("location")),
                        rs.getObject("owner_id", UUID.class),
                        rs.getInt("item_template_id"),
                        rs.getLong("count")))
                .list();
    }

    public void insert(UUID id, UUID ownerId, String location, UUID mailId, int itemTemplateId, long count) {
        jdbc.sql("INSERT INTO items (id, owner_id, location, mail_id, item_template_id, count) VALUES (?, ?, ?,"
                        + " ?, ?, ?)")
                .param(id)
                .param(ownerId)
                .param(location)
                .param(mailId)
                .param(itemTemplateId)
                .param(count)
                .update();
    }

    public List<TestRow> itemsInMail(UUID mailId) {
        return jdbc.sql("SELECT id, item_template_id, count FROM items WHERE location = 'MAIL' AND mail_id = ? ORDER"
                        + " BY id")
                .param(mailId)
                .query((rs, rowNum) -> new TestRow(
                        rs.getObject("id", UUID.class),
                        HolderType.MAIL,
                        mailId,
                        rs.getInt("item_template_id"),
                        rs.getLong("count")))
                .list();
    }

    public List<TestRow> itemsOf(Holder holder) {
        return jdbc.sql("""
            SELECT id, item_template_id, count FROM items
            WHERE owner_id = ? AND location = ?
            ORDER BY item_template_id, id
            """)
                .param(holder.id())
                .param(holder.type().name())
                .query((rs, rowNum) -> new TestRow(
                        rs.getObject("id", UUID.class),
                        holder.type(),
                        holder.id(),
                        rs.getInt("item_template_id"),
                        rs.getLong("count")))
                .list();
    }

    public void deleteItem(UUID itemId) {
        jdbc.sql("DELETE FROM items WHERE id = ?").param(itemId).update();
    }

    private TestRow stackOf(Holder holder, int itemTemplateId) {
        return itemsOf(holder).stream()
                .filter(row -> row.itemTemplateId() == itemTemplateId)
                .findFirst()
                .orElse(null);
    }

    private static ItemMovement movement(HolderType type, UUID holderId, int itemTemplateId, UUID itemId, long delta) {
        return new ItemMovement(type, holderId, itemTemplateId, itemId, delta);
    }

    private static void require(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("An owner condition failed: " + updated + " rows updated");
        }
    }
}
