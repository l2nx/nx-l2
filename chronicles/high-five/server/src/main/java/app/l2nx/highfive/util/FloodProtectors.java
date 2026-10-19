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
package app.l2nx.highfive.util;

import app.l2nx.highfive.config.FloodProtectorConfig;
import app.l2nx.highfive.network.GameClient;

/**
 * Collection of flood protectors for single player.
 * @author fordfrog, Mobius
 */
public class FloodProtectors {
    private final FloodProtectorAction useItem;
    private final FloodProtectorAction rollDice;
    private final FloodProtectorAction itemPetSummon;
    private final FloodProtectorAction heroVoice;
    private final FloodProtectorAction globalChat;
    private final FloodProtectorAction subclass;
    private final FloodProtectorAction dropItem;
    private final FloodProtectorAction serverBypass;
    private final FloodProtectorAction multiSell;
    private final FloodProtectorAction transaction;
    private final FloodProtectorAction manufacture;
    private final FloodProtectorAction sendMail;
    private final FloodProtectorAction characterSelect;
    private final FloodProtectorAction itemAuction;
    private final FloodProtectorAction playerAction;

    /**
     * Creates new instance of FloodProtectors.
     * @param client game client for which the collection of flood protectors is being created.
     */
    public FloodProtectors(GameClient client) {
        useItem = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_USE_ITEM);
        rollDice = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_ROLL_DICE);
        itemPetSummon = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_ITEM_PET_SUMMON);
        heroVoice = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_HERO_VOICE);
        globalChat = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_GLOBAL_CHAT);
        subclass = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_SUBCLASS);
        dropItem = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_DROP_ITEM);
        serverBypass = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_SERVER_BYPASS);
        multiSell = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_MULTISELL);
        transaction = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_TRANSACTION);
        manufacture = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_MANUFACTURE);
        sendMail = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_SENDMAIL);
        characterSelect = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_CHARACTER_SELECT);
        itemAuction = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_ITEM_AUCTION);
        playerAction = new FloodProtectorAction(client, FloodProtectorConfig.FLOOD_PROTECTOR_PLAYER_ACTION);
    }

    public boolean canUseItem() {
        return useItem.canPerformAction();
    }

    public boolean canRollDice() {
        return rollDice.canPerformAction();
    }

    public boolean canUsePetSummonItem() {
        return itemPetSummon.canPerformAction();
    }

    public boolean canUseHeroVoice() {
        return heroVoice.canPerformAction();
    }

    public boolean canUseGlobalChat() {
        return globalChat.canPerformAction();
    }

    public boolean canChangeSubclass() {
        return subclass.canPerformAction();
    }

    public boolean canDropItem() {
        return dropItem.canPerformAction();
    }

    public boolean canUseServerBypass() {
        return serverBypass.canPerformAction();
    }

    public boolean canUseMultiSell() {
        return multiSell.canPerformAction();
    }

    public boolean canPerformTransaction() {
        return transaction.canPerformAction();
    }

    public boolean canManufacture() {
        return manufacture.canPerformAction();
    }

    public boolean canSendMail() {
        return sendMail.canPerformAction();
    }

    public boolean canSelectCharacter() {
        return characterSelect.canPerformAction();
    }

    public boolean canUseItemAuction() {
        return itemAuction.canPerformAction();
    }

    public boolean canPerformPlayerAction() {
        return playerAction.canPerformAction();
    }
}
