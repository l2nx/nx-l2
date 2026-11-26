# Reflections

Instanced zones (reflections): engine, script catalog, re-entry cooldowns, timed hunting zones and related Essence systems.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

Naming: the instanced-zone entity is `reflection`; the data object created from a template is a reflection, the template is `reflection-template` (Mobius: `Instance` / `InstanceWorld`, XML under `data/instances`).

## Features

### reflection.engine — Reflection engine

ID: `reflection.engine` · Shared: **partial**

Creates isolated copies of an area from an XML template (doors, spawn groups, exit point, time limits, PvP flag, entry conditions, buff-removal policy), assigns players to it, enforces re-entry cooldowns (fixed weekday/hour reset or time-after-leave) and destroys it when empty. High Five has full conditions (party size, command channel, level, items, distance, reenter); Interlude retail has no instanced zones, so Interlude support exists only as a backport in custom builds; Essence reuses the same engine with new template tags.
Sub-features: template loading · create / destroy · enter / exit location · empty / duration timers · re-entry lock (per template, per account/char) · entry conditions · remove-buff policy · door and spawn groups · world-state scripts.

| Chronicle | Present | Builds (evidence)                                          | Difference                                                      |
| --------- | ------- | ---------------------------------------------------------- | --------------------------------------------------------------- |
| Interlude | differs | l2jfrozen, mobius-il (engine + 1 template only); aCis none | not a retail feature; engine ported, a single Coliseum template |
| High Five | yes     | nx-l2 (142 templates), l2j-server (48)                     | baseline: 25 script folders, 142 XML templates                  |
| Essence   | yes     | mobius-ess (12 templates + `InstanceNames.xml`)            | template tags `time/misc/locations/conditions/reenter`, XSD'd   |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/InstanceManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/instancezone/{Instance.java,InstanceWorld.java,InstanceReenterType.java,InstanceRemoveBuffType.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/script/InstanceScript.java`, `nx-l2:chronicles/high-five/datapack/data/instances/`
- `mobius-il:java/org/l2jmobius/gameserver/managers/InstanceManager.java`, `mobius-il:dist/game/data/instances/Coliseum.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/InstanceManager.java`, `mobius-ess:dist/game/data/instances/LastImperialTomb.xml`, `mobius-ess:dist/game/data/InstanceNames.xml`

Packets

| Chronicle | Client → server | Server → client                                                              |
| --------- | --------------- | ---------------------------------------------------------------------------- |
| Interlude | —               | `SystemMessage` `64`, `ExShowScreenMessage` `FE:38`                          |
| High Five | —               | `SystemMessage` `62`, `EXSendUIEvent` `FE:8E`, `ExShowScreenMessage` `FE:39` |
| Essence   | —               | `SystemMessage` `62`, `EXSendUIEvent` `FE:8F`, `ExShowScreenMessage` `FE:39` |

Notes: Essence forks backport HF instances (Pailaka Rune Castle, Fortress/Castle Dungeon, Kartia, Kamaloka, Chamber of Delusion) into the Essence engine, which shows the template format is stable across HF and Essence.

Known fixes: `l2j-server@4f6c8ba5a0` (2009-07-06, crash-npe) — Fix for Instance NPE. Thx Edoo for testing; `l2j-server@262500b07e` (2009-03-12, wrong-formula-value) — Fix for reset timer after leaving the instance. Thx Janiii :)

### reflection.scripts — Reflection script catalog

ID: `reflection.scripts` · Shared: **partial (HF+Ess)**

Per-chronicle catalog of instance scripts (`InstanceScript` subclasses: wave logic, boss phases, rewards, NPC dialogs) plus area scripts that spawn inside a reflection. Not listed per script. High Five: 25 folders (Kamaloka, Rim Kamaloka, Pailaka x3, Crystal Caverns, Dark Cloud Mansion, Nornils Garden x2, Jinia hideouts x4, Seven Signs x5, Chambers of Delusion, Castle Dungeon, Mithril Mine, Cavern of the Pirate Captain, Ice Queen's Castle x2). Essence: 14 folders (Clan Dungeon, Dream Dungeon, Dwelling of Spirits, Golberg Room, Kelbim Fortress, Leona's Dungeon, Monster Arena, Pagan Temple, Queen Ant Lair, Timed Hunting, Training Zone, Valakas Temple, Water Temple, Continued Raid Battle).

| Chronicle | Present | Builds (evidence)               | Difference                                |
| --------- | ------- | ------------------------------- | ----------------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il      | no instance scripts (Mobius IL: none)     |
| High Five | yes     | nx-l2 (25), l2j-server-dp (15+) | baseline                                  |
| Essence   | yes     | mobius-ess (14)                 | different set, all re-authored for 5-man+ |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/instances/`
- `mobius-ess:dist/game/data/scripts/instances`, `mobius-ess:java/org/l2jmobius/gameserver/managers/events/LeonasDungeonManager.java`

Packets: none dedicated.

Notes: forks add many HF-origin instances to Essence (Pailaka Rune Castle 31 files, Fortress Dungeon 22, Kamaloka 20, Kartia's Labyrinth 15, Castle Dungeon 10).

### reflection.reentry-info — Re-entry time info

ID: `reflection.reentry-info` · Shared: **partial (HF+Ess)**

Player asks which reflections are on cooldown and when each becomes available again. High Five answers through the `/instancezone` user command (command id 114): the zone currently in use, then each locked zone with the remaining hours/minutes, or "no instance zone under a time limit". Essence replaces it with a dedicated window request.
Sub-features: current-zone line · per-template remaining time · empty-list message.

| Chronicle | Present | Builds (evidence)          | Difference                                                 |
| --------- | ------- | -------------------------- | ---------------------------------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | no user command (cooldown exists in Mobius IL engine only) |
| High Five | yes     | nx-l2, l2j-server-dp       | baseline: user command 114, `SystemMessage` output         |
| Essence   | yes     | mobius-ess                 | `RequestInzoneWaitingTime` -> `ExInzoneWaiting` window     |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/InstanceZone.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestInzoneWaitingTime.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/InstanceZoneInfo.java`

Packets

| Chronicle | Client → server                  | Server → client                                           |
| --------- | -------------------------------- | --------------------------------------------------------- |
| High Five | —                                | `SYSTEM_MESSAGE` `62`                                     |
| Essence   | `EX_INZONE_WAITING_TIME` `D0:BB` | `SYSTEM_MESSAGE` `62`, `EX_INZONE_WAITING_INFO` `FE:011F` |

Notes: Mobius IL ships the re-enter holder (`InstanceReenterTimeHolder`) but no player-facing command.

### reflection.timed-hunting — Timed hunting zones

ID: `reflection.timed-hunting` · Shared: **chronicle-only**

Essence hunting zones with a daily time budget: each zone has an initial time (e.g. 2 h), a reset delay, a maximum added time, refill time bought with an item/fee, level range, PvP/no-PvP flag and optional solo/weekly rules; the player enters by fee, the timer runs while inside, and a `TimedHuntingZone` marks the area. Some zones are real instances (Pagan Temple, Kelbim Fortress, Ironheart Fortress, Transcendent) with a reset on a fixed weekday.
Sub-features: zone list · enter / leave · charge extra time · premium-only zones · solo instance · weekly / even-week rotation.

| Chronicle | Present | Builds (evidence)                                   | Difference |
| --------- | ------- | --------------------------------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il                          | —          |
| High Five | no      | nx-l2, l2j-server                                   | —          |
| Essence   | yes     | mobius-ess (15 zones in `TimedHuntingZoneData.xml`) | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/TimedHuntingZoneData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/TimedHuntingZone.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/huntingzones/ExTimedHuntingZoneEnter.java`, `mobius-ess:dist/game/data/TimedHuntingZoneData.xml`, `mobius-ess:dist/game/data/scripts/instances/TimedHunting/TimedHunting.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                          | Server → client                                                                                                                                                                                                                                                                                                                                                                              |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_TIME_RESTRICT_FIELD_LIST` `D0:017F`, `EX_TIME_RESTRICT_FIELD_USER_ENTER` `D0:0180`, `EX_TIME_RESTRICT_FIELD_USER_LEAVE` `D0:0181`, `EX_TIME_RESTRICT_FIELD_HOST_USER_ENTER` `D0:0273`, `EX_TIME_RESTRICT_FIELD_HOST_USER_LEAVE` `D0:0274`, `EX_TIME_RESTRICT_FIELD_HOST_USER_ENTER_BY_NPC` `D0:0284` | `EX_TIME_RESTRICT_FIELD_LIST` `FE:022A`, `EX_TIME_RESTRICT_FIELD_USER_ENTER` `FE:022B`, `EX_TIME_RESTRICT_FIELD_USER_CHARGE_RESULT` `FE:022C`, `EX_TIME_RESTRICT_FIELD_USER_ALARM` `FE:022D`, `EX_TIME_RESTRICT_FIELD_USER_EXIT` `FE:022E`, `EX_TIME_RESTRICT_FIELD_DIE_LIMT_TIME` `FE:0306`, `EX_TIME_RESTRICT_FIELD_SERVER_GROUP` `FE:0315`, `EX_TIME_RESTRICT_FIELD_ENTER_INFO` `FE:0316` |

Notes: Mobius Main (SamuraiCrow) has 9 zones in its XML against 15 here.

Known fixes: `mobius@c0acb794c4` (2021-04-28, missing-check) — Fixed limit zone entry.; `mobius@b62ccc548a` (2022-04-05, wrong-formula-value) — Fixed timed hunting zone icon display on exit.; `mobius@c6bbb601be` (2021-05-25, other) — Fixed parsing instanceId.

### reflection.training-zone — Training camp

ID: `reflection.training-zone` · Shared: **chronicle-only**

Training in a special instance: the character is admitted for a fixed duration (default 18000 s = 5 h) at level 18-127 and gets experience/SP at configurable multipliers while training; premium-only by default and disabled by default.
Sub-features: admission / leave packets · duration · level range · exp / SP multipliers · premium gating.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:dist/game/config/TrainingCamp.ini`, `mobius-ess:java/org/l2jmobius/gameserver/config/TrainingCampConfig.java`, `mobius-ess:dist/game/data/scripts/instances/TrainingZone/TrainingZone.java`, `mobius-ess:dist/game/data/instances/TrainingZone.xml`

Packets

| Chronicle | Client → server                   | Server → client                                                            |
| --------- | --------------------------------- | -------------------------------------------------------------------------- |
| Essence   | `EX_TRAININGZONE_LEAVING` `D0:F3` | `EX_TRAININGZONE_ADMISSION` `FE:0163`, `EX_TRAININGZONE_LEAVING` `FE:0164` |

Notes: defaults in `TrainingCamp.ini`: `TrainingCampEnable = False`, `TrainingCampPremiumOnly = True`, `TrainingCampDuration = 18000`, levels 18..127.

Known fixes: `mobius@96570970d4` (2022-04-23, crash-npe) — Training zone NPE fix and improvements.

### reflection.clan-dungeon — Clan dungeon

ID: `reflection.clan-dungeon` · Shared: **chronicle-only**

Clan-leader-led instance for a command channel (4-40 members, 21-minute run): the leader creates it with a command channel, the zone has normal and hard modes, and results are ranked per clan; re-entry resets on Wednesday 06:30.
Sub-features: command-channel entry · normal / hard mode · clan ranking list and "my info" · weekly reset.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:dist/game/data/scripts/instances/ClanDungeon/ClanDungeon.java`, `mobius-ess:dist/game/data/instances/ClanDungeon.xml`, `mobius-ess:java/org/l2jmobius/gameserver/managers/events/ClanDungeonRankingManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/clandungeon/RequestDungeonRankingList.java`

Packets

| Chronicle | Client → server                                                           | Server → client                                                           |
| --------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Essence   | `EX_INZONE_RANKING_LIST` `D0:0283`, `EX_INZONE_RANKING_MY_INFO` `D0:0282` | `EX_INZONE_RANKING_LIST` `FE:0355`, `EX_INZONE_RANKING_MY_INFO` `FE:0354` |

Notes: handler classes are named `RequestDungeonRankingList` / `ExDungeonRankingList` while the packet enum is `EX_INZONE_RANKING_*`.
