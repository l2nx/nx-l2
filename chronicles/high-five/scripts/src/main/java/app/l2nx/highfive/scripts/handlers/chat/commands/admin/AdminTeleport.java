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

import app.l2nx.highfive.data.xml.MapRegionData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import app.l2nx.highfive.managers.AdminCharacterManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.NpcHtmlMessage;
import app.l2nx.highfive.partition.OfflineRows;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import lombok.extern.slf4j.Slf4j;

/**
 * This class handles following admin commands: - show_moves - show_teleport - teleport_to_character - move_to - teleport_character
 * @version $Revision: 1.3.2.6.2.4 $ $Date: 2005/04/11 10:06:06 $ con.close() change and small typo fix by Zoey76 24/02/2011
 */
@Slf4j
public class AdminTeleport implements IAdminCommandHandler {

    private static final String[] ADMIN_COMMANDS = {
        "admin_show_moves",
        "admin_show_moves_other",
        "admin_show_teleport",
        "admin_teleport_to_character",
        "admin_teleportto",
        "admin_move_to",
        "admin_teleport_character",
        "admin_recall",
        "admin_walk",
        "teleportto",
        "recall",
        "admin_gonorth",
        "admin_gosouth",
        "admin_goeast",
        "admin_gowest",
        "admin_goup",
        "admin_godown",
        "admin_tele",
        "admin_teleto",
        "admin_instant_move",
        "admin_sendhome"
    };

    @Override
    public boolean onCommand(String command, Player activeChar) {
        if (command.equals("admin_teleto")) {
            activeChar.setTeleMode(1);
        }

        if (command.equals("admin_instant_move")) {
            activeChar.sendSysMessage("Instant move ready. Click where you want to go.");
            activeChar.setTeleMode(1);
        }

        if (command.equals("admin_teleto r")) {
            activeChar.sendSysMessage("Instant move ready. Click where you want to go.");
            activeChar.setTeleMode(2);
        }

        if (command.equals("admin_teleto end")) {
            activeChar.setTeleMode(0);
        }

        if (command.equals("admin_show_moves")) {
            AdminHtml.showAdminHtml(activeChar, "teleports.htm");
        }

        if (command.equals("admin_show_moves_other")) {
            AdminHtml.showAdminHtml(activeChar, "teleports/OtherLocations.htm");
        } else if (command.equals("admin_show_teleport")) {
            showTeleportCharWindow(activeChar);
        } else if (command.equals("admin_teleport_to_character")) {
            teleportToCharacter(activeChar, activeChar.getTarget());
        } else if (command.startsWith("admin_walk")) {
            try {
                final String val = command.substring(11);
                final StringTokenizer st = new StringTokenizer(val);
                final int x = Integer.parseInt(st.nextToken());
                final int y = Integer.parseInt(st.nextToken());
                final int z = Integer.parseInt(st.nextToken());
                activeChar.getAI().setIntentionMoveTo(new Location(x, y, z));
            } catch (Exception e) {
                // Not important.
            }
        } else if (command.startsWith("admin_move_to")) {
            try {
                final String val = command.substring(14);
                teleportTo(activeChar, val);
            } catch (StringIndexOutOfBoundsException e) {
                // Case of empty or missing coordinates.
                AdminHtml.showAdminHtml(activeChar, "teleports.htm");
            } catch (NumberFormatException nfe) {
                activeChar.sendSysMessage("Usage: //move_to <x> <y> <z>");
                AdminHtml.showAdminHtml(activeChar, "teleports.htm");
            }
        } else if (command.startsWith("admin_teleport_character")) {
            try {
                final String val = command.substring(25);
                teleportCharacter(activeChar, val);
            } catch (StringIndexOutOfBoundsException e) {
                // Case of empty coordinates.
                activeChar.sendSysMessage("Wrong or no Coordinates given.");
                showTeleportCharWindow(activeChar); // back to character teleport
            }
        } else if (command.startsWith("admin_teleportto ")) {
            try {
                final String targetName = command.substring(17);
                final Player player = World.getPlayer(targetName);
                teleportToCharacter(activeChar, player);
            } catch (StringIndexOutOfBoundsException e) {
                // Not important.
            }
        } else if (command.startsWith("admin_recall ")) {
            try {
                final String[] param = command.split(" ");
                if (param.length != 2) {
                    activeChar.sendSysMessage("Usage: //recall <playername>");
                    return false;
                }

                final String targetName = param[1];
                // Captured now: the GM may move or log out before the target settles.
                final Location destination = activeChar.getLocation();
                final int gmHandle = activeChar.getObjectId();
                OfflineRows.whenSettled(targetName, () -> recall(World.getPlayer(gmHandle), destination, targetName));
            } catch (StringIndexOutOfBoundsException e) {
                // Not important.
            }
        } else if (command.equals("admin_tele")) {
            showTeleportWindow(activeChar);
        } else if (command.startsWith("admin_go")) {
            int intVal = 150;
            int x = activeChar.getX();
            int y = activeChar.getY();
            int z = activeChar.getZ();
            try {
                final String val = command.substring(8);
                final StringTokenizer st = new StringTokenizer(val);
                final String dir = st.nextToken();
                if (st.hasMoreTokens()) {
                    intVal = Integer.parseInt(st.nextToken());
                }

                if (dir.equals("east")) {
                    x += intVal;
                } else if (dir.equals("west")) {
                    x -= intVal;
                } else if (dir.equals("north")) {
                    y -= intVal;
                } else if (dir.equals("south")) {
                    y += intVal;
                } else if (dir.equals("up")) {
                    z += intVal;
                } else if (dir.equals("down")) {
                    z -= intVal;
                }

                activeChar.teleToLocation(new Location(x, y, z));
                showTeleportWindow(activeChar);
            } catch (Exception e) {
                activeChar.sendSysMessage("Usage: //go<north|south|east|west|up|down> [offset] (default 150)");
            }
        } else if (command.startsWith("admin_sendhome")) {
            final StringTokenizer st = new StringTokenizer(command, " ");
            st.nextToken(); // Skip command.
            if (st.countTokens() > 1) {
                activeChar.sendSysMessage("Usage: //sendhome <playername>");
            } else if (st.countTokens() == 1) {
                final String name = st.nextToken();
                final Player player = World.getPlayer(name);
                if (player == null) {
                    activeChar.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
                    return false;
                }

                teleportHome(player);
            } else {
                final WorldObject target = activeChar.getTarget();
                if ((target != null) && target.isPlayer()) {
                    teleportHome(target.asPlayer());
                } else {
                    activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
                }
            }
        }

        return true;
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }

    /**
     * This method sends a player to it's home town.
     * @param player the player to teleport.
     */
    private void teleportHome(Player player) {
        String regionName;
        switch (player.getRace()) {
            case ELF: {
                regionName = "elf_town";
                break;
            }
            case DARK_ELF: {
                regionName = "darkelf_town";
                break;
            }
            case ORC: {
                regionName = "orc_town";
                break;
            }
            case DWARF: {
                regionName = "dwarf_town";
                break;
            }
            case KAMAEL: {
                regionName = "kamael_town";
                break;
            }
            case HUMAN:
            default: {
                regionName = "talking_island_town";
            }
        }

        player.teleToLocation(MapRegionData.getInstance().getSpawnLocByRegionName(regionName), true);
        player.setInstanceId(0);
        player.setIn7sDungeon(false);
    }

    private void teleportTo(Player activeChar, String coords) {
        try {
            final StringTokenizer st = new StringTokenizer(coords);
            final String x1 = st.nextToken();
            final int x = Integer.parseInt(x1);
            final String y1 = st.nextToken();
            final int y = Integer.parseInt(y1);
            final String z1 = st.nextToken();
            final int z = Integer.parseInt(z1);
            activeChar.getAI().setIntentionIdle();
            activeChar.teleToLocation(x, y, z);
            activeChar.sendSysMessage("You have been teleported to " + coords);
        } catch (NoSuchElementException nsee) {
            activeChar.sendSysMessage("Wrong or no Coordinates given.");
        }
    }

    private void showTeleportWindow(Player activeChar) {
        AdminHtml.showAdminHtml(activeChar, "move.htm");
    }

    private void showTeleportCharWindow(Player activeChar) {
        final WorldObject target = activeChar.getTarget();
        Player player = null;
        if ((target != null) && target.isPlayer()) {
            player = target.asPlayer();
        } else {
            activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
            return;
        }

        final NpcHtmlMessage adminReply = new NpcHtmlMessage();
        final String replyMSG =
                "<html><title>Teleport Character</title><body>The character you will teleport is " + player.getName()
                        + ".<br>Co-ordinate x<edit var=\"char_cord_x\" width=110>Co-ordinate y<edit"
                        + " var=\"char_cord_y\" width=110>Co-ordinate z<edit var=\"char_cord_z\" width=110><button"
                        + " value=\"Teleport\" action=\"bypass -h admin_teleport_character $char_cord_x $char_cord_y"
                        + " $char_cord_z\" width=60 height=15 back=\"L2UI_ct1.button_df\""
                        + " fore=\"L2UI_ct1.button_df\"><button value=\"Teleport near you\" action=\"bypass -h"
                        + " admin_teleport_character "
                        + activeChar.getX() + " " + activeChar.getY() + " " + activeChar.getZ()
                        + "\" width=115 height=15 back=\"L2UI_ct1.button_df\""
                        + " fore=\"L2UI_ct1.button_df\"><center><button value=\"Back\" action=\"bypass -h"
                        + " admin_current_player\" width=40 height=15 back=\"L2UI_ct1.button_df\""
                        + " fore=\"L2UI_ct1.button_df\"></center></body></html>";
        adminReply.setHtml(replyMSG);
        activeChar.sendPacket(adminReply);
    }

    private void teleportCharacter(Player activeChar, String coords) {
        final WorldObject target = activeChar.getTarget();
        Player player = null;
        if ((target != null) && target.isPlayer()) {
            player = target.asPlayer();
        } else {
            activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
            return;
        }

        if (player.getObjectId() == activeChar.getObjectId()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_USE_THIS_ON_YOURSELF);
        } else {
            try {
                final StringTokenizer st = new StringTokenizer(coords);
                final String x1 = st.nextToken();
                final int x = Integer.parseInt(x1);
                final String y1 = st.nextToken();
                final int y = Integer.parseInt(y1);
                final String z1 = st.nextToken();
                final int z = Integer.parseInt(z1);
                teleportCharacter(player, new Location(x, y, z), null);
            } catch (NoSuchElementException nsee) {
                // Not important.
            }
        }
    }

    /** @param gm {@code null} when the GM logged out since the command */
    private void teleportCharacter(Player player, Location loc, Player gm) {
        if (player != null) {
            // Check for jail
            if (player.isJailed()) {
                if (gm != null) {
                    gm.sendSysMessage("Sorry, player " + player.getName() + " is in Jail.");
                }
            } else {
                player.setInstanceId(loc.getInstanceId());
                if (gm != null) {
                    gm.sendSysMessage("You have recalled " + player.getName());
                }

                player.sendMessage("Admin is teleporting you.");
                player.getAI().setIntentionIdle();
                player.teleToLocation(loc, true);
            }
        }
    }

    private void teleportToCharacter(Player activeChar, WorldObject target) {
        if (target == null) {
            activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
            return;
        }

        Player player = null;
        if (target.isPlayer()) {
            player = target.asPlayer();
        } else {
            activeChar.sendPacket(SystemMessageId.INVALID_TARGET);
            return;
        }

        if (player.getObjectId() == activeChar.getObjectId()) {
            player.sendPacket(SystemMessageId.YOU_CANNOT_USE_THIS_ON_YOURSELF);
        } else {
            // move to targets instance
            activeChar.setInstanceId(target.getInstanceId());

            final int x = player.getX();
            final int y = player.getY();
            final int z = player.getZ();
            activeChar.getAI().setIntentionIdle();
            activeChar.teleToLocation(new Location(x, y, z), true);
            activeChar.sendSysMessage("You have teleported to character " + player.getName() + ".");
        }
    }

    private void recall(Player gm, Location destination, String targetName) {
        final Player player = World.getPlayer(targetName);
        if (player != null) {
            teleportCharacter(player, destination, gm);
        } else {
            changeCharacterPosition(gm, destination, targetName);
        }
    }

    private void changeCharacterPosition(Player gm, Location destination, String name) {
        final int x = destination.getX();
        final int y = destination.getY();
        final int z = destination.getZ();
        final boolean moved = AdminCharacterManager.getInstance().moveCharacter(name, x, y, z);
        if (gm == null) {
            return;
        }
        if (moved) {
            gm.sendSysMessage("Player's [" + name + "] position is now set to (" + x + "," + y + "," + z + ").");
        } else {
            gm.sendSysMessage("Character not found or position unaltered.");
        }
    }
}
