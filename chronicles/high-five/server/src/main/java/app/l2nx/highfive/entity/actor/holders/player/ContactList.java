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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.engine.core.loop.SingleWriter;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CharacterRelationsManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

/**
 * TODO: System messages:<br>
 * ADD: 3223: The previous name is being registered. Please try again later.<br>
 * DEL 3219: $s1 was successfully deleted from your Contact List.<br>
 * DEL 3217: The name is not currently registered.
 * @author UnAfraid, mrTJO
 */
@Slf4j
@SingleWriter
public class ContactList {

    private final Player player;

    // Insertion order is the order of the client's contact list.
    private final ObjectLinkedOpenHashSet<String> allContacts = new ObjectLinkedOpenHashSet<>();

    public ContactList(Player player) {
        this.player = player;
        load();
    }

    public List<String> getAllContacts() {
        WorldPartition.checkOwner();
        return allContacts.isEmpty() ? List.of() : ObjectLists.unmodifiable(new ObjectArrayList<>(allContacts));
    }

    public void restore() {
        WorldPartition.checkOwner();
        load();
    }

    private void load() {
        allContacts.clear();

        try {
            for (int contactId : CharacterRelationsManager.getInstance().findContactIds(player.getObjectId())) {
                final String contactName = CharInfoTable.getInstance().getNameById(contactId);
                if ((contactName == null)
                        || contactName.equals(player.getName())
                        || (contactId == player.getObjectId())) {
                    continue;
                }

                allContacts.add(contactName);
            }
        } catch (DataAccessException e) {
            log.warn("Error found in {}'s ContactsList: {}", player.getName(), e.getMessage(), e);
        }
    }

    public boolean add(String name) {
        WorldPartition.checkOwner();
        if (allContacts.contains(name)) {
            player.sendPacket(SystemMessageId.THE_NAME_ALREADY_EXISTS_ON_THE_ADDED_LIST);
            return false;
        }

        if (player.getName().equals(name)) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_ADD_YOUR_OWN_NAME);
            return false;
        }

        if (allContacts.size() >= 100) {
            player.sendPacket(
                    SystemMessageId.THE_MAXIMUM_NUMBER_OF_NAMES_100_HAS_BEEN_REACHED_YOU_CANNOT_REGISTER_ANY_MORE);
            return false;
        }

        final int contactId = CharInfoTable.getInstance().getIdByName(name);
        SystemMessage sm;
        if (contactId < 1) {
            sm = new SystemMessage(SystemMessageId.THE_NAME_S1_DOESN_T_EXIST_PLEASE_TRY_ANOTHER_NAME);
            sm.addString(name);
            player.sendPacket(sm);
            return false;
        }

        for (String contactName : allContacts) {
            if (contactName.equalsIgnoreCase(name)) {
                player.sendPacket(SystemMessageId.THE_NAME_ALREADY_EXISTS_ON_THE_ADDED_LIST);
                return false;
            }
        }

        try {
            CharacterRelationsManager.getInstance().addContact(player.getObjectId(), contactId);

            allContacts.add(name);

            sm = new SystemMessage(SystemMessageId.S1_WAS_SUCCESSFULLY_ADDED_TO_YOUR_CONTACT_LIST);
            sm.addString(name);
            player.sendPacket(sm);
        } catch (DataAccessException e) {
            log.warn("Error found in {}'s ContactsList: {}", player.getName(), e.getMessage(), e);
        }

        return true;
    }

    public void remove(String name) {
        WorldPartition.checkOwner();
        if (!allContacts.contains(name)) {
            player.sendPacket(SystemMessageId.THE_NAME_IS_NOT_CURRENTLY_REGISTERED);
            return;
        }

        final int contactId = CharInfoTable.getInstance().getIdByName(name);
        if (contactId < 1) {
            // TODO: Message?
            return;
        }

        allContacts.remove(name);

        try {
            CharacterRelationsManager.getInstance().removeContact(player.getObjectId(), contactId);

            final SystemMessage sm =
                    new SystemMessage(SystemMessageId.S1_WAS_SUCCESSFULLY_DELETED_FROM_YOUR_CONTACT_LIST);
            sm.addString(name);
            player.sendPacket(sm);
        } catch (DataAccessException e) {
            log.warn("Error found in {}'s ContactsList: {}", player.getName(), e.getMessage(), e);
        }
    }
}
