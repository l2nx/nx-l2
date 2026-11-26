# PvP, PK and karma

PvP flag, karma and PK handling, cursed weapons, anti-feed, rewards and special PvP systems.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### pvp.flag — PvP flag

ID: `pvp.flag` · Shared: **engine**

Attacking a non-flagged player sets the PvP flag for a timer (longer against flagged targets); flag zones and flag
effects set it too; the flag decays in a task manager.
Sub-features: flag on attack · normal vs PvP timer · flag on raid/champion attack · flag zones · flag effect.

| Chronicle | Present | Builds (evidence)                             | Difference                                  |
| --------- | ------- | --------------------------------------------- | ------------------------------------------- |
| Interlude | yes     | mobius-il, acis, hellas, l2kt-cozmin91-kotlin | baseline                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise                    | same                                        |
| Essence   | yes     | mobius-ess                                    | same (`PvpFlagTaskManager` identical to IL) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/PvpFlagTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/FlagPvPZone.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/Flag.java`, `nx-l2:chronicles/high-five/datapack/config/PVP.ini` (`PvPVsNormalTime`, `PvPVsPvPTime`, `FlagPlayerOn*`)
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/PvpFlagTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/PvpFlagTaskManager.java`

Packets

| Chronicle | Client → server | Server → client                                                                                       |
| --------- | --------------- | ----------------------------------------------------------------------------------------------------- |
| Interlude | —               | `UserInfo` `04`/`CharInfo` `03` pvp flag field; `RelationChanged` `CE` bit `RELATION_PVP_FLAG` `0x02` |
| High Five | —               | `UserInfo` `32`/`CharInfo` `31` pvp flag field; `RelationChanged` `CE`                                |
| Essence   | —               | `UserInfo` `32` mask `UserInfoType.SOCIAL` (bit `0x10`) (pvp flag); `RelationChanged` `CE`            |

Notes: —

### pvp.karma — Karma and PK

ID: `pvp.karma` · Shared: **partial**

Killing a non-flagged player makes the killer a PK: karma is added (Essence: reputation goes negative), PK and PvP
counters grow; karma is cleansed by experience from monsters per the karma-loss table; PKs have restrictions (shops,
gatekeepers, teleport).
Sub-features: karma gain formula · karma loss by XP (`nx-l2:chronicles/high-five/datapack/data/stats/players/karmaLoss.xml`) · PvP/PK counters · PK restrictions · reputation (Ess).

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                                                                 |
| --------- | ------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server, ares-melnkovmaxim | baseline: positive karma                                                                                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise                                | same                                                                                                                                       |
| Essence   | differs | mobius-ess                                                | reputation (negative = PK, `MaxReputation`, `ReputationIncrease`), `KarmaCount`/`PkCount` skill effects, `OnPlayerReputationChanged` event |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/KarmaLossData.java`, `nx-l2:chronicles/high-five/datapack/data/stats/players/karmaLoss.xml`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Player.java` (karma/PK), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/events/holders/actor/player/{OnPlayerKarmaChanged,OnPlayerPKChanged,OnPlayerPvPChanged,OnPlayerPvPKill}.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/KarmaLossData.java` (identical to Essence)
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Player.java` (`setReputation` ~l.2449), `mobius-ess:dist/game/data/scripts/handlers/skill/effects/{KarmaCount,PkCount}.java`, `mobius-ess:dist/game/config/PVP.ini`

Packets

| Chronicle | Client → server | Server → client                                                                                                |
| --------- | --------------- | -------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `UserInfo` `04`/`CharInfo` `03` karma, PvP/PK counters; `RelationChanged` `CE` bit `RELATION_HAS_KARMA` `0x04` |
| High Five | —               | `UserInfo` `32`/`CharInfo` `31` karma, counters                                                                |
| Essence   | —               | `UserInfo` `32` mask `UserInfoType.SOCIAL` (bit `0x10`) (reputation, PK, PvP); `RelationChanged` `CE`          |

Notes: `AwardPKKillPVPPoint` (IL/HF) is dropped in Essence.

### pvp.pk-drop — PK item drop

ID: `pvp.pk-drop` · Shared: **partial**

A PK (and, by config, a player killed by a PK) may drop equipped or inventory items on death, depending on PK count,
karma and drop chances; some items are never dropped.
Sub-features: minimum PK count · drop chances (equipped/inventory) · drop limit · non-droppable lists · Essence penalty
item restore.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                      |
| --------- | ------- | -------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline: items dropped to the ground                                                                           |
| High Five | yes     | nx-l2, l2j-server          | same                                                                                                            |
| Essence   | differs | mobius-ess                 | dropped items go to a penalty list (`addPenaltyItem`), the owner can restore them; ground drop is a visual only |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Player.java` (`onDieDropItem` ~l.4851), `nx-l2:chronicles/high-five/datapack/config/PVP.ini` (`MinimumPKRequiredToDrop`, `ListOfNonDroppableItems`)
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/Player.java` (`onDieDropItem` ~l.5234)
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Player.java` (`addPenaltyItem` ~l.5949), `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/penaltyitemdrop/{ExRequestPenaltyItemList,ExRequestPenaltyItemRestore}.java`

Packets

| Chronicle | Client → server                                                               | Server → client                                              |
| --------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Interlude | —                                                                             | — (generic `DropItem` `0C`)                                  |
| High Five | —                                                                             | — (generic `DropItem` `16`)                                  |
| Essence   | `ExRequestPenaltyItemList` `D0:01E8`, `ExRequestPenaltyItemRestore` `D0:01E9` | `ExPenaltyItemDrop` `FE:029F`, `ExPenaltyItemInfo` `FE:02A7` |

Notes: —

### pvp.pk-penalty — PK list (Essence)

ID: `pvp.pk-penalty` · Shared: **chronicle-only**

Essence shows a list of current PKs on the server (name, level, class) with the time of the last PK, and can show only
their locations on the map.
Sub-features: PK list · location-only request.

| Chronicle | Present | Builds (evidence) | Difference                        |
| --------- | ------- | ----------------- | --------------------------------- |
| Interlude | no      | mobius-il         | —                                 |
| High Five | no      | nx-l2             | —                                 |
| Essence   | yes     | mobius-ess        | baseline (`World.getPkPlayers()`) |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pk/{RequestExPkPenaltyList,RequestExPkPenaltyListOnlyLoc}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/pk/ExPkPenaltyList.java`

Packets

| Chronicle | Client → server                                                               | Server → client                                                 |
| --------- | ----------------------------------------------------------------------------- | --------------------------------------------------------------- |
| Essence   | `RequestExPkPenaltyList` `D0:01C0`, `RequestExPkPenaltyListOnlyLoc` `D0:01C1` | `ExPkPenaltyList` `FE:027C`, `ExPkPenaltyListOnlyLoc` `FE:027D` |

Notes: —

### pvp.cursed-weapons — Cursed weapons

ID: `pvp.cursed-weapons` · Shared: **engine**

Zariche and Akamanah drop from monsters with a tiny chance; the holder transforms, becomes a PK with huge karma, the
weapon levels up with kills and disappears after its timer or drops on the holder's death; both weapons can merge;
their location is visible to everyone.
Sub-features: drop chance · holder transformation · level by kills · timer · merge · location list.

| Chronicle | Present | Builds (evidence)                      | Difference                                               |
| --------- | ------- | -------------------------------------- | -------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server | baseline                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise             | same                                                     |
| Essence   | yes     | mobius-ess                             | same weapons and logic; loader rewritten as `IXmlReader` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CursedWeaponsManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/CursedWeapon.java`, `nx-l2:chronicles/high-five/datapack/data/CursedWeapons.xml`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminCursedWeapons.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/CursedWeaponsManager.java`, `mobius-il:dist/game/data/CursedWeapons.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/CursedWeaponsManager.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/OpNotCursedSkillCondition.java`

Packets

| Chronicle | Client → server                                                          | Server → client                                                                                                                                        |
| --------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestCursedWeaponList` `D0:22`, `RequestCursedWeaponLocation` `D0:23` | `ExCursedWeaponList` `FE:45`, `ExCursedWeaponLocation` `FE:46`                                                                                         |
| High Five | `RequestCursedWeaponList` `D0:2A`, `RequestCursedWeaponLocation` `D0:2B` | `ExCursedWeaponList` `FE:46`, `ExCursedWeaponLocation` `FE:47`                                                                                         |
| Essence   | `RequestCursedWeaponList` `D0:2A`, `RequestCursedWeaponLocation` `D0:2B` | `ExCursedWeaponList` `FE:47`, `ExCursedWeaponLocation` `FE:48`; `UserInfo` `32` mask `UserInfoType.INVENTORY_LIMIT` (bit `0x15`) (cursed weapon level) |

Notes: —
Known fixes: `l2j-server@0f0b4c61` (2007-05-05, exploit-dupe) — fix for #893 Zariche: server start fix by promethium and login cheat fix by me; `sunrise@3d397f21` (2017-05-13, missing-check) — Instance mobs should not drop cursed weapons.

### pvp.anti-feed — PvP anti-feed

ID: `pvp.anti-feed` · Shared: **engine**

PvP points and karma rewards are not granted when killer and victim come from the same IP / client (dual box) or the
same victim was killed recently; the same manager limits dual-box participation in events and the Olympiad.
Sub-features: dual-box check · disconnected-as-dual-box · repeat-kill interval · per-feature limits (events, Olympiad).

| Chronicle | Present | Builds (evidence)          | Difference                |
| --------- | ------- | -------------------------- | ------------------------- |
| Interlude | yes     | mobius-il                  | baseline                  |
| High Five | yes     | nx-l2, l2j-server, sunrise | same                      |
| Essence   | yes     | mobius-ess                 | same (4 diff lines vs IL) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/AntiFeedManager.java`, `nx-l2:chronicles/high-five/datapack/config/PVP.ini` (`AntiFeed*`)
- `mobius-il:java/org/l2jmobius/gameserver/managers/AntiFeedManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/AntiFeedManager.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: aCis has no `AntiFeedManager`; the L2jone (aCis) fork and the MaskedRiders L2JTW PvP fork add one.
Known fixes: `l2j-server@2aa7400f` (2015-06-13, crash-npe) — Fix Antifeed and npe when the player tries to login.

### pvp.rewards-titles — PvP rewards and colors

ID: `pvp.rewards-titles` · Shared: **engine**

Custom options: give an item for a PvP/PK kill, announce kills server-wide, recolor name/title by PvP/PK counts.
Sub-features: PvP reward item · PvP/PK announce · title/name color by counters.

| Chronicle | Present | Builds (evidence)                                                                     | Difference               |
| --------- | ------- | ------------------------------------------------------------------------------------- | ------------------------ |
| Interlude | yes     | mobius-il (custom), l2jfrozen-r1596 (`PvpReward`), l2jmega-megapack (`PvpColorTable`) | baseline (Mobius custom) |
| High Five | yes     | nx-l2 (custom), l2journey                                                             | same                     |
| Essence   | yes     | mobius-ess (custom)                                                                   | same                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/{PvpRewardItemConfig,PvpAnnounceConfig,PvpTitleColorConfig}.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/{PvpRewardItem,PvpAnnounce,PvpTitleColor}.ini`
- `mobius-il:java/org/l2jmobius/gameserver/config/custom/PvpRewardItemConfig.java`, `mobius-il:dist/game/config/Custom/PvpTitleColor.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/config/custom/PvpRewardItemConfig.java`

Packets

| Chronicle | Client → server | Server → client                                        |
| --------- | --------------- | ------------------------------------------------------ |
| Interlude | —               | `UserInfo` `04`/`CharInfo` `03` name/title color       |
| High Five | —               | `UserInfo` `32`/`CharInfo` `31` name/title color       |
| Essence   | —               | `UserInfo` `32` mask `UserInfoType.COLOR` (bit `0x14`) |

Notes: Mobius custom (`config/Custom`), not an official mechanic.

### pvp.revenge — Revenge

ID: `pvp.revenge` · Shared: **chronicle-only**

Essence keeps a revenge book: when a player is killed by another player the killer is recorded; the victim can see the
killer's location, ask clan/friends to help, teleport to the killer or let helpers teleport, with daily limits.
Sub-features: revenge list · killer location · share revenge request · teleport to killer · shared teleport · limits.

| Chronicle | Present | Builds (evidence)                  | Difference |
| --------- | ------- | ---------------------------------- | ---------- |
| Interlude | no      | mobius-il                          | —          |
| High Five | no      | nx-l2, l2j-server                  | —          |
| Essence   | yes     | mobius-ess (since Frost Lord 05.2) | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/RevengeHistoryManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/revenge/*`, `mobius-ess:java/org/l2jmobius/gameserver/network/holders/RevengeHistoryHolder.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/enums/RevengeType.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                        | Server → client                                                                                                                                                  |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExPvpBookList` `D0:018E`, `RequestExPvpBookShareRevengeList` `D0:01E3`, `RequestExPvpBookShareRevengeKillerLocation` `D0:01E5`, `RequestExPvpBookShareRevengeReqShareRevengeInfo` `D0:01E4`, `RequestExPvpBookShareRevengeTeleportToKiller` `D0:01E6`, `RequestExPvpBookShareRevengeSharedTeleportToKiller` `D0:01E7` | `PvpBookList` `FE:023B`, `ExPvpBookShareRevengeList` `FE:029C`, `ExPvpBookShareRevengeKillerLocation` `FE:029D`, `ExPvpBookShareRevengeNewRevengeInfo` `FE:029E` |

Notes: absent in Mobius Essence 04.2, added in 05.2.

### pvp.faction-system — Faction system

ID: `pvp.faction-system` · Shared: **engine**

Custom good-vs-evil mode: characters choose one of two factions at faction NPCs, each faction has its own town and
name color, and players of opposing factions can attack each other without karma.
Sub-features: faction choice NPCs · faction towns · colors · cross-faction PvP rules.

| Chronicle | Present | Builds (evidence)   | Difference |
| --------- | ------- | ------------------- | ---------- |
| Interlude | yes     | mobius-il (custom)  | baseline   |
| High Five | yes     | nx-l2 (custom)      | same       |
| Essence   | yes     | mobius-ess (custom) | same       |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/FactionSystem/FactionSystem.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/FactionSystemConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/FactionSystem.ini`, `nx-l2:chronicles/high-five/datapack/data/stats/npcs/custom/gve_factions.xml`
- `mobius-il:dist/game/data/scripts/custom/FactionSystem/FactionSystem.java`
- `mobius-ess:dist/game/data/scripts/custom/FactionSystem/FactionSystem.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: Mobius custom; no other family root has it.
Known fixes: `mobius@2b9c7936` (2019-02-28, other) — Unhardcoded faction guards, fixed aggro and various improvements.

### pvp.find-pvp — Find PvP

ID: `pvp.find-pvp` · Shared: **engine**

Custom NPC bypass that teleports the player to the area with the most active PvP right now.
Sub-features: active PvP detection · teleport · restrictions.

| Chronicle | Present | Builds (evidence)   | Difference |
| --------- | ------- | ------------------- | ---------- |
| Interlude | yes     | mobius-il (custom)  | baseline   |
| High Five | yes     | nx-l2 (custom)      | same       |
| Essence   | yes     | mobius-ess (custom) | same       |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/FindPvP.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/FindPvpConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/FindPvP.ini`
- `mobius-il:dist/game/data/scripts/handlers/bypass/npc/FindPvP.java`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/FindPvP.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | — (NPC bypass)  | —               |
| High Five | — (NPC bypass)  | —               |
| Essence   | — (NPC bypass)  | —               |

Notes: Mobius custom.
