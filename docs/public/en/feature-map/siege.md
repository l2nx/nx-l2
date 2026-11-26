# Sieges and castles

Castle sieges and everything around them: schedule, headquarters, artifact capture, towers and golems, guards and mercenaries, castle management, Territory War and dungeons.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### siege.castle-siege — Castle siege

ID: `siege.castle-siege` · Shared: **partial**

Clans register as attackers or defenders of a castle, the owner confirms defenders, the siege runs for a fixed length
inside the siege zone; whoever engraves the seal (artifact) takes the castle, ownership changes, and the zone rules
(respawn at HQ, flags, fame) apply while it is active.
Sub-features: attacker/defender registration · defender confirmation · siege zone and respawn rules · ownership change ·
end-of-siege rewards · Essence: relic NPCs, mercenary payout, castle side.

| Chronicle | Present | Builds (evidence)                                                                                         | Difference                                                                                                                                                                       |
| --------- | ------- | --------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server-686500, ares-melnkovmaxim, l2kt-cozmin91-kotlin | baseline: 9 castles, 120 min, clan level ≥ 4                                                                                                                                     |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, advanced, premium, lineage2ts, bh8nexus-l2-retail-engine            | clan level ≥ 5, Blood Alliance reward, Territory War link                                                                                                                        |
| Essence   | differs | mobius-ess, l2emu-ess                                                                                     | 6 castles (Aden, Dion, Giran, Gludio, Goddard, Oren), 60 min, clan level ≥ 3, relic NPCs spawned at siege start, mercenary reward mailed from treasury, castle side (light/dark) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/Siege.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/Castle.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/SiegeManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestJoinSiege.java`, `nx-l2:chronicles/high-five/datapack/config/Siege.ini`, `nx-l2:chronicles/high-five/datapack/data/zones/castle_siege.xml`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/siege/Siege.java`, `mobius-il:java/org/l2jmobius/gameserver/managers/SiegeManager.java`, `mobius-il:dist/game/config/Siege.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/Siege.java` (relics `spawnRelic`, mercenary payout ~l.295), `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/CastleSide.java`, `mobius-ess:dist/game/data/scripts/ai/others/NewSiege/Relics.java`, `mobius-ess:dist/game/config/Siege.ini` (`Relic = ...`), `mobius-ess:dist/game/data/residences/castles/*.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                          | Server → client                                                                                                                                                                                                                                               |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestJoinSiege` `A4`, `RequestSiegeAttackerList` `A2`, `RequestSiegeDefenderList` `A3`, `RequestConfirmSiegeWaitingList` `A5`, `RequestSiegeInfo` `47`, `RequestAllCastleInfo` (class present, not registered in the IL opcode table) | `SiegeInfo` `C9`, `SiegeAttackerList` `CA`, `SiegeDefenderList` `CB`, `ExShowCastleInfo` (writes the `SiegeInfo` opcode `C9` in Mobius IL), `RelationChanged` `CE` (bits `RELATION_INSIEGE` `0x200`, `RELATION_ATTACKER` `0x400`)                             |
| High Five | `RequestJoinSiege` `AD`, `RequestSiegeAttackerList` `AB`, `RequestSiegeDefenderList` `AC`, `RequestConfirmSiegeWaitingList` `AE`, `RequestSiegeInfo` `AA`, `RequestAllCastleInfo` `D0:3C`                                                | `SiegeInfo` `C9`, `SiegeAttackerList` `CA`, `SiegeDefenderList` `CB`, `ExShowCastleInfo` `FE:14`, `RelationChanged` `CE` (bits `RELATION_INSIEGE` `0x200`, `RELATION_ATTACKER` `0x400`)                                                                       |
| Essence   | `RequestJoinSiege` `AD`, `RequestSiegeAttackerList` `AB`, `RequestSiegeDefenderList` `AC`, `RequestConfirmSiegeWaitingList` `AE`, `RequestSiegeInfo` `AA`, `RequestAllCastleInfo` `D0:39`                                                | `SiegeInfo` `C9`, `SiegeAttackerList` `CA`, `SiegeDefenderList` `CB`, `ExShowCastleInfo` `FE:14`, `ExCastleState` `FE:012E`, `RelationChanged` `CE` (bits `RELATION_INSIEGE`, `RELATION_ATTACKER`); `UserInfo` `32` mask `UserInfoType.RELATION` (bit `0x00`) |

Notes: in Mobius IL/HF the Seal of Strife influences only respawn location for defenders (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/MapRegionData.java:475`), not registration. Essence adds a siege daily mission (`mobius-ess:dist/game/data/scripts/handlers/dailymissions/SiegeDailyMissionHandler.java`). The L2-Advanced / L2JPremium family model sieges as events (`SiegeEvent`, `DominionSiegeEvent`) rather than a manager class.
Known fixes: `l2j-server@2e68f624` (2012-07-04, concurrency) — BETA: Fixing ConcurrentModification in Siege; `mobius@4ed1538a` (2018-09-03, crash-npe) — Prevent probable L2SiegeGuardAI NPE. Contributed by Sahar; `mobius@ff1525bc` (2022-02-05, crash-npe) — Prevent SiegeScheduleData setNextSiegeDate NPE.

### siege.siege-schedule — Siege time

ID: `siege.siege-schedule` · Shared: **engine**

The castle lord picks the next siege date from a list of allowed slots; otherwise the default slot from the schedule
config applies; registration closes before the siege.
Sub-features: allowed days/hours list · default date · registration close time.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                    |
| --------- | ------- | -------------------------- | --------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline; `RequestSetCastleSiegeTime` is class present, not registered in the IL opcode table |
| High Five | yes     | nx-l2, l2j-server, sunrise | same                                                                                          |
| Essence   | yes     | mobius-ess                 | same (`SiegeScheduleData` identical to IL)                                                    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/SiegeScheduleData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/SiegeScheduleDate.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestSetCastleSiegeTime.java`, `nx-l2:chronicles/high-five/datapack/config/SiegeSchedule.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/SiegeScheduleData.java`, `mobius-il:dist/game/config/SiegeSchedule.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SiegeScheduleData.java` (byte-identical to IL ignoring whitespace)

Packets

| Chronicle | Client → server                                                                    | Server → client  |
| --------- | ---------------------------------------------------------------------------------- | ---------------- |
| Interlude | `RequestSetCastleSiegeTime` (class present, not registered in the IL opcode table) | `SiegeInfo` `C9` |
| High Five | `RequestSetCastleSiegeTime` `AF`                                                   | `SiegeInfo` `C9` |
| Essence   | `RequestSetCastleSiegeTime` `AF`                                                   | `SiegeInfo` `C9` |

Notes: aCis uses fixed weekly siege days with no lord choice (older behaviour); Mobius kept the client packet.

### siege.headquarters — Headquarters and flags

ID: `siege.headquarters` · Shared: **engine**

An attacking clan leader places a siege flag (headquarters) in the siege zone; clan members respawn at it; a destroyed
flag stops respawn. High Five adds territory outposts for Territory War.
Sub-features: flag placement limits (max flags per clan) · respawn at flag · flag HP/destruction · outpost (HF, TW).

| Chronicle | Present | Builds (evidence)          | Difference                      |
| --------- | ------- | -------------------------- | ------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline                        |
| High Five | yes     | nx-l2, l2j-server, sunrise | + TW outposts                   |
| Essence   | yes     | mobius-ess                 | same core (17 diff lines vs IL) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/SiegeFlag.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/status/SiegeFlagStatus.java`, `nx-l2:chronicles/high-five/datapack/data/zones/castle_hq.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/SiegeFlag.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/SiegeFlag.java`

Packets

| Chronicle | Client → server               | Server → client                    |
| --------- | ----------------------------- | ---------------------------------- |
| Interlude | — (flag is placed by a skill) | — (flag is an NPC: `NpcInfo` `16`) |
| High Five | — (flag is placed by a skill) | — (flag is an NPC: `NpcInfo` `0C`) |
| Essence   | — (flag is placed by a skill) | — (flag is an NPC: `NpcInfo` `0C`) |

Notes: `MaxFlags = 1` in all three `nx-l2:chronicles/high-five/datapack/config/Siege.ini`.
Known fixes: `l2j-server@8b4d7e24` (2006-10-09, other) — Fix for spawning siege flax (thanks Kriau); `sunrise-data@6d6e2512` (2016-08-18, other) — Fixed some issues with territory bases.

### siege.artifact — Artifact capture

ID: `siege.artifact` · Shared: **partial**

During a siege, the attacking clan leader casts the seal skill on the castle artifact (Holy Artifact); on success the
castle changes owner. Essence splits the cast into start/finish effects and a «holy thing» target type.
Sub-features: seal skill (Seal of Ruler) · cast conditions · crown/circlet on ownership.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                                        |
| --------- | ------- | -------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline: `TakeCastle` effect, `Holy` target                                                                                      |
| High Five | yes     | nx-l2, l2j-server          | same; plus fortress flag pole (`TakeFort`)                                                                                        |
| Essence   | differs | mobius-ess                 | `TakeCastleStart` + `TakeCastle`, `HolyThing` target, `PossessHolythingSkillCondition`; artefact registers itself with the castle |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Artefact.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/ArtefactClick.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/TakeCastle.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerCanTakeCastle.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/Artefact.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/TakeCastle.java`, `mobius-il:dist/game/data/scripts/handlers/skill/targets/Holy.java`
- `mobius-ess:dist/game/data/scripts/handlers/skill/effects/TakeCastleStart.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/targets/HolyThing.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/PossessHolythingSkillCondition.java`

Packets

| Chronicle | Client → server                    | Server → client |
| --------- | ---------------------------------- | --------------- |
| Interlude | — (skill cast on the artefact NPC) | —               |
| High Five | — (skill cast on the artefact NPC) | —               |
| Essence   | — (skill cast on the artefact NPC) | —               |

Notes: Essence `ArtifactSlot` effect is the item «artifact» slot (equipment domain), not the castle artefact.
Known fixes: `l2j-server-dp@2f1b9c21` (2009-09-16, missing-check) — fix for siege msg on start writing seal and missing update file i forgot on last commit. tnx JIV; `l2j-server-dp@632a2a1e` (2012-09-08, other) — BETA: Castle and Fort skills fix. * DataPack-Part for [L5624]; `mobius@6e1df8a4` (2019-11-07, other) — Fixed capturing holy artefact under the shrine. Thanks to Sahar.

### siege.towers-golems — Towers and siege golems

ID: `siege.towers-golems` · Shared: **engine**

Control towers keep defenders' respawn and siege-zone effects alive until destroyed; flame towers power castle traps;
attackers summon siege golems (Dwarven skill) under a summon condition.
Sub-features: control tower · flame tower · trap upgrade link · siege golem summon condition.

| Chronicle | Present | Builds (evidence)          | Difference                                            |
| --------- | ------- | -------------------------- | ----------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline                                              |
| High Five | yes     | nx-l2, l2j-server          | same                                                  |
| Essence   | yes     | mobius-ess                 | same (ControlTower 17, FlameTower 4 diff lines vs IL) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/ControlTower.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/FlameTower.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Tower.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/TowerSpawn.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerCanSummonSiegeGolem.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/ControlTower.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/conditions/ConditionPlayerCanSummonSiegeGolem.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/ControlTower.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/CanSummonSiegeGolemSkillCondition.java`

Packets

| Chronicle | Client → server | Server → client     |
| --------- | --------------- | ------------------- |
| Interlude | —               | — (towers are NPCs) |
| High Five | —               | — (towers are NPCs) |
| Essence   | —               | — (towers are NPCs) |

Notes: Underground Coliseum `UCTower` (HF) belongs to its minigame, not to sieges.

### siege.guards-mercenaries — Guards and mercenaries

ID: `siege.guards-mercenaries` · Shared: **partial**

Castle owners place mercenary tickets inside the castle before a siege; each ticket spawns a guard of the matching
type when the siege starts; siege guards use dedicated AI.
Sub-features: mercenary ticket placement and limit per castle · ticket-to-guard spawn · siege guard AI · guard pricing.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                                                  |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline: `MercTicketManager`, per-castle limit in `mobius-il:dist/game/config/Siege.ini`                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise | same; fortress guards separate                                                                                                              |
| Essence   | differs | mobius-ess                 | no `MercTicketManager`; tickets resolved by `SiegeGuardManager` + `SiegeGuardHolder`/`SiegeGuardType` (manager rewritten, 424 vs 269 lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/MercTicketManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/SiegeGuardManager.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/MercTicket.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/SiegeGuardAI.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/CastleMercenaryManager/CastleMercenaryManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/MercTicketManager.java`, `mobius-il:java/org/l2jmobius/gameserver/ai/SiegeGuardAI.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/SiegeGuardManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/SiegeGuardHolder.java`, `mobius-ess:dist/game/data/scripts/handlers/items/MercTicket.java`

Packets

| Chronicle | Client → server           | Server → client |
| --------- | ------------------------- | --------------- |
| Interlude | — (ticket is an item use) | —               |
| High Five | — (ticket is an item use) | —               |
| Essence   | — (ticket is an item use) | —               |

Notes: Essence mercenary _players_ (castle war recruiting) are a separate feature — `siege.mercenary-war`.
Known fixes: `l2j-server@a034b329` (2013-11-03, crash-npe) — BETA: NPE Fix for SiegeGuardManager. * Reported by: Maui * Tested by: Maui.

### siege.castle-management — Castle management

ID: `siege.castle-management` · Shared: **partial**

The owning clan manages the castle through NPCs: chamberlain (doors, traps, functions, vault, tax, manor certificate,
defender list), doormen, blacksmith, court magician, warehouse, teleporter. Essence adds the castle side choice
(light/dark) with its own effects.
Sub-features: chamberlain menu · door/trap upgrades · castle functions · castle vault · doormen · Essence castle side.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                    |
| --------- | ------- | -------------------------- | --------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline                                                                                      |
| High Five | yes     | nx-l2, l2j-server          | + ambassador (fortress contract), Territory War clan lists                                    |
| Essence   | differs | mobius-ess                 | castle side LIGHT/DARK (`CastleSide`, `CastleSideEffect`), door manager script, no ambassador |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/CastleChamberlain/CastleChamberlain.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/{CastleBlacksmith,CastleCourtMagician,CastleWarehouse,CastleTeleporter,CastleAmbassador}/`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/CastleDoorman.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CastleManager.java`, `nx-l2:chronicles/high-five/datapack/data/zones/castle_trap.xml`
- `mobius-il:dist/game/data/scripts/ai/others/CastleChamberlain/CastleChamberlain.java`, `mobius-il:java/org/l2jmobius/gameserver/managers/CastleManager.java`
- `mobius-ess:dist/game/data/scripts/ai/others/CastleChamberlain/CastleChamberlain.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/CastleSide.java`, `mobius-ess:dist/game/data/scripts/ai/others/CastleSideEffect.java`, `mobius-ess:dist/game/data/scripts/ai/others/CastleDoorManager/CastleDoorManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/CastleData.java`

Packets

| Chronicle | Client → server         | Server → client                         |
| --------- | ----------------------- | --------------------------------------- |
| Interlude | — (NPC dialog bypasses) | —                                       |
| High Five | — (NPC dialog bypasses) | —                                       |
| Essence   | — (NPC dialog bypasses) | `ExCastleState` `FE:012E` (castle side) |

Notes: castle tax and manor belong to the `economy` domain.

### siege.territory-war — Territory War

ID: `siege.territory-war` · Shared: **chronicle-only**

Every other week, clans and players registered for a territory fight over territory wards in all castle and fortress
zones at once; ward capture, territory quests and badges reward participants; outposts replace siege flags.
Sub-features: registration (clan / mercenary) · territory wards · outposts · TW quests (717–729) · badges and rewards ·
disguise transformation.

| Chronicle | Present | Builds (evidence)                                                     | Difference                                                                                                                                         |
| --------- | ------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis                                                       | —                                                                                                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, advanced (`DominionSiegeEvent`) | baseline                                                                                                                                           |
| Essence   | no      | mobius-ess                                                            | only leftovers: `mobius-ess:dist/game/data/zones/territory_war_*.xml` zones, `CanTransformInDominion` / `OpTerritory` skill conditions; no manager |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/TerritoryWarManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/TerritoryWard.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/TerritoryWard.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/TerritoryManagers/TerritoryManagers.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/TakeTerritoryFlag.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestJoinDominionWar,RequestDominionInfo}.java`, `nx-l2:chronicles/high-five/datapack/config/TerritoryWar.ini`
- `mobius-ess:dist/game/data/zones/territory_war_hall.xml` (leftover)
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/TerritoryStatus.java`
- `mobius-il:dist/game/data/scripts/handlers/bypass/npc/TerritoryStatus.java`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/TerritoryStatus.java`

Packets

| Chronicle | Client → server                                                 | Server → client                                                                                                                                                                                                     |
| --------- | --------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | `RequestDominionInfo` `D0:58`, `RequestJoinDominionWar` `D0:57` | `ExReplyDominionInfo` `FE:92`, `ExShowDominionRegistry` `FE:90`, `ExDominionWarStart` `FE:A3`, `ExDominionWarEnd` `FE:A4`, `ExShowTerritory` `FE:8C`; `RelationChanged` `CE` bit `RELATION_TERRITORY_WAR` `0x80000` |
| Essence   | —                                                               | `ExShowTerritory` `FE:8D`                                                                                                                                                                                           |

Notes: L2Emu Essence 542 (forum release) ships a config `l2emu-ess:gs-sr/L2Emu-Essence-GS_SR-542-main/config/main/events/territorywar.properties` — legacy carry-over, not an Essence mechanic. MaskedRiders PvP fork of L2JTW re-adds `TerritoryWarManager` from L2J Server. The `TerritoryStatus` NPC bypass handler shows the territory war status in all three Mobius trees.
Known fixes: `l2j-server@5c43e702` (2010-05-30, crash-npe) — NPE fix and use getCastle() for checking TW ward location.

### siege.mercenary-war — Mercenaries in Essence sieges

ID: `siege.mercenary-war` · Shared: **chronicle-only**

In Essence, a clan registered for a castle siege can recruit individual players as mercenaries through the castle-war
UI; mercenaries fight for that side, see the siege HUD and get a share of the castle treasury by mail after the siege.
Sub-features: recruit settings · join / leave · member list · attacker/defender lists · siege HUD · observer start ·
treasury payout.

| Chronicle | Present | Builds (evidence)     | Difference |
| --------- | ------- | --------------------- | ---------- |
| Interlude | no      | mobius-il             | —          |
| High Five | no      | nx-l2                 | —          |
| Essence   | yes     | mobius-ess, l2emu-ess | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/castlewar` (8 packets: `ExMercenaryCastleWarCastleSiege{Info,HudInfo,AttackerList,DefenderList}`, `ExPledgeMercenary{MemberJoin,MemberList,RecruitInfoSet}`, `ExCastleWarObserverStart`), `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/castlewar`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/MercenaryPledgeHolder.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/Siege.java` (payout ~l.295–352)

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                    | Server → client                                                                                                                                                                                                                                                                                |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExMercenaryCastleWarCastleSiegeInfo` `D0:0188/D0:0189`, `ExMercenaryCastleWarCastleSiegeHudInfo` `D0:0189`, `ExMercenaryCastleWarCastleSiegeAttackerList` `D0:018A`, `ExMercenaryCastleWarCastleSiegeDefenderList` `D0:018B`, `ExPledgeMercenaryRecruitInfoSet` `D0:0187`, `ExPledgeMercenaryMemberJoin` `D0:018D`, `ExPledgeMercenaryMemberList` `D0:018C`, `ExCastleWarObserverStart` `D0:0197` | `MercenaryCastleWarCastleInfo` `FE:0235`, `MercenaryCastleWarCastleSiegeInfo` `FE:0237`, `MercenaryCastleWarCastleSiegeHudInfo` `FE:0236`, `MercenaryCastleWarCastleSiegeAttackerList` `FE:0238`, `MercenaryCastleWarCastleSiegeDefenderList` `FE:0239`, `MercenaryPledgeMemberList` `FE:023A` |

Notes: present since the earliest Essence line in Mobius (04.2).

### siege.prison — Castle and fortress dungeons

ID: `siege.prison` · Shared: **chronicle-only**

Instanced dungeons of the castle and fortress owners (called prisons in some builds): a party made of members of the owning clan
enters through a castle NPC, fights three waves of raid bosses in an isolated instance and may re-enter only after a timer;
entry is closed while a siege is running.
Sub-features: castle dungeon (one entry NPC per castle, three boss waves) · fortress dungeon instances · re-entry timer · siege lock.

| Chronicle | Present | Builds (evidence) | Difference                                                                                                                                       |
| --------- | ------- | ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | mobius-il         | —                                                                                                                                                |
| High Five | yes     | nx-l2             | baseline: entry NPCs 36403–36411 (instance ids 13–21), 4 h re-entry, entry needs a party of the castle-owning clan and a fortress contract state |
| Essence   | no      | mobius-ess        | — (`ClanDungeon` / `LeonasDungeon` are other content)                                                                                            |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/instances/CastleDungeon/CastleDungeon.java`, `nx-l2:chronicles/high-five/datapack/data/instances/CastleDungeon/*.xml`, `nx-l2:chronicles/high-five/datapack/data/instances/FortressDungeon/*.xml`, `nx-l2:chronicles/high-five/datapack/data/scripts/instances/CastleDungeon/*.html`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| High Five | — (NPC dialog)  | —               |

Notes: the 21 fortress dungeon instance files exist in the datapack but no entry script for them is present in nx-l2; fortress quests (`Q00511_AwlUnderFoot`, `Q00726_LightWithinTheDarkness`) reference them.
