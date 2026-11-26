# Trade

Item exchange between players and with NPCs: trade window, private stores, NPC shops, multisell.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### trade.exchange — Player-to-player trade

ID: `trade.exchange` · Shared: **engine**

Two players trade items and adena in a trade window: request, accept, add items (with counts), confirm by both
sides, cancel; items move atomically on confirmation. Sub-features: request/answer · add item · double confirm ·
distance and state checks · cancel.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                               |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | same model; `TradeList` is longer (1043 lines vs 883 in HF) because of backported checks |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                                                 |
| Essence   | yes     | mobius-ess, l2emu-ess                | same flow; trade request and list recognise fake players (`FakePlayerData`)              |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{TradeRequest,AnswerTradeRequest,AddTradeItem,TradeDone}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/holders/{TradeList,TradeItem,RequestTrade}.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/TradeRequest.java`, `mobius-il:java/org/l2jmobius/gameserver/network/holders/TradeList.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/TradeRequest.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/holders/TradeList.java`

Packets

| Chronicle | Client → server                                                                       | Server → client                                                                                                                                                                                   |
| --------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `TradeRequest` `15`, `AnswerTradeRequest` `44`, `AddTradeItem` `16`, `TradeDone` `17` | `SendTradeRequest` `5E`, `TradeStart` `1E`, `TradeOwnAdd` `20`, `TradeOtherAdd` `21`, `TradeUpdate` `74`, `TradePressOwnOk` `75`, `TradePressOtherOk` `7C`, `TradeDone` `22`                      |
| High Five | `TradeRequest` `1A`, `AnswerTradeRequest` `55`, `AddTradeItem` `1B`, `TradeDone` `1C` | `SendTradeRequest` `70`, `TradeStart` `14`, `TradeOwnAdd` `1A`, `TradeOtherAdd` `1B`, `TradeUpdate` `81`, `TradePressOwnOk` `53`, `TradePressOtherOk` `82`, `TradeDone` `1C`, `TradeStartOk` `15` |
| Essence   | `TradeRequest` `1A`, `AnswerTradeRequest` `55`, `AddTradeItem` `1B`, `TradeDone` `1C` | `SendTradeRequest` `70`, `TradeStart` `14`, `TradeOwnAdd` `1A`, `TradeOtherAdd` `1B`, `TradeUpdate` `81`, `TradePressOwnOk` `53`, `TradePressOtherOk` `82`, `TradeDone` `1C`, `TradeStartOk` `15` |

Notes: logic is identical by the `ZoneId`/config references diff; only the Essence fake-player hook differs. The
Interlude column is confirmed against aCis and L2jFrozen (same four client packets).

Known fixes: `l2j-server@2b55d996` (2010-11-16, exploit-dupe) — lil exploit fix; `hellas@45a669b4` (2012-12-07, exploit-dupe) — Possible tarde exploit fix,Added block list for tardes!; `l2jfrozen@5cb5fe69` (2011-04-04, exploit-dupe) — Fix over Sell/Buy cast exploit, new loginserver Database timeout to 7 days by default.

### trade.private-store — Private store

ID: `trade.private-store` · Shared: **partial**

A player opens a store in town: sell list, buy list, package sale (all items for one price), store message, buyer
side purchase and seller-side sale into a buy list; range limit between stores and a ban of stores in zones.
Sub-features: sell · buy · package sale · store message · range between stores · restricted zones.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                           |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | no whole-store message packet; a "package sale" flag lives inside `SetPrivateStoreListSell`                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `SetPrivateStoreWholeMsg` + `ExPrivateStoreSetWholeMsg`, `ExPrivateStorePackageMsg`                                        |
| Essence   | differs | mobius-ess, l2emu-ess                | adds result packets (`ExPrivateStoreSellingResult`, `ExPrivateStoreBuyingResult`) and registers each sale into the store-history log |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{SetPrivateStoreListSell,SetPrivateStoreListBuy,RequestPrivateStoreBuy,RequestPrivateStoreSell,RequestPrivateStoreManageSell,RequestPrivateStoreManageBuy,RequestPrivateStoreQuitSell,RequestPrivateStoreQuitBuy,SetPrivateStoreMsgSell,SetPrivateStoreMsgBuy,SetPrivateStoreWholeMsg}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/enums/player/PrivateStoreType.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/PrivateStoreRange.ini`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/SetPrivateStoreListSell.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/enums/player/PrivateStoreType.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/SetPrivateStoreListSell.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExPrivateStoreSellingResult.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                              | Server → client                                                                                                                                                                                                                                                                                                                                                 |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestPrivateStoreManageSell` `73`, `SetPrivateStoreListSell` `74`, `RequestPrivateStoreQuitSell` `76`, `SetPrivateStoreMsgSell` `77`, `RequestPrivateStoreBuy` `79`, `RequestPrivateStoreManageBuy` `90`, `SetPrivateStoreListBuy` `91`, `RequestPrivateStoreQuitBuy` `93`, `SetPrivateStoreMsgBuy` `94`, `RequestPrivateStoreSell` `96`                                                  | `PrivateStoreManageListSell` `9A`, `PrivateStoreListSell` `9B`, `PrivateStoreMsgSell` `9C`, `PrivateStoreManageListBuy` `B7`, `PrivateStoreListBuy` `B8`, `PrivateStoreMsgBuy` `B9`                                                                                                                                                                             |
| High Five | `RequestPrivateStoreManageSell` `30`, `SetPrivateStoreListSell` `31`, `RequestPrivateStoreQuitSell` `96`, `SetPrivateStoreMsgSell` `97`, `RequestPrivateStoreBuy` `83`, `RequestPrivateStoreManageBuy` `99`, `SetPrivateStoreListBuy` `9A`, `RequestPrivateStoreQuitBuy` `9C`, `SetPrivateStoreMsgBuy` `9D`, `RequestPrivateStoreSell` `9F`, `SetPrivateStoreWholeMsg` `D0:4A`               | `PrivateStoreManageListSell` `A0`, `PrivateStoreListSell` `A1`, `PrivateStoreMsgSell` `A2`, `PrivateStoreManageListBuy` `BD`, `PrivateStoreListBuy` `BE`, `PrivateStoreMsgBuy` `BF`, `ExPrivateStoreSetWholeMsg` `FE:80`, `ExPrivateStorePackageMsg` `FE:80`                                                                                                    |
| Essence   | `RequestPrivateStoreManageSell` `30`, `SetPrivateStoreListSell` `31`, `RequestPrivateStoreQuitSell` `96`, `SetPrivateStoreMsgSell` `97`, `RequestPrivateStoreBuy` `83`, `RequestPrivateStoreManageBuy` `99`, `SetPrivateStoreListBuy` `9A`, `RequestPrivateStoreQuitBuy` `9C`, `SetPrivateStoreMsgBuy` `9D`, `RequestPrivateStoreSell` `9F`, `SetPrivateStoreWholeMsg` `D0:47` (class names) | `PrivateStoreManageListSell` `A0`, `PrivateStoreListSell` `A1`, `PrivateStoreMsgSell` `A2`, `PrivateStoreManageListBuy` `BD`, `PrivateStoreListBuy` `BE`, `PrivateStoreMsgBuy` `BF`, `ExPrivateStoreSetWholeMsg` `FE:81`, `ExPrivateStorePackageMsg`, `ExPrivateStoreSellingResult` `FE:01C7`, `ExPrivateStoreBuyingResult` `FE:01C6`, `RequestEnchant` `FE:81` |

Notes: `PrivateStoreType` values (NONE, SELL, SELL_MANAGE, BUY, BUY_MANAGE, MANUFACTURE, PACKAGE_SELL, SELL_BUFFS)
are identical in all three Mobius trees. Essence lists `EX_PRIVATE_STORE_BUY_SELL` and `EX_TRADE_LIMIT_INFO` in its
opcode table without handlers. The recipe (manufacture) store belongs to `craft.*`.

Known fixes: `l2j-server@b428635d` (2006-11-12, exploit-dupe) — Fix for RequestBuyItem Packet exploit; `mobius@7d81ad1d` (2021-11-22, wrong-formula-value) — Fixed restock_delay by adding current time

### trade.offline-trade — Offline trade

ID: `trade.offline-trade` · Shared: **engine**

A player leaves the game with the store open (sell, buy or craft) and stays in the world as an offline trader;
stores are saved and restored after a restart; optional time limit, name colour, visual effect, adena price.
Mobius custom (`config/Custom/OfflineTrade.ini`). Sub-features: offline sell/buy/craft · save and restore on restart ·
days limit · name colour · no-damage flag · peace-zone restriction.

| Chronicle | Present | Builds (evidence)                                       | Difference                                                                                         |
| --------- | ------- | ------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, l2jfrozen (`OfflineShop`), hellas, mobius-c6 | custom only; aCis has no offline shop; the key set of `OfflineTrade.ini` is identical to High Five |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, advanced          | baseline                                                                                           |
| Essence   | yes     | mobius-ess, l2emu-ess                                   | same key set, same defaults and `OfflineTraderTable` (626 vs 619 lines in IL)                      |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/OfflineTraderTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/OfflineTradeConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Offline.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/OfflineTrade.ini`, `nx-l2:chronicles/high-five/datapack/sql/character_offline_trade.sql`
- `mobius-il:java/org/l2jmobius/gameserver/data/sql/OfflineTraderTable.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/voiced/Offline.java`, `mobius-il:dist/game/config/Custom/OfflineTrade.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/data/sql/OfflineTraderTable.java`, `mobius-ess:dist/game/data/scripts/handlers/chat/commands/voiced/Offline.java`, `mobius-ess:dist/game/config/Custom/OfflineTrade.ini`

Packets

| Chronicle | Client → server                    | Server → client                    |
| --------- | ---------------------------------- | ---------------------------------- |
| Interlude | — (uses the private-store packets) | — (uses the private-store packets) |
| High Five | —                                  | —                                  |
| Essence   | —                                  | —                                  |

Notes: no dedicated packets; the offline mode is a server-side state (the client connection is dropped, the player
object stays). `OfflineTrade.ini` has the same keys in all three Mobius trees (empty key diff).

Known fixes: `l2jfrozen@71192c01` (2011-05-20, missing-check) — Fix on offline trades table to allow same item sell in offline mode, fix to hero weapons chance skills (to update weapon; `l2j-server-dp@835cb6f3` (2011-02-10, other) — Fix for Tickets #L5315 & #L5339.

### trade.store-search — Private store search

ID: `trade.store-search` · Shared: **chronicle-only**

Search window over all open private stores: filter by item name, type, store kind; paged result (120 per page), price
statistics for an item.

| Chronicle | Present | Builds (evidence)          | Difference                                                        |
| --------- | ------- | -------------------------- | ----------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                 |
| High Five | no      | nx-l2, l2j-server, sunrise | — (no search packet in the High Five client table)                |
| Essence   | yes     | mobius-ess, l2emu-ess      | baseline: one handler serves both search list and history request |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/storereview/ExRequestPrivateStoreSearchList.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/storereview/ExRequestPrivateStoreSearchStatistics.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/storereview/ExPrivateStoreSearchItem.java`

Packets

| Chronicle | Client → server                                                                                | Server → client                                                                                                                                                                                         |
| --------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExRequestPrivateStoreSearchList` `D0:0214`, `ExRequestPrivateStoreSearchStatistics` `D0:0215` | `ExPrivateStoreSearchItem` `FE:02D4`, `ExPrivateStoreSearchHistory` `FE:02D5`, `ExPrivateStoreSearchStatistics` `FE:02D6` (class `RequestPrivateStoreSearchStatistics` `FE:02D6` in the server package) |

Notes: page size constant `MAX_ITEM_PER_PAGE = 120`.

### trade.store-history — Private store history

ID: `trade.store-history` · Shared: **chronicle-only**

Server-wide log of private store transactions (type, item, enchant, price, count) kept in DB table
`item_transaction_history`; the Essence search window shows the item history.

| Chronicle | Present | Builds (evidence) | Difference                                                 |
| --------- | ------- | ----------------- | ---------------------------------------------------------- |
| Interlude | no      | mobius-il, acis   | —                                                          |
| High Five | no      | nx-l2, l2j-server | —                                                          |
| Essence   | yes     | mobius-ess        | baseline: in-memory list loaded from DB, TRUNCATE on reset |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/PrivateStoreHistoryManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/storereview/ExPrivateStoreSearchHistory.java`

Packets

| Chronicle | Client → server                                            | Server → client                         |
| --------- | ---------------------------------------------------------- | --------------------------------------- |
| Essence   | `ExRequestPrivateStoreSearchList` `D0:0214` (history page) | `ExPrivateStoreSearchHistory` `FE:02D5` |

Notes: the history is a plain in-memory list; there is no pruning besides the explicit reset.

### trade.npc-shop — NPC shops

ID: `trade.npc-shop` · Shared: **partial**

Buying and selling with NPC merchants through buy lists: buy list files with price and optional limited stock
(restored on a timer), tax of the owning castle added to the price, selling items back at half value, zero-price items.
Sub-features: buylist data · limited stock restore · castle tax · sell · zero sell price (custom).

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                    |
| --------- | ------- | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `BuyListHolder`/`BuyListData`, tax from the castle through the merchant price config; no `ExBuySellList` (separate sell list) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline; `ExBuySellList` for the sell/refund tab                                                                             |
| Essence   | differs | mobius-ess, l2emu-ess                | `Product`/`ProductList` in `mechanics/buylist`; castle tax by `TaxType.BUY/SELL` (see `economy.castle-tax`)                   |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/BuyListData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/BuyListTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestBuyItem,RequestSellItem}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{BuyList,ExBuySellList}.java`, `nx-l2:chronicles/high-five/datapack/data/buylists/`, `nx-l2:chronicles/high-five/datapack/config/Custom/MerchantZeroSellPrice.ini`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/BuyListData.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/buylist/BuyListHolder.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestBuyItem.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/BuyListData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/buylist/{Product,ProductList}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestBuyItem.java`

Packets

| Chronicle | Client → server                                                                | Server → client                            |
| --------- | ------------------------------------------------------------------------------ | ------------------------------------------ |
| Interlude | `RequestBuyItem` `1F`, `RequestSellItem` `1E`                                  | `BuyList` `11`                             |
| High Five | `RequestBuyItem` `40`, `RequestSellItem` `37`, `RequestBuySellUIClose` `D0:76` | `BuyList` `FE:B7`, `ExBuySellList` `FE:B7` |
| Essence   | `RequestBuyItem` `40`, `RequestSellItem` `37`                                  | `BuyList` `FE:B9`, `ExBuySellList` `FE:B9` |

Notes: Interlude confirmed against aCis, where `Merchant.java` passes `getCastle().getTaxRate()` to `BuyList` and
`RequestBuyItem` reads the same castle rate.

Known fixes: `vae-soli@d52b66fc` (2022-04-10, exploit-dupe) — Changing seed price to avoid infinite adena exploit (#19); `mobius@2723644d` (2020-09-16, exploit-dupe) — RequestBuyItem exploit prevention. Contributed by G-hamsteR; `l2jfrozen-shyla@8869c39b` (2018-11-01, exploit-dupe) — Fixed L2DorrAI NPE. Fixed deadlock on L2Character. Fixed exploit on request buy.

### trade.refund — Refund of sold items

ID: `trade.refund` · Shared: **partial (HF+Ess)**

Items sold to an NPC go to a per-player refund container; the player can buy back up to 12 last items at the sold
price (interface: tab in the shop window).

| Chronicle | Present | Builds (evidence)                     | Difference                                                                                                                                            |
| --------- | ------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | acis, l2jfrozen; Mobius IL (rudiment) | the client has no refund request; Mobius IL keeps `PlayerRefund` and `AllowRefund = True`, so sold items enter the container and can not be taken out |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity  | baseline                                                                                                                                              |
| Essence   | yes     | mobius-ess                            | same handler `RequestRefundItem`; answers with `ExBuySellList`                                                                                        |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestRefundItem.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/PlayerRefund.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExBuySellList.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/itemcontainer/PlayerRefund.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestSellItem.java` (puts items into the container, line 173)
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestRefundItem.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/PlayerRefund.java`

Packets

| Chronicle | Client → server             | Server → client                                                           |
| --------- | --------------------------- | ------------------------------------------------------------------------- |
| Interlude | —                           | —                                                                         |
| High Five | `RequestRefundItem` `D0:75` | `ExBuySellList` `FE:B7` (also `ExRefundList` `FE:A8` in the opcode table) |
| Essence   | `RequestRefundItem` `D0:73` | `ExBuySellList` `FE:B9`                                                   |

Notes: refund is a client-version feature; the IL rudiment is a trap for a refactor (do not port it as a feature).

Known fixes: `l2j-server@9ffb43ce` (2008-10-06, crash-npe) — NPE fix by GodKratos and Kerberos; `mobius@e7fb5706` (2022-12-14, other) — Workaround for admin shop buylists without adena

### trade.multisell — Multisell

ID: `trade.multisell` · Shared: **partial**

NPC exchange lists (multisell): items and adena or other items for items; optional enchant preservation, tax of
the castle, fixed list per NPC, item-bound lists from the inventory (open-multisell items).
Sub-features: list data · ingredient check · enchant carry-over · tax · count limit · list from item.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                                                         |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `MultisellData` with ingredient/product lists; tax through `PreparedListContainer` (aCis); no multisell result packet                                                              |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline (`MultiSellChoose` 498 lines)                                                                                                                                             |
| Essence   | differs | mobius-ess, l2emu-ess                | `MultisellListHolder`/`MultisellEntryHolder`/`PreparedMultisellListHolder`, `RequestMultisellList`, result packet `ExMultiSellResult`; choose handler 767 lines (more entry kinds) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/MultisellData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/MultiSellChoose.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/Multisell.java`, `nx-l2:chronicles/high-five/datapack/data/multisell/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/MultisellData.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/MultiSellChoose.java`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc/Multisell.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/MultisellData.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/holders/{MultisellListHolder,MultisellEntryHolder,PreparedMultisellListHolder}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{MultiSellChoose,RequestMultisellList}.java`

Packets

| Chronicle | Client → server                                          | Server → client                                     |
| --------- | -------------------------------------------------------- | --------------------------------------------------- |
| Interlude | `MultiSellChoose` `A7`                                   | `MultiSellList` `D0`                                |
| High Five | `MultiSellChoose` `B0`                                   | `MultiSellList` `D0`                                |
| Essence   | `MultiSellChoose` `B0`, `RequestMultisellList` `D0:019F` | `MultiSellList` `D0`, `ExMultiSellResult` `FE:0183` |

Notes: the largest data set of the domain; per-NPC lists are data, not code. In the Essence build the data file
`multisell` has its own XSD.

Known fixes: `l2j-server@4739570b` (2009-01-30, exploit-dupe) — Fix for Multisell packet exploits in case admins configure multisells wrong; `mobius@96d24e63` (2020-10-03, exploit-dupe) — Fixed multisell integer limit adena exploit. Contributed by G-hamsteR; `l2jfrozen@af473a00` (2011-04-19, exploit-dupe) — Fix exploit with GmShop with Bssb (Thank Zatarra for report).

### trade.wear-preview — Item preview (wear)

ID: `trade.wear-preview` · Shared: **engine**

At an NPC the player previews armour/weapon on the character for a few seconds (`Wear` bypass), paying a small fee.

| Chronicle | Present | Builds (evidence)                    | Difference                                                         |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | same flow                                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                           |
| Essence   | yes     | mobius-ess                           | same flow; `RequestPreviewItem` slightly longer (250 vs 233 lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestPreviewItem.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ShopPreviewList,ShopPreviewInfo}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/Wear.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestPreviewItem.java`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc/Wear.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestPreviewItem.java`, `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/Wear.java`

Packets

| Chronicle | Client → server           | Server → client                                |
| --------- | ------------------------- | ---------------------------------------------- |
| Interlude | `RequestPreviewItem` `C6` | `ShopPreviewList` `EF`, `ShopPreviewInfo` `F0` |
| High Five | `RequestPreviewItem` `C7` | `ShopPreviewList` `F5`, `ShopPreviewInfo` `F6` |
| Essence   | `RequestPreviewItem` `C7` | `ShopPreviewList` `F5`, `ShopPreviewInfo` `F6` |

Notes: config `AllowWear = True` in all three (General.ini).

### trade.sell-buffs — Sell buffs

ID: `trade.sell-buffs` · Shared: **engine**

A buffer class player opens a "buff store" (store type SELL_BUFFS): list of skills with prices, buyers receive the
buff for adena. Mobius custom. Sub-features: skill list · price limits · third-class only option · MP cost option ·
peace zone option.

| Chronicle | Present | Builds (evidence)                                      | Difference                                                                                  |
| --------- | ------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, l2jfrozen (`SellBuffs` in the r1596 branch) | custom only; 6 config keys identical to High Five                                           |
| High Five | yes     | nx-l2, sunrise (Purity branch)                         | baseline: `SellBuffsManager` (521 lines), `SellBuffData.xml`, `PrivateStoreType.SELL_BUFFS` |
| Essence   | yes     | mobius-ess                                             | same (519 lines); same 6 keys                                                               |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/SellBuffsManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/holders/SellBuffHolder.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/SellBuff/{SellBuffBypassHandler,SellBuffCommandHandler}.java`, `nx-l2:chronicles/high-five/datapack/data/SellBuffData.xml`, `nx-l2:chronicles/high-five/datapack/config/Custom/SellBuffs.ini`
- `mobius-il:java/org/l2jmobius/gameserver/managers/SellBuffsManager.java`, `mobius-il:dist/game/data/scripts/custom/SellBuff/SellBuffBypassHandler.java`, `mobius-il:dist/game/data/SellBuffData.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/SellBuffsManager.java`, `mobius-ess:dist/game/data/scripts/custom/SellBuff/SellBuffBypassHandler.java`

Packets

| Chronicle | Client → server | Server → client                     |
| --------- | --------------- | ----------------------------------- |
| Interlude | — (HTML bypass) | `NpcHtmlMessage` `0F` (HTML window) |
| High Five | —               | `NpcHtmlMessage` `19`               |
| Essence   | —               | `NpcHtmlMessage` `19`               |

Notes: purely HTML-driven; the only protocol trace is the store type value 9 in `PrivateStoreType`.

Known fixes: `mobius@0cb0a963` (2018-05-27, missing-check) — Fix for sell buff html limit. Contributed by facab; `mobius@4d9f34b5` (2020-08-03, other) — Fixed sellbuffs not working. Contributed by CostyKiller

### trade.limit-shop — Limit Shop

ID: `trade.limit-shop` · Shared: **chronicle-only**

Essence shop paid with L-Coin: regular shop, clan shop and craft shop, each product with per-account/period purchase
limit; ingredients for the craft variant; result packets with the product list.

| Chronicle | Present | Builds (evidence)     | Difference                                                                        |
| --------- | ------- | --------------------- | --------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il             | —                                                                                 |
| High Five | no      | nx-l2                 | —                                                                                 |
| Essence   | yes     | mobius-ess, l2emu-ess | baseline: `LimitShopData`, `LimitShopClanData`, `LimitShopCraftData`, 3 XML files |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/{LimitShopData,LimitShopClanData,LimitShopCraftData}.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/holders/{LimitShopProductHolder,LimitShopRandomCraftReward}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/limitshop/{RequestPurchaseLimitShopItemList,RequestPurchaseLimitShopItemBuy}.java`, `mobius-ess:dist/game/data/LimitShop.xml`, `mobius-ess:dist/game/data/LimitShopClan.xml`, `mobius-ess:dist/game/data/LimitShopCraft.xml`

Packets

| Chronicle | Client → server                                                                           | Server → client                                                                                                                                                |
| --------- | ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestPurchaseLimitShopItemList` `D0:0162`, `RequestPurchaseLimitShopItemBuy` `D0:0163` | `ExPurchaseLimitShopItemListNew` `FE:0252`, `ExPurchaseLimitShopItemResult` `FE:0207`, `ExBloodyCoinCount` `FE:0208`, `ExPurchaseLimitCraftItemList` `FE:03BB` |

Notes: the craft request packet (`RequestPurchaseLimitCraftItem`) appears in the 09.2 diff but is a later addition.

Known fixes: `mobius@04d169c3` (2022-01-08, missing-check) — Fixed RequestPurchaseLimitShopItemBuy item disappearing from reward. Contributed by nasseka.
