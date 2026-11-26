# Pets and summons

Pets, servitors, baby pets, tamed beasts, mounts, decoys and traps.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### pet.pets — Pets

ID: `pet.pets` · Shared: **partial**

Pets summoned from a collar item: levels and experience, HP/MP, death and resurrection rules, storage in the database, renaming, restore after reconnect.
Sub-features: summon from collar · level and exp tables · pet status window · death penalty · rename · restore on reconnect.

| Chronicle | Present | Builds (evidence)                                                                      | Difference                                                                                                      |
| --------- | ------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`Pet`, `PetDataEntry`), l2jfrozen (`L2PetInstance`, `L2PetDataTable`) | aCis stores pet data in the template records                                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                                   | baseline: `Pet`, `PetDataTable`, `PetData`, 49 files in `data/stats/pets`                                       |
| Essence   | yes     | mobius-ess                                                                             | `Pet` 67% and `PetDataTable` 76% of HF; adds pet types, pet equipment, pet extraction, pet ranking, pet preview |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Pet.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/PetDataTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/holders/PetData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/PetManager.java`, `nx-l2:chronicles/high-five/datapack/data/stats/pets/`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/Pet.java`, `mobius-il:java/org/l2jmobius/gameserver/data/xml/PetDataTable.java`, `mobius-il:dist/game/data/stats/pets`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/Pet.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/{PetDataTable,PetTypeData}.java`, `mobius-ess:dist/game/data/stats/pets`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/Pet.java`
- `l2jfrozen:**/L2PetInstance.java` (found by file name)
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/PetClick.java`
- `mobius-il:dist/game/data/scripts/handlers/actions/click/PetClick.java`
- `mobius-ess:dist/game/data/scripts/handlers/actions/click/PetClick.java`

Packets

| Chronicle | Client → server                                                                 | Server → client                                                                                                                                                                                    |
| --------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestChangePetName` `89`, `RequestPetUseItem` `8A`, `RequestPetGetItem` `8F` | `PetInfo` `B1`, `PetStatusShow` `B0`, `PetStatusUpdate` `B5`, `PetDelete` `B6`                                                                                                                     |
| High Five | `RequestChangePetName` `93`, `RequestPetUseItem` `94`, `RequestPetGetItem` `98` | `PetInfo` `B2`, `PetStatusShow` `B1`, `PetStatusUpdate` `B6`, `PetDelete` `B7`, `ExPartyPetWindowAdd` `FE:18`, `ExPartyPetWindowUpdate` `FE:19`, `ExPartyPetWindowDelete` `FE:6A`                  |
| Essence   | `RequestChangePetName` `93`, `RequestPetUseItem` `94`, `RequestPetGetItem` `98` | `PetInfo` `B2`, `ExPetInfo` `FE:015F`, `ExPetSkillList` `FE:0272`, `ExLoadPetPreview` `FE:03A6`, `ExPartyPetWindowAdd` `FE:18`, `ExPartyPetWindowUpdate` `FE:19`, `ExPartyPetWindowDelete` `FE:6B` |

Notes: `Pet` in HF and IL is 96% equal (backport); Essence diverges.

Known fixes: `l2j-server@71d7026b` (2008-03-09, wrong-formula-value) — great wolf kill/res fix, now checking only pets_stats from npctemplate minimum level and higher. After this some extra level stats could be…; `mobius@257ffff8` (2022-10-15, other) — Fixed pet skill display. Thanks to petryxa.

### pet.feeding — Pet feeding

ID: `pet.feeding` · Shared: **partial**

Pets lose satiety over time and must be fed with food items; hungry pets weaken and may leave; feeding interval is configurable.
Sub-features: feed task · pet food item handler · hunger penalty.

| Chronicle | Present | Builds (evidence)                                       | Difference            |
| --------- | ------- | ------------------------------------------------------- | --------------------- |
| Interlude | yes     | mobius-il (`PetFeedTask`, `PetFood`), acis (`PetFoods`) | aCis food table class |
| High Five | yes     | nx-l2, l2j-server, eternity                             | baseline              |
| Essence   | yes     | mobius-ess                                              | same classes          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/PetFeedTask.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/PetFood.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/PetFeedTask.java`, `mobius-il:dist/game/data/scripts/handlers/items/PetFood.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/PetFeedTask.java`, `mobius-ess:dist/game/data/scripts/handlers/items/PetFood.java`

Packets

| Chronicle | Client → server          | Server → client        |
| --------- | ------------------------ | ---------------------- |
| Interlude | `RequestPetUseItem` `8A` | `PetStatusUpdate` `B5` |
| High Five | `RequestPetUseItem` `94` | `PetStatusUpdate` `B6` |
| Essence   | `RequestPetUseItem` `94` | `PetStatusUpdate` `B6` |

Notes: food values are in `PetData` files.

Known fixes: `l2j-server-dp@6162e4b4` (2009-07-06, other) — JAVA: Fix for Core Ticket [#L3909] PetFoodRate, thanks janiii.

### pet.inventory — Pet inventory

ID: `pet.inventory` · Shared: **partial**

Pet has its own inventory: owner gives items to the pet and takes them back, pet uses items (food, potions), pet picks up items; Essence lets the pet wear equipment.
Sub-features: give / take items · pet item list · pet uses items · pick-up by pet · pet equipment (Essence).

| Chronicle | Present | Builds (evidence)                                            | Difference                                                 |
| --------- | ------- | ------------------------------------------------------------ | ---------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`PetInventory`), l2jfrozen (`PetInventory`) | same                                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise                                   | baseline: `PetInventory`                                   |
| Essence   | yes     | mobius-ess                                                   | adds `ExPetEquipItem` / `ExPetUnequipItem` (pet equipment) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/PetInventory.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestGiveItemToPet,RequestGetItemFromPet,RequestPetGetItem,RequestPetUseItem}.java`
- same names under `mobius-il:java/org/l2jmobius/gameserver`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pet/{RequestGiveItemToPet,RequestGetItemFromPet,RequestPetGetItem,RequestPetUseItem,ExPetEquipItem,ExPetUnequipItem}.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/itemcontainer/PetInventory.java`

Packets

| Chronicle | Client → server                                                                                                                                                         | Server → client                               |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| Interlude | `RequestGiveItemToPet` `8B`, `RequestGetItemFromPet` `8C`, `RequestPetGetItem` `8F`, `RequestPetUseItem` `8A`                                                           | `PetItemList` `B2`, `PetInventoryUpdate` `B3` |
| High Five | `RequestGiveItemToPet` `95`, `RequestGetItemFromPet` `2C`, `RequestPetGetItem` `98`, `RequestPetUseItem` `94`                                                           | `PetItemList` `B3`, `PetInventoryUpdate` `B4` |
| Essence   | `RequestGiveItemToPet` `95`, `RequestGetItemFromPet` `2C`, `RequestPetGetItem` `98`, `RequestPetUseItem` `94`, `ExPetEquipItem` `D0:01A6`, `ExPetUnequipItem` `D0:01A7` | `PetItemlist` `B3`, `PetInventoryUpdate` `B4` |

Notes: none.

Known fixes: `mobius@eb0a3cc5` (2026-01-26, other) — Fixed RequestGiveItemToPet packet messages. Contributed by scrab4ever; `l2j-server@e797cfd5` (2008-09-24, exploit-dupe) — Temporary quick exploit fix, thanks _DS_; `hellas@60224bd4` (2013-01-07, crash-npe) — Npe Fix Store Problem Fixed Phx Duplicate Method Fix.

### pet.servitors — Servitors and summons

ID: `pet.servitors` · Shared: **partial**

Servitors summoned by skills: summon cost (item, consumption), lifetime, behaviour modes, shared skills with the owner, limits on number, unsummon.
Sub-features: summon skill · lifetime and item upkeep · summon AI · servitor modes (Essence) · share skills / buffs · unsummon.

| Chronicle | Present | Builds (evidence)                                                                      | Difference                                                                                                                              |
| --------- | ------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`Servitor`, `SummonAI`), l2jfrozen (`L2SummonInstance`, `L2SummonAI`) | one summon at a time                                                                                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                                   | baseline: `Summon`, `Servitor`, `CharSummonTable`, `SummonAI`                                                                           |
| Essence   | yes     | mobius-ess                                                                             | several servitors; `ServitorMode`, `ServitorShareSkills`, `UnsummonServitors`, `ConditionPlayerCanSummonServitor`, `SUMMON_INFO` packet |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Summon.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Servitor.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/CharSummonTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/SummonAI.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/Summon.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/Servitor.java`, `mobius-il:java/org/l2jmobius/gameserver/data/sql/CharSummonTable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Summon.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/Servitor.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/ServitorMode.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/{UnsummonServitors,ServitorShareSkills}.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/Servitor.java`, `acis:**/SummonAI.java` (found by file name)
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/SummonClick.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/shiftclick/SummonShiftClick.java`
- `mobius-il:dist/game/data/scripts/handlers/actions/click/SummonClick.java`, `mobius-il:dist/game/data/scripts/handlers/actions/shiftclick/SummonShiftClick.java`
- `mobius-ess:dist/game/data/scripts/handlers/actions/click/SummonClick.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/shiftclick/SummonShiftClick.java`

Packets

| Chronicle | Client → server                        | Server → client                                                                                                                                                                                               |
| --------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestActionUse` `45`                | `PetInfo` `B1`, `PetStatusShow` `B0`, `PetStatusUpdate` `B5`, `SetSummonRemainTime` `D1`, `PetDelete` `B6`                                                                                                    |
| High Five | `RequestActionUse` `56`                | `PetInfo` `B2`, `PetStatusShow` `B1`, `PetStatusUpdate` `B6`, `SetSummonRemainTime` `D1`, `PetDelete` `B7`, `ExPartyPetWindowAdd` `FE:18`, `ExPartyPetWindowUpdate` `FE:19`, `ExPartyPetWindowDelete` `FE:6A` |
| Essence   | `RequestActionUse` `56` (user actions) | `SummonInfo` `8B`, `PetInfo` `B2`, `SetSummonRemainTime` `D1`, `PetStatusUpdate` `B6`                                                                                                                         |

Notes: HF and Essence differ in `Summon` by 30% of normalized lines.

Known fixes: `mobius@a1b8557c` (2022-10-01, crash-npe) — Fixed probable CharSummonTable NPE; `l2j-server@834ffd3e` (2014-06-05, wrong-formula-value) — Fixed a bug where servitor item consume and life time task could be started multiple times if servitor owner teleports; `l2journey@f4d7d983` (2025-12-26, other) — Fix no regen dos summons sob efeito da skill Servitor Share.

### pet.commands — Pet and summon commands

ID: `pet.commands` · Shared: **partial**

Action-bar commands to a pet or servitor: attack, stop, move to target, hold, use a pet skill, unsummon.
Sub-features: attack · stop · move · hold (Essence) · skill use · unsummon.

| Chronicle | Present | Builds (evidence)                               | Difference                                                                                                                                                                                           |
| --------- | ------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen (`RequestActionUse`) | handled inside `RequestActionUse`                                                                                                                                                                    |
| High Five | yes     | nx-l2, l2j-server                               | baseline: `RequestActionUse` handles pet commands                                                                                                                                                    |
| Essence   | yes     | mobius-ess                                      | action handlers `PetAttack`, `PetMove`, `PetStop`, `PetHold`, `PetSkillUse`, `ServitorAttack`, `ServitorMove`, `ServitorStop`, `ServitorHold`, `ServitorSkillUse`, `UnsummonPet`, `UnsummonServitor` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestActionUse.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestActionUse.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestActionUse.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/{Pet*,Servitor*,Unsummon*}.java`

Packets

| Chronicle | Client → server         | Server → client                          |
| --------- | ----------------------- | ---------------------------------------- |
| Interlude | `RequestActionUse` `45` | `PetStatusUpdate` `B5`, movement packets |
| High Five | `RequestActionUse` `56` | `PetStatusUpdate` `B6`                   |
| Essence   | `RequestActionUse` `56` | `PetStatusUpdate` `B6`                   |

Notes: Essence refactors commands into handlers; HF keeps them inline.

Known fixes: `mobius@b8316f90` (2021-03-21, exploit-dupe) — Fixed infinite attack speed exploit. Contributed by Iris.

### pet.skills — Pet skills

ID: `pet.skills` · Shared: **partial**

Pet skills: skills available at each pet level from a table, baby-pet buff skills, and in Essence a learnable pet skill list.
Sub-features: skill by pet level · buff pets · learnable skills (Essence).

| Chronicle | Present | Builds (evidence)                                               | Difference                                                                                        |
| --------- | ------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (`PetSkillData`), l2jfrozen/aCis (skills in pet data) | table `PetSkillData.xml`                                                                          |
| High Five | yes     | nx-l2, l2j-server (`PetSkillSaveDAO`)                           | baseline: `PetSkillData`                                                                          |
| Essence   | yes     | mobius-ess                                                      | `PetSkillData` 99% equal; adds `PetAcquireList.xml`, `RequestExAcquirePetSkill`, `ExPetSkillList` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/PetSkillData.java`, `nx-l2:chronicles/high-five/datapack/data/PetSkillData.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/PetSkillData.java`, `mobius-il:dist/game/data/PetSkillData.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/PetSkillData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pet/RequestExAcquirePetSkill.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/pet/{ExPetSkillList,ExAcquirePetSkillResult}.java`

Packets

| Chronicle | Client → server                      | Server → client                                                 |
| --------- | ------------------------------------ | --------------------------------------------------------------- |
| Interlude | —                                    | `SkillList` `58` (pet skills via the pet window)                |
| High Five | —                                    | `SkillList` `5F`                                                |
| Essence   | `RequestExAcquirePetSkill` `D0:01C7` | `ExPetSkillList` `FE:0272`, `ExAcquirePetSkillResult` `FE:039D` |

Notes: aCis and L2jFrozen were not found to have a separate pet skill table by file name.

### pet.baby-pets — Baby pets

ID: `pet.baby-pets` · Shared: **partial (IL+HF)**

Baby pets (Baby Buffalo, Kookaburra, Cougar and evolved forms): support pets that cast heal and buff skills automatically on the owner.
Sub-features: auto heal / buff AI · skill selection by level · evolved forms.

| Chronicle | Present | Builds (evidence)                                            | Difference          |
| --------- | ------- | ------------------------------------------------------------ | ------------------- |
| Interlude | yes     | mobius-il, acis (`BabyPet`), l2jfrozen (`L2BabyPetInstance`) | same                |
| High Five | yes     | nx-l2, sunrise, eternity                                     | baseline: `BabyPet` |
| Essence   | no      | mobius-ess (class absent)                                    | removed             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/BabyPet.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/BabyPet.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/BabyPet.java`

Packets: same as `pet.pets`.

| Chronicle | Client → server | Server → client                        |
| --------- | --------------- | -------------------------------------- |
| Interlude | —               | `PetInfo` `B1`, `PetStatusUpdate` `B5` |
| High Five | —               | `PetInfo` `B2`, `PetStatusUpdate` `B6` |

Notes: none.

### pet.tamed-beasts — Tamed beasts

ID: `pet.tamed-beasts` · Shared: **partial (IL+HF)**

Beast Farm (Rune): feedable beasts (Alpen Kookaburra, Buffalo, Cougar, ...) fed with spices become tamed and follow the owner; growth stages by feeding.
Sub-features: feedable beast AI · beast spice item · tamed beast following · beast farm spawns.

| Chronicle | Present | Builds (evidence)                                                                             | Difference                                                    |
| --------- | ------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`BeastSpices`), l2jfrozen (`BeastSpice`)                                     | same farm                                                     |
| High Five | yes     | nx-l2, l2j-server, eternity                                                                   | baseline: `TamedBeast`, `FeedableBeast`, `ai/areas/BeastFarm` |
| Essence   | no      | Mobius Essence: instance classes only (`TamedBeast` 98% equal to HF), no farm script / spawns | classes left over, content removed                            |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/{TamedBeast,FeedableBeast}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/BeastFarm/{BeastFarm,FeedableBeasts}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/BeastFarm/Tunatun/Tunatun.java`
- `mobius-il:dist/game/data/scripts/ai/areas/BeastFarm`, `mobius-il:dist/game/data/scripts/handlers/items/BeastSpice.java`, `mobius-il:dist/game/data/spawns/Rune/BeastFarm.xml`
- `acis:**/BeastSpices.java` (found by file name)

Packets: the `PetInfo` family for the tamed beast, `NpcInfo` for feedable beasts.

| Chronicle | Client → server | Server → client                |
| --------- | --------------- | ------------------------------ |
| Interlude | —               | `PetInfo` `B1`, `NpcInfo` `16` |
| High Five | —               | `PetInfo` `B2`, `NpcInfo` `0C` |

Notes: the `BeastSpice` item handler was found by file name only in Mobius IL (not in nx-l2 or Essence).

Known fixes: `l2j-server@eefcb471` (2007-08-31, missing-check) — BabyPets and TamedBeasts: Code Optimizations and minor visual fixes Removed redudant code from BabyPets (no longer needed since recent….

### pet.mounts — Mounts

ID: `pet.mounts` · Shared: **partial**

Mounting a strider or wyvern: ride / dismount, rented mounts from a pet manager, wyvern restrictions (siege, flight), optionally free mounts for everyone.
Sub-features: mount / dismount · rent pet · wyvern manager · free mounts (custom) · siege restrictions.

| Chronicle | Present | Builds (evidence)                                                                                                                 | Difference                                                                                                                               |
| --------- | ------- | --------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (`RentPetTask`, `WyvernManager`, `FreeMounts`), l2jfrozen (`L2FortWyvernManagerInstance`), acis (`StriderSiegeAssault`) | wyvern / strider only                                                                                                                    |
| High Five | yes     | nx-l2, l2j-server, eternity                                                                                                       | baseline: `RentPet`, `WyvernManager`, `FreeMountsConfig`                                                                                 |
| Essence   | yes     | mobius-ess                                                                                                                        | extra wyvern skill target / scope classes; `15955_Wyvern.xml`, `15977_Wyvern.xml`, `15979_Wyvern.xml` (Mobius IL has `12621_Wyvern.xml`) |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/RentPet.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/RentPetTask.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/WyvernManager/WyvernManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/FreeMountsConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/FreeMounts.ini`
- same names under `mobius-il:java/org/l2jmobius/gameserver`, `mobius-il:dist/game/data/scripts`
- `mobius-ess:dist/game/data/scripts/handlers/skill/targets/WyvernTarget.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/OpWyvernSkillCondition.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/custom/FreeMountsConfig.java`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/DismountTask.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/Dismount.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/Mount.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/DismountTask.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/user/Dismount.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/user/Mount.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/DismountTask.java`, `mobius-ess:dist/game/data/scripts/handlers/chat/commands/user/Dismount.java`, `mobius-ess:dist/game/data/scripts/handlers/chat/commands/user/Mount.java`

Packets

| Chronicle | Client → server         | Server → client |
| --------- | ----------------------- | --------------- |
| Interlude | `RequestActionUse` `45` | `Ride` `86`     |
| High Five | `RequestActionUse` `56` | `Ride` `8C`     |
| Essence   | `RequestActionUse` `56` | `Ride` `8C`     |

Notes: Essence mounts are driven by skills (wyvern target / scope), the NPC rental is not carried.

### pet.evolution — Pet evolution and extraction (Essence)

ID: `pet.evolution` · Shared: **chronicle-only**

Essence pets have types, can evolve through stages and be "extracted" (converted into a pet item) in a dedicated window; also pet equipment.
Sub-features: pet types · evolve · extract window · extraction data.

| Chronicle | Present | Builds (evidence)                                    | Difference                                                                                         |
| --------- | ------- | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen                           | —                                                                                                  |
| High Five | no      | nx-l2                                                | —                                                                                                  |
| Essence   | yes     | mobius-ess, l2emu-ess (`PetExtractData`, `PetTypes`) | `PetTypeData`, `PetExtractData`, `ExEvolvePet`, `ExTryPetExtractSystem`, `PetExtractWindow` bypass |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/{PetTypeData,PetExtractData}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pet/{ExEvolvePet,ExTryPetExtractSystem}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/pet/{ShowPetExtractSystem,ResultPetExtractSystem}.java`, `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/PetExtractWindow.java`, `mobius-ess:dist/game/data/PetTypes.xml`, `mobius-ess:dist/game/data/PetExtractData.xml`

Packets

| Chronicle | Client → server                                            | Server → client                                                                                              |
| --------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Essence   | `ExEvolvePet` `D0:01B0`, `ExTryPetExtractSystem` `D0:01CC` | `ExShowPetExtractSystem` `FE:027E`, `ExResultPetExtractSystem` `FE:027F`, `ExHidePetExtractSystem` `FE:0280` |

Notes: the pet ranking packets (`EX_PET_RANKING_*`) belong to the `ranking` domain.

### pet.decoys-traps — Decoys and traps

ID: `pet.decoys-traps` · Shared: **partial (HF+Ess)**

Skill-created objects: a decoy that looks like its owner and draws aggro, and traps placed on the ground that trigger on enemies and cast a skill; traps can be detected and disarmed by clicking.
Sub-features: decoy · trap · trap trigger · detect / click handling.

| Chronicle | Present | Builds (evidence)                                                     | Difference                                                           |
| --------- | ------- | --------------------------------------------------------------------- | -------------------------------------------------------------------- |
| Interlude | no      | acis, l2jfrozen (no trap / decoy classes); Mobius IL only as backport | Kamael-era mechanic                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                  | baseline: `Decoy`, `Trap`, action handlers `TrapClick`, `DecoyClick` |
| Essence   | yes     | mobius-ess                                                            | `Trap` 79% and `Decoy` 83% of HF                                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/{Decoy,Trap}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/{TrapClick,DecoyClick}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/{Decoy,Trap}.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/click/{TrapClick,DecoyClick}.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/{Decoy,Trap}.java`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/npc/trap/TrapTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/npc/trap/TrapTriggerTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/npc/trap/TrapUnsummonTask.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/trap/TrapTask.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/trap/TrapTriggerTask.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/trap/TrapUnsummonTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/trap/TrapTask.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/trap/TrapTriggerTask.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/trap/TrapUnsummonTask.java`

Packets

| Chronicle | Client → server | Server → client                                |
| --------- | --------------- | ---------------------------------------------- |
| High Five | `Action` `1F`   | `NpcInfo` `0C` (trap), `CharInfo` `31` (decoy) |
| Essence   | `Action` `1F`   | `NpcInfo` `0C`, `CharInfo` `31`                |

Notes: Mobius IL equals HF (97-100%), which is why Interlude presence is judged by aCis / L2jFrozen.
