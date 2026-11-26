# NPC and mob AI

AI engine, aggro, walking routes, minions, AI scripts and fake players.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### ai.engine — AI engine

ID: `ai.engine` · Shared: **partial**

Behaviour engine of every creature: intentions (idle, attack, cast, move, follow, pick-up), think tasks, separate AI
classes for monsters, players, summons, siege guards, doors and vehicles.
Sub-features: intention/action queue · attackable (monster) AI · player AI · summon AI · siege-guard AI · door and vehicle AI · batched think-task scheduling.

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                      |
| --------- | ------- | ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`model/actor/ai`), l2jfrozen (`L2AttackableAI`) | aCis replaces the intention model with Desire/DesireQueue                                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                             | baseline: `CreatureAI`, `AttackableAI`, `PlayerAI`, `SummonAI`, `SiegeGuardAI`, `DoorAI`, `BoatAI`, `AirShipAI` |
| Essence   | yes     | mobius-ess                                                       | `AttackableAI` much smaller (normalized 48% of HF), `CreatureAI` 73%; `AttackableThinkTaskManager` identical    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/CreatureAI.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/AttackableAI.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/PlayerAI.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/SummonAI.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/AttackableThinkTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/ai/AttackableAI.java`, `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/AttackableThinkTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/ai/AttackableAI.java`, `mobius-ess:java/org/l2jmobius/gameserver/ai/CreatureAI.java`, `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/AttackableThinkTaskManager.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/ai` (`acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/ai/{Desire,DesireQueue,Intention}.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/ai/type/`)
- `l2jfrozen:**/L2AttackableAI.java` (found by file name)
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/attackable/OnKillNotifyTask.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/attackable/OnKillNotifyTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/attackable/OnKillNotifyTask.java`

Packets

| Chronicle | Client → server                                                        | Server → client                                                                                                                                                                                |
| --------- | ---------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `AttackRequest` `0A`                                                   | `CharMoveToLocation` `01`, `StopMove` `47`, `MyTargetSelected` `A6`, `TargetSelected` `29`                                                                                                     |
| High Five | `AttackRequest` `01`                                                   | `MoveToLocation` `2F`, `StopMove` `47`, `MyTargetSelected` `B9`, `TargetSelected` `23`, `ExStopMoveAirship` `FE:66`, `ExStopMoveInAirship` `FE:6E`, `ExStopMoveInShuttle` `FE:CF` for vehicles |
| Essence   | `AttackRequest` `01`, `StopMoveToward` `33`, `RequestStopMove` `D0:EE` | `StopMove` `47`, `TargetSelected` `23`                                                                                                                                                         |

Notes: AI is server-internal; packets are movement and targeting side effects. `OnKillNotifyTask` delivers the delayed kill notification from a dying monster to the quest and AI listeners.

Known fixes: `mobius@e44fbf02` (2017-09-15, crash-npe) — Fixed possible L2AttackableAI thinkAttack NPE; `l2j-server@d21a7b25` (2011-01-10, missing-check) — Two fixes in L2AttackableAI. If mob is not mage/healer than it should not use constantly atk skill if target is running.

### ai.aggro — Aggro and hate

ID: `ai.aggro` · Shared: **engine**

Per-NPC hate list: damage and heal hate per attacker, most-hated target selection, help from clan/faction members,
return home when the target is out of range.
Sub-features: hate list (`AggroInfo`) · damage/hate modifiers · assist faction · leash / return home.

| Chronicle | Present | Builds (evidence)                                                               | Difference                                                        |
| --------- | ------- | ------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`AggroList`, `HateList`), l2jfrozen (inline in `L2Attackable`) | aCis: separate list classes; L2jFrozen: map inside the attackable |
| High Five | yes     | nx-l2, l2j-server, sunrise                                                      | baseline: `AggroInfo` holder used by `Attackable`                 |
| Essence   | yes     | mobius-ess                                                                      | `AggroInfo` identical to HF (normalized 100%)                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/npc/AggroInfo.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Attackable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/AttackableAI.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/npc/AggroInfo.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/npc/AggroInfo.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/container/attackable/AggroList.java`, `acis:.../HateList.java`

Packets: none (server-side only).

Notes: `engine` is conditional: the holder is identical in HF and Essence, but aCis and L2jFrozen keep hate in their own classes (not compared line by line).

### ai.walking-routes — NPC routes

ID: `ai.walking-routes` · Shared: **engine**

NPCs that walk fixed routes: waypoints, stop delays, optional chat lines at nodes; routes loaded from XML.
Sub-features: route data · stop delay · node chat messages · run/walk · random walk (Essence guards).

| Chronicle | Present | Builds (evidence)                                                                          | Difference                                                                 |
| --------- | ------- | ------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`walkerRoutes.xml`, `WalkerTaskManager`), l2jfrozen (`walker_routes.csv`) | data format differs (CSV in L2jFrozen)                                     |
| High Five | yes     | nx-l2, l2j-server, eternity                                                                | baseline: `WalkingManager` + `Routes.xml`                                  |
| Essence   | yes     | mobius-ess                                                                                 | `WalkingManager` identical to HF (100%); adds `RandomWalkingGuards` script |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/WalkingManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/holders/NpcRoutesHolder.java`, `nx-l2:chronicles/high-five/datapack/data/Routes.xml`, `nx-l2:chronicles/high-five/datapack/data/xsd/Routes.xsd`
- `mobius-il:java/org/l2jmobius/gameserver/managers/WalkingManager.java`, `mobius-il:dist/game/data/Routes.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/WalkingManager.java`, `mobius-ess:dist/game/data/Routes.xml`, `mobius-ess:dist/game/data/scripts/ai/others/RandomWalkingGuards.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/WalkerRouteData.java`, `acis:aCis_gameserver/java/net/sf/l2j/gameserver/taskmanager/WalkerTaskManager.java`

Packets

| Chronicle | Client → server | Server → client                          |
| --------- | --------------- | ---------------------------------------- |
| Interlude | —               | `CharMoveToLocation` `01`, `NpcSay` `02` |
| High Five | —               | `MoveToLocation` `2F`, `NpcSay` `30`     |
| Essence   | —               | `MoveToLocation` `2F`, `NpcSay` `30`     |

Notes: conditional `engine`: identical manager in HF and Essence; Interlude presence confirmed in aCis and L2jFrozen.

Known fixes: `l2j-server@d1c4e8b6` (2016-01-17, concurrency) — Fixes multithreading issue with walking routes nodes; `l2j-server@ed26f887` (2013-01-13, wrong-formula-value) — BETA: WalkingManager impovements, random respawn interval fix.; `sunrise-data@4c6a942b` (2016-06-03, other) — Correct a bit 35966 npc routes.

### ai.minions — Minions

ID: `ai.minions` · Shared: **engine**

Monsters that spawn with a leader: minion groups defined per leader, respawn of dead minions, following the leader,
despawn with the leader.
Sub-features: minion list per leader · respawn delay · minion definitions · raid-minion tuning.

| Chronicle | Present | Builds (evidence)                                                               | Difference                       |
| --------- | ------- | ------------------------------------------------------------------------------- | -------------------------------- |
| Interlude | yes     | mobius-il, acis (`MinionSpawn`), l2jfrozen (`L2MinionData`, `L2MinionInstance`) | classes differ, concept the same |
| High Five | yes     | nx-l2, l2j-server (`minionData.xml`), sunrise                                   | baseline: `MinionList`           |
| Essence   | yes     | mobius-ess                                                                      | `MinionList` 99% equal to HF     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/npc/MinionList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Monster.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/npc/MinionList.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/npc/MinionList.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/spawn/MinionSpawn.java`

Packets

| Chronicle | Client → server | Server → client                     |
| --------- | --------------- | ----------------------------------- |
| Interlude | —               | `NpcInfo` `16`, `DeleteObject` `12` |
| High Five | —               | `NpcInfo` `0C`, `DeleteObject` `08` |
| Essence   | —               | `NpcInfo` `0C`, `DeleteObject` `08` |

Notes: conditional `engine`, same reasoning as `ai.aggro`.

Known fixes: `l2j-server@85171ea0` (2015-02-16, crash-npe) — Fix for UnsupportedOperationException in MinionList; `mobius@5c712ec4` (2020-01-02, other) — Fixed aggressive minions after respawn; `sunrise@5db30152` (2015-07-28, crash-npe) — Fixed one npe when minion is a bit far away from leader and attacker is a trap Correct some messages in battlefield event and removed some….

### ai.npc-scripts — NPC AI scripts

ID: `ai.npc-scripts` · Shared: **partial**

Per-NPC and per-area AI scripts: area scripts (`ai/areas`), boss scripts (`ai/bosses`, see `boss.grand-boss`) and behaviour scripts
(`ai/others`). One engine feature; scripts are not enumerated.
Sub-features: area AI · boss AI · behaviour scripts · personal AI data (`NpcPersonalAIData`, IL/HF only).

| Chronicle | Present | Builds (evidence)                                                                                  | Difference                               |
| --------- | ------- | -------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| Interlude | yes     | mobius-il (`ai/areas` 17, `ai/bosses` 11, `ai/others` 46), acis (`scripting/script/ai/individual`) | aCis layout: `ai/individual/Monster/...` |
| High Five | yes     | nx-l2 (`ai/areas` 31, `ai/bosses` 14, `ai/others` 83), l2j-server                                  | baseline                                 |
| Essence   | yes     | mobius-ess (`ai/areas` 28, `ai/bosses` 16, `ai/others` 51)                                         | `NpcPersonalAIData` absent; own area set |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/bosses/`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/NpcPersonalAIData.java`
- `mobius-il:dist/game/data/scripts/ai/areas`, `mobius-il:dist/game/data/scripts/ai/bosses`, `mobius-il:dist/game/data/scripts/ai/others`
- `mobius-ess:dist/game/data/scripts/ai/areas`, `mobius-ess:dist/game/data/scripts/ai/bosses`, `mobius-ess:dist/game/data/scripts/ai/others`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/scripting/script/ai/individual`

Packets

| Chronicle | Client → server | Server → client                           |
| --------- | --------------- | ----------------------------------------- |
| Interlude | —               | `NpcSay` `02`, skill and movement packets |
| High Five | —               | `NpcSay` `30`, skill and movement packets |
| Essence   | —               | `NpcSay` `30`, skill and movement packets |

Notes: counts are directory entries as of `5ddd6ff4b98`.

### ai.fake-players — Fake players

ID: `ai.fake-players` · Shared: **partial**

Server-controlled characters that populate towns and hunting grounds: generated from class templates with equipment, buffs, spawn waves,
chat phrases and PvP flagging. A Mobius custom, not a retail mechanic.
Sub-features: generation by class · equipment and enchant · wave spawn/despawn · chat phrases · PvP flagging · admin command.

| Chronicle | Present | Builds (evidence)                                          | Difference                                                         |
| --------- | ------- | ---------------------------------------------------------- | ------------------------------------------------------------------ |
| Interlude | differs | mobius-il (custom backport); not found in aCis / L2jFrozen | Mobius custom only; the Interlude family roots have no such system |
| High Five | yes     | nx-l2, eternity (`phantoms`), premium                      | baseline: `FakePlayers` custom (config + XML data)                 |
| Essence   | yes     | mobius-ess, l2emu-ess (`fakeplayers` scripts)              | same Mobius custom; data files same layout                         |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/FakePlayerData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/FakePlayerChatManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/npc/FakePlayerHolder.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/FakePlayerInfo.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/FakePlayers/`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminFakePlayers.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/FakePlayers.ini`, `nx-l2:chronicles/high-five/datapack/data/FakePlayerChatData.xml`, `nx-l2:chronicles/high-five/datapack/data/spawns/Others/FakePlayers.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/FakePlayerData.java`, `mobius-il:dist/game/config/Custom/FakePlayers.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/FakePlayerData.java`, `mobius-ess:dist/game/config/Custom/FakePlayers.ini`

Packets

| Chronicle | Client → server                       | Server → client                                                                 |
| --------- | ------------------------------------- | ------------------------------------------------------------------------------- |
| Interlude | — (looks like a player to the client) | player-like info packet: class `FakePlayerInfo`, sent as `CharInfo` `03`        |
| High Five | — (looks like a player to the client) | player-like info packet: class `FakePlayerInfo`, sent as `CharInfo` `31`        |
| Essence   | — (looks like a player to the client) | player-like info packet: class `FakePlayerInfo`, sent as `ExCharInfo` `FE:0255` |

Notes: forum HF builds (L2jEternity, L2JPremium) implement phantom parties with class AI; that is a separate, richer design.
