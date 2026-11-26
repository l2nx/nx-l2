# Items and inventory

Items as entities, the inventory, warehouses and movement of items between containers.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### item.templates — Item templates

ID: `item.templates` · Shared: **partial**

Static definitions of every item: weapon / armor / etc-item, grade, crystal count, weight, price, trade / drop /
destroy / sell flags, skills, conditions, stats. Loaded at boot from XML (or CSV/SQL in older engines); an item in the
world is an instance built from its template.
Sub-features: weapon · armor · etc-item · grade and crystal data · flags · item skills · custom item ranges.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                 |
| --------- | ------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server | XML (`ItemData`) in Mobius IL / aCis; CSV+SQL tables (`ItemTable`) in L2jFrozen            |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts          | baseline; `ItemData` identical to Mobius IL                                                |
| Essence   | yes     | mobius-ess                                                | same loader (≈8 changed lines); own item set (ids above 90000, e.g. 91462 in crystal data) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ItemData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/ItemTemplate.java`, `nx-l2:.../entity/item/{Weapon,Armor,EtcItem}.java`, `nx-l2:chronicles/high-five/datapack/data/stats/items/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/ItemData.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/item/ItemTemplate.java`, `mobius-il:dist/game/data/stats/items`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ItemData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/ItemTemplate.java`, `mobius-ess:dist/game/data/stats/items`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/ItemData.java`; L2jFrozen: `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/datatables/sql/ItemTable.java`

Packets

| Chronicle | Client → server | Server → client                                                                      |
| --------- | --------------- | ------------------------------------------------------------------------------------ |
| Interlude | — (static data) | `ItemList` `1B`, `InventoryUpdate` `27` carry template ids; client reads item `.dat` |
| High Five | —               | `ItemList` `11`, `InventoryUpdate` `21`, `.dat`                                      |
| Essence   | —               | `ItemList` `11`, `InventoryUpdate` `21`, `.dat`                                      |

Notes: in `nx-l2` the template is `ItemTemplate`, the instance is `Item` (`entity/item/instance`); in aCis the pair is
`Item` / `ItemInstance`, in L2jFrozen `L2Item` / `L2ItemInstance`.

### item.inventory — Inventory

ID: `item.inventory` · Shared: **partial**

Player inventory: slot limit by race/class, item list, quest-item list, ordering saved on the server, weight; Essence
adds two switchable equipment inventories (A / B) and many more paperdoll slots.
Sub-features: slot limit · item list and update packets · saved item order · quest items · dual inventory (Ess) ·
warehouse sorting (custom).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                     |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | 17 paperdoll slots (incl. cloak, face, hair, hair2); no `RequestSaveInventoryOrder` in aCis (only in Mobius IL)                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, premium | 25 paperdoll slots (bracelets, 6 talisman/deco slots); `RequestSaveInventoryOrder` present                                     |
| Essence   | yes     | mobius-ess                                    | 59 paperdoll slots (belt, brooch, jewels, 5 agathion, 21 artifact slots); `RequestExDualInventorySwap` switches slot set A / B |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/PlayerInventory.java`, `nx-l2:.../Inventory.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestSaveInventoryOrder.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ItemList,InventoryUpdate}.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/itemcontainer/Inventory.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestSaveInventoryOrder.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/Inventory.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/dualinventory/RequestExDualInventorySwap.java`
- Also: scheduled task `InventoryEnableTask` (Mobius Essence, Ess/IL): `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/InventoryEnableTask.java`
- Also: scheduled task `InventoryEnableTask` (nx-l2, HF): `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/InventoryEnableTask.java`

Packets

| Chronicle | Client → server                                                                                                                           | Server → client                                                                                                                                                                                                                                                                         |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestItemList` `0F`, `RequestSaveInventoryOrder` (Mobius only)                                                                         | `ItemList` `1B`, `InventoryUpdate` `27`, `GMViewItemList` `94`, `ExStorageMaxCount` `FE:2E`                                                                                                                                                                                             |
| High Five | `RequestItemList` `14`, `RequestSaveInventoryOrder` `D0:24`                                                                               | `ItemList` `11`, `InventoryUpdate` `21`, `ExQuestItemList` `FE:C6`, `GMViewItemList` `9A`, `ExStorageMaxCount` `FE:2F`                                                                                                                                                                  |
| Essence   | `RequestItemList` `14`, `RequestSaveInventoryOrder` `D0:24`, `RequestExDualInventorySwap` `D0:0267`, `RequestChangeInventorySlot` `D0:24` | `ItemList` `11`, `InventoryUpdate` `21`, `ExQuestItemList` `FE:C8`, `GMViewItemList` `9A`, `ExDualInventoryInfo` `FE:0334`, `ExAdenaInvenCount` `FE:013F`, `ExUserInfoInvenWeight` `FE:0167`, `ExItemScore` `FE:038E`, `ExStorageMaxCount` `FE:2F`, `UniqueGachaInvenAddItem` `FE:032F` |

Mask bits: Interlude — `InventoryUpdate.change` in `InventoryUpdate`: `ADD` 0x01, `MODIFY` 0x02, `REMOVE` 0x03. High Five — `InventoryUpdate.change` in `InventoryUpdate`: `ADD` 0x01, `MODIFY` 0x02, `REMOVE` 0x03. Essence — `InventoryUpdate.change` in `InventoryUpdate`: `ADD` 0x01, `MODIFY` 0x02, `REMOVE` 0x03; `UserInfoType` in `UserInfo`: `INVENTORY_LIMIT` 0x15.

Notes: warehouse sorting is a Mobius custom (`WarehouseSorting.ini` in `nx-l2:chronicles/high-five/datapack/config/Custom/`, `WarehouseSortingConfig` in IL).

Known fixes: `l2j-server@db059281` (2007-09-19, exploit-dupe) — - Fixed an item dupe exploit on transfers (

### item.use — Use item

ID: `item.use` · Shared: **engine**

Player uses an item: `UseItem` validates (stun/sleep/trade state, item reuse group), finds the item handler by item
type and runs it; potions, scrolls, shots, skills-on-item and special items are separate handlers registered in an
item-handler registry. Reuse delay is tracked per item and shared reuse group.
Sub-features: handler registry · reuse and shared groups · equip-on-use · pet use.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                             |
| --------- | ------- | --------------------------------------------------------- | ---------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server | baseline (aCis: `handler/itemhandlers`, Frozen `handler/itemhandlers`) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, premium, lineage2ts | same registry; HF `UseItem` ≈ IL (87 changed lines in Mobius IL vs HF) |
| Essence   | yes     | mobius-ess                                                | same registry; Essence `UseItem` adds more branches (≈167 lines vs HF) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/ItemHandler.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/IItemHandler.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/UseItem.java`, handlers `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/` (30 files)
- `mobius-il:java/org/l2jmobius/gameserver/handler/ItemHandler.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/UseItem.java`, `mobius-il:dist/game/data/scripts/handlers/items` (25 files)
- `mobius-ess:java/org/l2jmobius/gameserver/handler/ItemHandler.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/UseItem.java`, `mobius-ess:dist/game/data/scripts/handlers/items` (37 files)

Packets

| Chronicle | Client → server                          | Server → client                                                    |
| --------- | ---------------------------------------- | ------------------------------------------------------------------ |
| Interlude | `UseItem` `14`, `RequestPetUseItem` `8A` | `ExUseSharedGroupItem` `FE:49` (reuse group), `SystemMessage` `64` |
| High Five | `UseItem` `19`, `RequestPetUseItem` `94` | `ExUseSharedGroupItem` `FE:4A`, `SystemMessage` `62`               |
| Essence   | `UseItem` `19`, `RequestPetUseItem` `94` | `ExUseSharedGroupItem` `FE:4B`, `SystemMessage` `62`               |

Mask bits: Essence — `ItemListType` in `AbstractItemPacket`: `REUSE_DELAY` 0x10.

Notes: handler sets differ in size (IL 25, HF 30, Ess 37) — the extra handlers are broken out in `item.consumables`
and `item.misc-handlers`; the dispatch contract itself is identical.

Known fixes: `l2j-server@605655ea` (2011-07-31, exploit-dupe) — Pet exploit fix, by UnAfraid's request; `l2j-server-dp@de617c6d` (2012-01-19, missing-check) — Minor fix for [8613], let's restrict all items with Extractable Skills, not only the ones that consume an item, thanks U

### item.consumables — Consumables

ID: `item.consumables` · Shared: **partial**

Potions, elixirs, scrolls, soulshots / spiritshots, beast shots, and "item skill" items: the handler casts the skill
bound to the item and consumes it. Essence adds elixir usage limits and vitality items.
Sub-features: HP/MP/CP potions · shots (soul, spirit, blessed, beast, fish) · item-skill items · elixirs · resurrection scrolls.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                       |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | shots handlers (`SoulShots`, `SpiritShot`, `BlessedSpiritShot`, `BeastSoulShot`...); no `Elixir` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | + `Elixir`, `ItemSkills`, `ItemSkillsTemplate`                                                   |
| Essence   | yes     | mobius-ess                                    | + effect handler `ElixirUsageLimit`, `SpiritshotResistance`; shots simplified                    |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/{ItemSkills,ItemSkillsTemplate,Elixir,SoulShots,SpiritShot,BlessedSpiritShot,BeastSoulShot,BeastSpiritShot,FishShots}.java`
- `mobius-il:dist/game/data/scripts/handlers/items/{ItemSkills,SoulShots,SpiritShot,BlessedSpiritShot}.java`
- `mobius-ess:dist/game/data/scripts/handlers/items/{ItemSkills,SoulShots,SpiritShot}.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/ElixirUsageLimit.java`
- Also: item handler `ChallengePointsCoupon` (Mobius Essence, Ess): `mobius-ess:dist/game/data/scripts/handlers/items/ChallengePointsCoupon.java`
- Also: scheduled task `UsePotionTask` (nx-l2, HF): `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/creature/UsePotionTask.java`
- Also: scheduled task `UsePotionTask` (Mobius IL, IL): `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/creature/UsePotionTask.java`

Packets

| Chronicle | Client → server | Server → client                                                      |
| --------- | --------------- | -------------------------------------------------------------------- |
| Interlude | `UseItem` `14`  | `MagicSkillUse` `48`, `SystemMessage` `64`, `ExAutoSoulShot` `FE:12` |
| High Five | `UseItem` `19`  | `MagicSkillUse` `48`, `SystemMessage` `62`, `ExAutoSoulShot` `FE:0C` |
| Essence   | `UseItem` `19`  | `MagicSkillUse` `48`, `SystemMessage` `62`, `ExAutoSoulShot` `FE:0C` |

Notes: `ExAutoSoulShot` and `MagicSkillUse` are produced by the skill domain; listed here only as the visible outcome.
Presence of `Elixir` in IL was not verified in aCis (no Elixir class found in the listing).

Known fixes: `mobius@1c401951` (2021-12-31, missing-check) — Fixed Elixir use restrictions. Contributed by MacuK; `l2j-server-dp@de617c6d` (2012-01-19, missing-check) — Minor fix for [8613], let's restrict all items with Extractable Skills, not only the ones that consume an item, thanks U; `l2j-server@e341a06c` (2007-04-30, other) — Fix for #771 by Durgus: When a player tryed to use a scroll of resurrection while sitting it would disappear without eff.

### item.extractable — Extractable items

ID: `item.extractable` · Shared: **partial**

Boxes / sacks that give random items on use: product list with chance and count per box, rate multiplier. Essence adds
auto-peel: open a stack in a loop with a result window.
Sub-features: weighted product list · multiplier rate · auto-peel (Ess).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                      |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | aCis: skill handler `Extractable`; Frozen: `ExtractableItems` handler + `extractable_items.csv` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | item handler `ExtractableItems` + XML                                                           |
| Essence   | yes     | mobius-ess                                    | + auto-peel dialog (`ExRequestItemAutoPeel`, `ExResultItemAutoPeel`)                            |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/ExtractableItems.java`
- `mobius-il:dist/game/data/scripts/handlers/items/ExtractableItems.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/handler/skillhandlers/Extractable.java`; L2jFrozen: `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/handler/itemhandlers/ExtractableItems.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/autopeel/ExRequestItemAutoPeel.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/request/AutoPeelRequest.java`
- Also: item handler `FatedSupportBox` (Mobius Essence, Ess): `mobius-ess:dist/game/data/scripts/handlers/items/FatedSupportBox.java`

Packets

| Chronicle | Client → server                                                                                                    | Server → client                                                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `UseItem` `14`                                                                                                     | `SystemMessage` `64`, `InventoryUpdate` `27`                                                                                                    |
| High Five | `UseItem` `19`                                                                                                     | `SystemMessage` `62`, `InventoryUpdate` `21`                                                                                                    |
| Essence   | `UseItem` `19`, `ExRequestItemAutoPeel` `D0:0245`, `ExReadyItemAutoPeel` `D0:0244`, `ExStopItemAutoPeel` `D0:0246` | `SystemMessage` `62`, `InventoryUpdate` `21`, `ExResultItemAutoPeel` `FE:0304`, `ExReadyItemAutoPeel` `FE:0303`, `ExStopItemAutoPeel` `FE:0305` |

Notes: `Config RateExtractable` scales counts.

Known fixes: `l2j-server@3cc98d3a` (2007-10-02, crash-npe) — debugging npe in extractableitems; `l2j-server-dp@aadd2439` (2009-06-26, other) — extractable items fix

### item.misc-handlers — Special item handlers

ID: `item.misc-handlers` · Shared: **partial**

Items with a unique effect, each its own handler: books, maps, calculator, disguise, nickname-color change, rolling
dice, mercenary ticket, seven-signs record, summon items, pet food, seeds, harvester, event items, bypass items.
Sub-features: books / maps · calculator · disguise · dice · merc tickets · SSQ record · summon item · pet food · seeds.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                 |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | Mobius IL has SpecialXMas, SevenSignsRecord, MercTicket; no `NicknameColor`, `Elixir`      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | + `NicknameColor` (`RequestChangeNicknameColor`), `Bypass`, `TeleportBookmark`, `Disguise` |
| Essence   | yes     | mobius-ess                                    | `NicknameColor` kept; no MercTicket / SSQ record / Seed handlers in the listing            |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/{Book,Maps,Calculator,Disguise,SpecialXMas,SevenSignsRecord,Bypass,NicknameColor,RollingDice,MercTicket,SummonItems,PetFood,Seed,Harvester,EventItem,TeleportBookmark}.java`
- `mobius-il:dist/game/data/scripts/handlers/items` (25 files)
- `mobius-ess:dist/game/data/scripts/handlers/items` (37 files)

Packets

| Chronicle | Client → server                                      | Server → client                                                                                                      |
| --------- | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| Interlude | `UseItem` `14`                                       | `ShowCalculator` `DC`, `ExShowAdventurerGuideBook` `FE:37` (HF+), `SSQStatus` `F5`                                   |
| High Five | `UseItem` `19`, `RequestChangeNicknameColor` `D0:4F` | `ShowCalculator` `E2`, `ExShowAdventurerGuideBook` `FE:38`, `SSQStatus` `FB`, `ExRequestChangeNicknameColor` `FE:83` |
| Essence   | `UseItem` `19`, `RequestChangeNicknameColor` `D0:4C` | `ShowCalculator` `E2`, `ExShowAdventurerGuideBook` `FE:38`, `SSQStatus` `FB`, `ExRequestChangeNicknameColor` `FE:84` |

Notes: per-handler presence was matched by file name only; the full 16-class list is HF (file listing seen), Essence
list was not enumerated class by class.

### item.drop-pickup-destroy — Drop, pick up, destroy

ID: `item.drop-pickup-destroy` · Shared: **engine**

Player drops an item on the ground (distance and flag checks), picks one up (ownership / loot protection), destroys one
(non-destroyable flag, confirmation); pets pick up for owners.
Sub-features: drop with protection timer · pickup · destroy · pet pickup · item log hooks.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                     |
| --------- | ------- | --------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server | baseline                                                                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, premium, lineage2ts | same                                                                                           |
| Essence   | yes     | mobius-ess                                                | same flow; ≈68 / ≈45 changed lines (drop / destroy) from item-processing enum and flood checks |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestDropItem,RequestDestroyItem}.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/{RequestDropItem,RequestDestroyItem}.java` (6 / 10 lines vs Mobius HF)
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{RequestDropItem,RequestDestroyItem}.java`
- Also: action handler `ItemClick` (Mobius Essence, Ess/IL): `mobius-ess:dist/game/data/scripts/handlers/actions/click/ItemClick.java`
- Also: action handler `ItemShiftClick` (Mobius Essence, Ess/IL): `mobius-ess:dist/game/data/scripts/handlers/actions/shiftclick/ItemShiftClick.java`
- Also: scheduled call site `ItemManager` (Mobius Essence, Ess/IL): `mobius-ess:java/org/l2jmobius/gameserver/managers/ItemManager.java`
- Also: action handler `ItemClick` (nx-l2, HF): `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/ItemClick.java`
- Also: action handler `ItemShiftClick` (nx-l2, HF): `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/shiftclick/ItemShiftClick.java`
- Also: scheduled call site `ItemManager` (nx-l2, HF): `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/ItemManager.java`

Packets

| Chronicle | Client → server                                                                                                                                     | Server → client                                                                                                                          |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestDropItem` `12`, `RequestDestroyItem` `59`, `RequestGetItemFromPet` `8C`, `RequestPetGetItem` `8F`                                           | `DropItem` `0C`, `GetItem` `0D`, `SpawnItem` `0B`, `DeleteObject` `12`                                                                   |
| High Five | `RequestDropItem` `17`, `RequestDestroyItem` `60`, `RequestGetItemFromPet` `2C`, `RequestPetGetItem` `98`                                           | `DropItem` `16`, `GetItem` `17`, `SpawnItem` `05`, `DeleteObject` `08`                                                                   |
| Essence   | `RequestDropItem` `17`, `RequestDestroyItem` `60`, `RequestPetGetItem` `98` (without `RequestGetItemFromPet` `2C`: not in the Essence opcode table) | `DropItem` `16`, `GetItem` `17`, `SpawnItem` `05`, `DeleteObject` `08`, `ExRaidDropItemAnnounce` `FE:023F`, `ItemDeletionInfo` `FE:027B` |

Notes: catalog lists no `RequestGetItemFromPet` for Essence; not code-verified.

Known fixes: `l2j-server@db059281` (2007-09-19, exploit-dupe) — - Fixed an item dupe exploit on transfers (; `mobius@6eb6b14e` (2022-07-28, crash-npe) — Fixed probable NPE when admin uses not owned items. Contributed by nasseka; `l2jfrozen@58db9ab6` (2010-10-11, missing-check) — Augmented item cannot be discarded fixed.

### item.ground-items — Ground items

ID: `item.ground-items` · Shared: **engine**

Items on the ground: periodic save to DB so they survive a restart, auto-removal after a configurable time (separate for
herbs and equipment).
Sub-features: persistence across restart · auto-destroy timer · herb timer · equipment exception.

| Chronicle | Present | Builds (evidence)                             | Difference                                            |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline (`ItemsOnGroundManager`, `ItemsAutoDestroy`) |
| High Five | yes     | nx-l2, l2j-server, sunrise                    | same (Mobius IL = HF: 2 changed lines)                |
| Essence   | yes     | mobius-ess                                    | ≈37 changed lines (storage details)                   |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/ItemsOnGroundManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/ItemsAutoDestroyTaskManager.java`, `nx-l2:chronicles/high-five/datapack/sql/itemsonground.sql`
- `mobius-il:java/org/l2jmobius/gameserver/managers/ItemsOnGroundManager.java`, `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/ItemsAutoDestroyTaskManager.java`
- same names in `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server | Server → client                                        |
| --------- | --------------- | ------------------------------------------------------ |
| Interlude | —               | `DropItem` `0C`, `SpawnItem` `0B`, `DeleteObject` `12` |
| High Five | —               | `DropItem` `16`, `SpawnItem` `05`, `DeleteObject` `08` |
| Essence   | —               | `DropItem` `16`, `SpawnItem` `05`, `DeleteObject` `08` |

Notes: no dedicated client packet; visible through the drop / spawn packets.

Known fixes: `mobius@4d3e62f0` (2020-05-16, crash-npe) — ItemsOnGroundManager NPE prevention; `l2j-server@5604e0f6` (2006-11-26, other) — One more fix. (hehe, trac is not generating html for those commits :P ); `l2j-server@c38bc6bf` (2012-09-17, other) — Minor typo fix in ItemsOnGroundManager from [5644].

### item.warehouse — Private warehouse

ID: `item.warehouse` · Shared: **engine**

NPC warehouse keeper: deposit and withdraw by slot, deposit fee, clan warehouse, slot limits by race.
Sub-features: private warehouse · clan warehouse · deposit fee · slot limit · custom depositable list (Ess) ·
sorting (custom).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                   |
| --------- | ------- | --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline (`PcWarehouse` / `PlayerWarehouse`, `ClanWarehouse`)                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same (`PlayerWarehouse` 0 changed lines vs Mobius IL)                                                                                        |
| Essence   | yes     | mobius-ess                                    | + `GachaWarehouse`, `CustomDepositableItems.ini` (extra allow-list of depositable items); ≈34–40 changed lines in deposit / withdraw packets |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/{Warehouse,PlayerWarehouse,ClanWarehouse}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{SendWareHouseDepositList,SendWareHouseWithDrawList}.java`
- same names in `mobius-il:java/org/l2jmobius/gameserver`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/itemcontainer/{PcWarehouse,ClanWarehouse}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/{Warehouse,PlayerWarehouse,ClanWarehouse,GachaWarehouse}.java`, `mobius-ess:dist/game/config/Custom/CustomDepositableItems.ini`

Packets

| Chronicle | Client → server                                                     | Server → client                                                                                                       |
| --------- | ------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Interlude | `SendWareHouseDepositList` `31`, `SendWareHouseWithDrawList` `32`   | `WareHouseDepositList` `41`, `WareHouseWithdrawalList` `42`, `GMViewWarehouseWithdrawList` `95`                       |
| High Five | `SendWareHouseDepositList` `3B`, `SendWareHouseWithDrawList` `3C`   | `WareHouseDepositList` `41`, `WareHouseWithdrawalList` `42`, `GMViewWarehouseWithdrawList` `9B`, `WareHouseDone` `43` |
| Essence   | `WarehouseDepositList` `3B`, `WarehouseWithdrawList` `3C` (renamed) | `WarehouseDepositList` `41`, `WarehouseWithdrawList` `42`                                                             |

Notes: Mobius IL also has `SortedWareHouseWithdrawalList` (warehouse sorting custom).

Known fixes: `l2jfrozen@b61caa42` (2011-04-17, exploit-dupe) — - Fixed exploit to deposit Cursed Weapon on wh with phx; `l2j-server@421300eb` (2009-02-08, exploit-dupe) — When using Warehouse Freightman, target (L2PcInstance) is not completly load (initialized), just its PcFreight. This fix

### item.freight — Freight

ID: `item.freight` · Shared: **engine**

Freight service: send items to another character of the same account via a freight keeper; fee per item slot from
config, items must be freightable, recipient picks up at a freight keeper.
Sub-features: recipient list of the account · fee per slot · freightable flag · freight pickup.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                         |
| --------- | ------- | --------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server | baseline (`PcFreight` / `PlayerFreight`)                                                           |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                      | same; `PlayerFreight` 0 changed lines vs Mobius IL                                                 |
| Essence   | yes     | mobius-ess                                                | same fee (`ALT_FREIGHT_PRICE` × slots), `isFreightable` check; `RequestPackageSend` 18 lines vs HF |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/PlayerFreight.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestPackageSend,RequestPackageSendableItemList}.java`
- same names in `mobius-il:java/org/l2jmobius/gameserver`; Mobius Essence: same names in `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                                  | Server → client                                  |
| --------- | ---------------------------------------------------------------- | ------------------------------------------------ |
| Interlude | `RequestPackageSend` `9F`, `RequestPackageSendableItemList` `9E` | `PackageToList` `C2`, `PackageSendableList` `C3` |
| High Five | `RequestPackageSend` `A8`, `RequestPackageSendableItemList` `A7` | `PackageToList` `C8`, `PackageSendableList` `D2` |
| Essence   | `RequestPackageSend` `A8`, `RequestPackageSendableItemList` `A7` | `PackageToList` `C8`, `PackageSendableList` `D2` |

Notes: the Freight NPC bypass is in `item`/`npc` scripts (`handlers/bypass/npc/Freight`), see `npc.*`.

Known fixes: `l2jfrozen@f9a65cb2` (2011-07-27, exploit-dupe) — Fixed exploit with Freight to trade augmented weapons. (thanks EEwka for report); `l2j-server@421300eb` (2009-02-08, exploit-dupe) — When using Warehouse Freightman, target (L2PcInstance) is not completly load (initialized), just its PcFreight. This fix

### item.crystallize — Crystallize

ID: `item.crystallize` · Shared: **partial**

Break an item into crystals of its grade; the Crystallize skill level gates the grade; counts come from the template
(or from data in Essence, including a preview of the result).
Sub-features: skill-level gate · crystal count by grade/enchant · preview (Ess) · crystallize on enchant failure.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                            |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `RequestCrystallizeItem` + skill 248; counts from template                                                                                            |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same                                                                                                                                                  |
| Essence   | differs | mobius-ess                                    | + `RequestCrystallizeEstimate` (preview), cancel packet, `CrystallizableItems.xml` / `ItemCrystallizationData` drive output (≈72 changed lines vs HF) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestCrystallizeItem.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestCrystallizeItem.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/network/clientpackets/RequestCrystallizeItem.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestCrystallizeItem.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/crystalization/RequestCrystallizeEstimate.java`, `mobius-ess:dist/game/data/CrystallizableItems.xml`

Packets

| Chronicle | Client → server                                                                                             | Server → client                                                                |
| --------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Interlude | `RequestCrystallizeItem` `72`                                                                               | `ItemList` `1B`/`InventoryUpdate` `27`                                         |
| High Five | `RequestCrystallizeItem` `2F`                                                                               | `ItemList` `11`, `InventoryUpdate` `21`                                        |
| Essence   | `RequestCrystallizeItem` `2F`, `RequestCrystallizeEstimate` `D0:8F`, `RequestCrystallizeItemCancel` `D0:90` | `ItemList` `11`, `InventoryUpdate` `21`, `ExGetCrystalizingEstimation` `FE:E2` |

Notes: the Essence data file also holds the "items on enchant failure" templates (crystal id 91462 / 91463 amounts by
grade and enchant level).

Known fixes: `hl4p3x-l2-hf-hardcore@fd022a99` (2018-12-03, other) — Crystallization Fix Craft and Crystallization for everyone.

### item.timed-items — Shadow and timed items

ID: `item.timed-items` · Shared: **engine**

Items with a life timer (shadow items: mana drains while equipped, item destroyed at 0) and items with an expiry date;
two task managers tick them, packets show the remaining mana.
Sub-features: shadow item mana · timed expiry · NPC buy of shadow items.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                               |
| --------- | ------- | --------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | aCis: `ShadowItemTaskManager`; Mobius: `ItemManaTaskManager` + `ItemLifeTimeTaskManager` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same; both task managers 0 changed lines vs Mobius IL                                    |
| Essence   | yes     | mobius-ess                                    | same task managers; no `BuyShadowItem` NPC bypass                                        |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/{ItemManaTaskManager,ItemLifeTimeTaskManager}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/BuyShadowItem.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/{ItemManaTaskManager,ItemLifeTimeTaskManager}.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/taskmanager/ShadowItemTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/{ItemManaTaskManager,ItemLifeTimeTaskManager}.java`

Packets

| Chronicle | Client → server | Server → client                                     |
| --------- | --------------- | --------------------------------------------------- |
| Interlude | —               | `InventoryUpdate` `27` (mana), `SystemMessage` `64` |
| High Five | —               | `InventoryUpdate` `21`, `SystemMessage` `62`        |
| Essence   | —               | `InventoryUpdate` `21`, `SystemMessage` `62`        |

Notes: aCis merges both timers into one task manager.

### item.item-link — Item link in chat

ID: `item.item-link` · Shared: **partial (HF+Ess)**

Player shows an item in chat as a clickable link; the client asks the server for the item data and gets it back.
Sub-features: item link request · item data reply.

| Chronicle | Present | Builds (evidence)                                | Difference                                           |
| --------- | ------- | ------------------------------------------------ | ---------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen, l2jbrasil, hellas    | no `RequestExRqItemLink` in any IL root              |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline                                             |
| Essence   | yes     | mobius-ess                                       | same pair, renamed (`ExRqItemLink` / `ExRpItemLink`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExRqItemLink.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExRpItemLink.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestExRqItemLink.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExRpItemLink.java`

Packets

| Chronicle | Client → server               | Server → client        |
| --------- | ----------------------------- | ---------------------- |
| Interlude | —                             | —                      |
| High Five | `RequestExRqItemLink` `D0:1E` | `ExRpItemLink` `FE:6C` |
| Essence   | `RequestExRqItemLink` `D0:1E` | `ExRpItemLink` `FE:6D` |

Notes: Mobius IL files list has no such class (verified by file name in Mobius IL and the IL roots).

Known fixes: `mobius@534e1493` (2018-09-06, crash-npe) — Fixed probable RequestExRqItemLink NPE; `l2j-server@45536632` (2008-06-21, other) — Fix for augmentation display for items posted in chat.

### item.recovery — Item recovery

ID: `item.recovery` · Shared: **chronicle-only**

Essence: restore lost items — "penalty item drop" list (items dropped on death-PK) and a black-coupon restore of
deleted or broken items from a history list.
Sub-features: item restore list · black coupon restore · deleted-item history.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                    |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | no restore flow                                                                                               |
| High Five | no      | nx-l2, l2j-server, sunrise | no restore flow in public builds                                                                              |
| Essence   | yes     | mobius-ess                 | `blackcoupon/RequestItemRestoreList`, `RequestItemRestore`, `BlackCouponRestoreCategory`, `ItemRestoreHolder` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/blackcoupon/RequestItemRestoreList.java`, `mobius-ess:.../RequestItemRestore.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/holders/ItemRestoreHolder.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/item/enums/BlackCouponRestoreCategory.java`

Packets

| Chronicle | Client → server                                                    | Server → client                                                                                                                                          |
| --------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                  | —                                                                                                                                                        |
| High Five | —                                                                  | —                                                                                                                                                        |
| Essence   | `RequestItemRestoreList` `D0:01F8`, `RequestItemRestore` `D0:01F9` | `ExItemRestoreList` `FE:02B4`, `ExItemRestore` `FE:02B5`, `ExItemRestoreOpen` `FE:0329`, `ExPenaltyItemList` `FE:02A0`, `ExPenaltyItemRestore` `FE:02A1` |

Notes: the "penalty item drop" part (`ExRequestPenaltyItemList/Restore`) belongs to `pvp.pk-drop` (group 6); not
duplicated here.

### item.delayed-delivery — Delayed item delivery

ID: `item.delayed-delivery` · Shared: **partial (HF+Ess)**

Items waiting for the player to claim them outside the normal flow (web purchases, event rewards): a premium-item list
the client shows and a withdraw request moves them to the inventory.
Sub-features: premium item list · notify on arrival · withdraw by index.

| Chronicle | Present | Builds (evidence)                             | Difference                                          |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | no premium-item packets in any IL root              |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | `PremiumItem` holder + `RequestWithDrawPremiumItem` |
| Essence   | yes     | mobius-ess                                    | same flow, names changed (`ExWithdrawPremiumItem`)  |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestWithDrawPremiumItem.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/holders/PremiumItem.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ExGetPremiumItemList,ExNotifyPremiumItem}.java`
- same names in `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                      | Server → client                                               |
| --------- | ------------------------------------ | ------------------------------------------------------------- |
| Interlude | —                                    | —                                                             |
| High Five | `RequestWithDrawPremiumItem` `D0:52` | `ExGetPremiumItemList` `FE:86`, `ExNotifyPremiumItem` `FE:85` |
| Essence   | `ExWithdrawPremiumItem` `D0:4F`      | `ExPremiumItemList` `FE:87`, `ExNotifyPremiumItem` `FE:86`    |

Notes: Mobius IL has no `PremiumItem` class (grep-by-name).

### item.variables — Item variables

ID: `item.variables` · Shared: **partial (HF+Ess)**

Arbitrary key/value properties stored with an item instance (persisted in a table), used by scripts and custom
systems.
Sub-features: persistent key/value store per item.

| Chronicle | Present | Builds (evidence)                            | Difference                                                                                                                                      |
| --------- | ------- | -------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il (custom backport), acis, l2jfrozen | not in the Interlude roots (aCis, L2jFrozen); Mobius IL only carries a backport of the High Five class (`ItemVariables` + `item_variables.sql`) |
| High Five | yes     | nx-l2                                        | same class                                                                                                                                      |
| Essence   | yes     | mobius-ess                                   | same class (`mechanics/variables/ItemVariables`)                                                                                                |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/variables/ItemVariables.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/variables/ItemVariables.java`; Mobius Essence: `mobius-ess:java/org/l2jmobius/gameserver/mechanics/variables/ItemVariables.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | — (server-only) | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: server-side only; no client visibility. `partial (HF+Ess)` rather than `engine`: the Interlude roots (aCis, L2jFrozen)
have no such mechanic and the Mobius Interlude class is a backport of the High Five one.
