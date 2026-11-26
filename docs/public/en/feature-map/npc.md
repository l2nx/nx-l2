# NPCs and dialogs

NPC templates, dialog HTML, bypass routing and service NPCs (buffers, observers, newbie helpers).

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### npc.templates — NPC templates

ID: `npc.templates` · Shared: **partial**

Static definition of every NPC: stats, skills, AI type, instance class, drop tables, localised names. Loaded at boot
from XML; a live NPC in the world is an instance built from its template.
Sub-features: stats and skills · AI type and instance type · drop lists · localised names · custom stat overrides.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                |
| --------- | ------- | -------------------------------------- | ----------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server | aCis `NpcData` XML; L2jFrozen `NpcTable` (SQL/CSV)                                        |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity   | baseline: `NpcData` + `NpcTemplate` with grouped drops (`DropGroupHolder`)                |
| Essence   | yes     | mobius-ess                             | same loader (91% of lines of `NpcData` equal to HF); own NPC set, extra drop type FORTUNE |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/NpcData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/NpcTemplate.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/NpcNameLocalisationData.java`, `nx-l2:chronicles/high-five/datapack/data/stats/npcs/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/NpcData.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/templates/NpcTemplate.java`, `mobius-il:dist/game/data/stats/npcs`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/NpcData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/templates/NpcTemplate.java`, `mobius-ess:dist/game/data/stats/npcs`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/xml/NpcData.java`
- `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/datatables/sql/NpcTable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/{Guardian,Doppelganger}.java`

Packets

| Chronicle | Client → server | Server → client                                                                                      |
| --------- | --------------- | ---------------------------------------------------------------------------------------------------- |
| Interlude | — (static data) | `NpcInfo` `16` (template id + name/title), `NpcSay` `02`                                             |
| High Five | —               | `NpcInfo` `0C`, `NpcSay` `30`, `ExChangeNpcState` `FE:BE`                                            |
| Essence   | —               | `NpcInfo` `0C` + `NpcInfoAbnormalVisualEffect` `4B`, `NpcInfoState` `C1`, `ExNpcInfoSpeed` `FE:0133` |

Notes: in `nx-l2` the template is `NpcTemplate`, the instance is `Npc` (subclasses under `entity/actor/instance`); aCis
uses `NpcTemplate`/`Npc`; L2jFrozen `L2NpcTemplate`/`L2NpcInstance`. `NpcData` is not byte-identical between HF and
Essence (91%), the template schema is the same. Essence adds the instance types `Guardian` and `Doppelganger`.

Known fixes: `l2j-server@48ac02ba` (2017-04-29, wrong-formula-value) — Problems with case-sensitive on npc data load Fix #54 #68; `mobius@be6b0566` (2018-01-15, concurrency) — Probable fix for concurrent droplist shuffle.

### npc.dialog-html — NPC dialogs

ID: `npc.dialog-html` · Shared: **engine**

HTML windows that NPCs show to players: HTML cache loaded from the datapack, placeholders, per-language folders,
bypass links embedded in the text.
Sub-features: HTML cache · per-language lookup · `NpcHtmlMessage` building · quest HTML (`ExNpcQuestHtmlMessage`) · tutorial HTML · missing-HTML finder (Essence admin).

| Chronicle | Present | Builds (evidence)                                                | Difference                                                               |
| --------- | ------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis (`HtmCache`), l2jfrozen (`HtmCache`), l2j-server | same cache idea; tutorial HTML (`TUTORIAL_SHOW_HTML`)                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                             | baseline (`HtmCache`, `NpcHtmlMessage`, `ExNpcQuestHtmlMessage`)         |
| Essence   | yes     | mobius-ess                                                       | `HtmCache` identical to HF after normalization; adds `AdminMissingHtmls` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/cache/HtmCache.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/NpcHtmlMessage.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/html/IBypassFormatter.java`
- `mobius-il:java/org/l2jmobius/gameserver/cache/HtmCache.java`, `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/NpcHtmlMessage.java`
- `mobius-ess:java/org/l2jmobius/gameserver/cache/HtmCache.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/NpcHtmlMessage.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/cache/HtmCache.java`
- `l2jfrozen:**/HtmCache.java` (found by file name)
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/NpcClick.java`
- `mobius-il:dist/game/data/scripts/handlers/actions/click/NpcClick.java`
- `mobius-ess:dist/game/data/scripts/handlers/actions/click/NpcClick.java`

Packets

| Chronicle | Client → server                                                                                                      | Server → client                                                                                                                              |
| --------- | -------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21`, `RequestTutorialLinkHtml` `7B`, `RequestLinkHtml` `20`                                 | `NpcHtmlMessage` `0F`, `TutorialShowHtml` `A0`, `TutorialCloseHtml` `A3`                                                                     |
| High Five | `RequestBypassToServer` `23`, `RequestTutorialLinkHtml` `85`, `RequestExSetTutorial` `D0:1D`, `RequestLinkHtml` `22` | `NpcHtmlMessage` `19`, `TutorialShowHtml` `A6`, `TutorialCloseHtml` `A9`, `ExNpcQuestHtmlMessage` `FE:8D`, `ExShowPetitionHtml` `FE:B1`      |
| Essence   | `ExSetTutorial` `D0:1D`, `RequestTutorialLinkHtml` `85` (renamed), `ExOpenHtml` `D0:0164`, `RequestLinkHtml` `22`    | `NpcHtmlMessage` `19`, `TutorialShowHtml` `A6`, `TutorialCloseHtml` `A9`, `ExNpcQuestHtmlMessage` `FE:8E`, `ExHtmlWithNpcViewport` `FE:01D9` |

Notes: `HtmCache`, `AggroInfo`, `RespawnTaskManager` are the files that are normalized-identical between Mobius HF and
Essence; IL equals HF byte-for-byte (backport), so IL is confirmed only by aCis / L2jFrozen having the same cache.

Known fixes: `sunrise@7dd32006` (2016-08-17, wrong-formula-value) — Fixed some wrong order when send image from server; `l2j-server@be391ba9` (2020-12-06, missing-check) — Adding temp fix to missing files when multilanguage support is enabled.

### npc.bypass — Bypass routing

ID: `npc.bypass` · Shared: **partial**

Routing of `bypass` links from HTML windows and the community board to server handlers (NPC links, voice commands,
community board, item bypass), with a registry of handlers and allow-listing of the links the server actually sent.
Sub-features: NPC bypass handlers (`Buy`, `Multisell`, `Link`, `ChatLink`, `Observation`, ...) · community board bypass · voice
commands via bypass · item bypass · admin build-command bypass.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                                                                                  |
| --------- | ------- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server | aCis routes inside `RequestBypassToServer`; L2jFrozen adds `ICustomByPassHandler`                                                                           |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity   | baseline: `BypassHandler` registry, `IBypassHandler`, `IBypassFormatter`                                                                                    |
| Essence   | yes     | mobius-ess                             | same registry; extra NPC handlers (`ItemAuctionLink`, `ReleaseAttribute`, `UpgradeEquipment`, `EnsoulWindow`, `PetExtractWindow`) and `Bypass` item handler |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/BypassHandler.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/IBypassHandler.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestBypassToServer.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/` (≈40 handlers), `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/`
- `mobius-il:java/org/l2jmobius/gameserver/handler/BypassHandler.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestBypassToServer.java`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc`
- `mobius-ess:java/org/l2jmobius/gameserver/handler/BypassHandler.java`, `mobius-ess:dist/game/data/scripts/handlers/bypass/npc`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/network/clientpackets/RequestBypassToServer.java`
- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/PlayerHelp.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/VoiceCommand.java`
- `mobius-il:dist/game/data/scripts/handlers/bypass/npc/PlayerHelp.java`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc/VoiceCommand.java`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/PlayerHelp.java`, `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/VoiceCommand.java`

Packets

| Chronicle | Client → server                                                               | Server → client       |
| --------- | ----------------------------------------------------------------------------- | --------------------- |
| Interlude | `RequestBypassToServer` `21`, `BypassUserCmd` `AA`, `SendBypassBuildCmd` `5B` | `NpcHtmlMessage` `0F` |
| High Five | `RequestBypassToServer` `23`, `BypassUserCmd` `B3`, `SendBypassBuildCmd` `74` | `NpcHtmlMessage` `19` |
| Essence   | `BypassUserCmd` `B3`, `RequestBypassToServer` `23`, `SendBypassBuildCmd` `74` | `NpcHtmlMessage` `19` |

Notes: the handler set is the part that differs; the routing contract is shared.

Known fixes: `l2j-server@eaf5a340` (2007-12-01, exploit-dupe) — Fix: Manor bypass exploit plug (credit: kerberos_20); `mobius@1c108bde` (2020-12-18, crash-npe) — Fixed Link NPE for sealed_runes_01.htm bypass

### npc.buffer — NPC buffers

ID: `npc.buffer` · Shared: **partial**

NPCs that cast buffs on request: free support magic for newbies, scheme buffer (saved buff sets paid per use),
blessing, and community-board buffers.
Sub-features: newbie support magic (`SupportMagic`) · scheme buffer (saved profiles) · blessing (`SupportBlessing`) · per-level limits · price.

| Chronicle | Present | Builds (evidence)                                                        | Difference                                                                             |
| --------- | ------- | ------------------------------------------------------------------------ | -------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`BufferManager`, `bufferSkills.xml`, `newbieBuffs.xml`) | aCis: buffer manager plus newbie buffs; no buffer NPC file found in L2jFrozen by name  |
| High Five | yes     | nx-l2, l2j-server, eternity                                              | baseline: `SupportMagic` / `SupportBlessing` bypass + Mobius custom `SchemeBuffer` NPC |
| Essence   | yes     | mobius-ess                                                               | `SchemeBuffer` config and table present; `SupportMagic` handler present (same bypass)  |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/SupportMagic.java`, `nx-l2:.../SupportBlessing.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/SchemeBuffer.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/SchemeBufferTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/SchemeBufferConfig.java`, `nx-l2:chronicles/high-five/datapack/data/SchemeBufferSkills.xml`, `nx-l2:chronicles/high-five/datapack/config/Custom/SchemeBuffer.ini`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/SchemeBuffer.java`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc/SupportMagic.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/SchemeBuffer.java`, `mobius-ess:dist/game/config/Custom/SchemeBuffer.ini`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/data/manager/BufferManager.java` (data `acis:aCis_datapack/data/xml/bufferSkills.xml`)

Packets

| Chronicle | Client → server              | Server → client                             |
| --------- | ---------------------------- | ------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `NpcHtmlMessage` `0F`, skill effect packets |
| High Five | `RequestBypassToServer` `23` | `NpcHtmlMessage` `19`                       |
| Essence   | `RequestBypassToServer` `23` | `NpcHtmlMessage` `19`                       |

Notes: no dedicated packets; buffs ride on skill/abnormal packets (domain `skill`).

Known fixes: `mobius@f3cc2bf2` (2019-10-25, crash-npe) — Fixed duplicate key exception in buffer schemes. Contributed by Sahar; `l2j-server-dp@270532a9` (2022-02-20, crash-npe) — Fix class cast exception in BufferService when using the healing function; `l2jfrozen@c117c301` (2010-11-29, concurrency) — Some little fixes to concurrent access and to Buffer Handler.

### npc.observation — Observation via NPC

ID: `npc.observation` · Shared: **partial**

An NPC lets a player enter observer mode: a free camera over a fixed list of locations (broadcasting tower, siege
viewpoints, arena viewing) for adena.
Sub-features: fixed viewpoint list · adena fee · observer return · olympiad observation · siege observation (Essence packet).

| Chronicle | Present | Builds (evidence)                                                           | Difference                                                                      |
| --------- | ------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`observerGroups.xml`), l2jfrozen (`L2ObservationInstance`) | packets `OBSERVATION_MODE` / `OBSERVATION_RETURN`                               |
| High Five | yes     | nx-l2, l2j-server, sunrise                                                  | packets renamed `OBSERVER_START` / `OBSERVER_END`; `BroadcastingTower` instance |
| Essence   | yes     | mobius-ess                                                                  | `ExCastleWarObserverStart`; `BroadcastingTower` kept (92% equal to HF)          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/Observation.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/BroadcastingTower.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/ObserverReturn.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ObservationMode,ObservationReturn}.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/BroadcastingTower.java`, `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/ObservationEnter.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/BroadcastingTower.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/castlewar/ExCastleWarObserverStart.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/network/clientpackets/ObserverReturn.java`
- `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/model/actor/instance/L2ObservationInstance.java`

Packets

| Chronicle | Client → server                                                                                                                    | Server → client                                                                                             |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Interlude | `ObserverReturn` `B8`, `RequestOlympiadObserverEnd` `D0:12`                                                                        | `ObservationMode` `DF`, `ObservationReturn` `E0`                                                            |
| High Five | `ObserverReturn` `C1`, `RequestOlympiadObserverEnd` `D0:29`, `RequestExEventMatchObserverEnd` `D0:77`                              | `ObserverStart` `EB`, `ObserverEnd` `EC`, `ExEventMatchObserver` `FE:0E`                                    |
| Essence   | `ObserverReturn` `C1`, `RequestOlympiadObserverEnd` `D0:29`, `ExCastleWarObserverStart` `D0:0197`, `ExObserveCuriousHouse` `D0:C0` | `ObserverStart` `EB`, `ObserverEnd` `EC`, `ExCuriousHouseObserveMode` `FE:0128`, `ExArenaObserve` `FE:01D8` |

Notes: opcode name differences IL → HF are renames of the same two server packets.

### npc.info — NPC information (shift-click)

ID: `npc.info` · Shared: **partial (HF+Ess)**

Shift-clicking an NPC opens an info window: drop list, skills, resists, aggression, level, optionally restricted by an
allow/block list; a server-side alternative to the missing client window.
Sub-features: drop list view · skills/resists/aggression views · per-NPC block list · shift-command config.

| Chronicle | Present | Builds (evidence)                                       | Difference                                                     |
| --------- | ------- | ------------------------------------------------------- | -------------------------------------------------------------- |
| Interlude | no      | acis, l2jfrozen (not found); Mobius IL only as backport | no shift-click NPC info in the Interlude family roots checked  |
| High Five | yes     | nx-l2, l2j-server (`NpcViewMod`), eternity (`DropInfo`) | baseline: `NpcViewMod` bypass + `NpcShiftClick` action handler |
| Essence   | yes     | mobius-ess                                              | same two handlers                                              |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/NpcViewMod.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/shiftclick/NpcShiftClick.java`
- `mobius-il:dist/game/data/scripts/handlers/bypass/npc/NpcViewMod.java`, `mobius-il:dist/game/data/scripts/handlers/actions/shiftclick/NpcShiftClick.java` (backport)
- `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/NpcViewMod.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/shiftclick/NpcShiftClick.java`

Packets

| Chronicle | Client → server                            | Server → client       |
| --------- | ------------------------------------------ | --------------------- |
| Interlude | `RequestBypassToServer` `21` (Mobius only) | `NpcHtmlMessage` `0F` |
| High Five | `RequestBypassToServer` `23`               | `NpcHtmlMessage` `19` |
| Essence   | `RequestBypassToServer` `23`               | `NpcHtmlMessage` `19` |

Notes: server-side custom (L2J), not an official client feature; hence Interlude "no" in the original-engine roots.

Known fixes: `l2j-server-dp@5acd2c9a` (2015-04-09, wrong-formula-value) — FIX single-item groups in NpcViewMod following wrong killer rules; `mobius@2c61857b` (2017-10-04, wrong-formula-value) — Fixed drop chance multiplier by item id.

### npc.newbie-help — Newbie help

ID: `npc.newbie-help` · Shared: **partial**

NPC guides that help new characters: first-steps guide NPC that gives starter buffs/items and teleports, pathfinder
equipment, and in Essence an in-game assistant NPC.
Sub-features: newbie guide NPC · starter buffs · tutorial markers · adventurer guide book packet · game assistant (Essence).

| Chronicle | Present | Builds (evidence)                                                    | Difference                                                    |
| --------- | ------- | -------------------------------------------------------------------- | ------------------------------------------------------------- |
| Interlude | yes     | mobius-il (`NewbieHelper`), acis (`NewbieHelper`, `newbieBuffs.xml`) | `NewbieHelper` script; tutorial packets drive the first steps |
| High Five | yes     | nx-l2 (`NewbieGuide`), l2j-server (`NewbieGuide`), eternity          | `NewbieGuide` script; `EX_SHOW_ADVENTURER_GUIDE_BOOK`         |
| Essence   | yes     | mobius-ess                                                           | `NewbieGuide` + `GameAssistant` (own `GameAssistant.ini`)     |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/NewbieGuide/NewbieGuide.java`
- `mobius-il:dist/game/data/scripts/ai/others/NewbieHelper/NewbieHelper.java`
- `mobius-ess:dist/game/data/scripts/ai/others/NewbieGuide/NewbieGuide.java`, `mobius-ess:dist/game/data/scripts/ai/others/GameAssistant/GameAssistant.java`, `mobius-ess:dist/game/config/GameAssistant.ini`, `mobius-ess:java/org/l2jmobius/gameserver/config/GameAssistantConfig.java`
- `acis:aCis_gameserver/java/net/sf/l2j/gameserver/scripting/script/feature/NewbieHelper.java`

Packets

| Chronicle | Client → server                                                                                                                                                            | Server → client                                                                               |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Interlude | `RequestTutorialLinkHtml` `7B`, `RequestTutorialPassCmdToServer` `7C`, `RequestTutorialQuestionMark` `7D`, `RequestTutorialClientEvent` `7E`, `RequestBypassToServer` `21` | `TutorialShowHtml` `A0`, `TutorialShowQuestionMark` `A1`, `ExShowAdventurerGuideBook` `FE:37` |
| High Five | `RequestBypassToServer` `23`, `RequestExSetTutorial` `D0:1D`                                                                                                               | `ShowTutorialMark` `A7`, `ExTutorialList` `FE:6B`, `ExShowAdventurerGuideBook` `FE:38`        |
| Essence   | `RequestTutorialQuestionMark` `87`, `ExSetTutorial` `D0:1D`                                                                                                                | `ExTutorialShowId` `FE:01E6`                                                                  |

Notes: the IL name is `NewbieHelper` (aCis, Mobius IL); L2J HF/Mobius HF call it `NewbieGuide`.

Known fixes: `l2j-server-dp@89807d26` (2023-02-01, wrong-formula-value) — Fixed wrong message displayed to a player clicking on Teleport dialog from Newbie Guide; `mobius@69fd2036` (2021-06-19, other) — Fixed game assistant package deposit; `mobius@b8e3ea37` (2026-06-05, other) — Fixed Newbie Guide servitor support magic being cast on the player. Contributed by Altur.

### npc.service-scripts — Service NPC scripts

ID: `npc.service-scripts` · Shared: **partial**

Catalogue of scripts for service NPCs: Merchants of Mammon, castle/fort managers, traders, wyvern managers, village
helpers. One engine feature; scripts are not enumerated per file.
Sub-features: Mammon NPCs · castle/fort service NPCs · wyvern manager · traders · random walking guards (Essence).

| Chronicle | Present | Builds (evidence)                                             | Difference                                                                  |
| --------- | ------- | ------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (46 dirs in `ai/others`), l2jfrozen (`AdminMammon`) | smaller catalogue; Mammon handled in `SevenSigns`                           |
| High Five | yes     | nx-l2 (83 dirs/files in `ai/others`), l2j-server, eternity    | baseline                                                                    |
| Essence   | yes     | mobius-ess (51 in `ai/others`)                                | adds `RandomWalkingGuards`, `Spawns/DayNightSpawns`, `Servitors/TreeOfLife` |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/` (83 entries); IL `mobius-il:dist/game/data/scripts/ai/others` (46); Essence `mobius-ess:dist/game/data/scripts/ai/others` (51)

Packets

| Chronicle | Client → server                               | Server → client                      |
| --------- | --------------------------------------------- | ------------------------------------ |
| Interlude | — (script-driven, uses bypass / HTML packets) | `NpcHtmlMessage` `0F`, `NpcSay` `02` |
| High Five | — (script-driven, uses bypass / HTML packets) | `NpcHtmlMessage` `19`, `NpcSay` `30` |
| Essence   | — (script-driven, uses bypass / HTML packets) | `NpcHtmlMessage` `19`, `NpcSay` `30` |

Notes: counts are entries (directories + files) in `ai/others`.
