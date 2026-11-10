/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.data.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.character.CharacterLoader;
import app.l2nx.highfive.data.holders.SellBuffHolder;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.holders.TradeList;
import app.l2nx.highfive.repository.offline.OfflineTradeRepository;
import app.l2nx.highfive.repository.offline.model.OfflineTrade;
import app.l2nx.highfive.repository.offline.model.OfflineTradeItem;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfflineTraderTableTest {
    private static final int TRADER_ID = 268_435_459;
    private static final long STARTED_AT = 1_760_000_000_000L;

    private final OfflineTradeRepository trades = mock(OfflineTradeRepository.class);
    private final OfflineTraderTable table = new OfflineTraderTable(trades, mock(CharacterLoader.class));
    private final Player trader = mock(Player.class);

    @Nested
    class OnTransaction {
        @Test
        void onTransaction_shouldDeleteStore_whenTradeFinished() {
            when(trader.getObjectId()).thenReturn(TRADER_ID);

            table.onTransaction(trader, true);

            verify(trades).delete(TRADER_ID);
            verify(trades, never()).save(any(), anyCollection());
        }

        @Test
        void onTransaction_shouldDeleteItemsOnly_whenTraderIsOnline() {
            when(trader.getObjectId()).thenReturn(TRADER_ID);
            when(trader.getClient()).thenReturn(mock(GameClient.class));

            table.onTransaction(trader, false);

            verify(trades).deleteItems(TRADER_ID);
            verify(trades, never()).save(any(), anyCollection());
        }

        @Test
        void onTransaction_shouldStoreBuffsAsSellBuffsStore_whenBuffSellerIsOffline() {
            final TradeList sellList = mock(TradeList.class);
            when(sellList.getTitle()).thenReturn("Buffs");
            when(trader.getObjectId()).thenReturn(TRADER_ID);
            when(trader.getPrivateStoreType()).thenReturn(PrivateStoreType.PACKAGE_SELL);
            when(trader.isSellingBuffs()).thenReturn(true);
            when(trader.getSellList()).thenReturn(sellList);
            when(trader.getSellingBuffs()).thenReturn(List.of(new SellBuffHolder(1204, 5000)));
            when(trader.getOfflineStartTime()).thenReturn(STARTED_AT);

            table.onTransaction(trader, false);

            final ArgumentCaptor<OfflineTrade> trade = ArgumentCaptor.forClass(OfflineTrade.class);
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<Collection<OfflineTradeItem>> items = ArgumentCaptor.forClass(Collection.class);
            verify(trades).save(trade.capture(), items.capture());
            assertThat(trade.getValue())
                    .isEqualTo(OfflineTrade.builder()
                            .characterId(TRADER_ID)
                            .startedAt(Instant.ofEpochMilli(STARTED_AT))
                            .storeType(PrivateStoreType.SELL_BUFFS)
                            .title("Buffs")
                            .build());
            assertThat(items.getValue())
                    .containsExactly(OfflineTradeItem.builder()
                            .itemId(1204)
                            .count(0)
                            .price(5000)
                            .build());
        }
    }
}
