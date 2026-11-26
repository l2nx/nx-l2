# Client interface

Server side of client UI features: shortcuts, macros, key bindings, radar, localization, cinematics.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### ui.shortcuts — Shortcut bar

ID: `ui.shortcuts` · Shared: **partial**

The player binds items, skills, actions, macros and recipes to the 12-slot hotbars; the server stores and replays them on login and seeds new characters from a template list.
Sub-features: register · delete · initial shortcuts per class · per-bar limit (12) · Essence auto-use shortcut and costume shortcut list.

| Chronicle | Present | Builds (evidence)                    | Difference                                      |
| --------- | ------- | ------------------------------------ | ----------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `ShortcutType` has no `BOOKMARK`                |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise | baseline; adds `BOOKMARK`                       |
| Essence   | yes     | mobius-ess                           | auto-shortcut activation, costume shortcut list |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/Shortcuts.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/InitialShortcutData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestShortcutDel.java`, `nx-l2:chronicles/high-five/datapack/data/stats/players/initialShortcuts.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/player/Shortcuts.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/autoplay/ExRequestActivateAutoShortcut.java`

Packets

| Chronicle | Client → server                                                                                                               | Server → client                                                                                                                            |
| --------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestShortCutReg` `33`, `RequestShortCutDel` `35`                                                                          | `ShortCutRegister` `44`, `ShortCutInit` `45`                                                                                               |
| High Five | `RequestShortCutReg` `3D`, `RequestShortCutDel` `3F`                                                                          | `ShortCutRegister` `44`, `ShortCutInit` `45`, `ShortCutDelete` `46`                                                                        |
| Essence   | `RequestShortCutReg` `3D`, `RequestShortCutDel` `3F`, `ExActivateAutoShortcut` `D0:0171`, `ExCostumeChangeShortcut` `D0:016E` | `ShortCutRegister` `44`, `ShortCutInit` `45`, `ShortCutDelete` `46`, `ExCostumeShortcutList` `FE:0214`, `ExActivateAutoShortcut` `FE:0219` |

Notes: `MAX_SHORTCUTS_PER_BAR = 12` in all three Mobius trees.

Known fixes: `l2j-server@7ae5d34b87` (2014-04-07, crash-npe) — BETA: NPE vulnerability in macro presets.; `lineage2ts@0aa62ab165` (2024-04-16, other) — - fixing issues with initial shortcuts not populating on character creation

### ui.macros — Macros

ID: `ui.macros` · Shared: **partial**

Create, delete and reorder macros (up to a list of commands per macro); the server persists them per character and sends the list on login.
Sub-features: create/update · delete · command types · Essence autoplay macro hook.

| Chronicle | Present | Builds (evidence)                    | Difference                                       |
| --------- | ------- | ------------------------------------ | ------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | —                                                |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise | baseline                                         |
| Essence   | yes     | mobius-ess                           | `MacroUpdateType` enum, `ExAutoplayDoMacro` hook |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/MacroList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestMakeMacro.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/enums/player/MacroUpdateType.java`

Packets

| Chronicle | Client → server                                    | Server → client                                     |
| --------- | -------------------------------------------------- | --------------------------------------------------- |
| Interlude | `RequestMakeMacro` `C1`, `RequestDeleteMacro` `C2` | `SendMacroList` `E7`                                |
| High Five | `RequestMakeMacro` `CD`, `RequestDeleteMacro` `CE` | `SendMacroList` `E8`                                |
| Essence   | `RequestMakeMacro` `CD`, `RequestDeleteMacro` `CE` | `SendMacroList` `E8`, `ExAutoplayDoMacro` `FE:0221` |

Notes: `MacroUpdateType` exists only in the Essence tree of the three Mobius trees.

Known fixes: `l2j-server@ca75f62980` (2008-01-19, other) — Fix for macros to 48 (thanks rnn); `l2j-server@b690bd15b6` (2007-05-15, other) — ShortcutBar fix by Noctarius

### ui.key-mapping — Key bindings

ID: `ui.key-mapping` · Shared: **partial (HF+Ess)**

The client uploads its key-binding table on logout and requests it on login; the server stores it per character.
Sub-features: save mapping · request mapping.

| Chronicle | Present | Builds (evidence)                     | Difference                               |
| --------- | ------- | ------------------------------------- | ---------------------------------------- |
| Interlude | no      | mobius-il (no class), acis, l2jfrozen | —                                        |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise  | baseline                                 |
| Essence   | yes     | mobius-ess                            | handlers under `clientpackets/settings/` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestKeyMapping.java,RequestSaveKeyMapping.java}`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/settings/RequestSaveKeyMapping.java`

Packets

| Chronicle | Client → server                                              | Server → client |
| --------- | ------------------------------------------------------------ | --------------- |
| High Five | `RequestKeyMapping` `D0:21`, `RequestSaveKeyMapping` `D0:22` | —               |
| Essence   | `RequestKeyMapping` `D0:21`, `RequestSaveKeyMapping` `D0:22` | —               |

Notes: Interlude clients keep key bindings locally; no Mobius IL, aCis or L2jFrozen class matched `KeyMapping|UIKeys` by file name.

### ui.client-settings — Essence client settings

ID: `ui.client-settings` · Shared: **chronicle-only**

Essence stores client-side interaction and item-announce preferences on the server.
Sub-features: interact-modify · item announce settings.

| Chronicle | Present | Builds (evidence) | Difference |
| --------- | ------- | ----------------- | ---------- |
| Interlude | no      | mobius-il         | —          |
| High Five | no      | nx-l2 (no class)  | —          |
| Essence   | yes     | mobius-ess        | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/settings/{ExInteractModify.java,ExSaveItemAnnounceSetting.java}`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/settings/ExItemAnnounceSetting.java`

Packets

| Chronicle | Client → server                                                     | Server → client                                                                                                          |
| --------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `ExInteractModify` `D0:015F`, `ExSaveItemAnnounceSetting` `D0:01A0` | `ExItemAnnounceSetting` `FE:024B`, `ExItemAnnounce` `FE:01FF`, `ExRaidDropItemAnnounce` `FE:023F`, `ExUiSetting` `FE:71` |

Notes: no equivalent in aCis/L2jFrozen/L2J-HF by name.

### ui.radar-minimap — Radar and minimap

ID: `ui.radar-minimap` · Shared: **partial**

The server draws quest/target markers on the radar (add, remove) and answers minimap requests.
Sub-features: radar control (add/delete marker) · minimap · Essence open-minimap request.

| Chronicle | Present | Builds (evidence)                    | Difference                                   |
| --------- | ------- | ------------------------------------ | -------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `RadarControl`, `ShowMiniMap` only           |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise | adds `ShowRadar`, `DeleteRadar`              |
| Essence   | yes     | mobius-ess                           | c2s `OpenMinimap`; same server packets as HF |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/Radar.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestShowMiniMap.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/RadarControl.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ExShowOwnthingPos.java,ExShowTrace.java}`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/player/Radar.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExShowTrace.java`

Packets

| Chronicle | Client → server           | Server → client                                                                                                                        |
| --------- | ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestShowMiniMap` `CD` | `ShowMiniMap` `9D`, `RadarControl` `EB`                                                                                                |
| High Five | `RequestShowMiniMap` `6C` | `ShowMiniMap` `A3`, `RadarControl` `F1`, `ShowRadar` `AA`, `DeleteRadar` `B8`, `EX_SHOW_OWNTHING_POS` `FE:93`, `EX_SHOW_TRACE` `FE:67` |
| Essence   | `RequestShowMiniMap` `6C` | `ShowMiniMap` `A3`, `RadarControl` `F1`, `ShowRadar` `AA`, `DeleteRadar` `B8`, `EX_SHOW_TRACE` `FE:68`                                 |

Notes: `ShowRadar`/`DeleteRadar` are absent in IL; the `Radar` holder itself is present in all three.

Known fixes: `l2jdevs-core@977805cf` (2018-06-08, other) — Displaying flagged (radar) positions on map fix.

### ui.localization — Multilingual support

ID: `ui.localization` · Shared: **partial**

Each player picks a language (`.lang`); NPC names, NPC strings, system messages and server messages are replaced from per-language XML tables.
Sub-features: player language command · NPC-name localisation · NpcString localisation · SystemMessage localisation · send-message localisation · Essence item-name localisation.

| Chronicle | Present | Builds (evidence)                 | Difference                                  |
| --------- | ------- | --------------------------------- | ------------------------------------------- |
| Interlude | differs | mobius-il (custom)                | tables: NpcName, SendMessage, SystemMessage |
| High Five | differs | nx-l2 (custom), eternity, sunrise | adds `NpcStringLocalisation.xml`            |
| Essence   | differs | mobius-ess (custom)               | adds `ItemNamesLocalisation.xml`            |

`differs` = optional Mobius custom, off by default (`MultilingualSupport.ini`).

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/MultilingualSupportConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/MultilingualSupport.ini`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Lang.java`, `nx-l2:chronicles/high-five/datapack/data/lang/el/NpcStringLocalisation.xml`
- `mobius-ess:dist/game/data/lang/el/ItemNamesLocalisation.xml`

Packets

| Chronicle | Client → server | Server → client                                                                           |
| --------- | --------------- | ----------------------------------------------------------------------------------------- |
| Interlude | —               | `SystemMessage` `64`, `NpcHtmlMessage` `0F`, `NpcSay` `02`, `ExShowScreenMessage` `FE:38` |
| High Five | —               | `SystemMessage` `62`, `NpcHtmlMessage` `19`, `NpcSay` `30`, `ExShowScreenMessage` `FE:39` |
| Essence   | —               | `SystemMessage` `62`, `NpcHtmlMessage` `19`, `NpcSay` `30`, `ExShowScreenMessage` `FE:39` |

Notes: sample language directory is `el`.

### ui.server-info — Server info commands

ID: `ui.server-info` · Shared: **partial**

Optional server-info conveniences: `.online` player count, server time, screen welcome message, online-info on login.
Sub-features: online command · server time · screen welcome message · online info.

| Chronicle | Present | Builds (evidence)   | Difference |
| --------- | ------- | ------------------- | ---------- |
| Interlude | differs | mobius-il (custom)  | —          |
| High Five | differs | nx-l2 (custom)      | baseline   |
| Essence   | differs | mobius-ess (custom) | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Online.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/{OnlineInfo.ini,ScreenWelcomeMessage.ini,ServerTime.ini}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/WarnUserTakeBreakTask.java`
- `mobius-il:dist/game/config/Custom/ServerTime.ini`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/WarnUserTakeBreakTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/WarnUserTakeBreakTask.java`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/DlgAnswer.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ConfirmDlg.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/DlgAnswer.java`, `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/ConfirmDlg.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/DlgAnswer.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ConfirmDlg.java`

Packets

| Chronicle | Client → server               | Server → client                                               |
| --------- | ----------------------------- | ------------------------------------------------------------- |
| Interlude | `Say2` `38`, `DlgAnswer` `C5` | `ExShowScreenMessage` `FE:38`, `ConfirmDlg` `ED`              |
| High Five | `DlgAnswer` `C6`              | `ExShowScreenMessage` `FE:39`, `Say2` `4A`, `ConfirmDlg` `F3` |
| Essence   | `DlgAnswer` `C6`              | `ExShowScreenMessage` `FE:39`, `Say2` `4A`, `ConfirmDlg` `F3` |

Notes: the `Online` voiced command exists in all three Mobius trees. `ConfirmDlg` / `DlgAnswer` is the shared yes/no confirmation dialog used by many features (party invite, resurrection, trade request); it exists in all three versions.

### ui.cinematics — Cinematics and scene effects

ID: `ui.cinematics` · Shared: **partial**

Server-driven cinematic effects: scene (movie) playback and its end/stop requests, camera mode and special camera, earthquake, red-sky effect, event triggers and sound playback. Scripts and the player object start a scene and the client reports when it ends.
Sub-features: scene player · camera mode · earthquake · red sky · event trigger · sound · Essence USM event.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                                         |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | camera, earthquake, red sky, event trigger; scenes only through the Kamael slideshow packet (`FE:5B`, named `ExPlayScene` in aCis) |
| High Five | yes     | nx-l2, l2j-server          | baseline: adds `ExStartScenePlayer`, `ExPlayScene`                                                                                 |
| Essence   | yes     | mobius-ess                 | adds scene stop and USM event packets                                                                                              |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/EndScenePlayer.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{CameraMode.java,Earthquake.java,OnEventTrigger.java,ExPlayScene.java,ExRedSky.java,ExStartScenePlayer.java,PlaySound.java}`
- `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/{CameraMode.java,Earthquake.java,OnEventTrigger.java,ExRedSky.java,PlaySound.java}`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{EndScenePlayer.java,RequestExEscapeScene.java}`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/{CameraMode.java,Earthquake.java,OnEventTrigger.java,ExPlayScene.java,ExRedSky.java,ExShowUsm.java,ExStartScenePlayer.java,ExStopScenePlayer.java,PlaySound.java}`

Packets

| Chronicle | Client → server                                            | Server → client                                                                                                                                                                                                               |
| --------- | ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                          | `CAMERA_MODE` `F1`, `EARTHQUAKE` `C4`, `EVENT_TRIGGER` `CF`, `EX_RED_SKY` `FE:40`, `EX_SHOW_SLIDESHOW_KAMAEL` `FE:5B`, `PLAY_SOUND` `98`                                                                                      |
| High Five | `END_SCENE_PLAYER` `D0:5B`                                 | `CAMERA_MODE` `F7`, `EARTHQUAKE` `D3`, `EVENT_TRIGGER` `CF`, `EX_RED_SKY` `FE:41`, `EX_PLAY_SCENE` `FE:5C`, `EX_START_SCENE_PLAYER` `FE:99`, `PLAY_SOUND` `9E`                                                                |
| Essence   | `END_SCENE_PLAYER` `D0:58`, `EX_STOP_SCENE_PLAYER` `D0:91` | `CAMERA_MODE` `F7`, `EARTHQUAKE` `D3`, `EVENT_TRIGGER` `CF`, `EX_RED_SKY` `FE:42`, `EX_PLAY_SCENE` `FE:5D`, `EX_START_SCENE_PLAYER` `FE:9A`, `EX_STOP_SCENE_PLAYER` `FE:E8`, `EX_SEND_USM_EVENT` `FE:010A`, `PLAY_SOUND` `9E` |

Notes: the scene is started from `Player` in nx-l2 (`ExPlayScene`, `ExStartScenePlayer`).
