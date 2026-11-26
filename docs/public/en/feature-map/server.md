# Server and service tasks

Boot and shutdown, configuration, thread pools, persistence, resets, announcements, network layer and remote administration.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### server.lifecycle — Start and stop

ID: `server.lifecycle` · Shared: **engine**

Boot sequence, shutdown and restart with a countdown, scheduled restart and precautionary restart on resource pressure; players are warned and saved.
Sub-features: boot · shutdown/restart countdown · scheduled restart · precautionary restart · no-restart zones.

| Chronicle | Present | Builds (evidence)                                     | Difference |
| --------- | ------- | ----------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | baseline   |
| Essence   | yes     | mobius-ess, l2emu-ess                                 | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/{Shutdown.java,GameServer.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/{ServerRestartManager.java,PrecautionaryRestartManager.java}`, `nx-l2:chronicles/high-five/datapack/config/Server.ini` (`ServerRestartScheduleEnabled`, `ServerRestartScheduleCountdown`)
- `mobius-il:java/org/l2jmobius/gameserver/Shutdown.java`, `mobius-il:java/org/l2jmobius/gameserver/managers/ServerRestartManager.java`

Packets

| Chronicle | Client → server                                                  | Server → client                                                                                                     |
| --------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Interlude | `Logout` `09`, `RequestRestart` `46`, `RequestRestartPoint` `6D` | `ServerClose` `26`, `RestartResponse` `5F`, `ExRestartClient` `FE:47`                                               |
| High Five | `Logout` `00`, `RequestRestart` `57`, `RequestRestartPoint` `7D` | `ServerClose` `20`, `RestartResponse` `71`, `LogOutOk` `84`, `ExRestartClient` `FE:48`, `ExRestartResponse` `FE:C8` |
| Essence   | `Logout` `00`, `RequestRestart` `57`, `RequestRestartPoint` `7D` | `ServerClose` `20`, `RestartResponse` `71`, `LogOutOk` `84`, `ExRestartClient` `FE:49`, `ExRestartResponse` `FE:CA` |

Notes: `ServerRestartScheduleEnabled = False` by default in nx-l2 (`Server.ini:204`).

Known fixes: `sunrise@5cc74a4c9a` (2015-03-04, other) — Reworked back up manager More options added For scheduled back ups etc Now you can add full path for back ups and different HD

### server.config — Configuration

ID: `server.config` · Shared: **engine**

Typed configuration loaded from ini/xml files at boot, with reload of selected files.
Sub-features: ini loader · typed config classes · reload.

| Chronicle | Present | Builds (evidence)                             | Difference                               |
| --------- | ------- | --------------------------------------------- | ---------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —                                        |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise          | baseline: `config/*Config.java` + loader |
| Essence   | yes     | mobius-ess                                    | same loader, more config classes         |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/{ConfigLoader.java,ConfigReader.java,GeneralConfig.java,ServerConfig.java,ThreadConfig.java,NetworkConfig.java}`, `nx-l2:chronicles/high-five/datapack/config/Development.ini`
- `mobius-il:java/org/l2jmobius/gameserver/config/`, `mobius-il:dist/game/config/`

Packets: none.

Notes: Mobius keeps one `*Config` class per file; the Custom group lives in `config/custom/` and `dist/game/config/Custom/`.

### server.threads-tasks — Thread pools and task managers

ID: `server.threads-tasks` · Shared: **engine**

Shared scheduled/executor/IO pools, per-subsystem task managers (decay, respawn, auto-save, item lifetime, think, ...) and a deadlock watcher.
Sub-features: thread pools · priorities · task managers · deadlock watcher.

| Chronicle | Present | Builds (evidence)                                     | Difference |
| --------- | ------- | ----------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | baseline   |
| Essence   | yes     | mobius-ess                                            | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/threads/{ThreadPool.java,ThreadPriority.java,ThreadProvider.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/ThreadConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/RandomAnimationTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/util/DeadlockWatcher.java`
- `mobius-il:java/org/l2jmobius/commons/threads/ThreadPool.java`, `mobius-il:java/org/l2jmobius/gameserver/util/DeadlockWatcher.java`

Packets: none.

Notes: pool sizes default to `-1` (auto) in `ThreadConfig` (`ScheduledThreadPoolSize`, `ExecutorThreadPoolSize` keys). A scan of scheduled tasks lists 411 task rows for IL, 593 for HF, 535 for Essence.

### server.id-factory — Object IDs

ID: `server.id-factory` · Shared: **engine**

Allocation of unique object ids (items, characters, clans, pets) with release and range checks at boot.
Sub-features: allocate · release · invalid-id check.

| Chronicle | Present | Builds (evidence)                                     | Difference |
| --------- | ------- | ----------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | baseline   |
| Essence   | yes     | mobius-ess                                            | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/IdManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/IdManager.java`

Packets: none.

Notes: Mobius `IdManager` is a static manager; Eternity and the older L2J roots use an `IdFactory` / `BitSetIDFactory` pair.

### server.persistence — Database and saving

ID: `server.persistence` · Shared: **engine**

Connection pool, periodic autosave of online players, table creation and cleanup.
Sub-features: connection pool · player autosave · periodic cleanup · optional DB backup.

| Chronicle | Present | Builds (evidence)                             | Difference                                               |
| --------- | ------- | --------------------------------------------- | -------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —                                                        |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise          | baseline (nx-l2: `repository/` + shared `libs/database`) |
| Essence   | yes     | mobius-ess                                    | —                                                        |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/PlayerAutoSaveTaskManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/repository/**`, `nx-l2:libs/database` (shared library of nx-l2)
- `mobius-il:java/org/l2jmobius/commons/database/DatabaseFactory.java`, `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/PlayerAutoSaveTaskManager.java`

Packets: none.

Notes: Mobius code issues SQL from managers/holders; nx-l2 moves SQL into repositories, that is a refactor difference, not a behaviour difference.

### server.daily-reset — Daily and weekly resets

ID: `server.daily-reset` · Shared: **partial**

A fixed daily time (06:30) fires resets (daily skills, Nevit, recommendations, clan leader apply) and a weekly step on Wednesday; scripts listen to a daily-reset event.
Sub-features: daily reset · weekly reset · reset event · persistence of the last reset time.

| Chronicle | Present | Builds (evidence) | Difference                                                                                         |
| --------- | ------- | ----------------- | -------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il         | baseline set (skills, Nevit, recommends)                                                           |
| High Five | yes     | nx-l2             | baseline; clock through `ClockBridge`                                                              |
| Essence   | yes     | mobius-ess        | adds daily items, vitality, clan donation, world chat, training camp, VIP, hunt pass, monthly step |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/DailyResetManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/clock/ClockBridge.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/schedule/RecurrenceSchedule.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/DailyResetManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExResponseResetList.java`

Packets

| Chronicle | Client → server | Server → client                    |
| --------- | --------------- | ---------------------------------- |
| Essence   | —               | `EX_RESPONSE_RESET_LIST` `FE:0138` |

Notes: `DailyResetManager` normalized diff HF-vs-IL is 164 lines, HF-vs-Essence 743 lines. aCis and L2jFrozen have no class with this name (file-name scan found none).

### server.announcements — Announcements

ID: `server.announcements` · Shared: **engine**

Server announcements from DB or file: one-off, scheduled (auto) and event announcements; boss and PvP announce switches; critical announcements.
Sub-features: auto announcements · event announcements · boss announce · PvP announce.

| Chronicle | Present | Builds (evidence)                             | Difference                      |
| --------- | ------- | --------------------------------------------- | ------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —                               |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise          | baseline                        |
| Essence   | yes     | mobius-ess                                    | adds `RaidDropAnnounceData.xml` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/AnnouncementsTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/announce/{Announcement.java,AutoAnnouncement.java,EventAnnouncement.java}`, `nx-l2:chronicles/high-five/datapack/config/Custom/{BossAnnouncements.ini,PvpAnnounce.ini}`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/RaidDropAnnounceData.java`

Packets

| Chronicle | Client → server | Server → client                                                                                            |
| --------- | --------------- | ---------------------------------------------------------------------------------------------------------- |
| Interlude | `Say2` `38`     | `CreatureSay` `4A`, `ExShowScreenMessage` `FE:38`                                                          |
| High Five | —               | `Say2` `4A`, `ExShowScreenMessage` `FE:39`                                                                 |
| Essence   | —               | `Say2` `4A`, `ExShowScreenMessage` `FE:39`, `ExItemAnnounce` `FE:01FF`, `ExRaidDropItemAnnounce` `FE:023F` |

Notes: the announcement classes are present in all three Mobius trees under the same names.

Known fixes: `l2j-server@aec08cb245` (2015-06-21, other) — Critical announcement fixed: it's displayed like normal announce on enterworld

### server.global-variables — Global variables

ID: `server.global-variables` · Shared: **engine**

Key-value storage for server-wide state (last daily reset, event flags) persisted to DB.
Sub-features: get/set · persistence.

| Chronicle | Present | Builds (evidence)                                | Difference |
| --------- | ------- | ------------------------------------------------ | ---------- |
| Interlude | yes     | mobius-il                                        | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise, lineage2ts | baseline   |
| Essence   | yes     | mobius-ess, l2emu-ess                            | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/GlobalVariablesManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/variables/`
- `mobius-il:java/org/l2jmobius/gameserver/managers/GlobalVariablesManager.java`

Packets: none.

Notes: older Interlude roots (aCis, L2jFrozen, L2J-IL, Hellas) have no class named `GlobalVariable*` by file name.

Known fixes: `l2j-server@bdf2f43602` (2013-07-25, other) — BETA: Minor fix for GlobalVariablesManager. * Reported by: lucan * Tested by: lucan

### server.network — Network layer

ID: `server.network` · Shared: **partial**

Socket server, packet encryption, protocol-version negotiation, connection-state machine, ping and client packet dispatch.
Sub-features: game client and connection state · packet handler and opcode tables · protocol version check · net ping · encryption.

| Chronicle | Present | Builds (evidence)                         | Difference                                                                               |
| --------- | ------- | ----------------------------------------- | ---------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (protocol 746), acis, l2jfrozen | `AllowedProtocolRevisions = 746`; `NetPing` is a backport in Mobius IL                   |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise      | revisions `267;268;271;273`; `NetPing` added                                             |
| Essence   | differs | mobius-ess                                | protocol 520 (`AllowedProtocolRevisions = 520`), `PacketLogger`, different opcode tables |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/{GameClient.java,GamePacketHandler.java,ClientPackets.java,ExClientPackets.java,ServerPackets.java,Encryption.java,ConnectionState.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/ProtocolVersion.java`, `nx-l2:chronicles/high-five/datapack/config/Server.ini`, `nx-l2:libs/network`
- `mobius-il:java/org/l2jmobius/gameserver/network/GamePacketHandler.java`, `mobius-il:dist/game/config/Server.ini`

Packets

| Chronicle | Client → server                                                                         | Server → client                     |
| --------- | --------------------------------------------------------------------------------------- | ----------------------------------- |
| Interlude | `ProtocolVersion` `00`, `AuthLogin` `08`, `NetPing` `A8`                                | `KeyPacket` `00`, `NetPing` `D3`    |
| High Five | `ProtocolVersion` `0E`, `AuthLogin` `2B`, `NetPing` `B1`, `SEND_TIME_CHECK_PACKET` `A0` | `VersionCheck` `2E`, `NetPing` `D9` |
| Essence   | `ProtocolVersion` `0E`, `AuthLogin` `2B`, `NetPing` `B1`                                | `VersionCheck` `2E`, `NetPing` `D9` |

Notes: Mobius enumerates opcodes in `ClientPackets` / `ExClientPackets` / `ServerPackets` enums; `PacketEncryption = False` by default in all three (`Server.ini:39`).

Known fixes: `sunrise@20637e468d` (2015-04-01, exploit-dupe) — added delay for some packets Fixed a nasty exploit using del key to increase attack speed

### server.script-engine — Script engine

ID: `server.script-engine` · Shared: **engine**

Compiles and loads server scripts (quests, AI, handlers) at boot; base classes, event dispatcher with listeners, script timers.
Sub-features: script loading · event dispatcher · listeners container · timers.

| Chronicle | Present | Builds (evidence)                                   | Difference                                 |
| --------- | ------- | --------------------------------------------------- | ------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500       | scripts under `dist/game/data/scripts`     |
| High Five | yes     | nx-l2 (`ScriptEngine`, `ScriptCatalog`), l2j-server | nx-l2: compiled module `scripts/`, catalog |
| Essence   | yes     | mobius-ess                                          | same engine (diff 3 lines vs HF)           |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/scripting/{ScriptEngine.java,ScriptCatalog.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/script/`, `nx-l2:chronicles/high-five/datapack/config/Scripts.xml`
- `mobius-il:java/org/l2jmobius/gameserver/scripting/ScriptEngine.java`

Packets: none.

Notes: Mobius ships a runtime source compiler (`scripting/engine`); nx-l2 replaces it with a compiled Gradle module (`nx-l2:chronicles/high-five/scripts`) and a `ScriptCatalog`. `ScriptEngine`/`EventDispatcher`/`ListenersContainer` differ by 0-10 normalized lines across the three Mobius trees.

### server.audit-logs — Audit logs

ID: `server.audit-logs` · Shared: **engine**

Chat, item, item-enchant, skill-enchant, GM and game-damage logs written to rotating files.
Sub-features: chat log · item log (small/ids only/list) · enchant logs · GM audit.

| Chronicle | Present | Builds (evidence)                             | Difference |
| --------- | ------- | --------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise          | baseline   |
| Essence   | yes     | mobius-ess                                    | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/util/GMAudit.java`, `nx-l2:chronicles/high-five/datapack/config/General.ini` (`LogChat:106`, `LogItems:111`, `LogItemEnchants:131`, `GMAudit:141`)
- `mobius-il:java/org/l2jmobius/log/filter/GMAuditFilter.java`

Packets: none.

Notes: nx-l2 defaults: `LogChat = False`, `LogItems = False`, `GMAudit = False`.

### server.remote-admin — Remote administration

ID: `server.remote-admin` · Shared: **partial**

External administration channels, separate from the game protocol: a telnet-style status console with commands (server, status, world, performance, say, ban, config, debug) and, in one Interlude build, an XML-RPC server. Not part of the Mobius trees; present as fork add-ons.
Sub-features: telnet console · status commands · XML-RPC management.

| Chronicle | Present | Builds (evidence)                         | Difference                                       |
| --------- | ------- | ----------------------------------------- | ------------------------------------------------ |
| Interlude | yes     | l2jfrozen (status thread, XML-RPC server) | not in Mobius IL                                 |
| High Five | yes     | advanced                                  | telnet command set                               |
| Essence   | yes     | dimasik228qq-waytrel-vanguard             | telnet server added to the Mobius Essence layout |

Entry points

- `l2jfrozen:gameserver/head-src/com/l2jfrozen/status/GameStatusThread.java`, `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/powerpak/xmlrpc/XMLRPCServer.java`
- `advanced:src/main/java/l2f/gameserver/network/telnet/TelnetServer.java`, `advanced:src/main/java/l2f/gameserver/network/telnet/commands/{TelnetBan.java,TelnetConfig.java,TelnetDebug.java,TelnetPerfomance.java,TelnetSay.java,TelnetServer.java,TelnetStatus.java,TelnetWorld.java}`
- `dimasik228qq-waytrel-vanguard:java/org/l2jmobius/gameserver/network/telnet/{TelnetServer.java,TelnetServerHandler.java,TelnetServerInitializer.java}`

Packets: none.

Notes: none of the Mobius Interlude, High Five and Essence trees ships a console.
