# Market and auctions

Asynchronous trade through the server: auctions, commission house, world exchange.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### market.item-auction — Item auction

ID: `market.item-auction` · Shared: **partial (HF+Ess)**

Scheduled auction of a single rare item at an auctioneer NPC: the item and starting bid come from a schedule file,
players bid in adena, the last minutes extend on a bid, the winner collects the item, losers get adena back; finished
auctions are kept for N days. Sub-features: schedule (weekday/hour or interval) · bidding · time extension · winner
claim · refund of losers · history retention.

| Chronicle | Present | Builds (evidence)                              | Difference                                                                                                                                                        |
| --------- | ------- | ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen                     | not in the Interlude client; no auction code in any Interlude root                                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, advanced | baseline: `AltItemAuctionEnabled = True` by default, 248 item entries in `ItemAuctions.xml`                                                                       |
| Essence   | differs | mobius-ess                                     | same classes; `ItemAuction` is longer (551 vs 442 lines), disabled by default (`AltItemAuctionEnabled = False`), opcode table adds status and "next info" packets |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/ItemAuctionManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/auction/{ItemAuction,ItemAuctionInstance,ItemAuctionBid,AuctionDateGenerator}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/ItemAuctionLink.java`, `nx-l2:chronicles/high-five/datapack/data/ItemAuctions.xml`, `nx-l2:chronicles/high-five/datapack/sql/item_auction.sql`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/ItemAuctionManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/auction/ItemAuction.java`, `mobius-ess:dist/game/data/ItemAuctions.xml`

Packets

| Chronicle | Client → server                                                   | Server → client                   |
| --------- | ----------------------------------------------------------------- | --------------------------------- |
| High Five | `RequestBidItemAuction` `D0:39`, `RequestInfoItemAuction` `D0:3A` | `ExItemAuctionInfoPacket` `FE:68` |
| Essence   | `RequestBidItemAuction` `D0:36`, `RequestInfoItemAuction` `D0:37` | `ExItemAuctionInfoPacket` `FE:69` |

Notes: not to be confused with the clan hall auction (`ClanHallAuctionManager`, `residence.*`). L2Emu Essence has no
item-auction files.

Known fixes: `lineage2ts@cd60af98` (2021-04-18, wrong-formula-value) — Fixing problem with IDFactory reused ids not being able to be checked due to size; correcting messages for item auctions; `l2j-server@485ff48c` (2013-08-23, wrong-formula-value) — Fixing wrong format in AbstractItemPacket for quest items

### market.commission — Commission (trade house)

ID: `market.commission` · Shared: **chronicle-only**

Essence trade house: a player registers an item at a price per unit for 1/3/5/7/15/30 days at a commission NPC,
others search by category/tree and buy; sold items pay out by mail; unsold items return by mail; registration and
sale fees depend on the duration. Sub-features: register · search by tree/type · buy · cancel · delete · fees ·
mail payout/return.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                                                  |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis            | —                                                                                                                                           |
| High Five | no      | nx-l2, l2j-server, sunrise | — (only the unrelated opcode name `ExPutCommissionResultForVariationMake` for augmentation)                                                 |
| Essence   | yes     | mobius-ess, l2emu-ess      | baseline: max 10 registered items per player, min fee 1000 adena, registration 0.0001 per day, sale 0.005 per day, `commission_items` table |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/ItemCommissionManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/CommissionManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/commission/{CommissionItem,CommissionItemType,CommissionTreeType}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/commission/*.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                | Server → client                                                                                                                                                                                                                                                                                                                       |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestCommissionInfo` `D0:9A`, `RequestCommissionRegister` `D0:9B`, `RequestCommissionRegisteredItem` `D0:A1`, `RequestCommissionRegistrableItemList` `D0:99`, `RequestCommissionList` `D0:9E`, `RequestCommissionBuyInfo` `D0:9F`, `RequestCommissionBuyItem` `D0:A0`, `RequestCommissionCancel` `D0:9C`, `RequestCommissionDelete` `D0:9D` | `ExShowCommission` `FE:F3`, `ExResponseCommissionInfo` `FE:F5`, `ExResponseCommissionItemList` `FE:F4`, `ExResponseCommissionList` `FE:F8`, `ExResponseCommissionRegister` `FE:F6`, `ExResponseCommissionBuyInfo` `FE:F9`, `ExResponseCommissionBuyItem` `FE:FA`, `ExResponseCommissionDelete` `FE:F7`, `ExCloseCommission` `FE:0113` |

Notes: payout and return use mail types `COMMISSION_ITEM_SOLD` / `COMMISSION_ITEM_RETURNED` (see `mail.system-mail`).

### market.world-exchange — World exchange

ID: `market.world-exchange` · Shared: **chronicle-only**

Essence server-wide exchange: a player lists an item for L-Coin or adena price, buyers browse by type/sort, the
listing sells, the seller collects the payment from a settle list; unsold items return after a period; average
price statistics per item.

| Chronicle | Present | Builds (evidence)     | Difference                                                                                                                                |
| --------- | ------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il             | —                                                                                                                                         |
| High Five | no      | nx-l2                 | —                                                                                                                                         |
| Essence   | yes     | mobius-ess, l2emu-ess | baseline: `WorldExchange.ini` — fee 5 % of L-Coin (max 20000), adena fee 10000, item sell period 14 days, payment take 120, item back 120 |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/WorldExchangeManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/holders/WorldExchangeHolder.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/enums/WorldExchange*.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/worldexchange/*.java`, `mobius-ess:dist/game/config/WorldExchange.ini`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                     | Server → client                                                                                                                                                                                                                                                                                                   |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExWorldExchangeItemList` `D0:023F`, `ExWorldExchangeRegisterItem` `D0:0240`, `ExWorldExchangeBuyItem` `D0:0241`, `ExWorldExchangeSettleList` `D0:0242`, `ExWorldExchangeSettleRecvResult` `D0:0243`, `ExWorldExchangeAveragePrice` `D0:025B`, `ExWorldExchangeTotalList` `D0:025C` | `WorldExchangeItemList` `FE:02FD`, `WorldExchangeRegisterItem` `FE:02FE`, `WorldExchangeBuyItem` `FE:02FF`, `WorldExchangeSettleList` `FE:0300`, `WorldExchangeSettleRecvResult` `FE:0301`, `WorldExchangeSellCompleteAlarm` `FE:0302`, `WorldExchangeAveragePrice` `FE:0323`, `WorldExchangeTotalList` `FE:0324` |

Notes: `EnableWorldExchange = True` by default.

Known fixes: `mobius@510c00a7` (2023-04-04, other) — World Trade stack pricing fix. Contributed by Paiplayer.

### market.broker — Item broker

ID: `market.broker` · Shared: **chronicle-only**

A "broker" NPC/window lists items currently on sale in private stores of a town (by store kind: sell, buy, craft),
with category filters, name search, paging, and moves the buyer to the trader. It is a directory over private stores,
not a lot-based market.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                     |
| --------- | ------- | -------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                                                              |
| High Five | yes     | advanced                   | no Mobius implementation; L2_Advanced has the `services/ItemBroker` script (10 items per page, rare-item list) |
| Essence   | no      | mobius-ess                 | superseded by the client-side private store search (`trade.store-search`)                                      |

Entry points

- `advanced:dist/gameserver/data/scripts/services/ItemBroker.java`

Packets

| Chronicle | Client → server | Server → client                     |
| --------- | --------------- | ----------------------------------- |
| High Five | — (HTML bypass) | `NpcHtmlMessage` `19` (HTML window) |

Notes: nothing to refactor from Mobius; the feature exists as a script in one fork family.

### market.price-tracking — Market price tracking

ID: `market.price-tracking` · Shared: **chronicle-only**

Average price per item from completed trades; shown in the Essence world exchange window as a price hint.

| Chronicle | Present | Builds (evidence) | Difference                                                               |
| --------- | ------- | ----------------- | ------------------------------------------------------------------------ |
| Interlude | no      | mobius-il         | —                                                                        |
| High Five | no      | nx-l2             | —                                                                        |
| Essence   | yes     | mobius-ess        | baseline: part of `WorldExchangeManager` (`ExWorldExchangeAveragePrice`) |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/WorldExchangeManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/worldexchange/ExWorldExchangeAveragePrice.java`

Packets

| Chronicle | Client → server                         | Server → client                       |
| --------- | --------------------------------------- | ------------------------------------- |
| Essence   | `ExWorldExchangeAveragePrice` `D0:025B` | `WorldExchangeAveragePrice` `FE:0323` |

Notes: the average comes from world exchange sales only, not from private stores.

Known fixes: `mobius@510c00a7` (2023-04-04, other) — World Trade stack pricing fix. Contributed by Paiplayer.
