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
package app.l2nx.highfive.entity.actor.holders.player;

import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.CharacterRelationsManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

@Slf4j
public class BlockList {

    private static final Map<Integer, Set<Integer>> OFFLINE_LIST = new ConcurrentHashMap<>();

    private final Player owner;

    @Getter(AccessLevel.PRIVATE)
    private Set<Integer> blockList;

    public BlockList(Player owner) {
        this.owner = owner;
        blockList = OFFLINE_LIST.get(owner.getObjectId());
        if (blockList == null) {
            blockList = loadList(this.owner.getObjectId());
        }
    }

    private void addToBlockList(int target) {
        blockList.add(target);
        try {
            CharacterRelationsManager.getInstance().block(owner.getObjectId(), target);
        } catch (DataAccessException e) {
            log.warn("Could not add block player: {}", e.getMessage(), e);
        }
    }

    private void removeFromBlockList(int target) {
        blockList.remove(Integer.valueOf(target));
        try {
            CharacterRelationsManager.getInstance().unblock(owner.getObjectId(), target);
        } catch (DataAccessException e) {
            log.warn("Could not remove block player: {}", e.getMessage(), e);
        }
    }

    public void playerLogout() {
        OFFLINE_LIST.put(owner.getObjectId(), blockList);
    }

    private static Set<Integer> loadList(int objId) {
        final Set<Integer> list = new HashSet<>();
        try {
            for (int blockedId : CharacterRelationsManager.getInstance().findBlockedIds(objId)) {
                if (blockedId != objId) {
                    list.add(blockedId);
                }
            }
        } catch (DataAccessException e) {
            log.warn("Error found in {} FriendList while loading BlockList: {}", objId, e.getMessage(), e);
        }

        return list;
    }

    public boolean isInBlockList(Player target) {
        return blockList.contains(target.getObjectId());
    }

    public boolean isInBlockList(int targetId) {
        return blockList.contains(targetId);
    }

    private boolean isBlockAll() {
        return owner.getMessageRefusal();
    }

    public static boolean isBlocked(Player listOwner, Player target) {
        final BlockList blockList = listOwner.getBlockList();
        return blockList.isBlockAll() || blockList.isInBlockList(target);
    }

    public static boolean isBlocked(Player listOwner, int targetId) {
        final BlockList blockList = listOwner.getBlockList();
        return blockList.isBlockAll() || blockList.isInBlockList(targetId);
    }

    private void setBlockAll(boolean value) {
        owner.setMessageRefusal(value);
    }

    public static void addToBlockList(Player listOwner, int targetId) {
        if (listOwner == null) {
            return;
        }

        if (listOwner.getFriendList().contains(targetId)) {
            listOwner.sendPacket(SystemMessageId.THIS_PLAYER_IS_ALREADY_REGISTERED_ON_YOUR_FRIENDS_LIST);
            return;
        }

        if (listOwner.getBlockList().getBlockList().contains(targetId)) {
            listOwner.sendMessage("Already in ignore list.");
            return;
        }

        listOwner.getBlockList().addToBlockList(targetId);

        final String charName = CharInfoTable.getInstance().getNameById(targetId);
        SystemMessage sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_ADDED_TO_YOUR_IGNORE_LIST);
        sm.addString(charName);
        listOwner.sendPacket(sm);

        final Player player = World.getPlayer(targetId);
        if (player != null) {
            sm = new SystemMessage(SystemMessageId.S1_HAS_PLACED_YOU_ON_HIS_HER_IGNORE_LIST);
            sm.addString(listOwner.getName());
            player.sendPacket(sm);
        }
    }

    public static void removeFromBlockList(Player listOwner, int targetId) {
        if (listOwner == null) {
            return;
        }

        SystemMessage sm;
        if (!listOwner.getBlockList().getBlockList().contains(targetId)) {
            sm = new SystemMessage(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
            listOwner.sendPacket(sm);
            return;
        }

        listOwner.getBlockList().removeFromBlockList(targetId);

        final String charName = CharInfoTable.getInstance().getNameById(targetId);
        sm = new SystemMessage(SystemMessageId.S1_HAS_BEEN_REMOVED_FROM_YOUR_IGNORE_LIST);
        sm.addString(charName);
        listOwner.sendPacket(sm);
    }

    public static boolean isInBlockList(Player listOwner, Player target) {
        return listOwner.getBlockList().isInBlockList(target);
    }

    public boolean isBlockAll(Player listOwner) {
        return listOwner.getBlockList().isBlockAll();
    }

    public static void setBlockAll(Player listOwner, boolean newValue) {
        listOwner.getBlockList().setBlockAll(newValue);
    }

    public static void sendListToOwner(Player listOwner) {
        int i = 1;
        listOwner.sendPacket(SystemMessageId.IGNORE_LIST);
        for (int playerId : listOwner.getBlockList().getBlockList()) {
            listOwner.sendMessage((i++) + ". " + CharInfoTable.getInstance().getNameById(playerId));
        }

        listOwner.sendPacket(SystemMessageId.EMPTY_3);
    }

    /**
     * @param ownerId object id of owner block list
     * @param targetId object id of potential blocked player
     * @return true if blocked
     */
    public static boolean isInBlockList(int ownerId, int targetId) {
        final Player player = World.getPlayer(ownerId);
        if (player != null) {
            return isBlocked(player, targetId);
        }

        return OFFLINE_LIST.computeIfAbsent(ownerId, k -> loadList(k)).contains(targetId);
    }
}
