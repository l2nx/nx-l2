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

import app.l2nx.highfive.data.xml.SpawnData;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.spawns.Spawn;
import app.l2nx.highfive.entity.zone.type.NpcSpawnTerritory;
import app.l2nx.highfive.handler.AdminCommandHandler;
import app.l2nx.highfive.handler.IAdminCommandHandler;
import app.l2nx.highfive.managers.RaidBossSpawnManager;
import org.apache.commons.lang3.StringUtils;

/**
 * @author Mobius
 */
public class AdminDelete implements IAdminCommandHandler {
    private static final String[] ADMIN_COMMANDS = {
        "admin_delete", // supports range parameter
        "admin_delete_group" // for territory spawns
    };

    @Override
    public boolean onCommand(String command, Player activeChar) {
        if (command.contains("group")) {
            handleDeleteGroup(activeChar);
        } else if (command.startsWith("admin_delete")) {
            final String[] split = command.split(" ");
            handleDelete(
                    activeChar, (split.length > 1) && StringUtils.isNumeric(split[1]) ? Integer.parseInt(split[1]) : 0);
        }

        return true;
    }

    private void handleDelete(Player player, int range) {
        if (range > 0) {
            World.forEachVisibleObjectInRange(player, Npc.class, range, target -> deleteNpc(player, target));
            return;
        }

        final WorldObject obj = player.getTarget();
        if (obj instanceof Npc) {
            deleteNpc(player, obj.asNpc());
        } else {
            player.sendSysMessage("Incorrect target.");
        }
    }

    private void handleDeleteGroup(Player player) {
        final WorldObject obj = player.getTarget();
        if (obj instanceof Npc) {
            deleteGroup(player, obj.asNpc());
        } else {
            player.sendSysMessage("Incorrect target.");
        }
    }

    private void deleteNpc(Player player, Npc target) {
        final Spawn spawn = target.getSpawn();
        if (spawn != null) {
            final NpcSpawnTerritory npcSpawnTerritory = spawn.getSpawnTerritory();
            if (npcSpawnTerritory == null) {
                target.deleteMe();
                spawn.stopRespawn();
                if (RaidBossSpawnManager.getInstance().isDefined(spawn.getId())) {
                    RaidBossSpawnManager.getInstance().deleteSpawn(spawn, true);
                } else {
                    SpawnData.getInstance().deleteSpawn(spawn);
                }

                player.sendSysMessage("Deleted " + target.getName() + " from " + target.getObjectId() + ".");
            } else {
                AdminCommandHandler.getInstance().onCommand(player, AdminDelete.ADMIN_COMMANDS[1], true);
            }
        }
    }

    private void deleteGroup(Player player, Npc target) {
        final Spawn spawn = target.getSpawn();
        if (spawn != null) {
            final NpcSpawnTerritory npcSpawnTerritory = spawn.getSpawnTerritory();
            if (npcSpawnTerritory == null) {
                player.sendSysMessage("Incorrect target.");
            } else {
                target.deleteMe();
                spawn.stopRespawn();
                if (RaidBossSpawnManager.getInstance().isDefined(spawn.getId())) {
                    RaidBossSpawnManager.getInstance().deleteSpawn(spawn, true);
                } else {
                    SpawnData.getInstance().deleteSpawn(spawn);
                }

                for (WorldObject wo : World.getVisibleObjects()) {
                    if (!wo.isNpc()) {
                        continue;
                    }

                    final Spawn npcSpawn = wo.asNpc().getSpawn();
                    if (npcSpawn != null) {
                        final NpcSpawnTerritory territory = npcSpawn.getSpawnTerritory();
                        if ((territory != null)
                                && !territory.getName().isEmpty()
                                && territory.getName().equals(npcSpawnTerritory.getName())) {
                            wo.asNpc().deleteMe();
                            npcSpawn.stopRespawn();
                        }
                    }
                }

                player.sendSysMessage("Deleted " + target.getName() + " group from " + target.getObjectId() + ".");
            }
        }
    }

    @Override
    public String[] getCommandList() {
        return ADMIN_COMMANDS;
    }
}
