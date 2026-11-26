# Spawning

Spawn data, respawn, corpse decay, day/night spawns, random spawns, champions and stat multipliers.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### spawn.engine — Spawn engine

ID: `spawn.engine` · Shared: **partial**

Spawning of NPCs from data: spawn lists, spawn groups with conditions, territories, manual (GM) spawns and spawn selections.
Sub-features: spawn list loading · spawn groups/templates · territories · manual spawn · auto-spawn handler.

| Chronicle | Present | Builds (evidence)                                                       | Difference                                                                                                          |
| --------- | ------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`SpawnManager`, `spawnlist`), l2jfrozen (`SpawnTable`) | aCis XML spawnlist; L2jFrozen SQL table                                                                             |
| High Five | yes     | nx-l2, l2j-server (`spawnlist`, `spawnZones`)                           | baseline: `SpawnData` + `SpawnGroup` + `SpawnGroupTable`, `AutoSpawns.xml`                                          |
| Essence   | yes     | mobius-ess                                                              | template model (`SpawnTemplate`, `NpcSpawnTemplate`, `SpawnSelection`); no `SpawnGroupTable`; `SpawnData` 30% of HF |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/SpawnData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/spawns/{Spawn,SpawnGroup,SpawnGroupEntry,AutoSpawnHandler}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/SpawnGroupTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/SpawnGroupTaskManager.java`, `nx-l2:chronicles/high-five/datapack/data/spawns/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/SpawnData.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/spawns`, `mobius-il:dist/game/data/spawns`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SpawnData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/spawns/{Spawn,SpawnGroup,SpawnTemplate,NpcSpawnTemplate,SpawnSelection}.java`, `mobius-ess:dist/game/data/spawns`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/manager/SpawnManager.java`
- `l2jfrozen:**/SpawnTable.java` (found by file name)

Packets

| Chronicle | Client → server | Server → client                                                                                             |
| --------- | --------------- | ----------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `NpcInfo` `16`, `ServerObjectInfo` `8C`, `DeleteObject` `12`                                                |
| High Five | —               | `NpcInfo` `0C`, `ServerObjectInfo` `92`, `DeleteObject` `08`, `ExSpawnEmitter` `FE:5D`                      |
| Essence   | —               | `NpcInfo` `0C`, `ServerObjectInfo` `92`, `DeleteObject` `08`, `ExSpawnEmitter` `FE:5E`, `NpcInfoState` `C1` |

Notes: the Essence spawn model is the most different of the three; spawn data is not interchangeable.

Known fixes: `l2j-server-dp@5b1d309c` (2005-11-14, other) — forgotten spawns html in admin and 224_sagittarius fix; `mobius@624e75d6` (2018-09-05, other) — Fixed border patrol guard spawns.

### spawn.respawn — Respawn

ID: `spawn.respawn` · Shared: **engine**

Respawn of dead NPCs: base delay, random spread, respawn-time multiplier, timed queue of pending respawns.
Sub-features: respawn queue · random delay · server multiplier · respawn zones (player respawn areas).

| Chronicle | Present | Builds (evidence)                                                     | Difference                                              |
| --------- | ------- | --------------------------------------------------------------------- | ------------------------------------------------------- |
| Interlude | yes     | mobius-il; aCis, l2jfrozen (no separate respawn manager by file name) | respawn timers live inside spawn classes outside Mobius |
| High Five | yes     | nx-l2, l2j-server, sunrise                                            | baseline: `RespawnTaskManager`                          |
| Essence   | yes     | mobius-ess                                                            | `RespawnTaskManager` identical to HF (100%)             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/RespawnTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/spawns/Spawn.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/RespawnTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/RespawnTaskManager.java`

Packets

| Chronicle | Client → server | Server → client           |
| --------- | --------------- | ------------------------- |
| Interlude | —               | `NpcInfo` `16` on respawn |
| High Five | —               | `NpcInfo` `0C` on respawn |
| Essence   | —               | `NpcInfo` `0C` on respawn |

Notes: conditional `engine`: only the Mobius-family manager was compared; aCis and L2jFrozen schedule respawn in their own classes (not opened).

Known fixes: `l2j-server@ce8f52cc` (2011-08-02, other) — Minor Fixes for /loc in RespawnZones by Nyaran.

### spawn.decay — Corpse decay

ID: `spawn.decay` · Shared: **engine**

Removal of NPC corpses after a delay: decay timers by NPC type, spoiled/sweepable corpse timer, raid decay.
Sub-features: decay queue · delay by type · spoiled-corpse delay · sweeper delay.

| Chronicle | Present | Builds (evidence)                                                    | Difference                         |
| --------- | ------- | -------------------------------------------------------------------- | ---------------------------------- |
| Interlude | yes     | mobius-il, acis (`DecayTaskManager`), l2jfrozen (`DecayTaskManager`) | same idea                          |
| High Five | yes     | nx-l2, l2j-server, sunrise                                           | baseline: `DecayTaskManager`       |
| Essence   | yes     | mobius-ess                                                           | `DecayTaskManager` 96% equal to HF |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/DecayTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/DecayTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/DecayTaskManager.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/taskmanager/DecayTaskManager.java`

Packets

| Chronicle | Client → server | Server → client     |
| --------- | --------------- | ------------------- |
| Interlude | —               | `DeleteObject` `12` |
| High Five | —               | `DeleteObject` `08` |
| Essence   | —               | `DeleteObject` `08` |

Notes: the 4% HF / Essence difference was not read line by line.

### spawn.day-night — Day/night spawns

ID: `spawn.day-night` · Shared: **partial**

Spawn groups that exist only at night or only by day; spawns swap on the day/night change event.
Sub-features: night/day spawn groups · transition event.

| Chronicle | Present | Builds (evidence)                                                                                     | Difference                                                      |
| --------- | ------- | ----------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| Interlude | yes     | mobius-il (`DayNightSpawnManager`), acis (`OnDayNightSpawnMaker`), l2jfrozen (`DayNightSpawnManager`) | manager vs spawn-maker variant                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise                                                                            | baseline: `DayNightSpawnManager`                                |
| Essence   | yes     | mobius-ess                                                                                            | no manager; script `Spawns/DayNightSpawns` + `OnDayNightChange` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/DayNightSpawnManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/events/holders/OnDayNightChange.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/DayNightSpawnManager.java`
- `mobius-ess:dist/game/data/scripts/ai/others/Spawns/DayNightSpawns.java`
- `acis:**/OnDayNightSpawnMaker.java` (found by file name)

Packets

| Chronicle | Client → server | Server → client                     |
| --------- | --------------- | ----------------------------------- |
| Interlude | —               | `NpcInfo` `16`, `DeleteObject` `12` |
| High Five | —               | `NpcInfo` `0C`, `DeleteObject` `08` |
| Essence   | —               | `NpcInfo` `0C`, `DeleteObject` `08` |

Notes: Essence moves the logic into the script layer.

Known fixes: `l2j-server@14573b26` (2006-11-17, other) — Little fix to DayNightSpawnManager; `l2j-server@19eb9b9c` (2022-04-26, concurrency) — fix(raidboss): Fixed RB Eilhalder von Hellmann not spawning.

### spawn.random-spawns — Random spawns

ID: `spawn.random-spawns` · Shared: **partial**

Mobius custom: selected NPCs spawn at a random point from a candidate list instead of a fixed point.
Sub-features: per-NPC random point list · config toggle.

| Chronicle | Present | Builds (evidence)                                                                                                             | Difference                                       |
| --------- | ------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| Interlude | differs | mobius-il (custom backport); aCis (`RandomSpawnMaker`, other mechanism); not in L2jFrozen                                     | Mobius config vs aCis spawn maker                |
| High Five | yes     | nx-l2 (`RandomSpawns.ini`, script `ai/others/RandomSpawn`), l2j-server (`RandomSpawn`), eternity (`ai/modifiers/RandomSpawn`) | baseline                                         |
| Essence   | yes     | mobius-ess (`RandomSpawnsConfig`)                                                                                             | config only (no script; logic read from `Spawn`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/RandomSpawnsConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/spawns/Spawn.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/RandomSpawn.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/RandomSpawns.ini`
- `mobius-il:java/org/l2jmobius/gameserver/config/custom/RandomSpawnsConfig.java`, `mobius-il:dist/game/config/Custom/RandomSpawns.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/config/custom/RandomSpawnsConfig.java`, `mobius-ess:dist/game/config/Custom/RandomSpawns.ini`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | `NpcInfo` `16`  |
| High Five | —               | `NpcInfo` `0C`  |
| Essence   | —               | `NpcInfo` `0C`  |

Notes: forum builds implement the same idea as scripts.

### spawn.champions — Champion monsters

ID: `spawn.champions` · Shared: **partial**

Mobius custom (also a classic L2J mod): a monster may spawn as a champion with boosted stats, extra rewards and a special title.
Sub-features: chance and level window · stat multipliers · extra drop / adena chance · title · aggressive/passive flag.

| Chronicle | Present | Builds (evidence)                                                                            | Difference                                                                                                 |
| --------- | ------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il (custom backport), l2jfrozen (`champion.properties`); not in aCis                  | Mobius `ChampionMonsters.ini` vs L2jFrozen `config/fun/champion.properties`                                |
| High Five | yes     | nx-l2 (`ChampionMonsters.ini`), eternity (`mods/champion.ini`), sunrise (`ChampionMobs.ini`) | baseline                                                                                                   |
| Essence   | yes     | mobius-ess                                                                                   | stat finalizers (`PAttackFinalizer`, `MAttackFinalizer`, `RegenHPFinalizer`, ...) read the champion config |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/ChampionMonstersConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Attackable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/NpcTemplate.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Creature.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/ChampionMonsters.ini`
- `mobius-il:java/org/l2jmobius/gameserver/config/custom/ChampionMonstersConfig.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/Attackable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/config/custom/ChampionMonstersConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Attackable.java`, stat finalizers under `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/finalizers`

Packets

| Chronicle | Client → server | Server → client                                                    |
| --------- | --------------- | ------------------------------------------------------------------ |
| Interlude | —               | `NpcInfo` `16` (title / abnormal effect carries the champion look) |
| High Five | —               | `NpcInfo` `0C` (title / abnormal effect carries the champion look) |
| Essence   | —               | `NpcInfo` `0C` (title / abnormal effect carries the champion look) |

Notes: no dedicated packet; champion state is a flag on the instance.

### spawn.npc-stat-multipliers — NPC stat multipliers

ID: `spawn.npc-stat-multipliers` · Shared: **partial**

Mobius custom: global multipliers for NPC stats (HP, MP, attack, defence, speed, ...) by NPC kind.
Sub-features: per-kind multipliers · config toggle.

| Chronicle | Present | Builds (evidence)                | Difference                            |
| --------- | ------- | -------------------------------- | ------------------------------------- |
| Interlude | differs | mobius-il (custom backport only) | not found in aCis / L2jFrozen         |
| High Five | yes     | nx-l2 (`NpcStatMultipliers.ini`) | baseline                              |
| Essence   | yes     | mobius-ess                       | same config, applied in `NpcTemplate` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/NpcStatMultipliersConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/NpcTemplate.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/NpcStatMultipliers.ini`
- `mobius-il:java/org/l2jmobius/gameserver/config/custom/NpcStatMultipliersConfig.java`
- `mobius-ess:java/org/l2jmobius/gameserver/config/custom/NpcStatMultipliersConfig.java`

Packets: none (server-side).

Notes: among the 44 `full` roots only the three Mobius builds contain it.
