# Geodata and movement

Geodata, pathfinding, movement validation, falling and water.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### geo.geodata — Geodata

ID: `geo.geodata` · Shared: **engine**

Loads `.l2j` geodata files per map tile (`<x>_<y>.l2j`) into flat, complex and multilayer blocks; answers height, NSWE
passability and line-of-sight; checks doors and fences between two points; produces valid locations for moves, summons and
teleports.
Sub-features: region/block loader · height lookup · NSWE cells · line of sight · door/fence check · valid-location
resolution · geodata path config.

| Chronicle | Present | Builds (evidence)                             | Difference                                       |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | same block model; instance id is an int          |
| High Five | yes     | nx-l2, l2j-server, sunrise                    | baseline                                         |
| Essence   | yes     | mobius-ess                                    | same logic; instance passed as `Instance` object |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/geoengine/GeoEngine.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/geoengine/geodata/{Cell,IBlock,IRegion}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/geoengine/geodata/blocks/*`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/geoengine/geodata/regions/*`, `nx-l2:chronicles/high-five/datapack/config/GeoEngine.ini`
- `mobius-il:java/org/l2jmobius/gameserver/geoengine/GeoEngine.java`, `mobius-il:dist/game/config/GeoEngine.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/geoengine/GeoEngine.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: Mobius IL and Essence `GeoEngine` differ only in the instance argument (`diff` shows nothing else); aCis and
L2jFrozen load the same `.l2j` format through their own engine classes (aCis: `geoengine.properties`; L2jFrozen:
`geo_index.txt`). Geodata files are data, not part of the repository's code.

Known fixes: `mobius@1a13176a` (2018-03-08, crash-npe) — GeoEngine possible NPE checks; `l2j-server@256a3cc9` (2007-11-07, wrong-formula-value) — Improving geodata debug messages for broken file or wrong region cases; `sunrise@d8161de6` (2015-05-27, wrong-formula-value) — Reowkred a bit geo engine Fixed broken formula for fall damages.

### geo.pathfinding — Pathfinding

ID: `geo.pathfinding` · Shared: **engine**

Finds a walkable path between two points over geodata nodes (A* with node buffers, diagonal moves, max Z difference), used
for monster and player click-to-move and for return-home movement; may be switched off (empty pathfinder).
Sub-features: node buffer pool · diagonal moves · Z limit · path postfilter · enable switch.

| Chronicle | Present | Builds (evidence)          | Difference                                              |
| --------- | ------- | -------------------------- | ------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | same A* idea; aCis/L2jFrozen use their own node classes |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline                                                |
| Essence   | yes     | mobius-ess                 | same                                                    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/geoengine/pathfinding/{PathFinding,NodeBuffer,GeoLocation}.java`, `nx-l2:chronicles/high-five/datapack/config/GeoEngine.ini` (`PathFinding = 2`)
- `mobius-il:java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java`
- `mobius-ess:java/org/l2jmobius/gameserver/geoengine/pathfinding/PathFinding.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: later Essence releases add `GeoNode.java` to `geoengine/pathfinding` (visible in the 10.1 diff); the 09.2 baseline
has none.

Known fixes: `l2j-server@afb8e91d` (2008-06-14, concurrency) — Pathfinding little check for invalid routes. Also a synchronization (deadlock) quick fix by Kombat; `l2j-server@7d4f5d9d` (2007-10-24, missing-check) — Pathfinding, should fix #1555. Adds door checks; `l2j-server@526fb2dc` (2008-10-02, other) — Bugfix for pathfinding's removal-of-extra-pathnodes part, thanks rage.

### geo.movement — Movement

ID: `geo.movement` · Shared: **partial**

Movement of creatures: click-to-move with geodata validation, follow, stop, position validation from the client, movement
ticks in a task manager; Essence adds keyboard (WASD) movement packets.
Sub-features: move to location · move to pawn (follow) · position validate · move with delta · movement tick manager ·
keyboard movement (Essence).

| Chronicle | Present | Builds (evidence)          | Difference                                                                     |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen | click-to-move; `MoveWithDelta`                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline                                                                       |
| Essence   | differs | mobius-ess                 | adds `MoveToward` / `StopMoveToward` / `ExStopMove` (WASD); click-to-move kept |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{MoveToLocation,ValidatePosition,MoveWithDelta}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/MovementTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/{MoveToLocation,ValidatePosition,MoveWithDelta}.java`, `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/MovementTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{MoveToLocation,MoveToward,ValidatePosition,MoveWithDelta}.java`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/npc/walker/ArrivedTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/CreatureFollowTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/walker/ArrivedTask.java`, `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/CreatureFollowTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/walker/ArrivedTask.java`, `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/CreatureFollowTaskManager.java`

Packets

| Chronicle | Client → server                                                                                                                                                                  | Server → client                                                                                                                                                                                                                                                                                                                                              |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `MoveToLocation` `01`, `ValidatePosition` `48`, `MoveWithDelta` `41`, `Appearing` `30`, `CannotMoveAnymore` `36`, `StartRotating` `4A`                                           | `CharMoveToLocation` `01`, `StopMove` `47`, `MoveToPawn` `60`, `StopRotation` `63`, `ChangeMoveType` `2E`, `ChangeWaitType` `2F`, `BeginRotation` `62`, `ValidateLocation` `61`                                                                                                                                                                              |
| High Five | `MoveToLocation` `0F`, `ValidatePosition` `59`, `MoveWithDelta` `52`, `Appearing` `3A`, `CannotMoveAnymore` `47`, `StartRotating` `5B`                                           | `MoveToLocation` `2F`, `StopMove` `47`, `MoveToPawn` `72`, `FinishRotating` `61`, `ChangeMoveType` `28`, `ChangeWaitType` `29`, `ExRotation` `FE:C1`, `FlyToLocation` `D4`, `StartRotating` `7A`, `ValidateLocation` `79`                                                                                                                                    |
| Essence   | `MoveToLocation` `0F`, `MoveToward` `4A`, `StopMoveToward` `33`, `ValidatePosition` `59`, `ExStopMove` `D0:EE`, `Appearing` `3A`, `CannotMoveAnymore` `47`, `StartRotating` `5B` | `MoveToLocation` `2F`, `StopMove` `47`, `MoveToPawn` `72`, `FinishRotating` `61`; `UserInfo` `32` mask bits `POSITION` (0x0A), `SPEED` (0x0B), `MOVEMENTS` (0x13); `NpcInfo` `0C` mask bits `POSITION` (0x04), `ATK_CAST_SPEED` (0x07), `SPEED_MULTIPLIER` (0x08), `ExRotation` `FE:C3`, `FlyToLocation` `D4`, `StartRotating` `7A`, `ValidateLocation` `79` |

Notes: the server → client move packet is called `CharMoveToLocation` in the Interlude catalog and `MoveToLocation` from
High Five on; same payload. `ArrivedTask` finishes a walker step (see `ai.walking-routes`); `CreatureFollowTaskManager` drives follow movement.

Known fixes: `l2j-server@8efc50ac` (2008-10-13, missing-check) — 1) Validate position packets for swimming 2) Improving multisell fix, was a general bug; `l2j-server@6441daa0` (2010-04-17, concurrency) — Attempt to fix critical errors with CoordSynchronize = 2.

### geo.falling-water — Falling and water

ID: `geo.falling-water` · Shared: **engine**

Fall damage when the Z drop between two validated positions exceeds a threshold; swimming in water zones with a drowning
timer (breath gauge) when the player stays underwater too long.
Sub-features: fall damage (`EnableFallingDamage`) · water zone · water task with breath gauge · water switch (`AllowWater`).

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | same       |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline   |
| Essence   | yes     | mobius-ess                 | same       |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/WaterZone.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/WaterTask.java`, `nx-l2:chronicles/high-five/datapack/config/General.ini` (`EnableFallingDamage`, `AllowWater`)
- `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/WaterZone.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/WaterTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/WaterZone.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/WaterTask.java`

Packets

| Chronicle | Client → server                       | Server → client                  |
| --------- | ------------------------------------- | -------------------------------- |
| Interlude | `ValidatePosition` `48` (fall detect) | `SetupGauge` `6D` (breath gauge) |
| High Five | `ValidatePosition` `59`               | `SetupGauge` `6B`                |
| Essence   | `ValidatePosition` `59`               | `SetupGauge` `6B`                |

Notes: —

Known fixes: `lineage2ts@a9fe90a8` (2021-12-12, other) — Upgrading node dependencies; fixing issue with water task.
