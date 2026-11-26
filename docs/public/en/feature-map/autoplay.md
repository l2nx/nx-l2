# Player automation

Player automation: auto-hunting, automatic use of skills and items, auto-potions and offline play.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### autoplay.auto-play — Auto hunting

ID: `autoplay.auto-play` · Shared: **partial**

The server picks targets in range, attacks, uses configured skills, picks up drop and optionally assists the party
leader. In IL/HF it is a server-side custom driven by a voiced command and html; in Essence it is a client feature
(auto-play panel) with the same server task manager.
Sub-features: target search (short/long range) · attack + skills · pickup with ignore list · assist leader · resume
after relog · premium-only option · disabled skills/items.

| Chronicle | Present | Builds (evidence)       | Difference                                                                                          |
| --------- | ------- | ----------------------- | --------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (custom)      | custom: `.play` voiced command + html, `EnableAutoPlay = False` by default                          |
| High Five | yes     | nx-l2 (custom)          | same as IL (`AutoPlayTaskManager` 4 lines apart)                                                    |
| Essence   | yes     | mobius-ess (09.2, 04.2) | native: client panel `ExAutoPlaySetting`, macro trigger `ExAutoPlayDoMacro`; task manager +53 lines |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/AutoPlayTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/AutoPlayConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/AutoPlay.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/AutoPlay.ini`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java`, `mobius-il:dist/game/config/Custom/AutoPlay.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/AutoPlayTaskManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/autoplay/ExAutoPlaySetting.java`

Packets

| Chronicle | Client → server                             | Server → client                                                  |
| --------- | ------------------------------------------- | ---------------------------------------------------------------- |
| Interlude | voiced command `.play` (chat) + html bypass | —                                                                |
| High Five | voiced command `.play` (chat) + html bypass | —                                                                |
| Essence   | `ExAutoPlaySetting` `D0:0177`               | `ExAutoPlaySettingSend` `FE:0220`, `ExAutoPlayDoMacro` `FE:0221` |

Notes: one task manager serves both models — a strong `partial` candidate with the trigger (voiced/html vs client
packet) as the extension point. Separate IL autofarm implementations exist in aCis/L2jOrion forks: L2jMega deraldinho,
L2jOrion Hl4p3x, L2jTesla (`AutofarmManager`, `AutofarmPlayerRoutine`) and AgeCold (`autofarm/`).

### autoplay.auto-use — Auto use of shortcuts

ID: `autoplay.auto-use` · Shared: **partial**

Shortcuts marked "auto" are used by the server when their condition holds: buffs re-applied, items consumed, skills
cast during auto play, pet skills. Sub-features: auto buffs · auto items · auto skills · auto pet skills · reuse checks.

| Chronicle | Present | Builds (evidence)       | Difference                                                                                                                 |
| --------- | ------- | ----------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (custom)      | custom, driven by the auto-play html                                                                                       |
| High Five | yes     | nx-l2 (custom)          | same as IL                                                                                                                 |
| Essence   | yes     | mobius-ess (09.2, 04.2) | native shortcut toggle `ExRequestActivateAutoShortcut`; `AutoUseTaskManager` 621 vs 356 lines (pet, more item/skill rules) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/AutoUseTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/AutoUseSettingsHolder.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/AutoUseTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/AutoUseTaskManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/autoplay/ExRequestActivateAutoShortcut.java`

Packets

| Chronicle | Client → server                           | Server → client                    |
| --------- | ----------------------------------------- | ---------------------------------- |
| Interlude | html bypass                               | —                                  |
| High Five | html bypass                               | —                                  |
| Essence   | `ExRequestActivateAutoShortcut` `D0:0171` | `ExActivateAutoShortcut` `FE:0219` |

Notes: `RequestAutoSoulShot` (auto soulshots) is a separate retail mechanic of all three chronicles and belongs to
`item.*`.

Known fixes: `mobius@8080df12` (2021-10-12, wrong-formula-value) — Fixed boxing auto skill parameters returning wrong values.

### autoplay.auto-potion — Auto potions

ID: `autoplay.auto-potion` · Shared: **engine**

Custom: the server drinks CP/HP/MP potions when the value drops below a configured percentage.
Sub-features: CP/HP/MP thresholds · item lists · min level · olympiad toggle · voiced command.

| Chronicle | Present | Builds (evidence)             | Difference                                            |
| --------- | ------- | ----------------------------- | ----------------------------------------------------- |
| Interlude | yes     | mobius-il (custom), mobius-c6 | baseline (off by default)                             |
| High Five | yes     | nx-l2 (custom)                | identical                                             |
| Essence   | yes     | mobius-ess (custom)           | identical (`AutoPotionTaskManager` same in all three) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/AutoPotionTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/AutoPotionsConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/AutoPotion.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/AutoPotionTaskManager.java`, `mobius-il:dist/game/config/Custom/AutoPotions.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/AutoPotionTaskManager.java`

Packets

| Chronicle | Client → server       | Server → client |
| --------- | --------------------- | --------------- |
| Interlude | voiced command (chat) | —               |
| High Five | voiced command (chat) | —               |
| Essence   | voiced command (chat) | —               |

Notes: in Essence the native auto-use of potions from shortcuts (`autoplay.auto-use`) makes this custom redundant.

### autoplay.auto-farm — Auto farm

ID: `autoplay.auto-farm` · Shared: **chronicle-only**

Server-side bot that plays for the character: a periodic routine picks a monster in a radius, attacks, casts skills from
the player's shortcut slots (attack / chance / self-buff groups) and loops until stopped. Sub-features: start/stop
command and HTML menu · skill groups from shortcut slots · peace-zone and VIP restrictions · target selection radius.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                         |
| --------- | ------- | --------------------------------------------- | ---------------------------------------------------------------------------------- |
| Interlude | yes     | l2jmega-deraldinho, l2jorion-hl4p3x, l2jtesla | custom in aCis-family and L2jFrozen-family forks; 450 ms routine per active player |
| High Five | no      | nx-l2, l2j-server, sunrise, eternity          | — (the Mobius auto-hunting custom is `autoplay.auto-play`)                         |
| Essence   | no      | mobius-ess                                    | — (client-driven auto-play, see `autoplay.auto-play`)                              |

Entry points

- `l2jmega-deraldinho:java/Dev/AutoFarm/AutofarmManager.java`, `l2jmega-deraldinho:java/Dev/AutoFarm/AutofarmPlayerRoutine.java`, `l2jmega-deraldinho:java/com/l2jmega/gameserver/handler/voicedcommandhandlers/VoicedAutofarm.java`
- `l2jorion-hl4p3x:L2jOrion_SourceCode/src/l2jorion/game/autofarm/AutofarmManager.java`, `l2jorion-hl4p3x:L2jOrion_Datapack/game/config/mods/autofarm.ini`
- `l2jtesla:src/main/kotlin/dev/l2j/tesla/autobots/autofarm/AutofarmManager.kt`, `l2jtesla:src/main/kotlin/dev/l2j/tesla/autobots/autofarm/AutofarmCommandHandler.kt`

Packets

| Chronicle | Client → server                     | Server → client |
| --------- | ----------------------------------- | --------------- |
| Interlude | — (voiced command / HTML menu only) | —               |

Notes: L2jMega adds `EnableCommandVipAutofarm` and `NoUseFarmInPeaceZone` style restrictions; L2jOrion exposes it as the `.farm` voiced command with `AutoFarmEnabled`.

### autoplay.offline-play — Offline play

ID: `autoplay.offline-play` · Shared: **engine**

Custom: the character keeps auto-hunting after the client disconnects; restored after server restart; optional name
colour / abnormal effect; logout on death.
Sub-features: voiced command · restore after restart · premium-only · logout on death · same-account disconnect ·
name colour.

| Chronicle | Present | Builds (evidence)   | Difference                                    |
| --------- | ------- | ------------------- | --------------------------------------------- |
| Interlude | yes     | mobius-il (custom)  | baseline (`EnableOfflinePlayCommand = False`) |
| High Five | yes     | nx-l2 (custom)      | identical                                     |
| Essence   | yes     | mobius-ess (custom) | same (`OfflinePlayTable` +18 lines)           |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/OfflinePlayTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/OfflinePlayConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/OfflinePlay.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/sql/OfflinePlayTable.java`, `mobius-il:dist/game/config/Custom/OfflinePlay.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/data/sql/OfflinePlayTable.java`

Packets

| Chronicle | Client → server       | Server → client |
| --------- | --------------------- | --------------- |
| Interlude | voiced command (chat) | —               |
| High Five | voiced command (chat) | —               |
| Essence   | voiced command (chat) | —               |

Notes: depends on `autoplay.auto-play`; shares the offline-mode switch with offline trade (`character.logout-restart`).

### autoplay.afk — AFK control

ID: `autoplay.afk` · Shared: **chronicle-only**

Idle-player detection: a periodic task compares the character state over a time window and kicks or flags players that
did not move or act. Sub-features: periodic position check · kick of idle players · GM exemption.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                                            |
| --------- | ------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | l2jhellas-awakenz, l2jmega-revoltado | custom in forks only: Hellas kicks a player whose position did not change over `MinutesAfkPlayers`; Revoltado flags a player as `afking` after one hour of inactivity |
| High Five | no      | nx-l2, l2j-server, sunrise           | —                                                                                                                                                                     |
| Essence   | no      | mobius-ess                           | —                                                                                                                                                                     |

Entry points

- `l2jhellas-awakenz:L2JHellasC/java/com/l2jhellas/shield/antibot/AntiAfk.java`
- `l2jmega-revoltado:SourceV13/extracted/java/net/sf/l2j/gameserver/taskmanager/AfkTaskManager.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |

Notes: no public Mobius or L2J Server build has an idle-kick task.
