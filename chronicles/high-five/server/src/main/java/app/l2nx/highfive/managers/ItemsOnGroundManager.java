/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
/*
 * SPDX-FileCopyrightText: L2J Mobius contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package app.l2nx.highfive.managers;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.repository.item.GroundItemRepository;
import app.l2nx.highfive.repository.item.model.GroundItem;
import app.l2nx.highfive.taskmanagers.ItemsAutoDestroyTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * This class manage all items on ground.
 * @author Enforcer
 */
@Slf4j
@Component
public class ItemsOnGroundManager implements Runnable {
    private static final long PROTECTED = -1;

    private static volatile ItemsOnGroundManager instance;

    private final Collection<Item> items = ConcurrentHashMap.newKeySet();

    private final GroundItemRepository groundItemRepository;

    public ItemsOnGroundManager(GroundItemRepository groundItemRepository) {
        this.groundItemRepository = groundItemRepository;
        instance = this;
    }

    public void load() {
        // If SaveDroppedItem is false, may want to delete all items previously stored to avoid add old items on
        // reactivate.
        if (!GeneralConfig.SAVE_DROPPED_ITEM && GeneralConfig.CLEAR_DROPPED_ITEM_TABLE) {
            emptyTable();
        }

        if (!GeneralConfig.SAVE_DROPPED_ITEM) {
            return;
        }

        if (GeneralConfig.SAVE_DROPPED_ITEM_INTERVAL > 0) {
            ThreadPool.scheduleAtFixedRate(
                    this, GeneralConfig.SAVE_DROPPED_ITEM_INTERVAL, GeneralConfig.SAVE_DROPPED_ITEM_INTERVAL);
        }

        // Add items to world.
        try {
            int count = 0;
            for (GroundItem row : groundItemRepository.findAll()) {
                final ItemTemplate template = ItemData.getInstance().getTemplate(row.itemId());
                if (template == null) {
                    log.warn("Skipped ground item {} of unknown template {}.", row.objectId(), row.itemId());
                    continue;
                }

                final long dropTime = unprotected(row, template)
                        ? ClockBridge.wallMillis()
                        : row.protectedItem() ? PROTECTED : epochMillisOrZero(row.dropTime());
                final Item item = new Item(row.objectId(), template);
                World.addObject(item);

                // this check and..
                if (item.isStackable() && (row.count() > 1)) {
                    item.setCount(row.count());
                }

                // This, are really necessary?
                if (row.enchantLevel() > 0) {
                    item.setEnchantLevel(row.enchantLevel());
                }

                item.setXYZ(row.x(), row.y(), row.z());
                item.setWorldRegion(World.getRegion(item));
                item.getWorldRegion().addVisibleObject(item);
                item.setDropTime(dropTime);
                item.setProtected(dropTime == PROTECTED);
                item.setSpawned(true);
                World.addVisibleObject(item, item.getWorldRegion());
                items.add(item);
                count++;

                // Add to ItemsAutoDestroy only items not protected.
                if (!GeneralConfig.LIST_PROTECTED_ITEMS.contains(item.getId())
                        && (dropTime > PROTECTED)
                        && (((GeneralConfig.AUTODESTROY_ITEM_AFTER > 0) && !template.hasExImmediateEffect())
                                || ((GeneralConfig.HERB_AUTO_DESTROY_TIME > 0) && template.hasExImmediateEffect()))) {
                    ItemsAutoDestroyTaskManager.getInstance().addItem(item);
                }
            }

            log.info("Loaded {} items.", count);
        } catch (DataAccessException e) {
            log.error("Error while loading ItemsOnGround {}", e.getMessage(), e);
        }

        if (GeneralConfig.EMPTY_DROPPED_ITEM_TABLE_AFTER_LOAD) {
            emptyTable();
        }
    }

    /**
     * If DestroyPlayerDroppedItem was previously false, items currently protected are added to ItemsAutoDestroy; the
     * next save stores them unprotected.
     */
    private static boolean unprotected(GroundItem row, ItemTemplate template) {
        return GeneralConfig.DESTROY_DROPPED_PLAYER_ITEM
                && row.protectedItem()
                && (GeneralConfig.DESTROY_EQUIPABLE_PLAYER_ITEM || !template.isEquipable());
    }

    public void save(Item item) {
        if (!GeneralConfig.SAVE_DROPPED_ITEM) {
            return;
        }

        items.add(item);
    }

    public void removeObject(Item item) {
        if (GeneralConfig.SAVE_DROPPED_ITEM) {
            items.remove(item);
        }
    }

    public void saveInDb() {
        run();
    }

    public void cleanUp() {
        items.clear();
    }

    public void emptyTable() {
        try {
            groundItemRepository.deleteAll();
        } catch (DataAccessException e) {
            log.error("Error while cleaning table ItemsOnGround {}", e.getMessage(), e);
        }
    }

    @Override
    public synchronized void run() {
        if (!GeneralConfig.SAVE_DROPPED_ITEM) {
            return;
        }

        final List<GroundItem> rows = new ArrayList<>();
        for (Item item : items) {
            if ((item == null) || CursedWeaponsManager.getInstance().isCursed(item.getId())) {
                continue; // Cursed Items not saved to ground, prevent double save.
            }

            rows.add(GroundItem.builder()
                    .objectId(item.getObjectId())
                    .itemId(item.getId())
                    .count(item.getCount())
                    .enchantLevel(item.getEnchantLevel())
                    .x(item.getX())
                    .y(item.getY())
                    .z(item.getZ())
                    .dropTime(item.isProtected() ? null : instantOrNull(item.getDropTime()))
                    .protectedItem(item.isProtected())
                    .build());
        }

        try {
            groundItemRepository.replaceAll(rows);
        } catch (DataAccessException e) {
            log.error("SQL error while storing items on ground: {}", e.getMessage(), e);
        }
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static ItemsOnGroundManager getInstance() {
        final ItemsOnGroundManager current = instance;
        if (current == null) {
            throw new IllegalStateException("ItemsOnGroundManager is used before the Spring context created it.");
        }
        return current;
    }
}
