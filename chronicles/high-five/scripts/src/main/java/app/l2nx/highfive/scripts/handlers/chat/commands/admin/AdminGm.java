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

import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * This class handles following admin commands: - GM = turns GM mode off
 * @version $Revision: 1.2.4.4 $ $Date: 2005/04/11 10:06:06 $
 */
@Slf4j
public class AdminGm implements IAdminCommandHandler {
    private static final String[] ADMIN_COMMANDS = {"admin_gm"};

    @Override
    public boolean onCommand(String command, Player activeChar) {
        if (command.equals("admin_gm") && activeChar.isGM()) {
            AdminData.getInstance().deleteGm(activeChar);
            activeChar.setAccessLevel(0);
            activeChar.sendSysMessage("You no longer have GM status.");
            log.info("GM: {}({}) turned his GM status off", activeChar.getName(), activeChar.getObjectId());
        }

        return true;
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }
}
