/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Live items of online characters, held in memory as a partition would. Tests must seed the same rows they insert
 * into the database.
 */
public final class FakeItems implements MailItems {
    public static final int ADENA = 57;
    public static final long ADENA_LIMIT = 99_900_000_000L;

    private final Map<Integer, Map<UUID, Item>> inventories = new LinkedHashMap<>();
    private final Map<Integer, Map<UUID, Item>> warehouses = new LinkedHashMap<>();
    private final Map<Integer, UUID> owners = new LinkedHashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final List<Reservation> released = new ArrayList<>();
    private final List<IncomingTargets> releasedTargets = new ArrayList<>();
    private final List<Reservation> appliedOutgoing = new ArrayList<>();
    private final List<List<CommittedItemRow>> appliedIncoming = new ArrayList<>();
    private MailRefusal incomingRefusal;
    private RuntimeException incomingFailure;

    public record Item(int handle, UUID id, UUID ownerId, int itemTemplateId, long count) {
        ItemRowState state() {
            return ItemRowState.builder()
                    .itemHandle(handle)
                    .itemId(id)
                    .ownerId(ownerId)
                    .itemTemplateId(itemTemplateId)
                    .count(count)
                    .build();
        }
    }

    public synchronized void register(int characterHandle, UUID characterId) {
        owners.put(characterHandle, characterId);
    }

    public synchronized void add(int characterHandle, Item item) {
        owners.put(characterHandle, item.ownerId());
        inventories
                .computeIfAbsent(characterHandle, handle -> new LinkedHashMap<>())
                .put(item.id(), item);
    }

    public synchronized void addToWarehouse(int characterHandle, Item item) {
        owners.put(characterHandle, item.ownerId());
        warehouses
                .computeIfAbsent(characterHandle, handle -> new LinkedHashMap<>())
                .put(item.id(), item);
    }

    public synchronized void reserveElsewhere(UUID itemId) {
        pending.add(itemId);
    }

    public synchronized List<Item> items(int characterHandle) {
        return List.copyOf(inventories.getOrDefault(characterHandle, Map.of()).values());
    }

    public synchronized boolean pending() {
        return !pending.isEmpty();
    }

    public synchronized List<Reservation> released() {
        return List.copyOf(released);
    }

    public synchronized List<IncomingTargets> releasedTargets() {
        return List.copyOf(releasedTargets);
    }

    public synchronized List<Reservation> appliedOutgoing() {
        return List.copyOf(appliedOutgoing);
    }

    public synchronized List<List<CommittedItemRow>> appliedIncoming() {
        return List.copyOf(appliedIncoming);
    }

    public synchronized void failIncoming(RuntimeException failure) {
        this.incomingFailure = failure;
    }

    public synchronized void refuseIncoming(MailRefusal reason) {
        this.incomingRefusal = reason;
    }

    @Override
    public synchronized List<Integer> mailable(int characterHandle) {
        return items(characterHandle).stream()
                .filter(item -> item.itemTemplateId() != ADENA || item.count() > 0)
                .map(Item::handle)
                .toList();
    }

    @Override
    public synchronized ReserveResult<Reservation> reserve(int characterHandle, List<ItemCount> wanted, long adena) {
        final Map<UUID, Item> inventory = inventories.getOrDefault(characterHandle, Map.of());
        final List<ReservedItem> reserved = new ArrayList<>();
        final List<AttachmentSnapshot> snapshots = new ArrayList<>();
        long adenaLeft = adenaRow(inventory) == null ? 0 : adenaRow(inventory).count();
        for (ItemCount count : wanted) {
            final Item item = byHandle(inventory, count.itemHandle());
            if ((item == null) || (item.count() < count.count())) {
                return new ReserveResult.Refused<>(MailRefusal.ITEM_NOT_MAILABLE);
            }
            if (pending.contains(item.id())) {
                return new ReserveResult.Refused<>(MailRefusal.BUSY);
            }
            if (item.itemTemplateId() == ADENA) {
                adenaLeft -= count.count();
            }
            reserved.add(new ReservedItem(item.state(), count.count()));
            snapshots.add(AttachmentSnapshot.builder()
                    .slot(snapshots.size())
                    .itemTemplateId(item.itemTemplateId())
                    .count(count.count())
                    .enchantOptions(List.of())
                    .build());
        }
        final Item adenaRow = adena > 0 ? adenaRow(inventory) : null;
        if ((adena > 0) && (adenaLeft < adena)) {
            return new ReserveResult.Refused<>(MailRefusal.NOT_ENOUGH_ADENA);
        }
        reserved.forEach(item -> pending.add(item.row().itemId()));
        if (adenaRow != null) {
            pending.add(adenaRow.id());
        }
        return new ReserveResult.Reserved<>(Reservation.builder()
                .characterHandle(characterHandle)
                .characterId(owners.get(characterHandle))
                .items(reserved)
                .adena(adenaRow == null ? null : adenaRow.state())
                .adenaAmount(adena)
                .snapshots(snapshots)
                .build());
    }

    @Override
    public synchronized void release(Reservation reservation) {
        reservation.items().forEach(item -> pending.remove(item.row().itemId()));
        if (reservation.adena() != null) {
            pending.remove(reservation.adena().itemId());
        }
        released.add(reservation);
    }

    @Override
    public synchronized void applyOutgoing(Reservation reservation) {
        final Map<UUID, Item> inventory = inventories.get(reservation.characterHandle());
        if (reservation.adena() != null) {
            change(inventory, reservation.adena().itemId(), -reservation.adenaAmount());
        }
        for (ReservedItem item : reservation.items()) {
            change(inventory, item.row().itemId(), -item.count());
        }
        release(reservation);
        released.remove(released.size() - 1);
        appliedOutgoing.add(reservation);
    }

    @Override
    public synchronized ReserveResult<IncomingTargets> incomingTargets(
            int characterHandle, HolderType holder, List<AttachmentSnapshot> incoming, long adena) {
        if (incomingFailure != null) {
            final RuntimeException failure = incomingFailure;
            incomingFailure = null;
            throw failure;
        }
        if (incomingRefusal != null) {
            final MailRefusal reason = incomingRefusal;
            incomingRefusal = null;
            return new ReserveResult.Refused<>(reason);
        }
        final Map<UUID, Item> inventory = (holder == HolderType.WAREHOUSE ? warehouses : inventories)
                .computeIfAbsent(characterHandle, handle -> new LinkedHashMap<>());
        final Item adenaRow = adenaRow(inventory);
        if ((adena < 0) && ((adenaRow == null) || (adenaRow.count() < -adena))) {
            return new ReserveResult.Refused<>(MailRefusal.NOT_ENOUGH_ADENA);
        }
        if ((adena > 0) && (adenaRow != null) && ((adenaRow.count() + adena) > ADENA_LIMIT)) {
            return new ReserveResult.Refused<>(MailRefusal.ADENA_LIMIT);
        }
        final List<ItemRowState> stacks = new ArrayList<>();
        for (AttachmentSnapshot snapshot : incoming) {
            inventory.values().stream()
                    .filter(item ->
                            item.itemTemplateId() == snapshot.itemTemplateId() && stackable(item.itemTemplateId()))
                    .findFirst()
                    .ifPresent(item -> stacks.add(item.state()));
        }
        final ItemRowState adenaState = (adena != 0) && (adenaRow != null) ? adenaRow.state() : null;
        if (stacks.stream().anyMatch(stack -> pending.contains(stack.itemId()))) {
            return new ReserveResult.Refused<>(MailRefusal.BUSY);
        }
        stacks.forEach(stack -> pending.add(stack.itemId()));
        if (adenaState != null) {
            pending.add(adenaState.itemId());
        }
        return new ReserveResult.Reserved<>(IncomingTargets.builder()
                .characterHandle(characterHandle)
                .characterId(owners.get(characterHandle))
                .stacks(stacks)
                .adena(adenaState)
                .build());
    }

    @Override
    public synchronized void release(IncomingTargets targets) {
        unmark(targets);
        releasedTargets.add(targets);
    }

    @Override
    public synchronized void applyIncoming(IncomingTargets targets, List<CommittedItemRow> committedRows) {
        unmark(targets);
        appliedIncoming.add(List.copyOf(committedRows));
    }

    @Override
    public long adenaLimit() {
        return ADENA_LIMIT;
    }

    public static boolean stackable(int itemTemplateId) {
        return itemTemplateId == ADENA || itemTemplateId < 100;
    }

    private void unmark(IncomingTargets targets) {
        targets.stacks().forEach(stack -> pending.remove(stack.itemId()));
        if (targets.adena() != null) {
            pending.remove(targets.adena().itemId());
        }
    }

    private static void change(Map<UUID, Item> inventory, UUID itemId, long delta) {
        inventory.computeIfPresent(itemId, (id, item) -> {
            final long count = item.count() + delta;
            return count == 0 ? null : new Item(item.handle(), id, item.ownerId(), item.itemTemplateId(), count);
        });
    }

    private static Item byHandle(Map<UUID, Item> inventory, int handle) {
        return inventory.values().stream()
                .filter(item -> item.handle() == handle)
                .findFirst()
                .orElse(null);
    }

    private static Item adenaRow(Map<UUID, Item> inventory) {
        return inventory.values().stream()
                .filter(item -> item.itemTemplateId() == ADENA)
                .findFirst()
                .orElse(null);
    }
}
