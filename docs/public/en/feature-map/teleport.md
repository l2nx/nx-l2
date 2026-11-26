# Teleports

Gatekeeper teleports, escape and unstuck, bookmarks and the Essence teleport features.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### teleport.gatekeeper — Gatekeeper teleport

ID: `teleport.gatekeeper` · Shared: **partial**

A teleporter NPC lists destinations with Adena prices; teleport is free up to a configured level on the main class; separate
lists for noblesse, castle, fortress and clan hall teleporters; some destinations need a quest or an item. Essence moves the
list into a client window.
Sub-features: price lists per NPC · free-teleport level · noblesse list · castle/clan hall/fortress teleporters · item fee ·
Essence destination list window.

| Chronicle | Present | Builds (evidence)                                   | Difference                                                                                                                                  |
| --------- | ------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`TeleportLocationData`), l2jfrozen | 147 teleporter XML files; `MaxFreeTeleportLevel = 40`; item fee via `feeId`                                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise                          | baseline; 201 teleporter XML files; `MaxFreeTeleportLevel = 40`                                                                             |
| Essence   | differs | mobius-ess                                          | 96 teleporter XML files incl. `TeleportListData.xml`; names by `npcStringId`; `MaxFreeTeleportLevel = 75` in `Player.ini` (code default 99) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/TeleporterData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/teleporter/{TeleportHolder,TeleportLocation}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Teleporter.java`, `nx-l2:chronicles/high-five/datapack/data/teleporters/**`, `nx-l2:chronicles/high-five/datapack/config/Player.ini` (`MaxFreeTeleportLevel`)
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/TeleporterData.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/teleporter/TeleportHolder.java`, `mobius-il:dist/game/data/teleporters/**`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/{TeleporterData,TeleportListData}.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/teleporter/TeleportHolder.java`, `mobius-ess:dist/game/data/TeleportListData.xml`, `mobius-ess:dist/game/data/teleporters/**`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/TeleportTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/TeleportWatchdogTask.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/TeleportTask.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/TeleportWatchdogTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/TeleportTask.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/TeleportWatchdogTask.java`

Packets

| Chronicle | Client → server                                      | Server → client                                                                                   |
| --------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` (NPC dialog)            | `TeleportToLocation` `28`, `NpcHtmlMessage` `0F`                                                  |
| High Five | `RequestBypassToServer` `23`, `RequestTeleport` `33` | `TeleportToLocation` `22`, `NpcHtmlMessage` `19`                                                  |
| Essence   | `ExRequestTeleport` `D0:0167`                        | `ExShowTeleportUi` `FE:0218`, `TeleportToLocation` `22`, `ExTeleportToLocationActivate` `FE:014B` |

Notes: the Interlude behaviour should be checked in aCis (`TeleportLocationData`) and L2jFrozen, which keep price lists in
their own formats rather than in `teleporters/*.xml`.

Known fixes: `sunrise@d9e6782e` (2015-03-17, missing-check) — One more option added in Gatekeeper teleports from now on you can configure if teleport is for noble chars only in custom_teleports Fixed…; `l2j-server-dp@35ae5b4b` (2005-12-05, other) — Jython based teleports: ToI and Charm QuestStates fixed; `hellas@495b37ff` (2013-11-10, other) — Fix for teleport scripts errors reported by properbow.

### teleport.escape — Escape and unstuck

ID: `teleport.escape` · Shared: **engine**

Scroll of escape and escape skills return the player to the nearest town (map region); `/unstuck` and the unstuck skill move a
stuck character to the nearest restart point after a cast delay; karma players go to the bad-location restart point.
Sub-features: Scroll of Escape (skill effect `Escape`) · `/unstuck` user command · restart point by region · cast delay ·
location types (town, castle, clan hall, fort).

| Chronicle | Present | Builds (evidence)          | Difference                                            |
| --------- | ------- | -------------------------- | ----------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | same                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline                                              |
| Essence   | yes     | mobius-ess                 | `Escape` effect plus `ConditionPlayerCanEscape` check |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/Unstuck.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/Escape.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/MapRegionData.java`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/user/Unstuck.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/Escape.java`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/user/Unstuck.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/Escape.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions/OpCanEscapeSkillCondition.java`

Packets

| Chronicle | Client → server                                                | Server → client                                                    |
| --------- | -------------------------------------------------------------- | ------------------------------------------------------------------ |
| Interlude | `BypassUserCmd` `AA` (`/unstuck`), `RequestMagicSkillUse` `2F` | `MagicSkillUse` `48`, `SetupGauge` `6D`, `TeleportToLocation` `28` |
| High Five | `BypassUserCmd` `B3`, `RequestMagicSkillUse` `39`              | `MagicSkillUse` `48`, `SetupGauge` `6B`, `TeleportToLocation` `22` |
| Essence   | `BypassUserCmd` `B3`, `MagicSkillUse` `39` (request)           | `MagicSkillUse` `48`, `SetupGauge` `6B`, `TeleportToLocation` `22` |

Notes: —

Known fixes: `l2j-server@3a012a0d` (2006-10-09, exploit-dupe) — SoE and /unstuck issues Prevent enchant scrolls while SoE (possible exploit was reported) Prevent simultaneous SoE and unstuck (minor…; `l2jfrozen@48ad5336` (2011-01-30, wrong-formula-value) — Fix to Immobilized Player during Unstuck, Fix to High Priest Van Halter Stats to last L2j, added script to update last revisions database; `l2j-server-dp@bfdb1032` (2021-04-06, wrong-formula-value) — Fixing the time shown in-game when "//unstuck" comand is used.

### teleport.bookmarks — Teleport bookmarks

ID: `teleport.bookmarks` · Shared: **partial (HF+Ess)**

A player saves current locations as named slots (icon, name, tag) and teleports to them with an item or skill; slot count is
raised by an item/effect; bookmarks are forbidden in no-bookmark zones.
Sub-features: save · rename/modify · delete · teleport (item/skill) · slot count increase · no-bookmark zones.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                    |
| --------- | ------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen           | aCis has a GM-only `BookmarkTable` (admin tool), not a player feature                                                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `TeleportBookmark` item handler                                                                                                     |
| Essence   | yes     | mobius-ess                           | teleport by action handler `TeleportBookmark` (`actions/user`); slot effect `AddTeleportBookmarkSlot`; the same bookmark packets as High Five |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestTeleportBookMark,RequestSaveBookMarkSlot,RequestModifyBookMarkSlot,RequestDeleteBookMarkSlot,RequestBookMarkSlotInfo,RequestChangeBookMarkSlot}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/TeleportBookmark.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/TeleportBookmark.java`, `nx-l2:chronicles/high-five/datapack/data/zones/no_bookmark.xml`
- `mobius-ess:dist/game/data/scripts/handlers/actions/user/TeleportBookmark.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/AddTeleportBookmarkSlot.java`, `mobius-ess:dist/game/data/zones/no_bookmark.xml`, `mobius-ess:dist/db_installer/sql/game/character_tpbookmark.sql`

Packets

| Chronicle | Client → server                                                                                                                                                                                     | Server → client                                                   |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| High Five | `RequestBookMarkSlotInfo`, `RequestSaveBookMarkSlot`, `RequestModifyBookMarkSlot`, `RequestDeleteBookMarkSlot`, `RequestTeleportBookMark` — one Ex packet `D0:51` (catalog name `ExBookmarkPacket`) | `ExGetBookMarkInfoPacket` `FE:84`                                 |
| Essence   | `ExUserBookmark` `D0:4E` (the same request classes as in High Five)                                                                                                                                 | `ExGetBookMarkInfoPacket` `FE:85` (catalog name `ExUserBookmark`) |

Notes: the catalog lists the High Five request under one Ex sub-opcode; the catalog is authoritative for opcodes.

### teleport.essence-teleport-ui — Essence teleport window

ID: `teleport.essence-teleport-ui` · Shared: **chronicle-only**

The Essence client uses a teleport window: destination list with prices, a favourites list, favourites add/remove and a UI toggle.
Sub-features: teleport UI · favourites list · favourites add/remove · UI toggle.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il         | —          |
| High Five | no      | nx-l2             | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/teleports/{ExRequestTeleport,ExRequestTeleportFavoriteList,ExRequestTeleportFavoritesAddDel,ExRequestTeleportFavoritesUIToggle}.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/TeleportListData.java`, `mobius-ess:dist/game/data/TeleportListData.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                                         | Server → client                                                                             |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Essence   | `ExRequestTeleport` `D0:0167`, `ExRequestTeleportFavoriteList` `D0:01B3`, `ExRequestTeleportFavoritesUIToggle` `D0:01B4`, `ExRequestTeleportFavoritesAddDel` `D0:01B5`, `RequestExTeleportUI` `D0:0257` | `ExShowTeleportUi` `FE:0218`, `ExTeleportFavoritesList` `FE:0266`, `ExTeleportUi` `FE:031D` |

Notes: —

### teleport.shared-location — Shared location teleport

ID: `teleport.shared-location` · Shared: **chronicle-only**

A player shares a map position with a clan or party; others open a window and pay to teleport to it.
Sub-features: share UI · teleport UI · teleport to shared position · shared position holder.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il         | —          |
| High Five | no      | nx-l2             | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/SharedTeleportManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/holders/SharedTeleportHolder.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/teleports/{ExRequestSharedLocationTeleport,ExRequestSharedLocationTeleportUi,ExRequestSharingLocationUi}.java`

Packets

| Chronicle | Client → server                                                                                                                    | Server → client                                                                 |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Essence   | `ExRequestSharingLocationUi` `D0:01A2`, `ExRequestSharedLocationTeleportUi` `D0:01A3`, `ExRequestSharedLocationTeleport` `D0:01A4` | `ExShowSharingLocationUi` `FE:0253`, `ExShowSharedLocationTeleportUi` `FE:0254` |

Notes: —

### teleport.raid — Teleport to raid boss

ID: `teleport.raid` · Shared: **chronicle-only**

A window lists raid bosses with their teleport price; the player teleports next to the boss spawn.
Sub-features: raid teleport list · raid info request · teleport to raid position.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il         | —          |
| High Five | no      | nx-l2             | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/RaidTeleportListData.java`, `mobius-ess:dist/game/data/RaidTeleportListData.xml`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/teleports/{RequestRaidTeleportInfo,ExTeleportToRaidPosition}.java`

Packets

| Chronicle | Client → server                                                           | Server → client                |
| --------- | ------------------------------------------------------------------------- | ------------------------------ |
| Essence   | `RequestRaidTeleportInfo` `D0:0198`, `ExTeleportToRaidPosition` `D0:0199` | `ExRaidTeleportInfo` `FE:0244` |

Notes: —

### teleport.sayune — Sayune

ID: `teleport.sayune` · Shared: **chronicle-only**

Sayune is a flight network: the player starts a flight to a point of a route; the flight is a series of server-driven moves, with a Sayune zone as the start area; "Sayune for all" is a custom switch that opens it to everyone.
Sub-features: sayune data (routes) · fly-move start/step · sayune zone · sayune-for-all switch.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il         | —          |
| High Five | no      | nx-l2             | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SayuneData.java`, `mobius-ess:dist/game/data/SayuneData.xml`, `mobius-ess:java/org/l2jmobius/gameserver/entity/zone/type/SayuneZone.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/request/SayuneRequest.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/sayune/{RequestFlyMove,RequestFlyMoveStart}.java`, `mobius-ess:dist/game/config/Custom/SayuneForAll.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/FlyMoveStartTask.java`

Packets

| Chronicle | Client → server                                         | Server → client                                                                       |
| --------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| Essence   | `RequestFlyMove` `D0:92`, `RequestFlyMoveStart` `D0:AE` | `ExFlyMove` `FE:E9`, `ExFlyMoveBroadcast` `FE:0109`, `ExNotifyFlyMoveStart` `FE:0111` |

Notes: —

### teleport.community — Community Board teleport

ID: `teleport.community` · Shared: **engine**

Community Board teleport menu: player picks a destination from the board page and pays a fixed price in a configured currency;
skills are disabled for three seconds after the teleport.
Sub-features: board page · fixed price · currency · destination map from config.

| Chronicle | Present | Builds (evidence)   | Difference                                                                           |
| --------- | ------- | ------------------- | ------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il (custom)  | Mobius custom: `_bbsteleport` in `HomeBoard`; destinations from `CommunityBoard.ini` |
| High Five | yes     | nx-l2 (custom)      | same                                                                                 |
| Essence   | yes     | mobius-ess (custom) | same                                                                                 |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/HomeBoard.java` (`_bbsteleport`), `nx-l2:chronicles/high-five/datapack/data/html/CommunityBoard/Custom/gatekeeper/main.html`, `nx-l2:chronicles/high-five/datapack/config/Custom/CommunityBoard.ini` (`CommunityEnableTeleports`, `CommunityTeleportPrice`)
- `mobius-il:dist/game/data/scripts/handlers/bypass/communityboard/HomeBoard.java`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/communityboard/HomeBoard.java`

Packets

| Chronicle | Client → server                                      | Server → client                             |
| --------- | ---------------------------------------------------- | ------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` (`_bbsteleport;<name>`) | `ShowBoard` `6E`, `TeleportToLocation` `28` |
| High Five | `RequestBypassToServer` `23`                         | `ShowBoard` `7B`, `TeleportToLocation` `22` |
| Essence   | `RequestBypassToServer` `23`                         | `ShowBoard` `7B`, `TeleportToLocation` `22` |

Notes: it is a custom of the build family, not a game feature; the Community Board shell itself is the community domain.
