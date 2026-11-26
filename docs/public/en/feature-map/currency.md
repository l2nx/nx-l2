# Money and currencies

Adena and the secondary currencies of the game.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### currency.adena — Adena

ID: `currency.adena` · Shared: **partial**

The base currency (item id 57): wallet limit (`MaxAdena`), loss/gain through every economic action, and the optional
Mobius custom "banking": exchange adena for gold bars and back by a voiced command, with an auto-conversion script
when the wallet exceeds a limit. Sub-features: wallet limit · banking (adena ↔ gold bars) · auto-conversion.

| Chronicle | Present | Builds (evidence)                                    | Difference                                                                                                  |
| --------- | ------- | ---------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen (`BankingCmd`), mobius-c6 | `MaxAdena = 2000000000` and `Inventory.MAX_ADENA` is an `int`; item counts are `int` in the Interlude model |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                 | baseline: `MAX_ADENA` is a `long`; nx-l2 default `MaxAdena = 3000000000000000`                              |
| Essence   | yes     | mobius-ess                                           | `long`, default `MaxAdena = 9999999999999`                                                                  |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/Inventory.java` (`MAX_ADENA`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/BankingConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Banking.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/BankingAutoConvert.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/Banking.ini`, `nx-l2:chronicles/high-five/datapack/config/Player.ini`
- `mobius-il:java/org/l2jmobius/gameserver/entity/itemcontainer/Inventory.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/voiced/Banking.java`, `mobius-il:dist/game/data/scripts/custom/BankingAutoConvert.java`, `mobius-il:dist/game/config/Custom/Banking.ini`
- the same files under `J:` / `DS:` / `DC:`

Packets

| Chronicle | Client → server | Server → client                                                        |
| --------- | --------------- | ---------------------------------------------------------------------- |
| Interlude | —               | `InventoryUpdate` `27`, `ItemList` `1B` (adena is an inventory item)   |
| High Five | —               | `InventoryUpdate` `21`, `ItemList` `11`                                |
| Essence   | —               | `InventoryUpdate` `21`, `ItemList` `11`, `ExAdenaInvenCount` `FE:013F` |

Notes: `Banking.ini` has five identical keys in all three Mobius trees (`BankingEnabled = False`,
`BankingGoldbarCount = 1`, `BankingAdenaCount = 1000000000`, `BankingAutoConvert = False`,
`BankingAutoConvertAdenaLimit`). The counter width is the real difference: a refactor to `long` counts removes it.

Known fixes: `l2j-server-dp@46e8b6b9` (2010-04-18, other) — Banking InventoryUpdate fix, thanks JiV.

### currency.ancient-adena — Ancient Adena

ID: `currency.ancient-adena` · Shared: **partial**

Secondary currency (item id 5575) earned through Seven Signs: seal stones are contributed to the priests, Ancient Adena
is paid as a seal reward and spent at the Blacksmith of Mammon and with the Seven Signs merchants. Sub-features:
wallet (`getAncientAdena`) · Seven Signs payout · stone contribution.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                  |
| --------- | ------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `SignsPriest` handles stone contribution and payout                                                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline (`ANCIENT_ADENA_ID = 5575` in `Inventory`)                                                                         |
| Essence   | differs | mobius-ess                           | the item and `Player` helpers remain, but there are no Seven Signs priests (`SignsPriest` is absent), so there is no source |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/SignsPriest.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/Inventory.java` (`ANCIENT_ADENA_ID`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/EtcItem.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/SignsPriest.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/itemcontainer/PlayerInventory.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/PlayerInventory.java`

Packets

| Chronicle | Client → server | Server → client                         |
| --------- | --------------- | --------------------------------------- |
| Interlude | —               | `InventoryUpdate` `27`, `ItemList` `1B` |
| High Five | —               | `InventoryUpdate` `21`, `ItemList` `11` |
| Essence   | —               | `InventoryUpdate` `21`, `ItemList` `11` |

Notes: the Seven Signs mechanics themselves belong to `seven-signs.*`.

### currency.pc-cafe-points — PC Café points

ID: `currency.pc-cafe-points` · Shared: **partial**

PC Café points earned online or per kill, spent in the PC Café shop and by coupons. Mobius implements two modes:
retail-like (points per reward time) and per-kill; both can require premium and have a cap and random/double bonus.
Sub-features: accrual (timer / kill) · cap · double chance · low-exp kill reward · coupon use · admin command.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                                              |
| --------- | ------- | --------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (custom), acis, l2jfrozen (`pcBang.properties`) | the Interlude client packets exist (`ExPCCafePointInfo`, coupon UI); disabled by default (`PcCafeEnabled = False`)      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                      | baseline (`PcCafePointsManager` 162 lines)                                                                              |
| Essence   | differs | mobius-ess                                                | adds `PcCafeOnlyVip`, the skill effect `AddPcCafePoints`, the open-window request `ExPCCafeRequestOpenWindowWithoutNPC` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/PcCafePointsManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/PremiumSystemConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminPcCafePoints.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/PremiumSystem.ini`
- `mobius-il:java/org/l2jmobius/gameserver/managers/PcCafePointsManager.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminPcCafePoints.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/PcCafePointsManager.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/AddPcCafePoints.java`

Packets

| Chronicle | Client → server                                                         | Server → client                                               |
| --------- | ----------------------------------------------------------------------- | ------------------------------------------------------------- |
| Interlude | `RequestPCCafeCouponUse` `D0:20`                                        | `ExPCCafePointInfo` `FE:31`, `ShowPCCafeCouponShowUI` `FE:43` |
| High Five | `RequestPCCafeCouponUse` `D0:19`                                        | `ExPCCafePointInfo` `FE:32`, `ShowPCCafeCouponShowUI` `FE:44` |
| Essence   | `RequestPCCafeCouponUse` `D0:19`, `ExPCCafeRequestOpenWindowWithoutNPC` | `ExPCCafePointInfo` `FE:32`, `ShowPCCafeCouponShowUI` `FE:45` |

Notes: config lives in `PremiumSystem.ini` next to the premium account keys.

### currency.l-coin — L-Coin

ID: `currency.l-coin` · Shared: **chronicle-only**

Essence currency (item id 91663): dropped by monsters (chance, minimum monster level, quantity range), earned from
attendance rewards, spent in the Limit Shop and the world exchange, used for the PK-penalty item restore and prison
release. Sub-features: drop · attendance reward · wallet use in shops · penalties.

| Chronicle | Present | Builds (evidence)     | Difference                                                                                                                                                |
| --------- | ------- | --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il             | —                                                                                                                                                         |
| High Five | no      | nx-l2                 | —                                                                                                                                                         |
| Essence   | yes     | mobius-ess, l2emu-ess | baseline: `LCoinDropEnable = False` by default, chance 15, minimum monster level 40; skill effect `BonusDropRateLCoin`; `PkPenaltyRestoreLCoinCost = 100` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/config/RatesConfig.java` (`LCOIN_DROP_*`), `mobius-ess:java/org/l2jmobius/gameserver/config/AttendanceRewardsConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/FeatureConfig.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/BonusDropRateLCoin.java`

Packets

| Chronicle | Client → server | Server → client                                         |
| --------- | --------------- | ------------------------------------------------------- |
| Essence   | —               | `InventoryUpdate` `21`, `ItemList` `11` (a normal item) |

Notes: no wallet class — L-Coin is an ordinary inventory item in Mobius.

### currency.adena-distribution — Adena distribution

ID: `currency.adena-distribution` · Shared: **chronicle-only**

The party (or command channel) leader splits an amount of adena evenly among members in range: start request,
members' confirmation, done/cancel; checks that the leader has enough adena for all targets.

| Chronicle | Present | Builds (evidence) | Difference                                          |
| --------- | ------- | ----------------- | --------------------------------------------------- |
| Interlude | no      | mobius-il         | —                                                   |
| High Five | no      | nx-l2             | —                                                   |
| Essence   | yes     | mobius-ess        | baseline: `AdenaDistributionRequest` request object |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/adenadistribution/{RequestDivideAdenaStart,RequestDivideAdena,RequestDivideAdenaCancel}.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/request/AdenaDistributionRequest.java`

Packets

| Chronicle | Client → server                                                                                     | Server → client                                                                                |
| --------- | --------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Essence   | `RequestDivideAdenaStart` `D0:E7`, `RequestDivideAdena` `D0:E9`, `RequestDivideAdenaCancel` `D0:E8` | `ExDivideAdenaStart` `FE:015C`, `ExDivideAdenaDone` `FE:015E`, `ExDivideAdenaCancel` `FE:015D` |

Notes: refused when the actor is not the party leader (or command channel leader).

### currency.exchanger — Currency exchangers

ID: `currency.exchanger` · Shared: **chronicle-only**

NPC (or window) exchangers that turn one item into another by a table of variants (a count range per ingredient),
used as converters between server currencies. No Mobius implementation.

| Chronicle | Present | Builds (evidence)          | Difference                                                                       |
| --------- | ------- | -------------------------- | -------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                                |
| High Five | yes     | advanced                   | `Exchanger` script with `ExchangeItemParser` (data table); no Mobius counterpart |
| Essence   | no      | mobius-ess                 | —                                                                                |

Entry points

- `advanced:dist/gameserver/data/scripts/services/Exchanger.java`, `advanced:src/main/java/l2f/gameserver/data/xml/parser/ExchangeItemParser.java`

Packets

| Chronicle | Client → server | Server → client       |
| --------- | --------------- | --------------------- |
| High Five | — (HTML bypass) | `NpcHtmlMessage` `19` |

Notes: functionally a multisell with randomised ingredient counts; see `trade.multisell`.
