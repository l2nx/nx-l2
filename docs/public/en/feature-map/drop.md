# Drop and spoil

Drop calculation, spoil and sweep, loot protection, herbs, soul crystals, treasure chests and loot helpers.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### drop.calculation — Drop calculation

ID: `drop.calculation` · Shared: **partial**

Computation of what a killed NPC drops: grouped and ungrouped drop lists in the NPC template, server rates, per-item rate
overrides, premium and effect bonuses, level-gap penalty, limits on the number of drops, champion and raid multipliers.
Sub-features: drop groups · ungrouped drops · rate overrides by item id · premium multiplier · level-gap penalty · max drop occurrences · seeded-monster filter (manor).

| Chronicle | Present | Builds (evidence)                                                   | Difference                                                                                                                                          |
| --------- | ------- | ------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`DropCategory`), l2jfrozen (`custom_droplist.sql`) | aCis category-based lists; L2jFrozen SQL drop tables                                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                | baseline: `NpcTemplate.calculateDrops` with `DropGroupHolder`, level-gap scaling for adena and items                                                |
| Essence   | yes     | mobius-ess                                                          | extra drop type FORTUNE; `Stat.BONUS_DROP_RATE` (+ L-coin bonus) instead of a single multiplier; level gap computed differently (`killer - victim`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/NpcTemplate.java` (`calculateDrops`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Attackable.java` (`doItemDrop`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/npc/{DropHolder,DropGroupHolder}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/RatesConfig.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/templates/NpcTemplate.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/Attackable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/templates/NpcTemplate.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Attackable.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/RatesConfig.java`
- `acis:**/DropCategory.java` (found by file name)

Packets

| Chronicle | Client → server                      | Server → client                   |
| --------- | ------------------------------------ | --------------------------------- |
| Interlude | `RequestDropItem` `12` (player drop) | `DropItem` `0C`, `SpawnItem` `0B` |
| High Five | `RequestDropItem` `17`               | `DropItem` `16`, `SpawnItem` `05` |
| Essence   | `RequestDropItem` `17`               | `DropItem` `16`, `SpawnItem` `05` |

Notes: the drop data lives inside NPC templates, so `npc.templates` and `drop.calculation` share XML.

### drop.spoil — Spoil and sweep

ID: `drop.spoil` · Shared: **partial**

Spoil skill marks a monster; after death the spoiler can sweep the corpse for the spoil list; others cannot sweep during a protection window.
Sub-features: spoil effect · sweeper skill · sweep protection window · spoil rate bonus · sweepable corpse timer.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                 |
| --------- | ------- | --------------------------------------------------------- | ---------------------------------------------------------- |
| Interlude | yes     | mobius-il (`Spoil`, `Sweeper`, `ConditionPlayerCanSweep`) | same; spoil list DropType.SPOIL                            |
| High Five | yes     | nx-l2, l2j-server, sunrise                                | baseline                                                   |
| Essence   | yes     | mobius-ess                                                | adds `BonusSpoilRate` effect and `OpSweeperSkillCondition` |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/{Spoil,Sweeper}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerCanSweep.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Attackable.java`
- `mobius-il:dist/game/data/scripts/handlers/skill/effects/{Spoil,Sweeper}.java`
- `mobius-ess:dist/game/data/scripts/handlers/skill/effects/{Spoil,Sweeper,BonusSpoilRate}.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/OpSweeperSkillCondition.java`

Packets

| Chronicle | Client → server            | Server → client                              |
| --------- | -------------------------- | -------------------------------------------- |
| Interlude | skill use (domain `skill`) | `InventoryUpdate` `27`, `SystemMessage` `64` |
| High Five | skill use (domain `skill`) | `InventoryUpdate` `21`, `SystemMessage` `62` |
| Essence   | skill use (domain `skill`) | `InventoryUpdate` `21`, `SystemMessage` `62` |

Notes: aCis / L2jFrozen have spoil as well (not compared in detail; file-name match found no separate class).

Known fixes: `l2j-server@141d75ae` (2007-02-03, wrong-formula-value) — New magic/spoil failures calculation formula and spoil/sweep fixes; `l2j-server@e6326a87` (2008-01-14, other) — Spoil fix. (if your target change after casting spoil it will spoil the old one instead of the current one) [by kerberos_20].

### drop.protection — Drop protection

ID: `drop.protection` · Shared: **partial**

Dropped items are protected for a time for the killer / party; others cannot pick them up until the timer ends.
Sub-features: owner protection · timer · party rule.

| Chronicle | Present | Builds (evidence)                                                     | Difference                                 |
| --------- | ------- | --------------------------------------------------------------------- | ------------------------------------------ |
| Interlude | yes     | mobius-il, acis (`DropProtection` in acis-180), l2jfrozen, l2j-server | same idea                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                  | baseline                                   |
| Essence   | yes     | mobius-ess                                                            | `DropProtection` 88% of HF (small changes) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/holders/DropProtection.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/item/holders/DropProtection.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/item/holders/DropProtection.java`
- `l2jfrozen:**/DropProtection.java` (found by file name)

Packets

| Chronicle | Client → server         | Server → client                      |
| --------- | ----------------------- | ------------------------------------ |
| Interlude | `Action` `04` (pick-up) | `SystemMessage` `64`, `GetItem` `0D` |
| High Five | `Action` `1F` (pick-up) | `SystemMessage` `62`, `GetItem` `17` |
| Essence   | `Action` `1F` (pick-up) | `SystemMessage` `62`, `GetItem` `17` |

Notes: aCis public (`acis-public`) has no such class (it exists in `acis-180`).

### drop.event-drop — Event and dynamic drop

ID: `drop.event-drop` · Shared: **partial**

Extra item drops from any monster for events (holiday items), with chance, level range and limits.
Sub-features: event drop list · level range · min/max count · date window.

| Chronicle | Present | Builds (evidence)                                                                                         | Difference                                          |
| --------- | ------- | --------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| Interlude | yes     | mobius-il (`EventDropManager`), acis not found, l2jfrozen (`EventDroplist`), l2j-server (`EventDroplist`) | L2J-style list class                                |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                                                                      | baseline: `EventDropManager` + `EventDropHolder`    |
| Essence   | yes     | mobius-ess                                                                                                | manager moved to `managers/events/EventDropManager` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/EventDropManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/npc/EventDropHolder.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/EventDropManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/EventDropManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/npc/EventDropHolder.java`
- `l2jfrozen:**/EventDroplist.java` (found by file name)

Packets: none.

Notes: used by `LongTimeEvent` scripts (domain `event`).

### drop.herbs — Herbs

ID: `drop.herbs` · Shared: **partial**

Herbs: instantly consumed drop items (restore HP/MP, short buffs); dropped as a separate rate class, auto-destroyed after a short time, optional auto-loot.
Sub-features: herb drop groups · herb rate · auto-destroy timer · herb auto-loot.

| Chronicle | Present | Builds (evidence)                                                                                                | Difference                                                                                                  |
| --------- | ------- | ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (`herb_droplist_groups.sql`), acis (`RATE_DROP_HERBS`, `AutoLootHerbs`), l2jfrozen (`RateCommonHerbs`) | rates by herb class in configs                                                                              |
| High Five | yes     | nx-l2, l2j-server, eternity                                                                                      | baseline: `herb_droplist_groups`                                                                            |
| Essence   | yes     | mobius-ess                                                                                                       | no `herb_droplist_groups`; herbs recognised by item flag (`hasExImmediateEffect()`) in the drop calculation |

Entry points

- `nx-l2:chronicles/high-five/datapack/sql/herb_droplist_groups.sql`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/NpcTemplate.java`, `nx-l2:chronicles/high-five/datapack/config/Player.ini` (`AutoLootHerbs`, line 719)
- `mobius-il:dist/db_installer/sql/game/herb_droplist_groups.sql`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/templates/NpcTemplate.java` (`hasExImmediateEffect`)
- `acis:aCis_gameserver/java/net/sf/l2j/Config.java` (`RATE_DROP_HERBS`, `AUTO_LOOT_HERBS`, `HERB_AUTO_DESTROY_TIME`)

Packets: none (herb is an item; `INVENTORY_UPDATE` not sent because it is consumed on pickup).

Notes: the herb effect itself is a skill / item handler (domain `item`).

### drop.soul-crystals — Soul crystal leveling

ID: `drop.soul-crystals` · Shared: **partial (IL+HF)**

Absorbing souls from monsters levels up soul crystals (stage 1-13 by colour); chance of failure, break and level restrictions per monster.
Sub-features: absorb chance · crystal level table · monster restrictions.

| Chronicle | Present | Builds (evidence)                                                                                          | Difference                                    |
| --------- | ------- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| Interlude | yes     | mobius-il (`LevelUpCrystalData`), acis (`soulCrystals.xml`, `SoulCrystalData`), l2jfrozen (`SoulCrystals`) | aCis item handler `SoulCrystals`              |
| High Five | yes     | nx-l2, l2j-server                                                                                          | baseline: `LevelUpCrystalData.xml`            |
| Essence   | no      | mobius-ess (no crystal level data)                                                                         | absent: no `LevelUpCrystalData` in `RoseVain` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/LevelUpCrystalData.java`, `nx-l2:chronicles/high-five/datapack/data/LevelUpCrystalData.xml`, `nx-l2:chronicles/high-five/datapack/data/xsd/LevelUpCrystalData.xsd`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/LevelUpCrystalData.java`, `mobius-il:dist/game/data/LevelUpCrystalData.xml`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/SoulCrystalData.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/handler/itemhandlers/SoulCrystals.java`, `acis:aCis_datapack/data/xml/soulCrystals.xml`

Packets: none (item use; `INVENTORY_UPDATE`).

Notes: reference data without instances; named without `-template` (справочник, исключение).

Known fixes: `l2j-server@15fb7484` (2006-11-06, other) — Correct System Messages for Soul Crystals.

### drop.treasure-chests — Treasure chests

ID: `drop.treasure-chests` · Shared: **partial**

Chest monsters: spawn with a chance next to other monsters, opened with a key skill or smashed, may have a trap / fake-chest behaviour, give drops on opening.
Sub-features: chest instance · unlock skill (`OpenChest`) · chest AI · drops from chests.

| Chronicle | Present | Builds (evidence)                                                                                                 | Difference                                                         |
| --------- | ------- | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| Interlude | yes     | mobius-il (`Chest`, script `ai/others/Chests`), acis (`Chest`, `TreasureChest` AI), l2jfrozen (`L2ChestInstance`) | IL `Chest` differs from HF (73% equal); script-driven in Mobius IL |
| High Five | yes     | nx-l2, l2j-server                                                                                                 | baseline: `Chest` instance + `OpenChest` effect                    |
| Essence   | yes     | mobius-ess                                                                                                        | `Chest` identical to HF (100%)                                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Chest.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/OpenChest.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/Chest.java`, `mobius-il:dist/game/data/scripts/ai/others/Chests.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/OpenChest.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/Chest.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/Chest.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/scripting/script/ai/individual/Monster/WarriorBase/Chests/TreasureChest.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/TreasureManager.java`

Packets: none (skill packets and `NPC_INFO`).

Notes: in HF the 100% match with Essence and 73% with IL shows that Mobius IL is not a faithful HF copy for this class.

Known fixes: `hellas@98ff2ad6` (2013-08-30, other) — Fixed drop error Zodiac optimize Treasure Chest revive and death added

### drop.auto-loot — Auto loot

ID: `drop.auto-loot` · Shared: **partial**

Mobius config feature: items dropped by monsters go directly to the killer's inventory; separate switches for raids, herbs, item-id lists and slot limit.
Sub-features: global switch · raids · herbs · by item id · slot limit · voiced command (L2J).

| Chronicle | Present | Builds (evidence)                                                                                         | Difference                       |
| --------- | ------- | --------------------------------------------------------------------------------------------------------- | -------------------------------- |
| Interlude | differs | mobius-il (`PlayerConfig` keys), acis (`AutoLoot`, `AutoLootHerbs` keys), l2jfrozen (`AutoLoot` keys)     | server config option, not retail |
| High Five | yes     | nx-l2 (`Player.ini` `AutoLoot`, `AutoLootRaids`, `AutoLootHerbs`), l2j-server (`AutoLoot` voiced command) | baseline                         |
| Essence   | yes     | mobius-ess (`PlayerConfig`)                                                                               | same keys                        |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/PlayerConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Attackable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Player.java`, `nx-l2:chronicles/high-five/datapack/config/Player.ini` (lines 719-736)
- `mobius-il:java/org/l2jmobius/gameserver/config/PlayerConfig.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/Attackable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/config/PlayerConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Attackable.java`

Packets: none.

Notes: HF-era forum builds expose it as a voiced command `.autoloot` (L2J, Eternity).

### drop.drop-search — Drop search

ID: `drop.drop-search` · Shared: **partial**

Mobius custom: Community Board page that searches which monsters drop a given item and shows the drop chance.
Sub-features: item search · monster list · chance display.

| Chronicle | Present | Builds (evidence)                | Difference                    |
| --------- | ------- | -------------------------------- | ----------------------------- |
| Interlude | differs | mobius-il (custom backport only) | not found in aCis / L2jFrozen |
| High Five | yes     | nx-l2                            | baseline: `DropSearchBoard`   |
| Essence   | yes     | mobius-ess                       | same board                    |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/DropSearchBoard.java`
- `mobius-il:dist/game/data/scripts/handlers/bypass/communityboard/DropSearchBoard.java`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/communityboard/DropSearchBoard.java`

Packets

| Chronicle | Client → server                          | Server → client  |
| --------- | ---------------------------------------- | ---------------- |
| Interlude | `RequestBypassToServer` `21` (`_bbs...`) | `ShowBoard` `6E` |
| High Five | `RequestBypassToServer` `23` (`_bbs...`) | `ShowBoard` `7B` |
| Essence   | `RequestBypassToServer` `23` (`_bbs...`) | `ShowBoard` `7B` |

Notes: only the three Mobius builds in the 44 `full` roots contain it.

Known fixes: `mobius@7563b887` (2021-05-10, other) — Small fix for DropSearchBoard handler. Contributed by CostyKiller.
