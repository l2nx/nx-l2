# Server economy

Server-side regulators: rates, taxes, manor, merchant prices.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### economy.rates — Rates

ID: `economy.rates` · Shared: **partial**

Server multipliers for experience, SP, adena, item drop, spoil, quest rewards, raid/epic boss drops, fishing and
other gains; premium multipliers; optional dynamic exp rates by level and calendar-day custom rates.
Sub-features: base rates · per-level exp rates · premium rates · dynamic exp rates (XML) · per-item rate overrides.

| Chronicle | Present | Builds (evidence)                                              | Difference                                                                |
| --------- | ------- | -------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis-180-devastated, l2jfrozen (`rates.properties`) | `Rates.ini` + `DynamicExpRates.xml`                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                           | baseline                                                                  |
| Essence   | yes     | mobius-ess                                                     | adds L-Coin drop and further rates (`RatesConfig` carries `LCOIN_DROP_*`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/RatesConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/DynamicExpRateData.java`, `nx-l2:chronicles/high-five/datapack/config/Rates.ini`, `nx-l2:chronicles/high-five/datapack/config/DynamicExpRates.xml`
- `mobius-il:java/org/l2jmobius/gameserver/config/RatesConfig.java`, `mobius-il:dist/game/config/Rates.ini`, `mobius-il:dist/game/config/DynamicExpRates.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/config/RatesConfig.java`, `mobius-ess:dist/game/config/Rates.ini`, `mobius-ess:dist/game/config/DynamicExpRates.xml`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: no protocol footprint; the multipliers apply in `character`, `drop`, `quest` code.

### economy.castle-tax — Taxes

ID: `economy.castle-tax` · Shared: **partial**

The owning clan of a castle sets a tax rate that is added to NPC shop and multisell prices in its territory; the tax
accumulates in the castle treasury; the lord can change the rate (with a ceiling) through the chamberlain.
Sub-features: tax rate · shop and multisell tax · treasury · chamberlain control · Seven Signs modifier.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                                                                                       |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | the rate comes from the castle through the merchant price config (aCis: `Merchant.getCastle().getTaxRate()`, `PreparedListContainer`)                                                                            |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `MerchantPriceConfigTable` supplies castle and base tax per zone                                                                                                                                       |
| Essence   | differs | mobius-ess                           | `TaxType.BUY/SELL`, `TaxZone` (zone parameter `domainId`) bind NPCs to a castle; the rate depends on the Seven Signs side (fixed light/dark values) or the clan variable; the Rune and Aden castles share income |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/MerchantPriceConfigTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestBuyItem.java` (castle and base tax), `nx-l2:chronicles/high-five/datapack/data/MerchantPriceConfig.xml`, `nx-l2:chronicles/high-five/datapack/data/zones/tax.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/MerchantPriceConfigTable.java`, `mobius-il:dist/game/data/MerchantPriceConfig.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/TaxZone.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/TaxType.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/Castle.java` (`getTaxPercent`)

Packets

| Chronicle | Client → server       | Server → client                            |
| --------- | --------------------- | ------------------------------------------ |
| Interlude | `RequestBuyItem` `1F` | `BuyList` `11` (carries the tax rate)      |
| High Five | `RequestBuyItem` `40` | `BuyList` `FE:B7`, `ExBuySellList` `FE:B7` |
| Essence   | `RequestBuyItem` `40` | `BuyList` `FE:B9`, `ExBuySellList` `FE:B9` |

Notes: `NpcHtmlMessage` pages of the castle chamberlain (`CastleChamberlain` script, `castlesettaxrate.html`)
change the rate.

### economy.manor — Manor

ID: `economy.manor` · Shared: **partial**

Castle manor: the castle's lord chooses seeds to sell and crops to buy for the next period; players buy seeds,
sow them on monsters, harvest and sell crops; maintenance, approval and refresh by schedule.
Sub-features: seed production · crop procure · sow/harvest · period modes (current/next/maintenance) · admin
command · manor manager NPC.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                    |
| --------- | ------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `CastleManorManager` + `ManorManager` NPC script; `AllowManor = True`                                                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline (`CastleManorManager` 649 lines in nx-l2); `AllowManor = True`; adds `RequestSeedPhase`, `ExShowSeedMapInfo`                         |
| Essence   | differs | mobius-ess                           | manager and client packets exist, but `AllowManor = False` by default and there is no `ManorManager` NPC script (only the castle chamberlain) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CastleManorManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/manor/*`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/ManorManager/ManorManager.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminManor.java`, `nx-l2:chronicles/high-five/datapack/data/Seeds.xml`, `nx-l2:chronicles/high-five/datapack/sql/castle_manor_procure.sql`, `nx-l2:chronicles/high-five/datapack/sql/castle_manor_production.sql`
- `mobius-il:java/org/l2jmobius/gameserver/managers/CastleManorManager.java`, `mobius-il:dist/game/data/scripts/ai/others/ManorManager/ManorManager.java`, `mobius-il:dist/game/data/Seeds.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/CastleManorManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{RequestSetSeed,RequestSetCrop,RequestBuySeed,RequestProcureCropList,RequestManorList}.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                | Server → client                                                                                                                                                                                                                                                                                                                                                                      |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestManorList` `D0:08`, `RequestSetSeed` `D0:0A`, `RequestSetCrop` `D0:0B`, `RequestBuySeed` `C4`, `RequestProcureCropList` `D0:09`, `RequestBuyProcure` `C3`                              | `ExSendManorList` `FE:1B`, `ExShowSeedInfo` `FE:1C`, `ExShowCropInfo` `FE:1D`, `ExShowManorDefaultInfo` `FE:1E`, `ExShowSeedSetting` `FE:1F`, `ExShowCropSetting` `FE:20`, `ExShowSellCropList` `FE:21`, `ExShowProcureCropDetail` `FE:22`, `BuyListSeed` `E8`, `SellListProcure` `E9`                                                                                               |
| High Five | `RequestManorList` `D0:01`, `RequestSetSeed` `D0:03`, `RequestSetCrop` `D0:04`, `RequestBuySeed` `C5`, `RequestProcureCropList` `D0:02`, `RequestSeedPhase` `D0:63`, `RequestProcureCrop` `CF` | `ExSendManorList` `FE:22`, `ExShowSeedInfo` `FE:23`, `ExShowCropInfo` `FE:24`, `ExShowManorDefaultInfo` `FE:25`, `ExShowSeedSetting` `FE:26`, `ExShowCropSetting` `FE:2B`, `ExShowSellCropList` `FE:2C`, `ExShowProcureCropDetail` `FE:78`, `BuyListSeed` `E9`, `SellListProcure` `EF`, `ExShowSeedMapInfo` `FE:A1`, `ExInitializeSeed` `FE:B5`                                      |
| Essence   | `RequestManorList` `D0:01`, `RequestSetSeed` `D0:03`, `RequestSetCrop` `D0:04`, `RequestBuySeed` `C5`, `RequestProcureCropList` `D0:02`, `RequestSeedPhase` `D0:60`                            | `ExSendManorList` `FE:22`, `ExShowSeedInfo` `FE:23`, `ExShowCropInfo` `FE:24`, `ExShowManorDefaultInfo` `FE:25`, `ExShowSeedSetting` `FE:26`, `ExShowCropSetting` `FE:2B`, `ExShowSellCropList` `FE:2C`, `ExShowProcureCropDetail` `FE:79`, `BuyListSeed` `E9`, `SellListProcure` `EF`, `ExShowSeedMapInfo` `FE:A2`, `ExInitializeSeed` `FE:B7` (partial set in the handler classes) |

Notes: seeds are a shared data file (`Seeds.xml`) in all three.

Known fixes: `mobius@2769582f` (2019-11-02, crash-npe) — Fixed disabled manor system NPE. Contributed by Sahar

### economy.merchant-price — Regional merchant prices

ID: `economy.merchant-price` · Shared: **partial (IL+HF)**

Per-region price configuration for NPC merchants: default price modifier, castle tax and base tax for NPCs inside
named price zones. Superseded in Essence by tax zones.

| Chronicle | Present | Builds (evidence)                                    | Difference                                                     |
| --------- | ------- | ---------------------------------------------------- | -------------------------------------------------------------- |
| Interlude | yes     | mobius-il                                            | `MerchantPriceConfigTable`, `MerchantPriceConfig.xml`          |
| High Five | yes     | nx-l2, sunrise (`MerchantPriceConfigData`), eternity | baseline                                                       |
| Essence   | no      | mobius-ess                                           | no `MerchantPriceConfig`; see `economy.castle-tax` (`TaxZone`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/MerchantPriceConfigTable.java`, `nx-l2:chronicles/high-five/datapack/data/MerchantPriceConfig.xml`, `nx-l2:chronicles/high-five/datapack/data/xsd/MerchantPriceConfig.xsd`
- `mobius-il:java/org/l2jmobius/gameserver/data/MerchantPriceConfigTable.java`, `mobius-il:dist/game/data/MerchantPriceConfig.xml`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |

Notes: aCis keeps the tax directly on the castle and has no separate price-zone table.
