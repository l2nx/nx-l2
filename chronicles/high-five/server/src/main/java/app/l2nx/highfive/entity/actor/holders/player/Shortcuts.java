/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ExAutoSoulShot;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShortcutInit;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ShortcutRegister;
import app.l2nx.highfive.repository.character.model.CharacterShortcut;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class Shortcuts {
    private static final int MAX_SHORTCUTS_PER_BAR = 12;
    private final Player owner;
    private final Map<Integer, Shortcut> shortcuts = new ConcurrentHashMap<>();

    public Shortcuts(Player owner) {
        this.owner = owner;
    }

    public Collection<Shortcut> getAllShortcuts() {
        return shortcuts.values();
    }

    public Shortcut getShortcut(int slot, int page) {
        Shortcut sc = shortcuts.get(slot + (page * MAX_SHORTCUTS_PER_BAR));

        // Verify shortcut.
        if ((sc != null)
                && (sc.getType() == ShortcutType.ITEM)
                && (owner.getInventory().getItemByObjectId(sc.getId()) == null)) {
            deleteShortcut(sc.getSlot(), sc.getPage());
            sc = null;
        }

        return sc;
    }

    public void registerShortcut(Shortcut shortcut) {
        // Verify shortcut.
        if (shortcut.getType() == ShortcutType.ITEM) {
            final Item item = owner.getInventory().getItemByObjectId(shortcut.getId());
            if (item == null) {
                return;
            }

            shortcut.setSharedReuseGroup(item.getSharedReuseGroup());
            // A new item reaches the database lazily; the stored shortcut references its row.
            item.updateDatabase(true);
        }

        shortcuts.put(shortcut.getSlot() + (shortcut.getPage() * MAX_SHORTCUTS_PER_BAR), shortcut);
        registerShortcutInDb(shortcut);
    }

    private void registerShortcutInDb(Shortcut shortcut) {
        try {
            PlayerPersistenceManager.getInstance()
                    .saveShortcut(
                            owner.getObjectId(),
                            owner.getClassIndex(),
                            CharacterShortcut.builder()
                                    .slot(shortcut.getSlot())
                                    .page(shortcut.getPage())
                                    .type(shortcut.getType())
                                    .id(shortcut.getId())
                                    .level(shortcut.getLevel())
                                    .build());
        } catch (DataAccessException e) {
            log.warn("Could not store character shortcut: {}", e.getMessage(), e);
        }
    }

    public void deleteShortcut(int slot, int page) {
        final Shortcut old = shortcuts.remove(slot + (page * MAX_SHORTCUTS_PER_BAR));
        if ((old == null) || (owner == null)) {
            return;
        }

        deleteShortcutFromDb(old);
        if (old.getType() == ShortcutType.ITEM) {
            final Item item = owner.getInventory().getItemByObjectId(old.getId());
            if ((item != null) && (item.getItemType() == EtcItemType.SHOT) && owner.removeAutoSoulShot(item.getId())) {
                owner.sendPacket(new ExAutoSoulShot(item.getId(), 0));
            }
        }

        owner.sendPacket(new ShortcutInit(owner));

        for (int shotId : owner.getAutoSoulShot()) {
            owner.sendPacket(new ExAutoSoulShot(shotId, 1));
        }
    }

    public void deleteShortcutByObjectId(int objectId) {
        for (Shortcut shortcut : shortcuts.values()) {
            if ((shortcut.getType() == ShortcutType.ITEM) && (shortcut.getId() == objectId)) {
                deleteShortcut(shortcut.getSlot(), shortcut.getPage());
                break;
            }
        }
    }

    private void deleteShortcutFromDb(Shortcut shortcut) {
        try {
            PlayerPersistenceManager.getInstance()
                    .deleteShortcut(owner.getObjectId(), owner.getClassIndex(), shortcut.getSlot(), shortcut.getPage());
        } catch (DataAccessException e) {
            log.warn("Could not delete character shortcut: {}", e.getMessage(), e);
        }
    }

    public boolean restoreMe() {
        shortcuts.clear();
        try {
            for (CharacterShortcut stored :
                    PlayerPersistenceManager.getInstance().findShortcuts(owner.getObjectId(), owner.getClassIndex())) {
                shortcuts.put(
                        stored.slot() + (stored.page() * MAX_SHORTCUTS_PER_BAR),
                        new Shortcut(stored.slot(), stored.page(), stored.type(), stored.id(), stored.level(), 1));
            }
        } catch (DataAccessException e) {
            log.warn("Could not restore character shortcuts: {}", e.getMessage(), e);
            return false;
        }

        // Verify shortcuts
        for (Shortcut sc : getAllShortcuts()) {
            if (sc.getType() == ShortcutType.ITEM) {
                final Item item = owner.getInventory().getItemByObjectId(sc.getId());
                if (item == null) {
                    deleteShortcut(sc.getSlot(), sc.getPage());
                } else if (item.isEtcItem()) {
                    sc.setSharedReuseGroup(item.getEtcItem().getSharedReuseGroup());
                }
            }
        }

        return true;
    }

    /**
     * Updates the shortcut bars with the new skill.
     * @param skillId the skill Id to search and update.
     * @param skillLevel the skill level to update.
     */
    public void updateShortcuts(int skillId, int skillLevel) {
        // Update all the shortcuts for this skill.
        for (Shortcut sc : shortcuts.values()) {
            if ((sc.getId() == skillId) && (sc.getType() == ShortcutType.SKILL)) {
                final Shortcut newsc =
                        new Shortcut(sc.getSlot(), sc.getPage(), sc.getType(), sc.getId(), skillLevel, 1);
                owner.sendPacket(new ShortcutRegister(newsc));
                owner.registerShortcut(newsc);
            }
        }
    }
}
