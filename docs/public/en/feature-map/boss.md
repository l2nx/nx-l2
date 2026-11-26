# Raid bosses and epics

Raid and epic boss state, raid points, announcements and boss information.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### boss.raid-boss — Raid bosses

ID: `boss.raid-boss` · Shared: **partial**

Raid bosses: spawn from a table, alive / dead / unknown status, saved HP and MP, respawn time with random spread, minions,
no-reward penalty rules and special debuff resistance.
Sub-features: spawn list · status and respawn persistence · HP/MP save · minions · raid curse / level rules · special raid list.

| Chronicle | Present | Builds (evidence)                                                                      | Difference                                                                                                                         |
| --------- | ------- | -------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`RaidBoss`), l2jfrozen (`RaidBossSpawnManager`, `L2RaidBossInstance`) | table `raidboss_spawnlist`                                                                                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                                   | baseline: `RaidBossSpawnManager` + `raidboss_spawnlist` + `RaidBossStatus`                                                         |
| Essence   | yes     | mobius-ess                                                                             | no `RaidBossSpawnManager`: `DatabaseSpawnManager` stores respawn / HP / MP in table `npc_respawns`; spawns in `RaidbossSpawns.xml` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/RaidBossSpawnManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/RaidBoss.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/enums/npc/RaidBossStatus.java`, `nx-l2:chronicles/high-five/datapack/sql/raidboss_spawnlist.sql`
- `mobius-il:java/org/l2jmobius/gameserver/managers/RaidBossSpawnManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/RaidBoss.java`, `mobius-il:dist/db_installer/sql/game/raidboss_spawnlist.sql`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/DatabaseSpawnManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/RaidBoss.java`, `mobius-ess:dist/game/data/spawns/RaidbossSpawns.xml`, `mobius-ess:dist/db_installer/sql/game/npc_respawns.sql`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/RaidBoss.java`
- `l2jfrozen:**/RaidBossSpawnManager.java` (found by file name)

Packets

| Chronicle | Client → server                                                         | Server → client                                                                               |
| --------- | ----------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Interlude | — (boss state is not visible to the client)                             | `NpcInfo` `16`                                                                                |
| High Five | —                                                                       | `NpcInfo` `0C`                                                                                |
| Essence   | `RequestRaidBossSpawnInfo` `D0:0129`, `RequestRaidServerInfo` `D0:012A` | `ExRaidBossSpawnInfo` `FE:01BA`, `ExRaidServerInfo` `FE:01BB`, `ExRaidTeleportInfo` `FE:0244` |

Notes: Essence `RaidBoss` is 48% of the HF class by normalized lines; the HF `RaidBossSpawnManager` is byte-identical to the IL backport.

Known fixes: `l2j-server@742e7a0e` (2007-06-29, exploit-dupe) — Raid boss heals to full if it has been dragged 5000 away from spawnpoint and it teleports home. Has been used as a minor exploit when group…; `mobius@9fb01cb2` (2026-10-10, missing-check) — Raid boss spawn locations corrected and missing raid bosses added; `l2jfrozen@3bbd732e` (2010-11-25, other) — Little fix to support also simple RaidBosses Infos.

### boss.grand-boss — Epic bosses

ID: `boss.grand-boss` · Shared: **partial**

Epic (grand) bosses: Antharas, Valakas, Baum, Core, Orfen, Queen Ant, Zaken, Frintezza, Beleth, Sailren and others — status
(alive / dead / waiting / fight), respawn by pattern or fixed time, boss zones with entry rules, scripted fights in `ai/bosses`.
Sub-features: status and respawn persistence · boss zones and entry · scripted encounters · player count limits · movie toggles · lair cleanup.

| Chronicle | Present | Builds (evidence)                                                                    | Difference                                                                         |
| --------- | ------- | ------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`GrandBoss`), l2jfrozen (`GrandBossManager`, `L2GrandBossInstance`) | fewer encounters (Interlude era: up to Beleth / Frintezza as Mobius adds)          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                                 | baseline: `GrandBossManager`, `BossZone`, `ai/bosses` (14 entries)                 |
| Essence   | yes     | mobius-ess                                                                           | `GrandBossManager` 69% of HF; `ai/bosses` 16 entries; `GrandBoss` instance reduced |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/GrandBossManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/GrandBoss.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/BossZone.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/GrandBossConfig.java`, `nx-l2:chronicles/high-five/datapack/config/GrandBoss.ini`, `nx-l2:chronicles/high-five/datapack/sql/grandboss_data.sql`, `nx-l2:chronicles/high-five/datapack/sql/grandboss_list.sql`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/bosses/`
- `mobius-il:java/org/l2jmobius/gameserver/managers/GrandBossManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/zone/type/BossZone.java`, `mobius-il:dist/game/data/scripts/ai/bosses`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/GrandBossManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/GrandBossConfig.java`, `mobius-ess:dist/game/data/scripts/ai/bosses`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/GrandBoss.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                         |
| --------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `NpcInfo` `16`, `SpecialCamera` `C7` / `NormalCamera` `C8` (movies)                                                                                     |
| High Five | —               | `NpcInfo` `0C`, `SpecialCamera` `D6`, `NormalCamera` `D7`, `ExSpawnEmitter` `FE:5D`                                                                     |
| Essence   | —               | `NpcInfo` `0C`, `SpecialCamera` `D6`, `NormalCamera` `D7`, `ExSpawnEmitter` `FE:5E`, `ExBalrogwarBossinfo` `FE:02FA` (Balrog war boss info, own system) |

Notes: in Essence the epic status lives in `grandboss_data`, while raid bosses moved to `DatabaseSpawnManager`.

Known fixes: `l2j-server@cabdaca7` (2008-02-22, crash-npe) — Preventing possible NPE in GrandBossManager when re-adding a Grand Boss, thanks Emperorc; `sunrise@29842e24` (2016-01-10, missing-check) — Fixed physical skills broken animations Missing grandbosses from community ranking.

### boss.raid-points — Raid points

ID: `boss.raid-points` · Shared: **partial (IL+HF)**

Raid points: points awarded for killing raid bosses, saved per character, ranking of players by points, rewards for ranks.
Sub-features: points per kill · ranking list · boss record request.

| Chronicle | Present | Builds (evidence)                                                             | Difference                                                             |
| --------- | ------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`RequestGetBossRecord`), l2jfrozen (`RaidBossPointsManager`) | boss record window in the client                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                          | baseline: `RaidBossPointsManager` + `RequestGetBossRecord`             |
| Essence   | no      | mobius-ess (manager absent)                                                   | only the opcode `EX_GET_BOSS_RECORD` remains in the protocol catalogue |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/RaidBossPointsManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestGetBossRecord.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/RaidBossPointsManager.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/network/clientpackets/RequestGetBossRecord.java`

Packets

| Chronicle | Client → server                                                              | Server → client           |
| --------- | ---------------------------------------------------------------------------- | ------------------------- |
| Interlude | `RequestGetBossRecord` `D0:18`                                               | `ExGetBossRecord` `FE:33` |
| High Five | `RequestGetBossRecord` `D0:40`                                               | `ExGetBossRecord` `FE:34` |
| Essence   | `RequestGetBossRecord` `D0:3D` (opcode only; no handler class in `RoseVain`) | `ExGetBossRecord` `FE:34` |

Notes: the Essence packet entries come from the catalogue, no `RaidBossPointsManager` exists in `RoseVain`.

Known fixes: `l2j-server@e0d1b0c7` (2008-09-05, crash-npe) — NPE prevention and minor cleaning for RaidBossPointsManager.calculateRanking Fixes #3082.

### boss.announcements — Boss announcements

ID: `boss.announcements` · Shared: **partial**

Server announcements about bosses: respawn, death and drop of rare items, shown to all players.
Sub-features: respawn announce · death announce · drop announce (Essence data-driven).

| Chronicle | Present | Builds (evidence)                                    | Difference                                                                     |
| --------- | ------- | ---------------------------------------------------- | ------------------------------------------------------------------------------ |
| Interlude | differs | mobius-il (custom backport); not in aCis / L2jFrozen | config-driven custom only                                                      |
| High Five | yes     | nx-l2 (`BossAnnouncements.ini`)                      | baseline: `BossAnnouncementsConfig` read in `Creature`                         |
| Essence   | yes     | mobius-ess                                           | custom config plus `RaidDropAnnounceData.xml` and `EX_RAID_DROP_ITEM_ANNOUNCE` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/BossAnnouncementsConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Creature.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/BossAnnouncements.ini`
- `mobius-il:java/org/l2jmobius/gameserver/config/custom/BossAnnouncementsConfig.java`
- `mobius-ess:java/org/l2jmobius/gameserver/config/custom/BossAnnouncementsConfig.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/RaidDropAnnounceData.java`, `mobius-ess:dist/game/data/RaidDropAnnounceData.xml`

Packets

| Chronicle | Client → server | Server → client                                          |
| --------- | --------------- | -------------------------------------------------------- |
| Interlude | —               | `CreatureSay` `4A` (announcement channel, domain `chat`) |
| High Five | —               | `CreatureSay` `4A`                                       |
| Essence   | —               | `CreatureSay` `4A`, `ExRaidDropItemAnnounce` `FE:023F`   |

Notes: Essence raid drop announce is a client-visible UI element, not plain chat.

### boss.info — Boss info

ID: `boss.info` · Shared: **partial**

Information about bosses for players: status, time of respawn, location; via a custom NPC (script) or client UI in Essence.
Sub-features: status list NPC · respawn time · teleport / locate · community board list (HF-era builds).

| Chronicle | Present | Builds (evidence)                                                                         | Difference                                                                                                      |
| --------- | ------- | ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il (`RaidbossInfo` custom), acis (`RaidbossInfo`), l2jfrozen (`8000_RaidbossInfo`) | NPC script showing raid boss status                                                                             |
| High Five | yes     | nx-l2 (`RaidbossInfo` custom), eternity, sunrise                                          | baseline                                                                                                        |
| Essence   | yes     | mobius-ess                                                                                | client UI: `RequestRaidBossSpawnInfo`, `RequestRaidServerInfo`, `ExRaidBossSpawnInfo` instead of the NPC script |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/RaidbossInfo/RaidbossInfo.java`
- `mobius-il:dist/game/data/scripts/custom/RaidbossInfo/RaidbossInfo.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/raidbossinfo/{RequestRaidBossSpawnInfo,RequestRaidServerInfo}.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/raidbossinfo/{ExRaidBossSpawnInfo,ExRaidServerInfo}.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/scripting/script/feature/RaidbossInfo.java`

Packets

| Chronicle | Client → server                                                         | Server → client                                               |
| --------- | ----------------------------------------------------------------------- | ------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` (script NPC)                               | `NpcHtmlMessage` `0F`                                         |
| High Five | `RequestBypassToServer` `23`                                            | `NpcHtmlMessage` `19`                                         |
| Essence   | `RequestRaidBossSpawnInfo` `D0:0129`, `RequestRaidServerInfo` `D0:012A` | `ExRaidBossSpawnInfo` `FE:01BA`, `ExRaidServerInfo` `FE:01BB` |

Notes: two different designs for the same need: HTML script vs UI packets.
