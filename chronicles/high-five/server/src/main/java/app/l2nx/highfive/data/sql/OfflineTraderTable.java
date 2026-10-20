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
package app.l2nx.highfive.data.sql;

import static app.l2nx.highfive.repository.LegacyValues.epochMillisOrZero;
import static app.l2nx.highfive.repository.LegacyValues.instantOrNull;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.config.custom.DualboxCheckConfig;
import app.l2nx.highfive.config.custom.OfflineTradeConfig;
import app.l2nx.highfive.data.holders.SellBuffHolder;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.Summon;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.item.recipe.ManufactureItem;
import app.l2nx.highfive.entity.zone.ZoneId;
import app.l2nx.highfive.managers.AntiFeedManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.TradeItem;
import app.l2nx.highfive.network.serverpackets.LeaveWorld;
import app.l2nx.highfive.network.serverpackets.ServerClose;
import app.l2nx.highfive.repository.offline.OfflineTradeRepository;
import app.l2nx.highfive.repository.offline.model.OfflineTrade;
import app.l2nx.highfive.repository.offline.model.OfflineTradeItem;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OfflineTraderTable {
    private static volatile OfflineTraderTable instance;

    private static final Logger ACCOUNTING_LOG = LoggerFactory.getLogger("accounting");

    private final OfflineTradeRepository offlineTradeRepository;

    public OfflineTraderTable(OfflineTradeRepository offlineTradeRepository) {
        this.offlineTradeRepository = offlineTradeRepository;
        instance = this;
    }

    public void storeOffliners() {
        try {
            offlineTradeRepository.deleteAll();
            for (Player pc : World.getPlayers()) {
                try {
                    if (pc.isInStoreMode()
                            && ((pc.getClient() == null) || pc.getClient().isDetached())
                            && isStoreKept(pc.getPrivateStoreType())) {
                        saveStore(pc);
                    }
                } catch (DataAccessException e) {
                    log.warn("Error while saving offline trader {}", pc.getObjectId(), e);
                }
            }

            log.info("Offline traders stored.");
        } catch (DataAccessException e) {
            log.warn("Error while saving offline traders", e);
        }
    }

    public void restoreOfflineTraders() {
        log.info("Loading offline traders...");
        int nTraders = 0;
        try {
            for (OfflineTrade trade : offlineTradeRepository.findAll()) {
                final long time = epochMillisOrZero(trade.startedAt());
                if (OfflineTradeConfig.OFFLINE_MAX_DAYS > 0) {
                    final Calendar cal = ClockBridge.calendar();
                    cal.setTimeInMillis(time);
                    cal.add(Calendar.DAY_OF_YEAR, OfflineTradeConfig.OFFLINE_MAX_DAYS);
                    if (cal.getTimeInMillis() <= ClockBridge.wallMillis()) {
                        continue;
                    }
                }

                final boolean isSellBuff = trade.storeType() == PrivateStoreType.SELL_BUFFS;
                final PrivateStoreType type = isSellBuff ? PrivateStoreType.PACKAGE_SELL : trade.storeType();
                Player player = null;

                try {
                    player = Player.load(trade.characterId());
                    player.setOnlineStatus(true, false);
                    player.setOfflineStartTime(time);

                    if (isSellBuff) {
                        player.setSellingBuffs(true);
                    }

                    player.spawnMe(player.getX(), player.getY(), player.getZ());
                    final List<OfflineTradeItem> items = offlineTradeRepository.findItems(player.getObjectId());
                    switch (type) {
                        case BUY: {
                            for (OfflineTradeItem item : items) {
                                player.getBuyList().addItemByItemId(item.itemId(), item.count(), item.price());
                            }

                            player.getBuyList().setTitle(trade.title());
                            break;
                        }
                        case SELL:
                        case PACKAGE_SELL: {
                            if (player.isSellingBuffs()) {
                                for (OfflineTradeItem item : items) {
                                    player.getSellingBuffs().add(new SellBuffHolder(item.itemId(), item.price()));
                                }
                            } else {
                                for (OfflineTradeItem item : items) {
                                    player.getSellList().addItem(item.itemId(), item.count(), item.price());
                                }
                            }

                            player.getSellList().setTitle(trade.title());
                            player.getSellList().setPackaged(type == PrivateStoreType.PACKAGE_SELL);
                            break;
                        }
                        case MANUFACTURE: {
                            for (OfflineTradeItem item : items) {
                                player.getManufactureItems()
                                        .put(item.itemId(), new ManufactureItem(item.itemId(), item.price()));
                            }

                            player.setStoreName(trade.title());
                            break;
                        }
                        default: {
                            break;
                        }
                    }

                    player.sitDown();
                    if (OfflineTradeConfig.OFFLINE_SET_NAME_COLOR) {
                        player.getAppearance().setNameColor(OfflineTradeConfig.OFFLINE_NAME_COLOR);
                    }

                    player.setPrivateStoreType(type);
                    player.setOnlineStatus(true, true);
                    player.restoreEffects();
                    if (!OfflineTradeConfig.OFFLINE_ABNORMAL_EFFECTS.isEmpty()) {
                        player.startAbnormalVisualEffect(
                                false,
                                OfflineTradeConfig.OFFLINE_ABNORMAL_EFFECTS.get(ClockBridge.random()
                                        .nextInt(OfflineTradeConfig.OFFLINE_ABNORMAL_EFFECTS.size())));
                    }

                    player.broadcastUserInfo();
                    nTraders++;
                } catch (Exception e) {
                    log.warn("Error loading trader: {}", player, e);
                    if (player != null) {
                        Disconnection.of(player).storeAndDeleteWith(LeaveWorld.STATIC_PACKET);
                    }
                }
            }

            World.OFFLINE_TRADE_COUNT = nTraders;
            log.info("Loaded {} offline traders.", nTraders);

            if (!OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME) {
                offlineTradeRepository.deleteAll();
            }
        } catch (DataAccessException e) {
            log.warn("Error while loading offline traders: ", e);
        }
    }

    public synchronized void onTransaction(Player trader, boolean finished) {
        try {
            if (finished) {
                offlineTradeRepository.delete(trader.getObjectId());
            } else if ((trader.getClient() == null) || trader.getClient().isDetached()) {
                saveStore(trader);
            } else {
                offlineTradeRepository.deleteItems(trader.getObjectId());
            }
        } catch (DataAccessException e) {
            log.warn("Error while saving offline trader {}", trader.getObjectId(), e);
        }
    }

    public synchronized void removeTrader(int traderObjId) {
        World.OFFLINE_TRADE_COUNT--;

        try {
            offlineTradeRepository.delete(traderObjId);
        } catch (DataAccessException e) {
            log.warn("Error while removing offline trader {}", traderObjId, e);
        }
    }

    private static boolean isStoreKept(PrivateStoreType type) {
        return switch (type) {
            case BUY, SELL, PACKAGE_SELL -> OfflineTradeConfig.OFFLINE_TRADE_ENABLE;
            case MANUFACTURE -> OfflineTradeConfig.OFFLINE_CRAFT_ENABLE;
            default -> false;
        };
    }

    /**
     * Writes the store of an offline trader with its current item list; a store being set up is not kept.
     */
    private void saveStore(Player trader) {
        final List<OfflineTradeItem> items = new ArrayList<>();
        final String title;
        switch (trader.getPrivateStoreType()) {
            case BUY -> {
                title = trader.getBuyList().getTitle();
                for (TradeItem i : trader.getBuyList().getItems()) {
                    items.add(item(i.getItem().getId(), i.getCount(), i.getPrice()));
                }
            }
            case SELL, PACKAGE_SELL -> {
                title = trader.getSellList().getTitle();
                if (trader.isSellingBuffs()) {
                    for (SellBuffHolder holder : trader.getSellingBuffs()) {
                        items.add(item(holder.getSkillId(), 0, holder.getPrice()));
                    }
                } else {
                    for (TradeItem i : trader.getSellList().getItems()) {
                        items.add(item(i.getObjectId(), i.getCount(), i.getPrice()));
                    }
                }
            }
            case MANUFACTURE -> {
                title = trader.getStoreName();
                for (ManufactureItem i : trader.getManufactureItems().values()) {
                    items.add(item(i.getRecipeId(), 0, i.getCost()));
                }
            }
            default -> {
                offlineTradeRepository.deleteItems(trader.getObjectId());
                return;
            }
        }

        final OfflineTrade trade = OfflineTrade.builder()
                .characterId(trader.getObjectId())
                .startedAt(instantOrNull(trader.getOfflineStartTime()))
                .storeType(trader.isSellingBuffs() ? PrivateStoreType.SELL_BUFFS : trader.getPrivateStoreType())
                .title(title)
                .build();
        offlineTradeRepository.save(trade, items);
    }

    private static OfflineTradeItem item(int itemId, long count, long price) {
        return OfflineTradeItem.builder()
                .itemId(itemId)
                .count(count)
                .price(price)
                .build();
    }

    /**
     * Check whether player is able to enter offline mode.
     * @param player the player to be check.
     * @return {@code true} if the player is allowed to remain as off-line shop.
     */
    private boolean offlineMode(Player player) {
        if ((player == null)
                || player.isInOlympiadMode()
                || player.isRegisteredOnEvent()
                || player.isJailed()
                || (player.getVehicle() != null)) {
            return false;
        }

        boolean canSetShop = false;
        switch (player.getPrivateStoreType()) {
            case SELL:
            case PACKAGE_SELL:
            case BUY: {
                canSetShop = OfflineTradeConfig.OFFLINE_TRADE_ENABLE;
                break;
            }
            case MANUFACTURE: {
                canSetShop = OfflineTradeConfig.OFFLINE_TRADE_ENABLE;
                break;
            }
            default: {
                canSetShop = OfflineTradeConfig.OFFLINE_CRAFT_ENABLE && player.isCrafting();
                break;
            }
        }

        if (OfflineTradeConfig.OFFLINE_MODE_IN_PEACE_ZONE && !player.isInsideZone(ZoneId.PEACE)) {
            canSetShop = false;
        }

        // Check whether client is null or player is already in offline mode.
        final GameClient client = player.getClient();
        if ((client == null) || client.isDetached()) {
            return false;
        }

        return canSetShop;
    }

    /**
     * Manages the disconnection process of offline traders.
     * @param player
     * @return {@code true} when player entered offline mode, otherwise {@code false}
     */
    public boolean enteredOfflineMode(Player player) {
        if (!offlineMode(player)) {
            return false;
        }

        World.OFFLINE_TRADE_COUNT++;

        final GameClient client = player.getClient();
        client.close(ServerClose.STATIC_PACKET);
        if (!DualboxCheckConfig.DUALBOX_COUNT_OFFLINE_TRADERS) {
            AntiFeedManager.getInstance().onDisconnect(client);
        }

        client.setDetached(true);

        player.leaveParty();
        OlympiadManager.getInstance().unRegisterNoble(player);

        // If the Player has Pet, unsummon it.
        Summon pet = player.getSummon();
        if (pet != null) {
            pet.setRestoreSummon(true);
            pet.unSummon(player);
            pet = player.getSummon();

            // Dead pet wasn't unsummoned, broadcast npcinfo changes (pet will be without owner name - means owner
            // offline).
            if (pet != null) {
                pet.broadcastNpcInfo(0);
            }
        }

        if (OfflineTradeConfig.OFFLINE_SET_NAME_COLOR) {
            player.getAppearance().setNameColor(OfflineTradeConfig.OFFLINE_NAME_COLOR);
            player.broadcastUserInfo();
        }

        if (player.getOfflineStartTime() == 0) {
            player.setOfflineStartTime(ClockBridge.wallMillis());
        }

        // Store trade on exit, if realtime saving is enabled.
        if (OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME) {
            onTransaction(player, false);
        }

        player.storeMe();
        ACCOUNTING_LOG.info("Entering offline mode, {}", client);

        if (!OfflineTradeConfig.OFFLINE_ABNORMAL_EFFECTS.isEmpty()) {
            player.startAbnormalVisualEffect(
                    true,
                    OfflineTradeConfig.OFFLINE_ABNORMAL_EFFECTS.get(
                            ClockBridge.random().nextInt(OfflineTradeConfig.OFFLINE_ABNORMAL_EFFECTS.size())));
        }

        return true;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static OfflineTraderTable getInstance() {
        final OfflineTraderTable current = instance;
        if (current == null) {
            throw new IllegalStateException("OfflineTraderTable is used before the Spring context created it.");
        }
        return current;
    }
}
