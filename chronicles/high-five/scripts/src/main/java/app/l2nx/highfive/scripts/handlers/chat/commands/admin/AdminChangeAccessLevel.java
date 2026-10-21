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
package app.l2nx.highfive.scripts.handlers.chat.commands.admin;

import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.sql.CharacterAccessTable;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.LeaveWorld;

/**
 * Change access level command handler.
 */
public class AdminChangeAccessLevel implements IAdminCommandHandler {
    private static final String[] ADMIN_COMMANDS = {"admin_changelvl"};

    @Override
    public boolean onCommand(String command, Player activeChar) {
        final String[] parts = command.split(" ");
        if (parts.length == 2) {
            try {
                final int lvl = Integer.parseInt(parts[1]);
                final WorldObject target = activeChar.getTarget();
                if ((target == null) || !target.isPlayer()) {
                    activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
                } else {
                    onlineChange(activeChar, target.asPlayer(), lvl);
                }
            } catch (Exception e) {
                activeChar.sendSysMessage("Usage: //changelvl <target_new_level> | <player_name> <new_level>");
            }
        } else if (parts.length == 3) {
            final String name = parts[1];
            final int lvl;
            try {
                lvl = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                activeChar.sendSysMessage("Usage: //changelvl <target_new_level> | <player_name> <new_level>");
                return true;
            }

            final Player player = World.getPlayer(name);
            if (player != null) {
                onlineChange(activeChar, player, lvl);
            } else {
                offlineChange(activeChar, name, lvl);
            }
        }

        return true;
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }

    /**
     * @param name the name of a character that is not in the world
     */
    private void offlineChange(Player activeChar, String name, int lvl) {
        if ((lvl >= 0) && !AdminData.getInstance().hasAccessLevel(lvl)) {
            activeChar.sendSysMessage(
                    "You are trying to set unexisting access level: " + lvl + " please try again with a valid one!");
            return;
        }

        if (CharacterAccessTable.getInstance().setAccessLevel(name, lvl)) {
            activeChar.sendSysMessage("Character's access level is now set to " + lvl);
        } else {
            activeChar.sendSysMessage("Character not found or access level unaltered.");
        }
    }

    /**
     * @param activeChar the active GM
     * @param player the online target
     * @param lvl the access level
     */
    private void onlineChange(Player activeChar, Player player, int lvl) {
        if (lvl >= 0) {
            if (AdminData.getInstance().hasAccessLevel(lvl)) {
                final AccessLevel acccessLevel = AdminData.getInstance().getAccessLevel(lvl);
                player.setAccessLevel(lvl);
                player.sendMessage("Your access level has been changed to " + acccessLevel.getName() + " ("
                        + acccessLevel.getLevel() + ").");
                activeChar.sendMessage(player.getName() + "'s access level has been changed to "
                        + acccessLevel.getName() + " (" + acccessLevel.getLevel() + ").");
            } else {
                activeChar.sendSysMessage("You are trying to set unexisting access level: " + lvl
                        + " please try again with a valid one!");
            }
        } else {
            player.setAccessLevel(lvl);
            player.sendMessage("Your character has been banned. Bye.");
            Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
        }
    }
}
