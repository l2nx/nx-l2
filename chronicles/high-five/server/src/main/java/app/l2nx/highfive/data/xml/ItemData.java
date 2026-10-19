/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.data.xml;

import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.ThreadConfig;
import app.l2nx.highfive.entity.item.Armor;
import app.l2nx.highfive.entity.item.EtcItem;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.Weapon;
import app.l2nx.highfive.threads.ThreadPool;
import app.l2nx.highfive.util.DocumentItem;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * This class serves as a container for all item templates in the game.
 */
@Slf4j
public class ItemData {

    @Getter
    private ItemTemplate[] allItems;

    private final Map<Integer, EtcItem> etcItems = new HashMap<>();
    private final Map<Integer, Armor> armors = new HashMap<>();
    private final Map<Integer, Weapon> weapons = new HashMap<>();
    private final List<File> itemFiles = new ArrayList<>();

    protected ItemData() {
        processDirectory("data/stats/items", itemFiles);
        if (GeneralConfig.CUSTOM_ITEMS_LOAD) {
            processDirectory("data/stats/items/custom", itemFiles);
        }

        load();
    }

    private void processDirectory(String dirName, List<File> list) {
        final File dir = new File(ServerConfig.DATAPACK_ROOT, dirName);
        if (!dir.exists()) {
            log.warn("Directory {} does not exist.", dir.getAbsolutePath());
            return;
        }

        final File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
                    list.add(file);
                }
            }
        }
    }

    private void load() {
        final Collection<ItemTemplate> items = ConcurrentHashMap.newKeySet();
        int highestId = 0;
        armors.clear();
        etcItems.clear();
        weapons.clear();

        // If multithreading is enabled, use a thread pool to parse files.
        if (ThreadConfig.THREADS_FOR_LOADING) {
            final Collection<ScheduledFuture<?>> tasks = ConcurrentHashMap.newKeySet();
            for (File file : itemFiles) {
                tasks.add(ThreadPool.schedule(
                        () -> {
                            final DocumentItem document = new DocumentItem(file);
                            document.parse();
                            items.addAll(document.getItemList());
                        },
                        0));
            }

            // Wait for all scheduled tasks to complete.
            while (!tasks.isEmpty()) {
                for (ScheduledFuture<?> task : tasks) {
                    if ((task == null) || task.isDone() || task.isCancelled()) {
                        tasks.remove(task);
                    }
                }
            }
        } else // Parse files sequentially if multithreading is not enabled.
        {
            for (File file : itemFiles) {
                final DocumentItem document = new DocumentItem(file);
                document.parse();
                items.addAll(document.getItemList());
            }
        }

        // Process each loaded item and organize them into their respective collections.
        for (ItemTemplate item : items) {
            if (highestId < item.getId()) {
                highestId = item.getId();
            }

            if (item instanceof EtcItem) {
                etcItems.put(item.getId(), (EtcItem) item);
            } else if (item instanceof Armor) {
                armors.put(item.getId(), (Armor) item);
            } else {
                weapons.put(item.getId(), (Weapon) item);
            }
        }

        // Log the highest item ID and create a fast lookup array based on this value.
        log.info("Highest item id used: {}", highestId);
        allItems = new ItemTemplate[highestId + 1];

        // Populate the fast lookup table with items from each category.
        for (Armor item : armors.values()) {
            allItems[item.getId()] = item;
        }

        for (Weapon item : weapons.values()) {
            allItems[item.getId()] = item;
        }

        for (EtcItem item : etcItems.values()) {
            allItems[item.getId()] = item;
        }

        log.info("Loaded {} etc items.", etcItems.size());
        log.info("Loaded {} armor items.", armors.size());
        log.info("Loaded {} weapon items.", weapons.size());
        log.info("Loaded {} items in total.", (etcItems.size() + armors.size() + weapons.size()));
    }

    public void reload() {
        load();
        EnchantItemHPBonusData.getInstance().load();
    }

    /**
     * Returns the item template corresponding to the specified item ID.
     * @param id the ID of the item to retrieve
     * @return the {@link ItemTemplate} associated with the specified ID, or {@code null} if the ID is out of bounds
     */
    public ItemTemplate getTemplate(int id) {
        if ((id >= allItems.length) || (id < 0)) {
            return null;
        }

        return allItems[id];
    }

    /**
     * Returns a set of all armor item IDs available in the collection.
     * @return a {@link Set} of integers representing the IDs of all armors
     */
    public Set<Integer> getAllArmorsId() {
        return armors.keySet();
    }

    /**
     * Returns a collection of all armor items.
     * @return a {@link Collection} of {@link Armor} objects representing all loaded armor items
     */
    public Collection<Armor> getAllArmors() {
        return armors.values();
    }

    /**
     * Returns a set of all weapon item IDs available in the collection.
     * @return a {@link Set} of integers representing the IDs of all weapons
     */
    public Set<Integer> getAllWeaponsId() {
        return weapons.keySet();
    }

    /**
     * Returns a collection of all weapon items.
     * @return a {@link Collection} of {@link Weapon} objects representing all loaded weapon items
     */
    public Collection<Weapon> getAllWeapons() {
        return weapons.values();
    }

    /**
     * Returns a set of all miscellaneous item IDs (non-armor, non-weapon items) available in the collection.
     * @return a {@link Set} of integers representing the IDs of all miscellaneous items
     */
    public Set<Integer> getAllEtcItemsId() {
        return etcItems.keySet();
    }

    /**
     * Returns a collection of all miscellaneous items (non-armor, non-weapon items).
     * @return a {@link Collection} of {@link EtcItem} objects representing all loaded miscellaneous items
     */
    public Collection<EtcItem> getAllEtcItems() {
        return etcItems.values();
    }

    /**
     * Returns the size of the item template array.
     * @return the length of the {@link ItemTemplate} array, representing the maximum item ID + 1
     */
    public int getArraySize() {
        return allItems.length;
    }

    public static ItemData getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final ItemData INSTANCE = new ItemData();
    }
}
