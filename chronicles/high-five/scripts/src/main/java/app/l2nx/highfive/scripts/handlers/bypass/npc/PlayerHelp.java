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

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IBypassHandler;
import app.l2nx.highfive.network.serverpackets.NpcHtmlMessage;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PlayerHelp implements IBypassHandler {
    private static final String[] COMMANDS = {"player_help"};

    @Override
    public boolean onCommand(String command, Player player, Creature target) {
        try {
            if (command.length() < 13) {
                return false;
            }

            final String path = command.substring(12);
            if (path.contains("..")) {
                return false;
            }

            final StringTokenizer st = new StringTokenizer(path);
            final String[] cmd = st.nextToken().split("#");
            final NpcHtmlMessage html;
            if (cmd.length > 1) {
                final int itemId = Integer.parseInt(cmd[1]);
                html = new NpcHtmlMessage(0, itemId);
            } else {
                html = new NpcHtmlMessage();
            }

            html.setFile(player, "data/html/help/" + cmd[0]);
            player.sendPacket(html);
        } catch (Exception e) {
            log.warn("Bypass failed", e);
        }

        return true;
    }

    @Override
    public String[] getCommandList() {
        return COMMANDS;
    }
}
