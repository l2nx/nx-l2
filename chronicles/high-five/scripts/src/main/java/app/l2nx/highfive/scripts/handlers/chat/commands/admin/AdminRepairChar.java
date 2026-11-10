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

import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import app.l2nx.highfive.managers.AdminCharacterManager;
import app.l2nx.highfive.partition.OfflineRows;

/**
 * This class handles following admin commands: - delete = deletes target
 * @version $Revision: 1.1.2.6.2.3 $ $Date: 2005/04/11 10:05:59 $
 */
public class AdminRepairChar implements IAdminCommandHandler {

    private static final String[] ADMIN_COMMANDS = {"admin_restore", "admin_repair"};

    @Override
    public boolean onCommand(String command, Player activeChar) {
        handleRepair(command, activeChar);
        return true;
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }

    private void handleRepair(String command, Player activeChar) {
        final String[] parts = command.split(" ");
        if (parts.length != 2) {
            return;
        }

        final String name = parts[1];
        OfflineRows.whenSettled(name, () -> repair(activeChar, name));
    }

    private void repair(Player activeChar, String name) {
        // An online character would write its position and equipment back over the repair.
        if (World.getPlayer(name) != null) {
            activeChar.sendSysMessage("Character " + name + " is online: only an offline character is repaired.");
            return;
        }

        if (AdminCharacterManager.getInstance().repairCharacter(name, -84318, 244579, -3730)) {
            activeChar.sendSysMessage("Character " + name + " is repaired.");
        } else {
            activeChar.sendSysMessage("Character " + name + " does not exist.");
        }
    }
}
