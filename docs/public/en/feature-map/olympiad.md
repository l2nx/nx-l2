# Olympiad

Olympiad cycle, registration, matches, observation, heroes and rankings.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### olympiad.cycle — Olympiad cycle

ID: `olympiad.cycle` · Shared: **partial**

The Grand Olympiad runs in periods: participants get start points and weekly points, earn and lose points in matches;
at period end a validation phase picks heroes, converts points to rewards (Olympiad tokens / marks) and resets.
Sub-features: competition period (start hour, length, days) · validation period · start/weekly points · rank points ·
point-to-reward exchange · period length (month / custom).

| Chronicle | Present | Builds (evidence)                                                 | Difference                                                                                                                                                    |
| --------- | ------- | ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server, hellas, ares-melnkovmaxim | baseline: monthly, 18:00 +6 h daily, start 18 / weekly 3 points, GP per point                                                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                              | start 10 / weekly 10, rank 1–5 bonus points, min matches for points, weekly match caps (total/classed/non-classed/team)                                       |
| Essence   | differs | mobius-ess                                                        | Saturday–Sunday only, 20:00 +4 h, min 20 participants, Olympiad marks per point, win/lose rewards, optional hidden names, separate weapon/armor enchant limit |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/olympiad/Olympiad.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/OlympiadConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Olympiad.ini`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/olympiad/Olympiad.java` (1802 lines), `mobius-il:dist/game/config/Olympiad.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/olympiad/Olympiad.java`, `mobius-ess:dist/game/config/Olympiad.ini`, `mobius-ess:dist/game/data/scripts/handlers/dailymissions/OlympiadDailyMissionHandler.java`

Packets

| Chronicle | Client → server        | Server → client                                          |
| --------- | ---------------------- | -------------------------------------------------------- |
| Interlude | — (manager NPC dialog) | —                                                        |
| High Five | — (manager NPC dialog) | —                                                        |
| Essence   | `OlympiadUI` `D0:01A1` | `ExOlympiadInfo` `FE:01FB`, `ExOlympiadRecord` `FE:01FC` |

Notes: all three Mobius keep `OlympiadPeriod = MONTH`; Essence drops `OlympiadUseCustomPeriodSettings`.
Known fixes: `l2j-server@cee094aa` (2007-08-23, crash-npe) — Many logout/crash NPE checks for olympiad. Untested

### olympiad.registration — Registration and matchmaking

ID: `olympiad.registration` · Shared: **partial**

Players register through the Olympiad manager for a competition type and wait in a queue; the game manager forms
matches when enough participants are queued.
Sub-features: classed queue · non-classed queue · team (3×3) queue · unregister · queue size display · Essence matchmaking UI.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                       |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline: classed and non-classed via manager NPC                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise | + team (3×3) competition (`CompetitionType.TEAMS`)                                                               |
| Essence   | differs | mobius-ess                 | client matchmaking button (`OlympiadMatchMaking`) registers non-classed only; manager is a script (`OlyManager`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/olympiad/OlympiadManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/olympiad/CompetitionType.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/OlympiadManagerLink.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/OlympiadManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/olympiad/OlympiadManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/OlympiadManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/olympiad/{OlympiadMatchMaking,OlympiadMatchMakingCancel}.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/olympiad/OlympiadManager.java`, `mobius-ess:dist/game/data/scripts/ai/others/OlyManager/OlyManager.java`

Packets

| Chronicle | Client → server                                                        | Server → client                         |
| --------- | ---------------------------------------------------------------------- | --------------------------------------- |
| Interlude | — (NPC dialog)                                                         | —                                       |
| High Five | — (NPC dialog)                                                         | —                                       |
| Essence   | `OlympiadMatchMaking` `D0:0178`, `OlympiadMatchMakingCancel` `D0:0179` | `ExOlympiadMatchMakingResult` `FE:0222` |

Notes: the aCis fork by banzzaj adds a dedicated `OlympiadManagerNpc` instance.
Known fixes: `l2j-server@b69e5b20` (2009-03-26, other) — Fix Olympiad Stadium bug. Thx Pompinis :).

### olympiad.match — Olympiad match

ID: `olympiad.match` · Shared: **partial**

Matched players are teleported to a stadium (instance), buffs and summons are cleaned, a countdown runs, they fight
under olympiad rules (restricted items, enchant limit), the winner takes points from the loser, and both return.
Sub-features: stadium allocation · preparation/countdown · item and enchant restrictions · fight · result and points ·
announcements · Essence pre-fight buffer.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                       |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline: single `OlympiadGame` + `OlympiadStadium`                                                              |
| High Five | yes     | nx-l2, l2j-server, sunrise | game hierarchy (`AbstractOlympiadGame` → Normal → Classed/NonClassed/Teams), arena instances, `OlympiadMaxBuffs` |
| Essence   | differs | mobius-ess                 | HF hierarchy without Teams; `OlyBuffer` NPC; `OlympiadMode`                                                      |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/olympiad/{AbstractOlympiadGame,OlympiadGameNormal,OlympiadGameClassed,OlympiadGameNonClassed,OlympiadGameTeams,OlympiadGameTask,OlympiadGameManager}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/zone/type/OlympiadStadiumZone.java`, `nx-l2:chronicles/high-five/datapack/data/instances/Olympiad/*.xml`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/olympiad/{OlympiadGame,OlympiadGameTask,OlympiadStadium}.java`, `mobius-il:dist/game/data/zones/olympiad_stadium.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/olympiad/OlympiadGameNormal.java`, `mobius-ess:dist/game/data/scripts/ai/others/OlyBuffer/OlyBuffer.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                                                         |
| --------- | --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `ExOlympiadMode` `FE:2B`, `ExOlympiadUserInfo` `FE:29`, `ExOlympiadSpelledInfo` `FE:2A`, `ExOlympiadMatchEnd` `FE:2C`                                                                   |
| High Five | —               | `ExOlympiadMode` `FE:7C`, `ExOlympiadUserInfo` `FE:7A`, `ExOlympiadSpelledInfo` `FE:7B`, `ExOlympiadMatchEnd` `FE:2D`, `ExOlympiadMatchResult` `FE:D4`                                  |
| Essence   | —               | `ExOlympiadMode` `FE:7D`, `ExOlympiadUserInfo` `FE:7B`, `ExOlympiadSpelledInfo` `FE:7C`, `ExOlympiadMatchEnd` `FE:2D`, `ExOlympiadMatchResult` `FE:D6`, `ExOlympiadMatchInfo` `FE:01FD` |

Notes: L2jOrion (Interlude) backports the HF game hierarchy.
Known fixes: `l2j-server@78a1e04d` (2009-05-18, crash-npe) — Unhardcoding htmls from L2OlympiadMangerInstance, also fix client crash. Inspired by Charus, tnx; `l2j-server@a02b835d` (2010-05-19, crash-npe) — Fix catched NPE not allowing skill reuse times to be cleared on the start of oly fight.

### olympiad.observation — Match observation

ID: `olympiad.observation` · Shared: **partial**

A player opens the list of running matches and enters observer mode over a stadium, switching between arenas,
then returns to the original location.
Sub-features: match list · enter/leave observer mode · switch arena.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                                              |
| --------- | ------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2kt-cozmin91-kotlin | baseline: from the manager NPC, `ObservationEnter`/`ObservationExit`                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise                       | `OlympiadObservation` bypass, `ExOlympiadMatchList` with refresh                                                        |
| Essence   | yes     | mobius-ess                                       | same as HF (packets moved to the `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/olympiad/` subpackage) |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/OlympiadObservation.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestOlympiadMatchList,RequestExOlympiadMatchListRefresh,RequestOlympiadObserverEnd}.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/{RequestOlympiadMatchList,RequestOlympiadObserverEnd}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/olympiad/{RequestOlympiadMatchList,RequestExOlympiadMatchListRefresh,RequestOlympiadObserverEnd}.java`

Packets

| Chronicle | Client → server                                                                                                                              | Server → client                                                                 |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| Interlude | `RequestOlympiadMatchList` `D0:13`, `RequestOlympiadObserverEnd` `D0:12`, `ObserverReturn` `B8`                                              | `ObservationEnter` `DF`, `ObservationExit` `E0`                                 |
| High Five | `RequestOlympiadMatchList` `D0:2E`, `RequestExOlympiadMatchListRefresh` `D0:88`, `RequestOlympiadObserverEnd` `D0:29`, `ObserverReturn` `C1` | `ExOlympiadMatchList` `FE:D4`, `ObservationMode` `EB`, `ObservationReturn` `EC` |
| Essence   | `RequestOlympiadMatchList` `D0:2E`, `RequestExOlympiadMatchListRefresh` `D0:86`, `RequestOlympiadObserverEnd` `D0:29`, `ObserverReturn` `C1` | `ExOlympiadMatchList` `FE:D6`, `ObservationMode` `EB`, `ObservationReturn` `EC` |

Notes: the observer-mode packets are shared with castle siege observation (broadcasting tower).

### olympiad.hero — Heroes

ID: `olympiad.hero` · Shared: **partial**

At period end the best participant of each class becomes hero: hero skills, hero weapons (claimed at the Monument of
Heroes), hero aura and hero voice chat, hero diary and fight history, hero message; the status lasts one period.
Sub-features: hero selection · hero skills and weapons · Monument of Heroes · hero diary/fights · hero words ·
hero voice chat · Essence legend info.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                  |
| --------- | ------- | -------------------------------------- | ------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server | baseline (Mobius IL already has diary/fights)                                               |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity   | same                                                                                        |
| Essence   | differs | mobius-ess                             | + hero/legend info window (`RequestOlympiadHeroAndLegendInfo`), `TRUE_HERO` user-info block |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/olympiad/Hero.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/MonumentOfHeroes/MonumentOfHeroes.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/channels/ChatHeroVoice.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestWriteHeroWords.java`, `nx-l2:chronicles/high-five/datapack/data/stats/players/skillTrees/heroSkillTree.xml`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/olympiad/Hero.java`, `mobius-il:java/org/l2jmobius/gameserver/data/HeroSkillTable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/olympiad/Hero.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/RequestOlympiadHeroAndLegendInfo.java`

Packets

| Chronicle | Client → server                                                               | Server → client                                                                                                                                                            |
| --------- | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestWriteHeroWords` `D0:0C`                                               | `ExHeroList` `FE:23`                                                                                                                                                       |
| High Five | `RequestWriteHeroWords` `D0:05`                                               | `ExHeroList` `FE:79`                                                                                                                                                       |
| Essence   | `RequestWriteHeroWords` `D0:05`, `RequestOlympiadHeroAndLegendInfo` `D0:0196` | `ExHeroList` `FE:7A`, `ExOlympiadHeroAndLegendInfo` `FE:0243`; `UserInfo` `32` masks `UserInfoType.SOCIAL` (bit `0x10`) (hero flag), `UserInfoType.TRUE_HERO` (bit `0x16`) |

Notes: aCis custom forks (L2jMega) add timed hero items (`Hero24h`, `HeroDay`, `HeroEternal`) and an admin hero command.
Known fixes: `l2j-server@bbb3b5e7` (2011-11-01, crash-npe) — BETA: NPE fix in Hero diary and possible memory leak thanks JIV; `mobius@0ecd2142` (2020-11-05, crash-npe) — Fixed probable Hero diaryEntry NPE

### olympiad.rankings — Olympiad rankings and statistics

ID: `olympiad.rankings` · Shared: **partial**

Players see their own Olympiad record (wins, losses, points) and, in Essence, ranking lists by class and server.
Sub-features: own statistics (`/olympiadstat`) · my ranking · ranking list.

| Chronicle | Present | Builds (evidence)                                | Difference                                                 |
| --------- | ------- | ------------------------------------------------ | ---------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen, l2kt-cozmin91-kotlin | own statistics command only                                |
| High Five | differs | nx-l2, l2j-server                                | own statistics command only                                |
| Essence   | yes     | mobius-ess                                       | full ranking UI: my ranking, ranking list, hero and legend |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/OlympiadStat.java`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/user/OlympiadStat.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/{RequestOlympiadMyRankingInfo,RequestOlympiadRankingInfo}.java`

Packets

| Chronicle | Client → server                                                                  | Server → client                                                        |
| --------- | -------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| Interlude | — (user command)                                                                 | —                                                                      |
| High Five | — (user command)                                                                 | —                                                                      |
| Essence   | `RequestOlympiadMyRankingInfo` `D0:0194`, `RequestOlympiadRankingInfo` `D0:0195` | `ExOlympiadMyRankingInfo` `FE:0241`, `ExOlympiadRankingInfo` `FE:0242` |

Notes: Essence PvP ranking (`RequestPvpRankingList`) belongs to the `ranking` domain.

### olympiad.anti-feed — Olympiad anti-feed

ID: `olympiad.anti-feed` · Shared: **chronicle-only**

To stop players from arranging fixed matches, the server masks the opponent's appearance on the Olympiad: race, gender,
class, equipment and recommendations shown to others are replaced by configured values.
Sub-features: race/gender/class masking · equipment visual masking · recommendations masking.

| Chronicle | Present | Builds (evidence) | Difference                                                                                                |
| --------- | ------- | ----------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis   | —                                                                                                         |
| High Five | yes     | eternity          | baseline; `core/dist/game/config/mods/olympiadAntiFeed.ini` in L2jEternityWorld; not in Mobius HF / nx-l2 |
| Essence   | no      | mobius-ess        | `OlympiadHideNames` hides names only (see `olympiad.cycle`)                                               |

Entry points

- `eternity:core/dist/game/config/mods/olympiadAntiFeed.ini`

Packets

| Chronicle | Client → server | Server → client                                          |
| --------- | --------------- | -------------------------------------------------------- |
| High Five | —               | `CharInfo` `31` (masked race / class / paperdoll fields) |

Notes: Eternity family only; dual-box limits for the Olympiad in Mobius live in `AntiFeedManager` (`pvp.anti-feed`).
