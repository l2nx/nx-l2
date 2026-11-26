# Fishing

Fishing, catch rewards and the fishing championship.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### fishing.core — Fishing

ID: `fishing.core` · Shared: **partial**

A player equips a fishing rod and bait (lure), casts at a fishing zone, waits for a bite, then fights the fish with pumping and
reeling skills against a fish HP gauge (Interlude/High Five) or lets the client auto-fish (Essence). Fishing grants fish items,
exp/SP bonus effects and fishing-shot support.
Sub-features: rod and bait/lure · cast at fishing zone · fish combat (pumping/reeling) · fishing shots · fishing skills
(mastery) · Essence auto-fish · global switch (`AllowFishing`).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                            |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | manual combat; `fishes.xml`, `fishingRods.xml`                                                                                        |
| High Five | yes     | nx-l2, l2j-server, sunrise                    | baseline                                                                                                                              |
| Essence   | differs | mobius-ess                                    | auto-fish (`ExRequestAutoFish`, `ExAutoFishAvailable`), baits and catches in `Fishing.xml`; skill conditions for cast/pumping/reeling |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/fishing/{Fishing,Fish,FishingMonster,FishingRod}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/{FishData,FishingMonstersData,FishingRodsData}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Fisherman.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/FishingZone.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/LookingForFishTask.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/FishShots.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/{Fishing,ChangeFishingMastery}.java`, `nx-l2:chronicles/high-five/datapack/data/stats/fishing/*.xml`, `nx-l2:chronicles/high-five/datapack/data/zones/fishing.xml`, `nx-l2:chronicles/high-five/datapack/config/General.ini` (`AllowFishing`)
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/fishing/*`, `mobius-il:java/org/l2jmobius/gameserver/data/xml/{FishData,FishingMonstersData,FishingRodsData}.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/Fishing.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/fishing/{Fishing,FishingBait,FishingCatch,FishingEndReason,FishingEndType,FishingRod}.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/FishingData.java`, `mobius-ess:dist/game/data/Fishing.xml`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ExRequestAutoFish.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/OpFishing{Cast,Pumping,Reeling}SkillCondition.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/FishingExpSpBonus.java`

Packets

| Chronicle | Client → server                                                | Server → client                                                                                                  |
| --------- | -------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestMagicSkillUse` `2F` (fishing, pumping, reeling skills) | `ExFishingStart` `FE:13`, `ExFishingEnd` `FE:14`, `ExFishingStartCombat` `FE:15`, `ExFishingHpRegen` `FE:16`     |
| High Five | `RequestMagicSkillUse` `39`                                    | `ExFishingStart` `FE:1E`, `ExFishingEnd` `FE:1F`, `ExFishingStartCombat` `FE:27`, `ExFishingHpRegen` `FE:28`     |
| Essence   | `ExRequestAutoFish` `D0:0105`, `RequestMagicSkillUse` `39`     | `ExFishingStart` `FE:1E`, `ExFishingEnd` `FE:1F`, `ExUserInfoFishing` `FE:015A`, `ExAutoFishAvailable` `FE:017C` |

Notes: Essence drops the combat packets (`ExFishingStartCombat`, `ExFishingHpRegen`): the fight is replaced by the auto-fish
flow.

Known fixes: `l2j-server@bd099bc0` (2013-12-04, crash-npe) — Fixing NPE at `L2Fishing`

### fishing.rewards — Catch rewards

ID: `fishing.rewards` · Shared: **partial**

What a catch yields: fish items by level and grade, fishing monsters (a chance that a monster bites instead of a fish),
chests/fish-opening in some builds; Essence moves catches into per-bait lists.
Sub-features: fish table · fishing monsters · rewards per bait/rod · Essence per-bait catch lists.

| Chronicle | Present | Builds (evidence)          | Difference                                              |
| --------- | ------- | -------------------------- | ------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | `fishes.xml`, `fishingMonsters.xml`                     |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline; adds `WarriorFishingBlock` AI                 |
| Essence   | differs | mobius-ess                 | catches are `<bait><catch …/>` entries in `Fishing.xml` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/{FishData,FishingMonstersData}.java`, `nx-l2:chronicles/high-five/datapack/data/stats/fishing/{fishes,fishingMonsters}.xml`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/WarriorFishingBlock.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/quests/Q00426_QuestForFishingShot`
- `mobius-il:dist/game/data/stats/fishing/{fishes,fishingMonsters}.xml`, `mobius-il:dist/game/data/scripts/ai/others/WarriorFishingBlock.java`
- `mobius-ess:dist/game/data/Fishing.xml`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/fishing/FishingCatch.java`

Packets

| Chronicle | Client → server | Server → client                 |
| --------- | --------------- | ------------------------------- |
| Interlude | —               | `ExFishingEnd` `FE:14` (result) |
| High Five | —               | `ExFishingEnd` `FE:1F`          |
| Essence   | —               | `ExFishingEnd` `FE:1F`          |

Notes: —

### fishing.championship — Fishing championship

ID: `fishing.championship` · Shared: **partial (IL+HF)**

Weekly fishing championship: the longest catches are ranked by fisherman; a ranking window shows the table; top fishermen
get Adena rewards at the end of the cycle; the cycle persists in a database table.
Sub-features: ranking table · cycle reset · rewards by place · ranking window (`RequestExFishRanking`) · fisherman NPC ranking page.

| Chronicle | Present | Builds (evidence)          | Difference                                   |
| --------- | ------- | -------------------------- | -------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | same                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline                                     |
| Essence   | no      | mobius-ess                 | `RequestExFishRanking` only logs; no manager |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/FishingChampionshipManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExFishRanking.java`, `nx-l2:chronicles/high-five/datapack/sql/fishing_championship.sql`, `nx-l2:chronicles/high-five/datapack/config/General.ini` (`AltFishChampionshipEnabled`, `AltFishChampionshipRewardItemId`, `AltFishChampionshipReward1..5`)
- `mobius-il:java/org/l2jmobius/gameserver/managers/FishingChampionshipManager.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestExFishRanking.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestExFishRanking.java` (stub)

Packets

| Chronicle | Client → server                       | Server → client                       |
| --------- | ------------------------------------- | ------------------------------------- |
| Interlude | `RequestExFishRanking` `D0:1F`        | `NpcHtmlMessage` `0F` (ranking pages) |
| High Five | `RequestExFishRanking` `D0:18`        | `NpcHtmlMessage` `19`                 |
| Essence   | `RequestExFishRanking` `D0:18` (stub) | —                                     |

Notes: —
