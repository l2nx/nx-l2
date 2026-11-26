# Mini-games

Self-contained games inside the world: lottery, races, arenas, dice and Essence games.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### minigame.lottery — Lottery

ID: `minigame.lottery` · Shared: **partial (IL+HF)**

Periodic lottery: players buy a ticket by picking numbers from an NPC dialog, the manager draws weekly, pays by the number of matches (jackpot plus lower tiers), rolls the unclaimed prize into the next round and lets winners claim from the NPC. Ticket price, prize base and per-match rates are config values. Present in Interlude and High Five with the same flow; removed from Essence.
Sub-features: ticket purchase · weekly draw · prize tiers · rollover · claim at NPC.

| Chronicle | Present | Builds (evidence)          | Difference                                      |
| --------- | ------- | -------------------------- | ----------------------------------------------- |
| Interlude | yes     | acis, l2jfrozen, mobius-il | retail feature                                  |
| High Five | yes     | nx-l2, l2j-server          | baseline (`managers/games/LotteryManager.java`) |
| Essence   | no      | mobius-ess                 | —                                               |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/games/LotteryManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/games/LotteryManager.java`

Packets: none dedicated (NPC HTML dialogs; item `Lottery Ticket`).

Notes: config keys `AllowLottery`, `AltLotteryPrize`, `AltLotteryTicketPrice`, `AltLottery5NumberRate` (General.ini) in both IL and HF.

### minigame.monster-race — Monster race

ID: `minigame.monster-race` · Shared: **partial**

Derby track: an NPC runs races of eight monsters, players buy tickets and bet, the manager simulates the race with periodic info packets, and winners are paid. A derby-track zone controls spectator teleport. Mobius Essence still carries the manager and the race-info packet.
Sub-features: race simulation · bet tickets · race info packet · spectator zone teleport.

| Chronicle | Present | Builds (evidence)          | Difference                                       |
| --------- | ------- | -------------------------- | ------------------------------------------------ |
| Interlude | yes     | acis, l2jfrozen, mobius-il | retail feature                                   |
| High Five | yes     | nx-l2, l2j-server          | baseline                                         |
| Essence   | differs | mobius-ess                 | manager and packet present; no HTML flow checked |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/games/MonsterRaceManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/games/MonsterRaceManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/games/MonsterRaceManager.java`

Packets

| Chronicle | Client → server | Server → client                            |
| --------- | --------------- | ------------------------------------------ |
| Interlude | —               | `MON_RACE_INFO` `DD`, `DELETE_OBJECT` `12` |
| High Five | —               | `MON_RACE_INFO` `E3`, `DELETE_OBJECT` `08` |
| Essence   | —               | `MON_RACE_INFO` `E3`, `DELETE_OBJECT` `08` |

Notes: Essence presence is by code only; whether any NPC dialog calls it was not traced.

### minigame.krateis-cube — Krateis' Cube

ID: `minigame.krateis-cube` · Shared: **chronicle-only**

Gracia PvP mini-game for levels 70+ in a dedicated cube: players register at an NPC, wait for a match, fight in one of three arenas for points, and receive end-of-match rewards; the client shows a ranking of the cube.
Sub-features: registration · arenas · waiting room · points · rewards · ranking window.

| Chronicle | Present | Builds (evidence)                    | Difference |
| --------- | ------- | ------------------------------------ | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il           | —          |
| High Five | yes     | nx-l2, l2j-server, sunrise, advanced | baseline   |
| Essence   | no      | mobius-ess                           | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/games/KrateisCubeManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/krateisCube/{KrateiArena.java,KrateiCubePlayer.java,KrateisReward.java,KrateiMsgType.java}`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/FantasyIsle/KrateisCube.java`

Packets

| Chronicle | Client → server                                                                                   | Server → client                                                                                         |
| --------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| High Five | `REQUEST_EX_START_SHOW_CRATAE_CUBE_RANK` `D0:54`, `REQUEST_EX_STOP_SHOW_CRATAE_CUBE_RANK` `D0:55` | `EX_PVP_MATCH_CC_RECORD` `FE:89`, `EX_PVP_MATCH_CC_MY_RECORD` `FE:8A`, `EX_PVP_MATCH_CC_RETIRE` `FE:8B` |

Notes: the packet names for the ranking use the PvP-match record family.

### minigame.underground-coliseum — Underground Coliseum

ID: `minigame.underground-coliseum` · Shared: **chronicle-only**

Gracia team-versus-team arena: parties register at the coliseum NPC, the scheduler starts battle periods by a recurrence rule, arenas run rounds with respawn points, a best-team record is kept, and winners get item rewards; has its own config file.
Sub-features: registration · waiting · arenas · scheduled battle periods · best team · rewards · announcements.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline   |
| Essence   | no      | mobius-ess                 | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/games/UndergroundColiseumManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/undergroundColiseum/{UCArena.java,UCTeam.java,UCWaiting.java,UCReward.java,UCBestTeam.java,UCPoint.java,UCRunningTask.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/UndergroundColiseumConfig.java`, `nx-l2:chronicles/high-five/datapack/config/UndergroundColiseum.ini`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExPVPMatchRecord.java`

Packets

| Chronicle | Client → server | Server → client                                         |
| --------- | --------------- | ------------------------------------------------------- |
| High Five | —               | `ExPVPMatchRecord` `FE:7E`, `ExPVPMatchUserDie` `FE:7F` |

Notes: schedule key is `BattleStartRrule`, `BattlePeriod`, `AllowAnnouncements`, `PartySize` in the HF config.

### minigame.block-checker — Handy's Block Checker

ID: `minigame.block-checker` · Shared: **chronicle-only**

Gracia block-checker arena: two teams (red / blue) of up to 6 vote and enter via the Handy's Block Checker NPC, collect blocks while a timer runs; scores decide rewards. The client shows team lists and score changes through the cube-game packets.
Sub-features: team vote / enter · block spawn · points · reward.

| Chronicle | Present | Builds (evidence)                        | Difference |
| --------- | ------- | ---------------------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il               | —          |
| High Five | yes     | nx-l2, l2j-server, sunrise               | baseline   |
| Essence   | no      | mobius-ess (packet ids kept, no manager) | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/HandysBlockCheckerManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/games/BlockCheckerManager.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/FantasyIsle/HandysBlockCheckerEvent.java`

Packets

| Chronicle | Client → server                                                                         | Server → client                                                 |
| --------- | --------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| High Five | `REQUEST_EX_CUBE_GAME_CHANGE_TEAM` `D0:5A`, `REQUEST_EX_CUBE_GAME_READY_ANSWER` `D0:5C` | `EX_BLOCK_UP_SET_LIST` `FE:97`, `EX_BLOCK_UP_SET_STATE` `FE:98` |

Notes: `EnableBlockCheckerEvent`, `BlockCheckerMinTeamMembers` in the config.

Known fixes: `l2j-server@a9ba7a24cd` (2014-01-22, crash-npe) — BETA: Fixed an `IndexOutOfBoundsException` in `HandysBlockCheckerManager`.

### minigame.rolling-dice — Rolling dice

ID: `minigame.rolling-dice` · Shared: **engine**

Player uses a dice item: the server rolls a random number, broadcasts the result as a `Dice` packet to nearby players and a system message, with a short use delay.
Sub-features: roll · nearby broadcast · item delay.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | yes     | acis, l2jfrozen, mobius-il | retail     |
| High Five | yes     | nx-l2, l2j-server          | same       |
| Essence   | yes     | mobius-ess                 | same       |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/RollingDice.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/Dice.java`
- `mobius-ess:dist/game/data/scripts/handlers/items/RollingDice.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | `DICE` `D4`     |
| High Five | —               | `DICE` `DA`     |
| Essence   | —               | `DICE` `DA`     |

Notes: Shared `engine`: identical behaviour in all three Mobius versions and in aCis / L2jFrozen.

Known fixes: `l2j-server-dp@f2b7a5a4af` (2009-04-17, other) — JAVA: Fix for Ticket #3239 DP-Side by _DS_; `l2j-server@c3ff8c3ffd` (2007-05-06, other) — Fix for #757, spamming dice. By Durgus.

### minigame.monastery-minigame — Monastery torch minigame

ID: `minigame.monastery-minigame` · Shared: **chronicle-only**

AI-script mini-game in the Monastery of Silence (Seven Signs line): NPC Sumiel starts a torch-lighting puzzle in one of two rooms, the player has three attempts with a timer, burners and treasure boxes spawn per room, and a successful run teleports the player to a reward location.
Sub-features: room allocation · torch / burner interaction · 3 attempts · treasure box reward.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | yes     | nx-l2                      | baseline   |
| Essence   | no      | mobius-ess                 | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/Minigame/Minigame.java`

Packets: none.

Notes: NPC ids Sumiel 32758, burner 18913, treasure box 18911.

Known fixes: `l2j-server-dp@fd793a35b2` (2017-10-05, crash-npe) — Fixed NPE on Minigame AI.

### minigame.br-scoreboard — BR mini-game score table

ID: `minigame.br-scoreboard` · Shared: **partial (HF+Ess)**

Server-side score table for a client-side mini-game: the client sends `RequestBrMiniGameInsertScore` after a game and loads the table with `RequestBrMiniGameLoadScores`; the server keeps a per-character score list and answers with the top scores. Mobius HF has the packet ids but no handler.

| Chronicle | Present | Builds (evidence)            | Difference |
| --------- | ------- | ---------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il   | —          |
| High Five | differs | nx-l2 (packet ids only)      | no handler |
| Essence   | differs | mobius-ess (packet ids only) | no handler |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/ClientPackets.java` (packet constants only)

Packets

| Chronicle | Client → server                                                                         | Server → client                      |
| --------- | --------------------------------------------------------------------------------------- | ------------------------------------ |
| High Five | `REQUEST_BR_MINI_GAME_INSERT_SCORE` `D0:8F`, `REQUEST_BR_MINI_GAME_LOAD_SCORES` `D0:8E` | `EX_BR_MINIGAME_LOAD_SCORES` `FE:DD` |

Notes: distinct from `minigame.monastery-minigame` (the NPC torch puzzle).

### minigame.lucky-game — Lucky Game

ID: `minigame.lucky-game` · Shared: **chronicle-only**

Essence ticket-based prize draw: the player opens the Lucky Game window, spends a ticket item for a draw from a typed reward table (normal and VIP games), bonus rewards accumulate with plays. Data is in `LuckyGameData.xml`.
Sub-features: start info · play · result · VIP info / item list / bonus.

| Chronicle | Present | Builds (evidence)          | Difference                          |
| --------- | ------- | -------------------------- | ----------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —                                   |
| High Five | no      | nx-l2, l2j-server          | no handler in the HF builds checked |
| Essence   | yes     | mobius-ess                 | baseline                            |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/LuckyGameData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/luckygame/RequestLuckyGamePlay.java`, `mobius-ess:dist/game/data/LuckyGameData.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                             | Server → client                                                                                                                                                                     |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_START_LUCKY_GAME` `D0:F1`, `EX_BETTING_LUCKY_GAME` `D0:F2`, `EX_REQUEST_LUCKY_GAME_INFO` `D0:010B`, `EX_REQUEST_LUCKY_GAME_ITEMLIST` `D0:010C`, `EX_REQUEST_LUCKY_GAME_BONUS` `D0:010D` | `EX_START_LUCKY_GAME` `FE:0161`, `EX_BETTING_LUCKY_GAME_RESULT` `FE:0162`, `EX_LUCKY_GAME_INFO` `FE:0187`, `EX_LUCKY_GAME_ITEMLIST` `FE:0188`, `EX_LUCKY_GAME_BONUS_INFO` `FE:0189` |

Notes: —

Known fixes: `mobius@5646221b7d` (2021-11-24, other) — Fixed RequestLuckyGamePlay inventory visual issue.

### minigame.mable-game — Mable game

ID: `minigame.mable-game` · Shared: **chronicle-only**

Essence board game: the player rolls a die and moves a token on a board with prison, skill and reward cells; a window shows player state, results, rewards and a nested mini-game; roll count resets.
Sub-features: open / close · roll dice · move · prison · rewards · skill info · reset.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/MableGameData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/mablegame/ExRequestMableGameRollDice.java`, `mobius-ess:dist/game/data/MableGameData.xml`

Packets

| Chronicle | Client → server                                                                                                                                                           | Server → client                                                                                                                                                                                                                                                                                                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_MABLE_GAME_OPEN` `D0:01BA`, `EX_MABLE_GAME_ROLL_DICE` `D0:01BB`, `EX_MABLE_GAME_POPUP_OK` `D0:01BC`, `EX_MABLE_GAME_RESET` `D0:01BD`, `EX_MABLE_GAME_CLOSE` `D0:01BE` | `EX_MABLE_GAME_SHOW_PLAYER_STATE` `FE:0268`, `EX_MABLE_GAME_DICE_RESULT` `FE:0269`, `EX_MABLE_GAME_MOVE` `FE:026A`, `EX_MABLE_GAME_PRISON` `FE:026B`, `EX_MABLE_GAME_REWARD_ITEM` `FE:026C`, `EX_MABLE_GAME_SKILL_INFO` `FE:026D`, `EX_MABLE_GAME_MINIGAME` `FE:026E`, `EX_MABLE_GAME_PLAY_UNABLE` `FE:026F`, `EX_MABLE_GAME_UI_LAUNCHER` `FE:0270`, `EX_MABLE_GAME_ROLL_COUNT_RESET` `FE:0271` |

Notes: —

### minigame.unique-gacha — Unique gacha

ID: `minigame.unique-gacha` · Shared: **chronicle-only**

Essence event gacha: the player opens the gacha window, plays with event coins for ranked prizes (A / B / C ranks), gets a history list and a bag of won items to claim; a sidebar shows event info.
Sub-features: open · game · history · inventory claim · sidebar info.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/UniqueGachaManager.java`, `mobius-ess:dist/game/data/scripts/events/UniqueGacha/{UniqueGacha.java,config.xml,rewards.xml}`

Packets

| Chronicle | Client → server                                                                                                                                                                                  | Server → client                                                                                              |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| Essence   | `EX_UNIQUE_GACHA_OPEN` `D0:0260`, `EX_UNIQUE_GACHA_GAME` `D0:0261`, `EX_UNIQUE_GACHA_INVEN_ITEM_LIST` `D0:0262`, `EX_UNIQUE_GACHA_INVEN_GET_ITEM` `D0:0263`, `EX_UNIQUE_GACHA_HISTORY` `D0:0264` | `EX_UNIQUE_GACHA_OPEN` `FE:032B`, `EX_UNIQUE_GACHA_GAME` `FE:032C`, `EX_UNIQUE_GACHA_SIDEBAR_INFO` `FE:032A` |

Notes: event window 11 11 2022 - 12 12 2023 in `config.xml`.

### minigame.roulette — Roulette

ID: `minigame.roulette` · Shared: **chronicle-only**

NPC casino roulette: the player bets an amount on a number or colour through a dialog and wins by chance. Not in Mobius; High Five forks ship it as a script event.

| Chronicle | Present | Builds (evidence)          | Difference   |
| --------- | ------- | -------------------------- | ------------ |
| Interlude | no      | acis, l2jfrozen, mobius-il | —            |
| High Five | yes     | advanced, premium          | script event |
| Essence   | no      | mobius-ess                 | —            |

Entry points

- `premium:scripts/scripts/services/Roulette.java`, `premium:world/data/html-en/scripts/services/roulette.htm`
- `advanced:dist/gameserver/data/html-en/scripts/services/roulette.htm`

Packets: none.

Notes: —
