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
package app.l2nx.highfive.scripts.handlers.bypass.npc;

import app.l2nx.highfive.data.xml.MultisellData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IBypassHandler;
import java.util.logging.Level;

public class Multisell implements IBypassHandler {
    private static final String[] COMMANDS = {"multisell", "exc_multisell"};

    @Override
    public boolean onCommand(String command, Player player, Creature target) {
        if (!target.isNpc()) {
            return false;
        }

        try {
            int listId;
            if (command.toLowerCase().startsWith(COMMANDS[0])) // multisell
            {
                listId = Integer.parseInt(command.substring(9).trim());
                MultisellData.getInstance().separateAndSend(listId, player, target.asNpc(), false);
                return true;
            } else if (command.toLowerCase().startsWith(COMMANDS[1])) // exc_multisell
            {
                listId = Integer.parseInt(command.substring(13).trim());
                MultisellData.getInstance().separateAndSend(listId, player, target.asNpc(), true);
                return true;
            }

            return false;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Exception in " + getClass().getSimpleName(), e);
        }

        return false;
    }

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }
}
