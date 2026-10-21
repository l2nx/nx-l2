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

import app.l2nx.highfive.cache.HtmCache;
import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.PremiumSystemConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import app.l2nx.highfive.managers.PcCafePointsManager;
import app.l2nx.highfive.managers.PremiumManager;
import app.l2nx.highfive.network.protocol.highfive.serverpackets.NpcHtmlMessage;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @author Mobius
 */
public class AdminPremium implements IAdminCommandHandler {
    private static final DateTimeFormatter PREMIUM_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final String[] ADMIN_COMMANDS = {
        "admin_premium_menu",
        "admin_premium_add1",
        "admin_premium_add2",
        "admin_premium_add3",
        "admin_premium_info",
        "admin_premium_remove"
    };

    @Override
    public boolean onCommand(String command, Player activeChar) {
        if (command.equals("admin_premium_menu")) {
            AdminHtml.showAdminHtml(activeChar, "premium_menu.htm");
        } else if (command.startsWith("admin_premium_add1")) {
            try {
                addPremiumStatus(activeChar, 1, command.substring(19));
            } catch (StringIndexOutOfBoundsException e) {
                activeChar.sendSysMessage("Please enter a valid character name.");
            }
        } else if (command.startsWith("admin_premium_add2")) {
            try {
                addPremiumStatus(activeChar, 2, command.substring(19));
            } catch (StringIndexOutOfBoundsException e) {
                activeChar.sendSysMessage("Please enter a valid character name.");
            }
        } else if (command.startsWith("admin_premium_add3")) {
            try {
                addPremiumStatus(activeChar, 3, command.substring(19));
            } catch (StringIndexOutOfBoundsException e) {
                activeChar.sendSysMessage("Please enter a valid character name.");
            }
        } else if (command.startsWith("admin_premium_info")) {
            try {
                viewPremiumInfo(activeChar, command.substring(19));
            } catch (StringIndexOutOfBoundsException e) {
                activeChar.sendSysMessage("Please enter a valid character name.");
            }
        } else if (command.startsWith("admin_premium_remove")) {
            try {
                removePremium(activeChar, command.substring(21));
            } catch (StringIndexOutOfBoundsException e) {
                activeChar.sendSysMessage("Please enter a valid character name.");
            }
        }

        final NpcHtmlMessage html = new NpcHtmlMessage(0, 0);
        html.setHtml(HtmCache.getInstance().getHtm(activeChar, "data/html/admin/premium_menu.htm"));
        activeChar.sendPacket(html);
        return true;
    }

    private void addPremiumStatus(Player admin, int months, String characterName) {
        final int objectId = findCharacter(admin, characterName);
        if (objectId <= 0) {
            return;
        }

        PremiumManager.getInstance().addPremiumTime(objectId, months * 30, TimeUnit.DAYS);
        admin.sendMessage(holder(characterName) + " will now have premium status until "
                + format(PremiumManager.getInstance().getPremiumExpiration(objectId)) + ".");
        if (PremiumSystemConfig.PC_CAFE_RETAIL_LIKE) {
            final Player player = World.getPlayer(objectId);
            if (player != null) {
                PcCafePointsManager.getInstance().run(player);
            }
        }
    }

    private void viewPremiumInfo(Player admin, String characterName) {
        final int objectId = findCharacter(admin, characterName);
        if (objectId <= 0) {
            return;
        }

        final long premiumExpiration = PremiumManager.getInstance().getPremiumExpiration(objectId);
        if (premiumExpiration > 0) {
            admin.sendMessage(holder(characterName) + " has premium status until " + format(premiumExpiration) + ".");
        } else {
            admin.sendMessage(holder(characterName) + " has no premium status.");
        }
    }

    private void removePremium(Player admin, String characterName) {
        final int objectId = findCharacter(admin, characterName);
        if (objectId <= 0) {
            return;
        }

        if (PremiumManager.getInstance().getPremiumExpiration(objectId) > 0) {
            PremiumManager.getInstance().removePremiumStatus(objectId);
            admin.sendMessage(holder(characterName) + " has no longer premium status.");
        } else {
            admin.sendMessage(holder(characterName) + " has no premium status.");
        }
    }

    /**
     * @return the object id of the character, {@code 0} or less after telling the admin why there is none
     */
    private static int findCharacter(Player admin, String characterName) {
        if (!PremiumSystemConfig.PREMIUM_SYSTEM_ENABLED) {
            admin.sendMessage("Premium system is disabled.");
            return 0;
        }

        final int objectId = CharInfoTable.getInstance().getIdByName(characterName.strip());
        if (objectId <= 0) {
            admin.sendMessage("Character " + characterName + " does not exist.");
        }

        return objectId;
    }

    private static String holder(String characterName) {
        return (PremiumSystemConfig.ACCOUNT_WIDE_PREMIUM ? "The account of " : "Player ") + characterName;
    }

    private static String format(long epochMillis) {
        return PREMIUM_FORMAT.format(
                Instant.ofEpochMilli(epochMillis).atZone(ClockBridge.wallClock().getZone()));
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }
}
