/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.mail;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.mail.CommittedItemRow;
import app.l2nx.engine.mail.IncomingTargets;
import app.l2nx.engine.mail.ItemCount;
import app.l2nx.engine.mail.ItemRowState;
import app.l2nx.engine.mail.MailItems;
import app.l2nx.engine.mail.MailRefusal;
import app.l2nx.engine.mail.Reservation;
import app.l2nx.engine.mail.ReserveResult;
import app.l2nx.engine.mail.ReservedItem;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemLocation;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.Elementals;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.ItemContainer;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.mail.repository.MailItemRow;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.item.model.ItemElemental;
import app.l2nx.highfive.repository.item.model.ItemRow;
import it.unimi.dsi.fastutil.ints.Int2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A reservation marks its items in the partition's pending registry; after the commit memory applies each row's
 * change, so a stack that took in more meanwhile keeps it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class WorldMailItems implements MailItems {
    private final IdentityRegistry registry;

    /** A pending item is listed: it is mailable, only busy now, and reserving it answers so. */
    @Override
    public List<Integer> mailable(int characterHandle) {
        final Player player = character(characterHandle);
        final List<Item> items = new ArrayList<>();
        player.getInventory().collectItems(items, item -> mailable(player, item));
        return items.stream().map(Item::getObjectId).toList();
    }

    @Override
    public ReserveResult<Reservation> reserve(int characterHandle, List<ItemCount> items, long adena) {
        final Player player = character(characterHandle);
        final UUID characterId = registry.idOf(characterHandle);
        final IntSet handles = new IntOpenHashSet();
        final List<ReservedItem> reserved = new ArrayList<>(items.size());
        final List<AttachmentSnapshot> snapshots = new ArrayList<>(items.size());
        long attachedAdena = 0;
        for (ItemCount attached : items) {
            final Item item = (attached.count() < 1)
                    ? null
                    : player.checkItemManipulation(attached.itemHandle(), attached.count(), "attach");
            if ((item == null) || !mailable(player, item) || !handles.add(item.getObjectId())) {
                return new ReserveResult.Refused<>(MailRefusal.ITEM_NOT_MAILABLE);
            }
            if (item.isPending()) {
                return new ReserveResult.Refused<>(MailRefusal.BUSY);
            }
            if (item.getId() == Inventory.ADENA_ID) {
                attachedAdena += attached.count();
            }
            reserved.add(new ReservedItem(row(characterId, item), attached.count()));
            snapshots.add(ItemSnapshots.of(item, snapshots.size(), attached.count()));
        }

        Item fee = null;
        if (adena > 0) {
            fee = player.getInventory().getAdenaInstance();
            if ((fee == null) || ((fee.getCount() - attachedAdena) < adena)) {
                return new ReserveResult.Refused<>(MailRefusal.NOT_ENOUGH_ADENA);
            }
            if (fee.isPending()) {
                return new ReserveResult.Refused<>(MailRefusal.BUSY);
            }
            handles.add(fee.getObjectId());
        }
        if (!WorldPartition.pendingItems().reserve(handles)) {
            return new ReserveResult.Refused<>(MailRefusal.BUSY);
        }
        return new ReserveResult.Reserved<>(Reservation.builder()
                .characterHandle(characterHandle)
                .characterId(characterId)
                .items(reserved)
                .adena(fee == null ? null : row(characterId, fee))
                .adenaAmount(adena)
                .snapshots(snapshots)
                .build());
    }

    @Override
    public void release(Reservation reservation) {
        WorldPartition.pendingItems().release(handles(reservation));
    }

    @Override
    public void applyOutgoing(Reservation reservation) {
        release(reservation);
        final Player player = loaded(reservation.characterHandle(), reservation.characterId());
        if (player == null) {
            log.error("Committed mail items of {} have no character in memory to apply to", reservation.characterId());
            return;
        }
        // An attached part of the adena row and the fee leave that row together.
        final Int2LongMap leaving = new Int2LongLinkedOpenHashMap();
        final Int2ObjectMap<ItemRowState> rows = new Int2ObjectOpenHashMap<>();
        for (ReservedItem item : reservation.items()) {
            leaving.mergeLong(item.row().itemHandle(), item.count(), Long::sum);
            rows.put(item.row().itemHandle(), item.row());
        }
        if (reservation.adenaAmount() > 0) {
            leaving.mergeLong(reservation.adena().itemHandle(), reservation.adenaAmount(), Long::sum);
            rows.put(reservation.adena().itemHandle(), reservation.adena());
        }

        final PlayerInventory inventory = player.getInventory();
        final List<Item> changed = new ArrayList<>();
        for (Int2LongMap.Entry entry : leaving.int2LongEntrySet()) {
            final ItemRowState row = rows.get(entry.getIntKey());
            final Item item = inventory.getItemByObjectId(row.itemHandle());
            if (item == null) {
                log.error("Item {} of a committed mail move is no longer in {}", row, inventory);
                continue;
            }
            if (entry.getLongValue() < row.count()) {
                take(inventory, item, entry.getLongValue(), changed);
                rowStored(inventory, item, row);
            } else {
                // The row left whole; what the stack took in since the reservation has no row and stays as a new one.
                replace(inventory, item, item.getCount() - entry.getLongValue(), changed);
            }
        }
        refresh(player, changed, false);
    }

    @Override
    public ReserveResult<IncomingTargets> incomingTargets(
            int characterHandle, HolderType holder, List<AttachmentSnapshot> items, long adena) {
        final UUID characterId = registry.idOf(characterHandle);
        final Player player = loaded(characterHandle, characterId);
        if (player == null) {
            throw new IllegalStateException("No character " + characterId + " in memory");
        }
        final ItemContainer container = switch (holder) {
            case INVENTORY -> player.getInventory();
            case WAREHOUSE -> player.getWarehouse();
            case MAIL -> throw new IllegalArgumentException("A mail is no holder of a character: " + holder);
        };
        if ((holder == HolderType.WAREHOUSE) && (adena != 0)) {
            throw new IllegalArgumentException("A warehouse takes no payment: " + adena);
        }
        final IntSet handles = new IntOpenHashSet();
        final IntSet newStacks = new IntOpenHashSet();
        final List<ItemRowState> stacks = new ArrayList<>();
        long weight = 0;
        long slots = 0;
        long arrivingAdena = adena;
        for (AttachmentSnapshot snapshot : items) {
            final ItemTemplate template = ItemData.getInstance().getTemplate(snapshot.itemTemplateId());
            if (template == null) {
                slots++;
                continue;
            }
            weight += template.getWeight() * snapshot.count();
            if (template.getId() == Inventory.ADENA_ID) {
                arrivingAdena += snapshot.count();
            }
            if (!template.isStackable()) {
                slots += snapshot.count();
                continue;
            }
            final Item stack = container.getItemByItemId(template.getId());
            if (stack == null) {
                if (newStacks.add(template.getId())) {
                    slots++;
                }
            } else if (handles.add(stack.getObjectId())) {
                stacks.add(row(characterId, stack));
            }
        }
        // Expired attachments go to the warehouse regardless of room; a refusal would strand them in the mail.
        if (holder == HolderType.WAREHOUSE) {
            return reserve(handles, newStacks, characterHandle, characterId, stacks, null);
        }

        final PlayerInventory inventory = player.getInventory();
        if (!inventory.validateCapacity(slots)) {
            return new ReserveResult.Refused<>(MailRefusal.INVENTORY_FULL);
        }
        if (!inventory.validateWeight(weight)) {
            return new ReserveResult.Refused<>(MailRefusal.WEIGHT_LIMIT);
        }
        final Item adenaItem = inventory.getAdenaInstance();
        final long balance = adenaItem == null ? 0 : adenaItem.getCount();
        // The transaction pays before it moves the attachments, so attached adena cannot pay.
        if (balance < -adena) {
            return new ReserveResult.Refused<>(MailRefusal.NOT_ENOUGH_ADENA);
        }
        if ((balance + arrivingAdena) > adenaLimit()) {
            return new ReserveResult.Refused<>(MailRefusal.ADENA_LIMIT);
        }
        ItemRowState adenaRow = null;
        if ((adena != 0) && (adenaItem != null)) {
            adenaRow = row(characterId, adenaItem);
            handles.add(adenaItem.getObjectId());
        } else if (adena > 0) {
            newStacks.add(Inventory.ADENA_ID);
        }
        return reserve(handles, newStacks, characterHandle, characterId, stacks, adenaRow);
    }

    private static ReserveResult<IncomingTargets> reserve(
            IntSet handles,
            IntSet newStacks,
            int characterHandle,
            UUID characterId,
            List<ItemRowState> stacks,
            ItemRowState adena) {
        if (!WorldPartition.pendingItems().reserve(handles)) {
            return new ReserveResult.Refused<>(MailRefusal.BUSY);
        }
        WorldPartition.awaitedStacks().await(characterHandle, newStacks);
        return new ReserveResult.Reserved<>(IncomingTargets.builder()
                .characterHandle(characterHandle)
                .characterId(characterId)
                .stacks(stacks)
                .adena(adena)
                .newStackTemplates(List.copyOf(newStacks))
                .build());
    }

    @Override
    public void release(IncomingTargets targets) {
        WorldPartition.pendingItems().release(handles(targets));
        WorldPartition.awaitedStacks()
                .release(targets.characterHandle(), new IntArrayList(targets.newStackTemplates()));
    }

    @Override
    public void applyIncoming(IncomingTargets targets, List<CommittedItemRow> committedRows) {
        release(targets);
        if (targets.stacks() == null) {
            return;
        }
        final Player player = loaded(targets.characterHandle(), targets.characterId());
        if (player == null) {
            log.error("Committed mail items of {} have no character in memory to apply to", targets.characterId());
            return;
        }
        final Map<UUID, ItemRowState> captured = new HashMap<>();
        targets.stacks().forEach(stack -> captured.put(stack.itemId(), stack));
        if (targets.adena() != null) {
            captured.put(targets.adena().itemId(), targets.adena());
        }

        // A row may come twice, paid from and then merged into: its last state counts, and what it took adds up.
        final Map<UUID, MailItemRow> rows = new LinkedHashMap<>();
        for (CommittedItemRow committed : committedRows) {
            rows.merge(
                    committed.itemId(),
                    (MailItemRow) committed,
                    (first, last) -> last.toBuilder()
                            .arrived(
                                    ((first.arrived() == null) || (last.arrived() == null))
                                            ? null
                                            : (first.arrived() + last.arrived()))
                            .build());
        }

        final List<Item> changed = new ArrayList<>();
        for (MailItemRow row : rows.values()) {
            final OptionalInt handle = registry.findHandle(row.itemId());
            final Held held = handle.isEmpty() ? null : held(player, handle.getAsInt());
            if (held != null) {
                applyToHeld(player, held, row, captured.get(row.itemId()), changed);
            } else if (!row.deleted()) {
                add(player, row, changed);
            }
        }
        refresh(player, changed, true);
    }

    @Override
    public long adenaLimit() {
        return PlayerConfig.MAX_ADENA;
    }

    private static boolean mailable(Player player, Item item) {
        return item.isAvailable(player, true, false) && player.getInventory().canManipulateWithItemId(item.getId());
    }

    private void applyToHeld(Player player, Held held, MailItemRow row, ItemRowState captured, List<Item> changed) {
        final Item item = held.item();
        if (row.deleted()) {
            final long left = captured == null ? 0 : (item.getCount() - captured.count());
            replace(held.container(), item, left, changed(player, held, changed));
            return;
        }
        final long delta;
        if (captured != null) {
            delta = row.count() - captured.count();
        } else if (row.arrived() != null) {
            delta = row.arrived();
        } else {
            // A replay into a stack nobody captured: only the row's state is known, and the row is the truth.
            delta = row.count() - item.getCount();
        }
        if (delta != 0) {
            take(held.container(), item, -delta, changed(player, held, changed));
        }
        rowStored(held.container(), item, captured);
    }

    private static void rowStored(ItemContainer container, Item item, ItemRowState row) {
        if ((row != null) && (row.unstoredRow() != null) && (container.getItemByObjectId(item.getObjectId()) == item)) {
            item.markRowStored();
        }
    }

    private void add(Player player, MailItemRow row, List<Item> changed) {
        final ItemContainer container = switch (row.location()) {
            case INVENTORY, PAPERDOLL -> player.getInventory();
            case WAREHOUSE -> player.getWarehouse();
            default -> null;
        };
        if (container == null) {
            log.error("Committed mail row {} lands where {} holds nothing in memory", row, player);
            return;
        }
        final Item item = Item.restoreCommitted(
                player.getObjectId(),
                ItemRow.builder()
                        .objectId(registry.handleOf(row.itemId()))
                        .itemId(row.itemTemplateId())
                        .count(row.count())
                        .location(row.location())
                        .locationSlot(row.locationSlot() == null ? 0 : row.locationSlot())
                        .enchantLevel(row.enchantLevel())
                        .customType1(row.customType1())
                        .customType2(row.customType2())
                        .mana(row.manaLeft() == null ? -1 : row.manaLeft())
                        .expiresAt(row.expiresAt())
                        .build(),
                row.augmentationId(),
                row.elementals());
        if (item == null) {
            return;
        }
        final boolean inventory = container == player.getInventory();
        final Item unsaved = item.isStackable() ? container.getItemByItemId(item.getId()) : null;
        if ((unsaved != null) && !unsaved.hasRow()) {
            // A stack that arrived mid-flight stays unsaved: the committed row absorbs it and the next save stores the
            // sum.
            item.setCount(item.getCount() + unsaved.getCount());
            container.removeCommitted(unsaved);
            if (inventory) {
                changed.add(unsaved);
            }
        }
        container.addCommitted(item);
        if (inventory) {
            changed.add(item);
        }
    }

    /** Takes {@code count} off a stack whose row stays; a negative count adds. */
    private static void take(ItemContainer container, Item item, long count, List<Item> changed) {
        if (!container.changeCommitted(item, -count)) {
            // Memory lost more of the stack than its row since the reservation: nothing of it is left, so the row goes.
            container.destroyItem(ItemProcessType.TRANSFER, item, null, null);
        }
        changed.add(item);
    }

    private static void replace(ItemContainer container, Item item, long left, List<Item> changed) {
        container.removeCommitted(item);
        changed.add(item);
        if (left > 0) {
            final Item rest = container.addItem(ItemProcessType.TRANSFER, item.getId(), left, null, null);
            if (rest != null) {
                changed.add(rest);
            }
        }
    }

    // Only inventory changes are shown: the warehouse window lists its items when it opens.
    private static List<Item> changed(Player player, Held held, List<Item> changed) {
        return held.container() == player.getInventory() ? changed : new ArrayList<>();
    }

    private static Held held(Player player, int handle) {
        final Item inInventory = player.getInventory().getItemByObjectId(handle);
        if (inInventory != null) {
            return new Held(player.getInventory(), inInventory);
        }
        final Item inWarehouse = player.getWarehouse().getItemByObjectId(handle);
        return inWarehouse == null ? null : new Held(player.getWarehouse(), inWarehouse);
    }

    // No packets before world entry: entering sends the whole inventory.
    private static void refresh(Player player, List<Item> changed, boolean fullList) {
        if (!changed.isEmpty() && (World.getPlayer(player.getObjectId()) == player)) {
            player.getInventory().sendCommittedChanges(changed, fullList);
        }
    }

    private ItemRowState row(UUID characterId, Item item) {
        final UUID itemId = registry.idOf(item.getObjectId());
        return ItemRowState.builder()
                .itemHandle(item.getObjectId())
                .itemId(itemId)
                .ownerId(characterId)
                .itemTemplateId(item.getId())
                .count(item.getCount())
                .unstoredRow(item.hasRow() ? null : unstoredRow(itemId, characterId, item))
                .build();
    }

    private static MailItemRow unstoredRow(UUID itemId, UUID ownerId, Item item) {
        final ItemLocation location = item.getItemLocation();
        final int mana = item.getMana();
        final List<ItemElemental> elementals = new ArrayList<>();
        final Elementals[] attributes = item.getElementals();
        if (attributes != null) {
            for (Elementals attribute : attributes) {
                elementals.add(ItemElemental.builder()
                        .element(attribute.getElement())
                        .value(attribute.getValue())
                        .build());
            }
        }
        return MailItemRow.builder()
                .itemId(itemId)
                .ownerId(ownerId)
                .location(location)
                .locationSlot(
                        switch (location) {
                            case INVENTORY, PAPERDOLL, PET_EQUIP -> item.getLocationSlot();
                            default -> null;
                        })
                .itemTemplateId(item.getId())
                .count(item.getCount())
                .enchantLevel(item.getEnchantLevel())
                .customType1(item.getCustomType1())
                .customType2(item.getCustomType2())
                .manaLeft(mana < 0 ? null : mana)
                .expiresAt(item.isTimeLimitedItem() ? Instant.ofEpochMilli(item.getTime()) : null)
                .augmentationId(item.isAugmented() ? item.getAugmentation().getAugmentationId() : null)
                .elementals(elementals)
                .build();
    }

    private static Player character(int characterHandle) {
        final Player player = World.getPlayer(characterHandle);
        if (player == null) {
            throw new IllegalStateException("No character " + characterHandle + " in the world");
        }
        return player;
    }

    private static Player loaded(int characterHandle, UUID characterId) {
        final Player player = World.getPlayer(characterHandle);
        return player != null
                ? player
                : WorldPartition.loadedCharacters().find(characterId).orElse(null);
    }

    private static IntSet handles(Reservation reservation) {
        final IntSet handles = new IntOpenHashSet();
        reservation.items().forEach(item -> handles.add(item.row().itemHandle()));
        if (reservation.adena() != null) {
            handles.add(reservation.adena().itemHandle());
        }
        return handles;
    }

    private static IntSet handles(IncomingTargets targets) {
        final IntSet handles = new IntOpenHashSet();
        if (targets.stacks() != null) {
            targets.stacks().forEach(stack -> handles.add(stack.itemHandle()));
        }
        if (targets.adena() != null) {
            handles.add(targets.adena().itemHandle());
        }
        return handles;
    }

    private record Held(ItemContainer container, Item item) {}
}
