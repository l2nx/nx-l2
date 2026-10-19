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

import app.l2nx.highfive.data.sql.DatabaseFactory;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.ShortcutType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.type.EtcItemType;
import app.l2nx.highfive.network.serverpackets.ExAutoSoulShot;
import app.l2nx.highfive.network.serverpackets.ShortcutInit;
import app.l2nx.highfive.network.serverpackets.ShortcutRegister;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

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
        }

        registerShortcutInDb(
                shortcut, shortcuts.put(shortcut.getSlot() + (shortcut.getPage() * MAX_SHORTCUTS_PER_BAR), shortcut));
    }

    private void registerShortcutInDb(Shortcut shortcut, Shortcut oldShortcut) {
        if (oldShortcut != null) {
            deleteShortcutFromDb(oldShortcut);
        }

        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(
                        "REPLACE INTO character_shortcuts (charId,slot,page,type,shortcut_id,level,class_index) values(?,?,?,?,?,?,?)")) {
            statement.setInt(1, owner.getObjectId());
            statement.setInt(2, shortcut.getSlot());
            statement.setInt(3, shortcut.getPage());
            statement.setInt(4, shortcut.getType().ordinal());
            statement.setInt(5, shortcut.getId());
            statement.setInt(6, shortcut.getLevel());
            statement.setInt(7, owner.getClassIndex());
            statement.execute();
        } catch (Exception e) {
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
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(
                        "DELETE FROM character_shortcuts WHERE charId=? AND slot=? AND page=? AND class_index=?")) {
            statement.setInt(1, owner.getObjectId());
            statement.setInt(2, shortcut.getSlot());
            statement.setInt(3, shortcut.getPage());
            statement.setInt(4, owner.getClassIndex());
            statement.execute();
        } catch (Exception e) {
            log.warn("Could not delete character shortcut: {}", e.getMessage(), e);
        }
    }

    public boolean restoreMe() {
        shortcuts.clear();
        try (Connection con = DatabaseFactory.getConnection();
                PreparedStatement statement = con.prepareStatement(
                        "SELECT charId, slot, page, type, shortcut_id, level FROM character_shortcuts WHERE charId=? AND class_index=?")) {
            statement.setInt(1, owner.getObjectId());
            statement.setInt(2, owner.getClassIndex());

            try (ResultSet rset = statement.executeQuery()) {
                while (rset.next()) {
                    final int slot = rset.getInt("slot");
                    final int page = rset.getInt("page");
                    shortcuts.put(
                            slot + (page * MAX_SHORTCUTS_PER_BAR),
                            new Shortcut(
                                    slot,
                                    page,
                                    ShortcutType.values()[rset.getInt("type")],
                                    rset.getInt("shortcut_id"),
                                    rset.getInt("level"),
                                    1));
                }
            }
        } catch (Exception e) {
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
