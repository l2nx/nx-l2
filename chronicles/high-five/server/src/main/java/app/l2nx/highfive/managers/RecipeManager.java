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
package app.l2nx.highfive.managers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.GeneralConfig;
import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.data.enums.StatType;
import app.l2nx.highfive.data.holders.RecipeHolder;
import app.l2nx.highfive.data.holders.RecipeStatHolder;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.RecipeData;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.item.recipe.ManufactureItem;
import app.l2nx.highfive.entity.item.recipe.RecipeItemInfo;
import app.l2nx.highfive.entity.item.recipe.RecipeList;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.mechanics.skill.CommonSkill;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.mechanics.stats.Stat;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ActionFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ItemList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.MagicSkillUse;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RecipeBookItemList;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RecipeItemMakeInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.RecipeShopItemInfo;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SetupGauge;
import app.l2nx.highfive.network.protocol.p267.serverpackets.StatusUpdate;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.taskmanagers.GameTimeTaskManager;
import app.l2nx.highfive.threads.ThreadPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

public class RecipeManager {
    protected static final Map<Integer, RecipeItemMaker> activeMakers = new ConcurrentHashMap<>();

    protected RecipeManager() {
        // Prevent external initialization.
    }

    public void requestBookOpen(Player player, boolean isDwarvenCraft) {
        // Check if player is trying to alter recipe book while engaged in manufacturing.
        if (!activeMakers.containsKey(player.getObjectId())) {
            final RecipeBookItemList response = new RecipeBookItemList(isDwarvenCraft, player.getMaxMp());
            response.addRecipes(isDwarvenCraft ? player.getDwarvenRecipeBook() : player.getCommonRecipeBook());
            player.sendPacket(response);
            return;
        }

        player.sendPacket(SystemMessageId.YOU_MAY_NOT_ALTER_YOUR_RECIPE_BOOK_WHILE_ENGAGED_IN_MANUFACTURING);
    }

    public void requestMakeItemAbort(Player player) {
        activeMakers.remove(player.getObjectId()); // TODO: anything else here?
    }

    public void requestManufactureItem(Player manufacturer, int recipeListId, Player player) {
        final RecipeList recipeList = RecipeData.getInstance().getValidRecipeList(player, recipeListId);
        if (recipeList == null) {
            return;
        }

        if (!manufacturer.getDwarvenRecipeBook().contains(recipeList)
                && !manufacturer.getCommonRecipeBook().contains(recipeList)) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "Warning!! Character " + player.getName() + " of account " + player.getAccountName()
                            + " sent a false recipe id.",
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        // Check if manufacturer is under manufacturing store or private store.
        if (PlayerConfig.ALT_GAME_CREATION && activeMakers.containsKey(manufacturer.getObjectId())) {
            player.sendPacket(
                    SystemMessageId
                            .PLEASE_CLOSE_THE_SETUP_WINDOW_FOR_YOUR_PRIVATE_MANUFACTURING_STORE_OR_PRIVATE_STORE_AND_TRY_AGAIN);
            return;
        }

        final RecipeItemMaker maker = new RecipeItemMaker(manufacturer, recipeList, player);
        if (maker.valid) {
            if (PlayerConfig.ALT_GAME_CREATION) {
                activeMakers.put(manufacturer.getObjectId(), maker);
                ThreadPool.schedule(maker, 100);
            } else {
                maker.run();
            }
        }
    }

    public void requestMakeItem(Player player, int recipeListId) {
        // Check if player is trying to operate a private store or private workshop while engaged in combat.
        if ((player.isInCombat() && !PlayerConfig.CRAFTING_IN_COMBAT) || player.isInDuel()) {
            player.sendPacket(
                    SystemMessageId
                            .WHILE_YOU_ARE_ENGAGED_IN_COMBAT_YOU_CANNOT_OPERATE_A_PRIVATE_STORE_OR_PRIVATE_WORKSHOP);
            return;
        }

        final RecipeList recipeList = RecipeData.getInstance().getValidRecipeList(player, recipeListId);
        if (recipeList == null) {
            return;
        }

        if (!player.getDwarvenRecipeBook().contains(recipeList)
                && !player.getCommonRecipeBook().contains(recipeList)) {
            PunishmentManager.handleIllegalPlayerAction(
                    player,
                    "Warning!! Character " + player.getName() + " of account " + player.getAccountName()
                            + " sent a false recipe id.",
                    GeneralConfig.DEFAULT_PUNISH);
            return;
        }

        // Check if player is busy (possible if alt game creation is enabled).
        if (PlayerConfig.ALT_GAME_CREATION && activeMakers.containsKey(player.getObjectId())) {
            final SystemMessage sm = new SystemMessage(SystemMessageId.S2_S1);
            sm.addItemName(recipeList.getItemId());
            sm.addString("You are busy creating.");
            player.sendPacket(sm);
            return;
        }

        final RecipeItemMaker maker = new RecipeItemMaker(player, recipeList, player);
        if (maker.valid) {
            if (PlayerConfig.ALT_GAME_CREATION) {
                activeMakers.put(player.getObjectId(), maker);
                ThreadPool.schedule(maker, 100);
            } else {
                maker.run();
            }
        }
    }

    @Slf4j
    private static class RecipeItemMaker implements Runnable {
        protected boolean valid;
        protected List<RecipeItemInfo> items = null;
        protected final RecipeList recipeList;
        protected final Player player; // "crafter"
        protected final Player target; // "customer"
        protected final Skill skill;
        protected final int skillId;
        protected final int skillLevel;
        protected int creationPasses = 1;
        protected int itemGrab;
        protected int exp = -1;
        protected int sp = -1;
        protected long price;
        protected int totalItems;
        protected int delay;

        public RecipeItemMaker(Player pPlayer, RecipeList pRecipeList, Player pTarget) {
            player = pPlayer;
            target = pTarget;
            recipeList = pRecipeList;
            valid = false;
            skillId = recipeList.isDwarvenRecipe()
                    ? CommonSkill.CREATE_DWARVEN.getId()
                    : CommonSkill.CREATE_COMMON.getId();
            skillLevel = player.getSkillLevel(skillId);
            skill = player.getKnownSkill(skillId);
            player.setCrafting(true);

            if (player.isAlikeDead()) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                abort();
                return;
            }

            if (target.isAlikeDead()) {
                target.sendPacket(ActionFailed.STATIC_PACKET);
                abort();
                return;
            }

            if (target.isProcessingTransaction()) {
                target.sendPacket(ActionFailed.STATIC_PACKET);
                abort();
                return;
            }

            if (player.isProcessingTransaction()) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                abort();
                return;
            }

            // Validate recipe list.
            if (recipeList.getRecipes().length == 0) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                abort();
                return;
            }

            // Validate skill level.
            if (recipeList.getLevel() > skillLevel) {
                player.sendPacket(ActionFailed.STATIC_PACKET);
                abort();
                return;
            }

            // Check that customer can afford to pay for creation services.
            if (player != target) {
                final ManufactureItem item = player.getManufactureItems().get(recipeList.getId());
                if (item != null) {
                    price = item.getCost();
                    if (target.getAdena() < price) // check price
                    {
                        target.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
                        abort();
                        return;
                    }
                }
            }

            // Make temporary items.
            items = listItems(false);
            if (items == null) {
                abort();
                return;
            }

            for (RecipeItemInfo i : items) {
                totalItems += i.getQuantity();
            }

            // Initial statUse checks.
            if (!calculateStatUse(false, false)) {
                abort();
                return;
            }

            // Initial AltStatChange checks.
            if (PlayerConfig.ALT_GAME_CREATION) {
                calculateAltStatChange();
            }

            updateMakeInfo(true);
            updateCurMp();
            updateCurLoad();

            player.setCrafting(false);
            valid = true;
        }

        @Override
        public void run() {
            if (!PlayerConfig.IS_CRAFTING_ENABLED) {
                target.sendMessage("Item creation is currently disabled.");
                abort();
                return;
            }

            if ((player == null) || (target == null)) {
                log.warn("player or target == null (disconnected?), aborting{}{}", target, player);
                abort();
                return;
            }

            // if (!_player.isOnline() || !_target.isOnline())
            // {
            // LOGGER.warning("Player or target is not online, aborting " + _target + _player);
            // abort();
            // return;
            // }

            if (PlayerConfig.ALT_GAME_CREATION && !activeMakers.containsKey(player.getObjectId())) {
                if (target != player) {
                    target.sendMessage("Manufacture aborted");
                    player.sendMessage("Manufacture aborted");
                } else {
                    player.sendMessage("Item creation aborted");
                }

                abort();
                return;
            }

            if (PlayerConfig.ALT_GAME_CREATION && !items.isEmpty()) {
                if (!calculateStatUse(true, true)) {
                    return; // check stat use
                }

                updateCurMp(); // Update craft window mp bar.
                grabSomeItems(); // Grab (equip) some more items with a nice msg to player.

                // If still not empty, schedule another pass.
                if (!items.isEmpty()) {
                    delay = (int) (PlayerConfig.ALT_GAME_CREATION_SPEED
                            * player.getMReuseRate(skill)
                            * GameTimeTaskManager.TICKS_PER_SECOND
                            * GameTimeTaskManager.MILLIS_IN_TICK);

                    // FIXME: please fix this packet to show crafting animation (somebody)
                    player.broadcastSkillPacket(new MagicSkillUse(player, skillId, skillLevel, delay, 0), player);
                    player.sendPacket(new SetupGauge(player.getObjectId(), 0, delay));
                    ThreadPool.schedule(this, 100 + delay);
                } else {
                    // For alt mode, sleep delay msec before finishing.
                    player.sendPacket(new SetupGauge(player.getObjectId(), 0, delay));

                    try {
                        Thread.sleep(delay);
                    } catch (Exception e) {
                        // Ignore.
                    } finally {
                        finishCrafting();
                    }
                }

            } // For old craft mode just finish.
            else {
                finishCrafting();
            }
        }

        private void finishCrafting() {
            if (!PlayerConfig.ALT_GAME_CREATION) {
                calculateStatUse(false, true);
            }

            // First take adena for manufacture.
            if ((target != player)
                    && (price > 0)
                    && (target.transferItem(
                                    ItemProcessType.TRANSFER,
                                    target.getInventory().getAdenaInstance().getObjectId(),
                                    price,
                                    player.getInventory(),
                                    player)
                            == null)) // Customer must pay for services.
            {
                target.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_ENOUGH_ADENA);
                abort();
                return;
            }

            items = listItems(true); // This line actually takes materials from inventory.
            if (items != null) {
                final boolean success = ClockBridge.random().nextInt(100) < recipeList.getSuccessRate();
                if (success) {
                    rewardPlayer();
                } else {
                    if (target != player) {
                        SystemMessage msg = new SystemMessage(
                                SystemMessageId.YOUR_ATTEMPT_TO_CREATE_S2_FOR_C1_AT_THE_PRICE_OF_S3_ADENA_HAS_FAILED);
                        msg.addString(target.getName());
                        msg.addItemName(recipeList.getItemId());
                        msg.addLong(price);
                        player.sendPacket(msg);
                        msg = new SystemMessage(SystemMessageId.C1_HAS_FAILED_TO_CREATE_S2_AT_THE_PRICE_OF_S3_ADENA);
                        msg.addString(player.getName());
                        msg.addItemName(recipeList.getItemId());
                        msg.addLong(price);
                        target.sendPacket(msg);
                    } else {
                        target.sendPacket(SystemMessageId.YOU_FAILED_AT_MIXING_THE_ITEM);
                    }
                }

                // Craft window counts come from the client inventory, so it is sent directly, sendItemList is delayed.
                // The pending item added update goes first, otherwise it arrives after the list and duplicates the
                // item.
                target.flushInventoryUpdate();
                target.sendPacket(new ItemList(target, false));
                updateMakeInfo(success);
            }

            // update load and mana bar of craft window
            updateCurMp();
            updateCurLoad();
            activeMakers.remove(player.getObjectId());
            player.setCrafting(false);
        }

        private void updateMakeInfo(boolean success) {
            if (target == player) {
                target.sendPacket(new RecipeItemMakeInfo(recipeList.getId(), target, success));
            } else {
                target.sendPacket(new RecipeShopItemInfo(player, recipeList.getId()));
            }
        }

        private void updateCurLoad() {
            final StatusUpdate su = new StatusUpdate(target);
            su.addAttribute(StatusUpdate.CUR_LOAD, target.getCurrentLoad());
            target.sendPacket(su);
        }

        private void updateCurMp() {
            final StatusUpdate su = new StatusUpdate(target);
            su.addAttribute(StatusUpdate.CUR_MP, (int) target.getCurrentMp());
            target.sendPacket(su);
        }

        private void grabSomeItems() {
            int grabItems = itemGrab;
            while ((grabItems > 0) && !items.isEmpty()) {
                final RecipeItemInfo item = items.get(0);
                final int count = item.getQuantity() >= grabItems ? grabItems : item.getQuantity();
                item.setQuantity(item.getQuantity() - count);
                if (item.getQuantity() <= 0) {
                    items.remove(0);
                } else {
                    items.set(0, item);
                }

                grabItems -= count;
                if (target == player) {
                    final SystemMessage sm = new SystemMessage(SystemMessageId.EQUIPPED_S1_S2);
                    sm.addLong(count);
                    sm.addItemName(item.getItemId());
                    player.sendPacket(sm);
                } else {
                    target.sendMessage(
                            "Manufacturer " + player.getName() + " used " + count + " " + item.getItemName());
                }
            }
        }

        // AltStatChange parameters make their effect here.
        private void calculateAltStatChange() {
            itemGrab = skillLevel;
            for (RecipeStatHolder altStatChange : recipeList.getAltStatChange()) {
                if (altStatChange.getType() == StatType.XP) {
                    exp = altStatChange.getValue();
                } else if (altStatChange.getType() == StatType.SP) {
                    sp = altStatChange.getValue();
                } else if (altStatChange.getType() == StatType.GIM) {
                    itemGrab *= altStatChange.getValue();
                }
            }

            // Determine number of creation passes needed.
            creationPasses = (totalItems / itemGrab) + ((totalItems % itemGrab) != 0 ? 1 : 0);
            if (creationPasses < 1) {
                creationPasses = 1;
            }
        }

        // StatUse
        private boolean calculateStatUse(boolean isWait, boolean isReduce) {
            boolean ret = true;
            for (RecipeStatHolder statUse : recipeList.getStatUse()) {
                final double modifiedValue = statUse.getValue() / creationPasses;
                if (statUse.getType() == StatType.HP) {
                    // we do not want to kill the player, so its CurrentHP must be greater than the reduce value
                    if (player.getCurrentHp() <= modifiedValue) {
                        // rest (wait for HP)
                        if (PlayerConfig.ALT_GAME_CREATION && isWait) {
                            player.sendPacket(new SetupGauge(player.getObjectId(), 0, delay));
                            ThreadPool.schedule(this, 100 + delay);
                        } else {
                            target.sendPacket(SystemMessageId.NOT_ENOUGH_HP);
                            abort();
                        }

                        ret = false;
                    } else if (isReduce) {
                        player.reduceCurrentHp(modifiedValue, player, skill);
                    }
                } else if (statUse.getType() == StatType.MP) {
                    if (player.getCurrentMp() < modifiedValue) {
                        // rest (wait for MP)
                        if (PlayerConfig.ALT_GAME_CREATION && isWait) {
                            player.sendPacket(new SetupGauge(player.getObjectId(), 0, delay));
                            ThreadPool.schedule(this, 100 + delay);
                        } else {
                            target.sendPacket(SystemMessageId.NOT_ENOUGH_MP);
                            abort();
                        }

                        ret = false;
                    } else if (isReduce) {
                        player.reduceCurrentMp(modifiedValue);
                    }
                } else {
                    // there is an unknown StatUse value
                    target.sendMessage("Recipe error!!!, please tell this to your GM.");
                    ret = false;
                    abort();
                }
            }

            return ret;
        }

        private List<RecipeItemInfo> listItems(boolean remove) {
            final RecipeHolder[] recipes = recipeList.getRecipes();
            final Inventory inv = target.getInventory();
            final List<RecipeItemInfo> materials = new ArrayList<>();
            SystemMessage sm;
            for (RecipeHolder recipe : recipes) {
                if (recipe.quantity() > 0) {
                    final Item item = inv.getItemByItemId(recipe.itemId());
                    final long itemQuantityAmount = item == null ? 0 : item.getCount();

                    // check materials
                    if (itemQuantityAmount < recipe.quantity()) {
                        sm = new SystemMessage(SystemMessageId.YOU_ARE_MISSING_S2_S1_REQUIRED_TO_CREATE_THAT);
                        sm.addItemName(recipe.itemId());
                        sm.addLong(recipe.quantity() - itemQuantityAmount);
                        target.sendPacket(sm);

                        abort();
                        return null;
                    }

                    // make new temporary object, just for counting purposes
                    materials.add(new RecipeItemInfo(item, recipe.quantity()));
                }
            }

            if (remove) {
                for (RecipeItemInfo tmp : materials) {
                    inv.destroyItemByItemId(ItemProcessType.FEE, tmp.getItemId(), tmp.getQuantity(), target, player);
                    if (tmp.getQuantity() > 1) {
                        sm = new SystemMessage(SystemMessageId.S2_S1_HAS_DISAPPEARED);
                        sm.addItemName(tmp.getItemId());
                        sm.addLong(tmp.getQuantity());
                    } else {
                        sm = new SystemMessage(SystemMessageId.S1_HAS_DISAPPEARED);
                        sm.addItemName(tmp.getItemId());
                    }

                    target.sendPacket(sm);
                }
            }

            return materials;
        }

        private void abort() {
            updateMakeInfo(false);
            player.setCrafting(false);
            activeMakers.remove(player.getObjectId());
        }

        private void rewardPlayer() {
            final int rareProdId = recipeList.getRareItemId();
            int itemId = recipeList.getItemId();
            int itemCount = recipeList.getCount();
            final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);

            // check that the current recipe has a rare production or not
            if ((rareProdId != -1) && ((rareProdId == itemId) || PlayerConfig.CRAFT_MASTERWORK)) {
                if (ClockBridge.random().nextInt(100)
                        < (recipeList.getRarity() * PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE)) {
                    itemId = rareProdId;
                    itemCount = recipeList.getRareCount();
                }
            }

            target.getInventory().addItem(ItemProcessType.CRAFT, itemId, itemCount, target, player);

            // inform customer of earned item
            SystemMessage sm = null;
            if (target != player) {
                // inform manufacturer of earned profit
                if (itemCount == 1) {
                    sm = new SystemMessage(
                            SystemMessageId.S2_HAS_BEEN_CREATED_FOR_C1_AFTER_THE_PAYMENT_OF_S3_ADENA_WAS_RECEIVED);
                    sm.addString(target.getName());
                    sm.addItemName(itemId);
                    sm.addLong(price);
                    player.sendPacket(sm);

                    sm = new SystemMessage(SystemMessageId.C1_CREATED_S2_AFTER_RECEIVING_S3_ADENA);
                    sm.addString(player.getName());
                    sm.addItemName(itemId);
                    sm.addLong(price);
                    target.sendPacket(sm);
                } else {
                    sm = new SystemMessage(SystemMessageId.S2_S3_HAVE_BEEN_CREATED_FOR_C1_AT_THE_PRICE_OF_S4_ADENA);
                    sm.addString(target.getName());
                    sm.addInt(itemCount);
                    sm.addItemName(itemId);
                    sm.addLong(price);
                    player.sendPacket(sm);

                    sm = new SystemMessage(SystemMessageId.C1_CREATED_S2_S3_AT_THE_PRICE_OF_S4_ADENA);
                    sm.addString(player.getName());
                    sm.addInt(itemCount);
                    sm.addItemName(itemId);
                    sm.addLong(price);
                    target.sendPacket(sm);
                }
            }

            if (itemCount > 1) {
                sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S2_S1_S);
                sm.addItemName(itemId);
                sm.addLong(itemCount);
            } else {
                sm = new SystemMessage(SystemMessageId.YOU_HAVE_EARNED_S1);
                sm.addItemName(itemId);
            }

            target.sendPacket(sm);

            if (PlayerConfig.ALT_GAME_CREATION) {
                final int recipeLevel = recipeList.getLevel();
                if (exp < 0) {
                    exp = template.getReferencePrice() * itemCount;
                    exp /= recipeLevel;
                }

                if (sp < 0) {
                    sp = exp / 10;
                }

                if (itemId == rareProdId) {
                    exp *= PlayerConfig.ALT_GAME_CREATION_RARE_XPSP_RATE;
                    sp *= PlayerConfig.ALT_GAME_CREATION_RARE_XPSP_RATE;
                }

                if (exp < 0) {
                    exp = 0;
                }

                if (sp < 0) {
                    sp = 0;
                }

                for (int i = skillLevel; i > recipeLevel; i--) {
                    exp /= 4;
                    sp /= 4;
                }

                // Added multiplication of Creation speed with XP/SP gain slower crafting -> more XP,
                // faster crafting -> less XP you can use ALT_GAME_CREATION_XP_RATE/SP to modify XP/SP gained (default =
                // 1)
                player.addExpAndSp(
                        (int) player.calcStat(
                                Stat.EXPSP_RATE,
                                exp * PlayerConfig.ALT_GAME_CREATION_XP_RATE * PlayerConfig.ALT_GAME_CREATION_SPEED,
                                null,
                                null),
                        (int) player.calcStat(
                                Stat.EXPSP_RATE,
                                sp * PlayerConfig.ALT_GAME_CREATION_SP_RATE * PlayerConfig.ALT_GAME_CREATION_SPEED,
                                null,
                                null));
            }

            updateMakeInfo(true); // success
        }
    }

    public static RecipeManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final RecipeManager INSTANCE = new RecipeManager();
    }
}
