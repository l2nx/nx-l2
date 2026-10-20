/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository.offline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import app.l2nx.database.JdbcTimes;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.integration.repository.RepositoryTest;
import app.l2nx.highfive.repository.offline.OfflineTradeRepository;
import app.l2nx.highfive.repository.offline.model.OfflineTrade;
import app.l2nx.highfive.repository.offline.model.OfflineTradeItem;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// findAll and deleteAll cover the whole tables, so the tests must not interleave.
@ResourceLock("character_offline_trades")
class OfflineTradeRepositoryTest extends RepositoryTest {
    private final OfflineTradeRepository repository = new OfflineTradeRepository(JDBC, JDBC_TEMPLATE, registry);

    @Nested
    class FindAll {
        @Test
        void findAll_shouldReturnHandlesMillisAndStoreType() {
            JDBC.sql("DELETE FROM character_offline_trades").update();
            final UUID trader = insertCharacter();
            final UUID untitled = insertCharacter();
            insertTrade(trader, "SELL_BUFFS", "Buffs");
            insertTrade(untitled, "BUY", null);

            final List<OfflineTrade> actual = repository.findAll();

            assertThat(actual)
                    .containsExactlyInAnyOrder(
                            new OfflineTrade(registry.handleOf(trader), NOW, PrivateStoreType.SELL_BUFFS, "Buffs"),
                            new OfflineTrade(registry.handleOf(untitled), NOW, PrivateStoreType.BUY, null));
        }
    }

    @Nested
    class FindItems {
        @Test
        void findItems_shouldResolveItemInstancesToHandles_andKeepOtherIds() {
            final UUID trader = insertCharacter();
            final UUID item = insertItem(trader, 57, 1000);
            insertTradeItem(trader, item, null, 1000, 5);
            insertTradeItem(trader, null, 1204, 0, 100);

            final List<OfflineTradeItem> actual = repository.findItems(registry.handleOf(trader));

            assertThat(actual)
                    .containsExactly(
                            new OfflineTradeItem(registry.handleOf(item), 1000, 5), new OfflineTradeItem(1204, 0, 100));
        }
    }

    @Nested
    class Save {
        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = PrivateStoreType.class,
                names = {"SELL", "PACKAGE_SELL"})
        void save_shouldStoreItemInstances_whenStoreSellsItems(PrivateStoreType storeType) {
            final UUID trader = insertCharacter();
            final UUID item = insertItem(trader, 57, 1000);
            final int traderId = registry.handleOf(trader);

            repository.save(
                    new OfflineTrade(traderId, NOW, storeType, "Shop"),
                    List.of(new OfflineTradeItem(registry.handleOf(item), 10, 7)));

            assertThat(trades(trader)).containsExactly(tuple(NOW, storeType.name(), "Shop"));
            assertThat(tradeItems(trader)).containsExactly(tuple(item, null, 10L, 7L));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(
                value = PrivateStoreType.class,
                names = {"BUY", "SELL_BUFFS", "MANUFACTURE"})
        void save_shouldStoreTargetIds_whenStoreListsTemplatesSkillsOrRecipes(PrivateStoreType storeType) {
            final UUID trader = insertCharacter();
            final int traderId = registry.handleOf(trader);

            repository.save(
                    new OfflineTrade(traderId, NOW, storeType, ""), List.of(new OfflineTradeItem(1204, 0, 100)));

            assertThat(trades(trader)).containsExactly(tuple(NOW, storeType.name(), null));
            assertThat(tradeItems(trader)).containsExactly(tuple(null, 1204, 0L, 100L));
        }

        @Test
        void save_shouldReplaceItemsAndTrade_whenTradeStored() {
            final UUID trader = insertCharacter();
            insertTrade(trader, "BUY", "Old");
            insertTradeItem(trader, null, 57, 1, 1);
            final int traderId = registry.handleOf(trader);
            final OfflineTrade trade = new OfflineTrade(traderId, NOW, PrivateStoreType.BUY, "New");
            final List<OfflineTradeItem> items = List.of(new OfflineTradeItem(1864, 3, 9));

            TRANSACTIONS.executeWithoutResult(status -> repository.save(trade, items));

            assertThat(trades(trader)).containsExactly(tuple(NOW, "BUY", "New"));
            assertThat(tradeItems(trader)).containsExactly(tuple(null, 1864, 3L, 9L));
        }
    }

    @Nested
    class DeleteItems {
        @Test
        void deleteItems_shouldKeepTheTrade() {
            final UUID trader = insertCharacter();
            insertTrade(trader, "BUY", null);
            insertTradeItem(trader, null, 57, 1, 1);

            repository.deleteItems(registry.handleOf(trader));

            assertThat(tradeItems(trader)).isEmpty();
            assertThat(trades(trader)).hasSize(1);
        }
    }

    @Nested
    class Delete {
        @Test
        void delete_shouldRemoveTradeAndItemsOfThatCharacter() {
            final UUID trader = insertCharacter();
            final UUID other = insertCharacter();
            insertTrade(trader, "BUY", null);
            insertTradeItem(trader, null, 57, 1, 1);
            insertTrade(other, "BUY", null);

            repository.delete(registry.handleOf(trader));

            assertThat(trades(trader)).isEmpty();
            assertThat(tradeItems(trader)).isEmpty();
            assertThat(trades(other)).hasSize(1);
        }
    }

    @Nested
    class DeleteAll {
        @Test
        void deleteAll_shouldEmptyBothTables() {
            final UUID trader = insertCharacter();
            insertTrade(trader, "BUY", null);
            insertTradeItem(trader, null, 57, 1, 1);

            repository.deleteAll();

            assertThat(count("character_offline_trades")).isZero();
            assertThat(count("character_offline_trade_items")).isZero();
        }
    }

    private static void insertTrade(UUID trader, String storeType, String title) {
        JDBC.sql("""
            INSERT INTO character_offline_trades (character_id, started_at, store_type, title)
            VALUES (?, ?, ?, ?)\
            """)
                .param(trader)
                .param(JdbcTimes.utc(NOW))
                .param(storeType)
                .param(title)
                .update();
    }

    private static void insertTradeItem(UUID trader, UUID itemId, Integer targetId, long count, long price) {
        JDBC.sql("""
            INSERT INTO character_offline_trade_items (character_id, item_id, target_id, item_count, price)
            VALUES (?, ?, ?, ?, ?)\
            """)
                .param(trader)
                .param(itemId)
                .param(targetId)
                .param(count)
                .param(price)
                .update();
    }

    private static List<Tuple> trades(UUID trader) {
        return JDBC.sql("SELECT started_at, store_type, title FROM character_offline_trades WHERE character_id = ?")
                .param(trader)
                .query((rs, rowNum) ->
                        tuple(JdbcTimes.instant(rs, "started_at"), rs.getString("store_type"), rs.getString("title")))
                .list();
    }

    private static List<Tuple> tradeItems(UUID trader) {
        return JDBC.sql("""
            SELECT item_id, target_id, item_count, price
            FROM character_offline_trade_items
            WHERE character_id = ?
            ORDER BY id\
            """)
                .param(trader)
                .query((rs, rowNum) -> tuple(
                        rs.getObject("item_id", UUID.class),
                        rs.getObject("target_id", Integer.class),
                        rs.getLong("item_count"),
                        rs.getLong("price")))
                .list();
    }

    private static int count(String table) {
        return JDBC.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
