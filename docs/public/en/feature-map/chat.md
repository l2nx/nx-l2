# Chat

Chat channels, world chat, filters, moderation and chat commands.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### chat.channels — Chat channels

ID: `chat.channels` · Shared: **partial**

`Say2` receives the chat type and message and dispatches to a per-type handler: general, shout, whisper, trade, party, clan, alliance,
hero voice, petition, party-match room, command-channel room, battlefield; handlers check mutes, level, flood and range, then broadcast
`CreatureSay`.
Sub-features: general · shout · trade · whisper · party · clan · alliance · hero · party rooms · petition · randomizer.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                             |
| --------- | ------- | --------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | 14 channel handlers in Mobius IL incl. `ChatBattlefield`; `ChatType` 0..21                                             |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same 14 handlers (`nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/channels`) |
| Essence   | differs | mobius-ess                                    | no `ChatBattlefield`; + `ChatWorld`, `ChatUniversal`; `ChatType` adds `NPC_WHISPER` 24, `WORLD` 25, `UNIVERSAL` 29     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/Say2.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/ChatHandler.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/channels/*.java`; also: `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/channels/{ChatAlliance,ChatClan,ChatGeneral,ChatParty,ChatShout,ChatWhisper}.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/Say2.java`, `mobius-il:java/org/l2jmobius/gameserver/network/enums/ChatType.java`, `mobius-il:dist/game/data/scripts/handlers/chat/channels/*.java`; also: `mobius-il:dist/game/data/scripts/handlers/chat/channels/{ChatAlliance,ChatClan,ChatGeneral,ChatParty,ChatShout,ChatWhisper}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/Say2.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/enums/ChatType.java`, `mobius-ess:dist/game/data/scripts/handlers/chat/channels/*.java`; also: `mobius-ess:dist/game/data/scripts/handlers/chat/channels/{ChatAlliance,ChatClan,ChatGeneral,ChatParty,ChatShout,ChatWhisper}.java`

Packets

| Chronicle | Client → server | Server → client    |
| --------- | --------------- | ------------------ |
| Interlude | `Say2` `38`     | `CreatureSay` `4A` |
| High Five | `Say2` `49`     | `CreatureSay` `4A` |
| Essence   | `Say2` `49`     | `CreatureSay` `4A` |

Notes: `Say2` differs between IL and Essence by 57 lines (world/universal handling).
Known fixes: `l2j-server@747bda5b` (2007-10-02, missing-check) — Another fix for chat in jail (check if receiver is). Thanks kerberos_20; `l2j-server@42f91b34` (2010-05-24, crash-npe) — Festival NPE fix and update for CreatureSay packet

### chat.world-chat — World chat

ID: `chat.world-chat` · Shared: **chronicle-only**

A global channel with a per-message cost (world chat points / counter) and cooldown; Essence shows the remaining count in the client.
Sub-features: points counter · cooldown · level limit.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                    |
| --------- | ------- | ------------------------------------ | --------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen           | —                                                                                             |
| High Five | no      | nx-l2, l2j-server, sunrise, eternity | no `ChatWorld` in any public HF build                                                         |
| Essence   | yes     | mobius-ess                           | `ChatWorld` handler, `ChatType.WORLD` (25), effect `WorldChatPoints`, packet `ExWorldChatCnt` |

Entry points

- `mobius-ess:dist/game/data/scripts/handlers/chat/channels/ChatWorld.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExWorldChatCnt.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/enums/ChatType.java`

Packets

| Chronicle | Client → server       | Server → client                                |
| --------- | --------------------- | ---------------------------------------------- |
| Essence   | `Say2` `49` (type 25) | `CreatureSay` `4A`, `ExWorldChatCnt` `FE:0176` |

Notes: —

### chat.filter — Chat filters

ID: `chat.filter` · Shared: **partial**

Words from `nx-l2:chronicles/high-five/datapack/config/chatfilter.txt` are masked in chat messages by a filter hook in `Say2`; a plugin event `ChatFilterReturn` lets scripts replace
or block text; the config picks the replacement characters.
Sub-features: word filter file · replacement chars · event hook.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                          |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `mobius-il:dist/game/config/chatfilter.txt` + `ChatFilterReturn` event hook (Mobius IL)                                                                                             |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | baseline (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/Say2.java:199-207`, `nx-l2:chronicles/high-five/datapack/config/chatfilter.txt`) |
| Essence   | yes     | mobius-ess                                    | same word list file `mobius-ess:dist/game/config/chatfilter.txt`; the event hook was not compared line by line                                                                      |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/Say2.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/events/returns/ChatFilterReturn.java`, `nx-l2:chronicles/high-five/datapack/config/chatfilter.txt`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/Say2.java`, `mobius-il:dist/game/config/chatfilter.txt`; Mobius Essence: `mobius-ess:dist/game/config/chatfilter.txt`

Packets

| Chronicle | Client → server | Server → client    |
| --------- | --------------- | ------------------ |
| Interlude | `Say2` `38`     | `CreatureSay` `4A` |
| High Five | `Say2` `49`     | `CreatureSay` `4A` |
| Essence   | `Say2` `49`     | `CreatureSay` `4A` |

Notes: —
Known fixes: `l2j-server@747bda5b` (2007-10-02, missing-check) — Another fix for chat in jail (check if receiver is). Thanks kerberos_20; `l2jfrozen@764b3beb` (2011-04-04, missing-check) — Fixes over Trade Chat and over Restricted Olympiad items.

### chat.moderation — Chat moderation

ID: `chat.moderation` · Shared: **partial**

Mobius custom: staff or trusted players ban chat of a player from inside the game by a voiced command, with an optional config limiting duration and channels.
Sub-features: in-game chat ban command · moderation config · punishment handler.

| Chronicle | Present | Builds (evidence)                                       | Difference                                                                                 |
| --------- | ------- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Interlude | differs | mobius-il (custom), acis, l2jfrozen (chat-admin voiced) | `Custom/ChatModeration.ini`, voiced `ChatAdmin`                                            |
| High Five | differs | nx-l2 (custom), eternity (`ChatAdmin` voiced)           | `nx-l2:chronicles/high-five/datapack/config/Custom/ChatModeration.ini`: `ChatAdmin = True` |
| Essence   | differs | mobius-ess (custom)                                     | `Custom/ChatModeration.ini`; punishment handler `ChatBanHandler`                           |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/ChatAdmin.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/ChatModeration.ini`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/GeneralConfig.java` (`BAN_CHAT_CHANNELS`)
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/voiced/ChatAdmin.java`, `mobius-il:java/org/l2jmobius/gameserver/config/custom/ChatModerationConfig.java`; Mobius Essence: `mobius-ess:dist/game/data/scripts/handlers/chat/commands/voiced/ChatAdmin.java`

Packets

| Chronicle | Client → server             | Server → client    |
| --------- | --------------------------- | ------------------ |
| Interlude | `Say2` `38` (voiced `.cmd`) | `CreatureSay` `4A` |
| High Five | `Say2` `49` (voiced `.cmd`) | `CreatureSay` `4A` |
| Essence   | `Say2` `49` (voiced `.cmd`) | `CreatureSay` `4A` |

Notes: it is a Mobius custom feature, not retail behaviour; aCis and L2jFrozen carry a `ChatAdmin` voiced command of the same name (matched by file name only).
Known fixes: `mobius@217c85ee` (2018-05-06, other) — Chat ban command fixed and upgraded.

### chat.commands — Chat commands

ID: `chat.commands` · Shared: **partial**

Text commands typed in chat: user commands (`/loc`, `/time`, `/unstuck`, `/partyinfo`, `/clanpenalty`, `/clanwarlist`…), voiced commands (`.cmd`)
and admin commands (`//cmd`, owned by the admin domain). A registry dispatches by command id or name.
Sub-features: user-command registry · voiced-command registry · bypass user commands.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                    |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | user commands: ChannelDelete/Info/Leave, ClanPenalty, ClanWarsList, Dismount, Loc, Mount, OlympiadStat, PartyInfo, SiegeStatus, Time, Unstuck |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | 13 + `InstanceZone`, `MyBirthday` = 15 user commands                                                                                          |
| Essence   | yes     | mobius-ess                                    | 15 user commands as in HF; no voiced `AutoPlay` (present in IL and HF)                                                                        |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/{UserCommandHandler,VoicedCommandHandler}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/{user,voiced}/*.java`
- Mobius IL / Essence: same layout under `mobius-il:dist/game/data/scripts` / `mobius-ess:dist/game/data/scripts`

Packets

| Chronicle | Client → server                            | Server → client                          |
| --------- | ------------------------------------------ | ---------------------------------------- |
| Interlude | `BypassUserCmd` `AA`, `Say2` `38` (voiced) | `CreatureSay` `4A`, `SystemMessage` `64` |
| High Five | `BypassUserCmd` `B3`, `Say2` `49` (voiced) | `CreatureSay` `4A`, `SystemMessage` `62` |
| Essence   | `BypassUserCmd` `B3`, `Say2` `49` (voiced) | `CreatureSay` `4A`, `SystemMessage` `62` |

Notes: voiced commands of other domains (Banking, Premium, Wedding) are listed in their features.

### chat.background — Chat background (Essence)

ID: `chat.background` · Shared: **chronicle-only**

The player chooses a background for the chat window from a list; the server stores the choice and returns the list.
Sub-features: set background · list.

| Chronicle | Present | Builds (evidence) | Difference               |
| --------- | ------- | ----------------- | ------------------------ |
| Interlude | no      | mobius-il, acis   | —                        |
| High Five | no      | nx-l2             | —                        |
| Essence   | yes     | mobius-ess        | package `chatbackground` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/chatbackground/ExChatBackgroundSetting.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/chatbackground/{ExChatBackgroundList,ExChatBackGroundSettingNotification}.java`

Packets

| Chronicle | Client → server                                     | Server → client                                                                   |
| --------- | --------------------------------------------------- | --------------------------------------------------------------------------------- |
| Essence   | `ExChatBackgroundSetting` (not in the opcode table) | `ExChatBackgroundList` `FE:03B7`, `ExChatBackGroundSettingNotification` `FE:03B6` |

Notes: `ExChatBackgroundList` and `ExChatBackGroundSettingNotification` are written with the opcodes of `ExCharacterStyleList` and `ExCharacterStyleUseItem` (they share the enum constants); the client class `ExChatBackgroundSetting` is not registered in the opcode table.
