# Crafting

Recipes, manufacturing and the private workshop.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### craft.recipe-book — Recipe book

ID: `craft.recipe-book` · Shared: **engine**

Learning recipes from recipe items, two lists (dwarven and common) with per-list limits, opening the book to craft
and deleting a recipe.
Sub-features: learn from recipe item · dwarven / common books · limits · delete · open book.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                              |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `RecipeBook` (aCis) / `RecipeList`; 50 + 50 default slots (`DwarfRecipeLimit`, `CommonRecipeLimit`)     |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same                                                                                                    |
| Essence   | yes     | mobius-ess                                    | same logic; `RequestRecipeBookDestroy` renamed `RequestRecipeItemDelete`; 594 recipes (HF 1000, IL 871) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/recipe/RecipeList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestRecipeBookOpen,RequestRecipeBookDestroy}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/Recipes.java`, `nx-l2:chronicles/high-five/datapack/data/Recipes.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/RecipeData.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestRecipeBookOpen.java`, `mobius-il:dist/game/data/Recipes.xml`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/RecipeData.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/container/player/RecipeBook.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestRecipeBookOpen.java`, `mobius-ess:dist/game/data/Recipes.xml`

Packets

| Chronicle | Client → server                                               | Server → client           |
| --------- | ------------------------------------------------------------- | ------------------------- |
| Interlude | `RequestRecipeBookOpen` `AC`, `RequestRecipeBookDestroy` `AD` | `RecipeBookItemList` `D6` |
| High Five | `RequestRecipeBookOpen` `B5`, `RequestRecipeBookDestroy` `B6` | `RecipeBookItemList` `DC` |
| Essence   | `RequestRecipeBookOpen` `B5`, `RequestRecipeItemDelete` `B6`  | `RecipeBookItemList` `DC` |

Notes: recipe counts are entries matching `<recipe`/`<item id` in `Recipes.xml`, rough.

### craft.manufacture — Manufacture

ID: `craft.manufacture` · Shared: **partial**

Crafting an item from a recipe: ingredient check, MP cost, success rate, the Create Common / Create Dwarven skill, rare
production, masterwork double craft, the crafting window MP bar. Not allowed in combat or duel unless configured.
Sub-features: success chance · MP and ingredient use · rare production · masterwork / double craft · craft in combat
config · Essence craft-rate stat.

| Chronicle | Present | Builds (evidence)                                                                      | Difference                                                                                |
| --------- | ------- | -------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`RecipeItemMaker`), l2jfrozen (`RecipeController`), l2jbrasil, hellas | success rate + MP; no rare production and no masterwork in Mobius IL's `RecipeManager`    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                                   | + rare product (`getRareItemId`), `CraftMasterwork`, `CraftDoubleCraftChance` config      |
| Essence   | differs | mobius-ess                                                                             | + rare product; success rate adds `Stat.CRAFT_RATE`; `CraftMasterwork = False` by default |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/RecipeManager.java` (rare production ~l.570), `nx-l2:chronicles/high-five/datapack/config/Player.ini` (`CraftingEnabled`, `CraftingInCombat`, `CraftMasterwork`)
- `mobius-il:java/org/l2jmobius/gameserver/managers/RecipeManager.java`; aCis: `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/craft/RecipeItemMaker.java`; L2jFrozen: `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/controllers/RecipeController.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/RecipeManager.java` (`Stat.CRAFT_RATE`, rare ~l.665)

Packets

| Chronicle | Client → server                                                    | Server → client           |
| --------- | ------------------------------------------------------------------ | ------------------------- |
| Interlude | `RequestRecipeItemMakeInfo` `AE`, `RequestRecipeItemMakeSelf` `AF` | `RecipeItemMakeInfo` `D7` |
| High Five | `RequestRecipeItemMakeInfo` `B7`, `RequestRecipeItemMakeSelf` `B8` | `RecipeItemMakeInfo` `DD` |
| Essence   | `RequestRecipeItemMakeInfo` `B7`, `RequestRecipeItemMakeSelf` `B8` | `RecipeItemMakeInfo` `DD` |

Notes: Mobius IL = HF engine (56 changed lines in `RecipeManager`) — the missing masterwork / rare branch there is a fact about
the Mobius port, aCis / L2jFrozen behaviour is the Interlude baseline.

Known fixes: `l2j-server@25454b97` (2006-11-29, exploit-dupe) — Omg i hate hlapex >.<. Fix for craft exploit; `mobius@2b7de9b7` (2020-04-23, missing-check) — Fixed offline craft checks. Contributed by ihearcolors; `hellas@3604b520` (2013-08-05, other) — Craft animation fix.

### craft.private-workshop — Private workshop (manufacture store)

ID: `craft.private-workshop` · Shared: **engine**

A crafter lists recipes with prices and a message; others order crafts for a fee, the crafter produces the item with
their own materials/MP, or the customer supplies materials. Slot count configurable.
Sub-features: list recipes · message · make on order · customer materials · slot limit.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                               |
| --------- | ------- | --------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline                                                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same (≈2–5 changed lines per packet vs Mobius IL)                                        |
| Essence   | yes     | mobius-ess                                    | same logic; renamed packets (`RecipeShopMakeDo`, `RecipeShopSellList`, `RecipeStoreMsg`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestRecipeShopListSet,RequestRecipeShopMakeItem,RequestRecipeShopManageList,RequestRecipeShopMessageSet,RequestRecipeShopMakeInfo}.java`
- Mobius IL / Essence: same names in `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                           | Server → client                                                                                          |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestRecipeShopListSet` `B2`, `RequestRecipeShopMakeInfo` `B5`, `RequestRecipeShopMakeItem` `B6`, `RequestRecipeShopManagePrev` `B7`, `RequestRecipeShopManageQuit` `B3`, `RequestRecipeShopMessageSet` `B1`                                                                           | `RecipeShopManageList` `D8`, `RecipeShopSellList` `D9`, `RecipeShopItemInfo` `DA`, `RecipeShopMsg` `DB`  |
| High Five | `RequestRecipeShopListSet` `BB`, `RequestRecipeShopMakeInfo` `BE`, `RequestRecipeShopMakeItem` `BF`, `RequestRecipeShopManagePrev` `C0`, `RequestRecipeShopManageQuit` `BC`, `RequestRecipeShopMessageSet` `BA`, `RequestRecipeShopManageList` `B9`, `RequestRecipeShopManageCancel` `BD` | `RecipeShopManageList` `DE`, `RecipeShopSellList` `DF`, `RecipeShopItemInfo` `E0`, `RecipeShopMsg` `E1`  |
| Essence   | `RecipeShopListSet` `BB`, `RecipeShopMakeInfo` `BE`, `RecipeShopMakeDo` `BF`, `RecipeShopManageQuit` `BC`, `RecipeShopMessageSet` `BA`, `RecipeShopSellList` `C0`                                                                                                                         | `RecipeShopManageList` `DE`, `RecipeShopSellList` `DF`, `RecipeShopItemInfo` `E0`, `RecipeStoreMsg` `E1` |

Notes: `ManufactureStoreSlots` and `StoreRecipeShopList` (persist across restart) are configuration in all builds.

Known fixes: `l2j-server@6d425aee` (2009-09-30, exploit-dupe) — Visual exploit fix: Player can add any recipe to private craft; `hellas@cd9965d0` (2014-10-31, exploit-dupe) — Fixed Phx exploit thanks to Clodoaldo Deniiz :) and npe with shop

### craft.random-craft — Random craft (Essence)

ID: `craft.random-craft` · Shared: **chronicle-only**

Essence: spend points to roll a random craft from a reward list (refresh shows another reward), lock slots, make the
craft, and extract an item back into points.
Sub-features: points · refresh · slot lock · make · extract.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                        |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | —                                                                                                 |
| High Five | no      | nx-l2, l2j-server, sunrise | —                                                                                                 |
| Essence   | yes     | mobius-ess                 | `RandomCraft.ini`, `RandomCraftRewardData.xml`, `RandomCraftExtractData.xml`, `PlayerRandomCraft` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/randomcraft/ExRequestRandomCraftMake.java`, `mobius-ess:.../ExRequestRandomCraftRefresh.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/itemcontainer/PlayerRandomCraft.java`, `mobius-ess:dist/game/config/RandomCraft.ini`, `mobius-ess:dist/game/data/RandomCraftRewardData.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                             | Server → client                                                                                                                                                                                                                                        |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | —                                                                                                                                                                                                                                           | —                                                                                                                                                                                                                                                      |
| High Five | —                                                                                                                                                                                                                                           | —                                                                                                                                                                                                                                                      |
| Essence   | `ExCraftRandomInfo` `D0:019B`, `ExCraftRandomRefresh` `D0:019D`, `ExCraftRandomLockSlot` `D0:019C`, `ExCraftRandomMake` `D0:019E`, `ExCraftExtract` `D0:019A`, `RequestCreateSlotProbList` `D0:02AF`, `RequestCreateItemProbList` `D0:02AE` | `ExCraftRandomInfo` `FE:0247`, `ExCraftRandomRefresh` `FE:0249`, `ExCraftRandomLockSlot` `FE:0248`, `ExCraftRandomMake` `FE:024A`, `ExCraftExtract` `FE:0246`, `ExCraftInfo` `FE:0245`, `CreateSlotProbList` `FE:0395`, `CreateItemProbList` `FE:0394` |

Notes: the catalog lists the server → client replies only as the same names; per-direction split not checked.

Known fixes: `mobius@7a268f0f` (2022-04-21, other) — Fixed RandomCraftData getNewReward IOOBE.
