# World

World grid and visibility, game time, doors, static objects, fences and map regions.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### world.regions-visibility — World and visibility

ID: `world.regions-visibility` · Shared: **partial**

The world is a grid of regions (2048-unit cells, `SHIFT_BY = 11`); each object lives in one region and sees objects in
the 3x3 neighbourhood. Entering or leaving a region updates known-object lists; creature visibility checks run in a
periodic task manager. World bounds are constants of the chronicle (tile ranges).
Sub-features: region grid · known/visible objects · region activation (AI on/off by player presence) · world bounds ·
see-task for creatures.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                  |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | tiles X 16..26, Y 10..25 (Mobius IL); no Gracia bounds                      |
| High Five | yes     | nx-l2, l2j-server, sunrise                    | baseline; tiles X 11..26, Y 10..26; `GRACIA_MAX_X/Z` constants              |
| Essence   | yes     | mobius-ess                                    | tiles X 11..28, Y 10..26; instance is an object (`Instance`), not an int id |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/World.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/WorldRegion.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/CreatureSeeTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/World.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/WorldRegion.java`, `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/CreatureSeeTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/World.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/WorldRegion.java`

Packets

| Chronicle | Client → server          | Server → client                                                                                                   |
| --------- | ------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestRecordInfo` `CF` | `CharInfo` `03`, `NpcInfo` `16`, `DeleteObject` `12`, `SpawnItem` `0B`, `DropItem` `0C` (object appear/disappear) |
| High Five | `RequestRecordInfo` `6E` | `CharInfo` `31`, `NpcInfo` `0C`, `DeleteObject` `08`, `SpawnItem` `05`, `DropItem` `16`                           |
| Essence   | —                        | `CharInfo` `31`, `NpcInfo` `0C`, `DeleteObject` `08`, `SpawnItem` `05`                                            |

Notes: aCis, L2jFrozen and L2J Server use the same 2048-unit region grid (found as `WorldRegion` in every IL root).
Region tile range is the only data that differs by chronicle; keep it as a per-chronicle constant.

Known fixes: `l2j-server@b5045aa3` (2008-07-12, wrong-formula-value) — 1) Added try/catch to avoid stopping update if something is wrong into updateRegion() 2) removed force update into L2WorldRegion, its will…; `mobius@7adde43b` (2020-02-01, crash-npe) — Fixed probable findCloseObjects NPE.

### world.game-time — Game time

ID: `world.game-time` · Shared: **engine**

In-game clock: 6 game days per real day (one game day = 4 real hours), 10 ticks per second; day/night state flips on a
quarter-day boundary and notifies day/night listeners (night spawns, night skill bonuses).
Sub-features: tick clock · day/night state · day/night listeners · `/time` display (`ClientSetTime`).

| Chronicle | Present | Builds (evidence)          | Difference              |
| --------- | ------- | -------------------------- | ----------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | same (4 h per game day) |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline                |
| Essence   | yes     | mobius-ess                 | same                    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/GameTimeTaskManager.java` (+ test `nx-l2:chronicles/high-five/server/src/test/java/app/l2nx/highfive/taskmanagers/GameTimeTaskManagerTest.java`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionGameTime.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/GameTimeTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/GameTimeTaskManager.java`

Packets

| Chronicle | Client → server | Server → client                     |
| --------- | --------------- | ----------------------------------- |
| Interlude | —               | `ClientSetTime` `EC`, `Sunset` `1D` |
| High Five | —               | `ClientSetTime` `F2`, `Sunset` `13` |
| Essence   | —               | `ClientSetTime` `F2`, `SunSet` `13` |

Notes: aCis keeps the same 4 h game day but expresses it in game minutes (`GameTimeTaskManager`: 10 s per game minute).

Known fixes: `l2j-server@96e4fd95` (2008-01-31, concurrency) — Removed toArray() expensive operation. Fixed synchronization wrongly placed on GameTimeController methods. Splited Arival notification…; `l2j-server@3537e519` (2008-02-22, concurrency) — Fix for GameTimeController synchronization problems.

### world.doors — Doors

ID: `world.doors` · Shared: **partial**

Doors are world objects created from XML templates: HP and defence, open/close by click, skill, clan or script, scheduled
open/close, linked clusters (child doors), geodata blocking, siege destruction (castle/fort/clan hall doors).
Sub-features: door data · open/close by click · auto open/close timers · door groups · siege doors · geodata door check.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                               |
| --------- | ------- | -------------------------- | -------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | 547 doors; `Doors.xml`                                                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise | 1301 doors; adds `check_collision`, `emitter_id`, `child_id_event`, `close_time`                         |
| Essence   | yes     | mobius-ess                 | `DoorData.xml` (1257 doors); stat attributes (`baseHpMax`, `basePDef`, …) dropped, doors use stat tables |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/DoorData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Door.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/DoorAI.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/DoorTemplate.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/DoorClick.java`, `nx-l2:chronicles/high-five/datapack/data/Doors.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/DoorData.java`, `mobius-il:java/org/l2jmobius/gameserver/ai/DoorAI.java`, `mobius-il:dist/game/data/Doors.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/DoorData.java`, `mobius-ess:dist/game/data/DoorData.xml`
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/shiftclick/DoorShiftClick.java`
- `mobius-il:dist/game/data/scripts/handlers/actions/shiftclick/DoorShiftClick.java`
- `mobius-ess:dist/game/data/scripts/handlers/actions/shiftclick/DoorShiftClick.java`

Packets

| Chronicle | Client → server            | Server → client                          |
| --------- | -------------------------- | ---------------------------------------- |
| Interlude | `Action` `04` (door click) | `DoorInfo` `4C`, `DoorStatusUpdate` `4D` |
| High Five | `Action` `1F` (door click) | `DoorInfo` `4C`, `DoorStatusUpdate` `4D` |
| Essence   | `Action` `1F`              | `DoorInfo` `4C`, `DoorStatusUpdate` `4D` |

Notes: aCis keeps the same 547-door set with a `level` attribute on every door.
Every door is described by its own data record and has a world position, so `door` is a unique entity (see the glossary).

### world.static-objects — Static objects

ID: `world.static-objects` · Shared: **engine**

Static world objects the client draws as clickable furniture: town signs, boards, thrones, maps. Loaded from XML with an
id, a type and a position; sent to the client as a static-object packet.
Sub-features: object table · static object packet · type (sign, map, throne).

| Chronicle | Present | Builds (evidence)                            | Difference |
| --------- | ------- | -------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis (`StaticObjects`), l2jfrozen | same       |
| High Five | yes     | nx-l2, l2j-server, sunrise                   | baseline   |
| Essence   | yes     | mobius-ess                                   | same       |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/StaticObjectData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/StaticObject.java`, `nx-l2:chronicles/high-five/datapack/data/StaticObjects.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/StaticObjectData.java`, `mobius-il:dist/game/data/StaticObjects.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/StaticObjectData.java`, `mobius-ess:dist/game/data/StaticObjects.xml`
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/StaticObjectClick.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/shiftclick/StaticObjectShiftClick.java`
- `mobius-il:dist/game/data/scripts/handlers/actions/click/StaticObjectClick.java`, `mobius-il:dist/game/data/scripts/handlers/actions/shiftclick/StaticObjectShiftClick.java`
- `mobius-ess:dist/game/data/scripts/handlers/actions/click/StaticObjectClick.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/shiftclick/StaticObjectShiftClick.java`

Packets

| Chronicle | Client → server | Server → client         |
| --------- | --------------- | ----------------------- |
| Interlude | —               | `StaticObject` `99`     |
| High Five | —               | `StaticObjectInfo` `9F` |
| Essence   | —               | `StaticObjectInfo` `9F` |

Notes: Click and shift-click on a static object are handled by the action handlers `StaticObjectClick` / `StaticObjectShiftClick`.

### world.fences — Fences

ID: `world.fences` · Shared: **engine**

Invisible-to-geodata walls drawn client-side as fence boxes (size, height), used by arenas and events; a fence blocks
movement and line of sight in its box and has states (closed/open, hidden). Loaded from XML; GM command can add or remove
fences at run time.
Sub-features: fence data · fence states · geodata line-of-sight check · GM fence command.

| Chronicle | Present | Builds (evidence)                                              | Difference                         |
| --------- | ------- | -------------------------------------------------------------- | ---------------------------------- |
| Interlude | yes     | mobius-il, acis (`FenceManager`), hellas, l2kt-cozmin91-kotlin | absent in L2jFrozen, L2J Server IL |
| High Five | yes     | nx-l2, eternity                                                | baseline                           |
| Essence   | yes     | mobius-ess                                                     | same                               |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/FenceData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Fence.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/enums/creature/FenceState.java`, `nx-l2:chronicles/high-five/datapack/data/FenceData.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/FenceData.java`, `mobius-il:dist/game/data/FenceData.xml`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminFence.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/FenceData.java`, `mobius-ess:dist/game/data/FenceData.xml`

Packets

| Chronicle | Client → server | Server → client                |
| --------- | --------------- | ------------------------------ |
| Interlude | —               | `ExColosseumFenceInfo` `FE:09` |
| High Five | —               | `ExColosseumFenceInfo` `FE:03` |
| Essence   | —               | `ExColosseumFenceInfo` `FE:03` |

Notes: the packet exists in all three client protocols; the Interlude presence rests on Mobius IL and aCis, so check
Interlude behaviour in aCis `FenceManager`, not in Mobius IL (a backport of the HF engine).

### world.map-regions — Map regions

ID: `world.map-regions` · Shared: **partial**

Map-region table: which town or restart area a coordinate belongs to, restart points per race, bad-location (PK) restart
points, castle/clan hall ownership of the region for respawn, and the compass zone code.
Sub-features: coordinate → region · restart point by race · karma restart · castle/clan hall respawn · town id.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                 |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen | 21 region files (Mobius IL); aCis keeps one `mapregion.xml` with areas and restarts        |
| High Five | yes     | nx-l2, l2j-server          | baseline; 24 region files                                                                  |
| Essence   | yes     | mobius-ess                 | 27 files (adds high elf, wind, assassin hideout towns in later releases); no `TownManager` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/MapRegionData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/TownManager.java`, `nx-l2:chronicles/high-five/datapack/data/mapregion/*.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/MapRegionData.java`, `mobius-il:dist/game/data/mapregion/*.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/MapRegionData.java`, `mobius-ess:dist/game/data/mapregion/*.xml`

Packets

| Chronicle | Client → server | Server → client    |
| --------- | --------------- | ------------------ |
| Interlude | —               | `ShowTownMap` `DE` |
| High Five | —               | `ShowTownMap` `EA` |
| Essence   | —               | `ShowTownmap` `EA` |

Notes: the reference table (region, restart points) has no instances; treat it as a reference table (exception to the
`-template` rule).

Known fixes: `l2j-server@f43c5ac9` (2008-01-07, crash-npe) — TownManager; Little fix for "java.lang.ArrayIndexOutOfBoundsException: 19"; `l2j-server@d917bc23` (2008-01-08, crash-npe) — Additional fix for Array out of bounds. Again, please doublecheck...this fix essentially assumes that none of the new regions are…; `l2j-server@6cef023f` (2012-04-15, crash-npe) — NPE Fixes thanks Starter for report.
