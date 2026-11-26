# Events

Seasonal events, custom mini events, PvP events and event-specific client windows.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### event.engine — Seasonal event engine

ID: `event.engine` · Shared: **partial**

Date-windowed events driven by a per-event `config.xml` (`<event name active="dd MM yyyy-dd MM yyyy">`), read by `LongTimeEvent`: when the window is open the engine spawns the event NPCs, registers the event drop list (item, count range, chance, level range, monster ids) with `EventDropManager`, applies rate bonuses, broadcasts start/end messages and removes everything at the end. Script classes extend `LongTimeEvent` and add only dialog logic.
Sub-features: active-date window · event NPC spawn list · event drop list · start / end announcement · enable shrines flag (Essence).

| Chronicle | Present | Builds (evidence)                    | Difference                                                         |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, l2jfrozen (event scripts) | Mobius IL ports the HF `LongTimeEvent`; Frozen uses per-event Java |
| High Five | yes     | nx-l2, l2j-server                    | baseline                                                           |
| Essence   | yes     | mobius-ess                           | `eventConfig.xsd`, `enableShrines`, multi-year windows             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/script/LongTimeEvent.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/EventDropManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/BrEventRankerList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExBrLoadEventTopRankers.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/script/LongTimeEvent.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/script/LongTimeEvent.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/events/EventShrineManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/BrEventRankerList.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/{ExBrLoadEventTopRankers.java,PopupEventHud.java}`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/appearance/ExCuriousHouseMemberUpdate.java`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ExBrBroadcastEventState,ExBrBuffEventState}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/{ExBrBroadcastEventState,ExBrBuffEventState}.java`

Packets

| Chronicle | Client → server                            | Server → client                                                                                                                                                                         |
| --------- | ------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | `BR_EVENT_RANKER_LIST` `D0:7B`             | `EX_BR_LOAD_EVENT_TOP_RANKERS` `FE:BD`, `ExBrBroadcastEventState` `FE:BC`, `ExBrBuffEventState` `FE:DB`                                                                                 |
| Essence   | `EX_BR_LOAD_EVENT_TOP_RANKERS_REQ` `D0:79` | `EX_BR_LOAD_EVENT_TOP_RANKERS_ACK` `FE:BF`, `EX_CURIOUS_HOUSE_MEMBER_UPDATE` `FE:0124`, `EX_POPUP_EVENT_HUD` `FE:0390`, `ExBrBroadcastEventState` `FE:BE`, `ExBrBuffEventState` `FE:DD` |

Notes: the Mobius IL engine file equals the HF one in structure (backport); do not read it as proof of a distinct Interlude implementation.

Known fixes: `mobius@b6d254a8bc` (2022-12-20, wrong-formula-value) — Reverted LongTimeEvent random heading support and fixed probable issues.; `l2j-server@8b196fbf` (2015-06-20, wrong-formula-value) — Drop period in LongTimeEvents fixed: it didn't consider the dates within the range if they were the same. Extra: it avoids adding global drop if the current time is longer than the drop period end.

### event.seasonal-scripts — Seasonal event catalog

ID: `event.seasonal-scripts` · Shared: **partial**

Per-chronicle catalog of seasonal event scripts (one folder per event, not listed individually). Interlude: Christmas, Heavy Medal, L2 Day. High Five: Character Birthday, Freya Celebration, Gift of Vitality, Heavy Medal, L2 Day, Love Your Gatekeeper, Master of Enchanting, Saving Santa, Valentine, Watermelon Ninja. Essence: 20 folders of Essence-era events (Balthus Festival, Battle With Keber, Black Coupon, Blossom Festival, Chef Monkey, Continued Raid, Cross Event, Defeat the Queen, Eve the Fortune Teller, Fortune Hunters, Golden Wheel, Holiday out of Schedule, Legend of the Water Temple, Leona's Dungeon, Letter Collector, Merry Squashmas, Secret Shop, Squash Event, Unique Gacha, Watermelon Ninja).

| Chronicle | Present | Builds (evidence)        | Difference                        |
| --------- | ------- | ------------------------ | --------------------------------- |
| Interlude | yes     | mobius-il (3), l2jfrozen | smallest set                      |
| High Five | yes     | nx-l2 (10), l2j-server   | baseline                          |
| Essence   | yes     | mobius-ess (20)          | Essence-only events, past windows |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/events/`
- `mobius-il:dist/game/data/scripts/events`
- `mobius-ess:dist/game/data/scripts/events`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ShowXMasSeal.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/SpecialXMas.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/ShowXMasSeal.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ShowXMasSeal.java`

Packets

| Chronicle | Client → server | Server → client     |
| --------- | --------------- | ------------------- |
| Interlude | —               | `ShowXMasSeal` `F2` |
| High Five | —               | `ShowXMasSeal` `F8` |
| Essence   | —               | `ShowXMasSeal` `F8` |

Notes: most Essence windows in `config.xml` lie in the past (2017-2024); only a few run to 2029-2034 (Letter Collector, Legend of the Water Temple, Leona's Dungeon event).

### event.custom-mini-events — Custom mini events

ID: `event.custom-mini-events` · Shared: **partial**

Server-side custom mini events not from retail: Elpies (timed hunt of small NPCs scattered in towns), Rabbits (collect rabbit items), Race (IOP race with checkpoint NPC and prize). Run as scripts with the seasonal config; weekly or admin-triggered.
Sub-features: Elpies · Rabbits · Race.

| Chronicle | Present | Builds (evidence)                  | Difference                |
| --------- | ------- | ---------------------------------- | ------------------------- |
| Interlude | yes     | mobius-il (Elpies, Race)           | Mobius custom, not retail |
| High Five | yes     | nx-l2 (Elpies, Rabbits, Race)      | Mobius custom             |
| Essence   | yes     | mobius-ess (Elpies, Rabbits, Race) | same set as HF            |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/Elpies/`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/Rabbits/`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/Race/`
- `mobius-il:dist/game/data/scripts/custom/events/{Elpies,Race}`
- `mobius-ess:dist/game/data/scripts/custom/events/{Elpies,Rabbits,Race}`

Packets: none.

Notes: custom (non-retail) in all three versions.

### event.quiz — Chat quiz

ID: `event.quiz` · Shared: **chronicle-only**

Chat quiz event: during a configured daily window the server asks questions in chat, players answer, and correct answers earn item rewards (a separate list for the first correct answer and for the others). Players opt out with a voiced command. Configurable question timing, answer time, pause between questions and question removal.
Sub-features: question schedule · answer window · first / other rewards · opt-out command.

| Chronicle | Present | Builds (evidence)          | Difference              |
| --------- | ------- | -------------------------- | ----------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —                       |
| High Five | yes     | advanced                   | baseline (script event) |
| Essence   | no      | mobius-ess                 | —                       |

Entry points

- `advanced:dist/gameserver/data/scripts/events/Viktorina/Viktorina.java`, `advanced:dist/gameserver/config/events/Victorina.properties`

Packets: none.

Notes: disabled by default (`Victorina_Enabled = False`).

### event.pvp-events — PvP events

ID: `event.pvp-events` · Shared: **partial**

Registered team/solo PvP events: registration, teleport to an event map, team split, rounds, scoring, rewards, return to town. Mobius custom ships Capture The Flag, Team vs Team and Deathmatch in all three versions; wider "fight event" frameworks (Last Hero, Korean Style, Lucky Creatures, Treasure Hunt, Monster Attack, Solo/Team PvP zone, Capture The Base, rounds) exist only in some High Five forks.
Sub-features: registration · team balance · arena maps · rounds · buffs / dispel on entry · rewards · AFK kick.

| Chronicle | Present | Builds (evidence)                                 | Difference                                                             |
| --------- | ------- | ------------------------------------------------- | ---------------------------------------------------------------------- |
| Interlude | yes     | mobius-il (CTF, TvT, DM)                          | Mobius custom                                                          |
| High Five | yes     | nx-l2 (CTF, TvT, DM), eternity, advanced, premium | forks add a full multi-event framework (fightclub / FightEventManager) |
| Essence   | yes     | mobius-ess (CTF, TvT, DM)                         | Mobius custom                                                          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/CaptureTheFlag/CtF.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/TeamVsTeam/TvT.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/Deathmatch/Deathmatch.java`
- `mobius-il:dist/game/data/scripts/custom/events/{CaptureTheFlag,TeamVsTeam,Deathmatch}`
- `mobius-ess:dist/game/data/scripts/custom/events/{CaptureTheFlag,TeamVsTeam,Deathmatch}`
- `eternity:core/java/l2e/gameserver/model/entity/events/` (fight events)

Packets: none dedicated (HTML window + voiced/bypass registration).

Notes: forks add tournaments, fight-club and VIP TvT variants (e.g. `fightclubmanager`, `tournament(s)`, `VIPTvT`).

### event.tournament — Tournament

ID: `event.tournament` · Shared: **partial (IL+HF)**

Team tournament: registered teams fight on prepared tournament maps under per-match rules (attack, item use, enchant, resurrection, log-out permissions), observers can watch, and admin and voiced commands manage the schedule. Read by class and directory names, behaviour was not traced. Not part of any Mobius tree.
Sub-features: team registration · tournament maps · per-match permissions · observers · admin and voiced commands.

| Chronicle | Present | Builds (evidence)                                   | Difference          |
| --------- | ------- | --------------------------------------------------- | ------------------- |
| Interlude | yes     | acis-custom-hl4p3x, l2jmega-megapack (custom forks) | custom fork feature |
| High Five | yes     | premium, bh8nexus-l2-retail-engine                  | baseline            |
| Essence   | no      | mobius-ess                                          | —                   |

Entry points

- `premium:java/premium/gameserver/model/entity/tournament/{TournamentTeamsManager.java,TournamentMap.java,TournamentStatus.java}`, `premium:java/premium/gameserver/handler/admincommands/impl/AdminTournament.java`, `premium:java/premium/gameserver/handler/voicecommands/impl/TournamentVoice.java`
- `bh8nexus-l2-retail-engine:l2r/gameserver/model/entity/tournament/{TournamentTeamsManager.java,TournamentMap.java,TournamentStatus.java}`, `bh8nexus-l2-retail-engine:l2r/gameserver/handler/admincommands/impl/AdminTournament.java`
- `acis-custom-hl4p3x:aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/Tournament.java`, `acis-custom-hl4p3x:aCis_gameserver/java/net/sf/l2j/gameserver/model/zone/type/TournamentZone.java`
- `l2jmega-megapack:aCis_gameserver/java/net/sf/l2j/gameserver/events/tournaments/Tournament.java`

Packets: none.

Notes: forks only; the generic fight events are `event.pvp-events`.

### event.gvg — GvG

ID: `event.gvg` · Shared: **partial (HF+Ess)**

Group-versus-group duel challenge: a party leader challenges another group, stakes are escrowed in a custom currency, the fight runs in a dedicated instance, the winner receives the stake and rating points. Public High Five forks implement it as a script event with a boss/instance; the Essence-line fork as a "mass events" GvG engine.
Sub-features: challenge / accept · stake · instance arena · rating.

| Chronicle | Present | Builds (evidence)                    | Difference                                 |
| --------- | ------- | ------------------------------------ | ------------------------------------------ |
| Interlude | no      | acis, l2jfrozen, mobius-il           | —                                          |
| High Five | yes     | advanced, premium (not in Mobius HF) | script `GvG`, boss `GvGBoss`, instance xml |
| Essence   | yes     | l2emu-ess (`massevents/gvg`)         | engine + player classes                    |

Entry points

- `premium:scripts/scripts/events/GvG/GvG.java`, `premium:scripts/scripts/ai/custom/GvGBoss.java`
-  (`dist/gameserver/data/instances/[504] GvG Instance.xml`)
- `l2emu-ess:dp/L2Emu-Essence-DP-542-main/data/scripts/massevents/gvg/L2GvGEngine.java`

Packets: none dedicated.

Notes: Mobius HF has no GvG; the feature is not part of the Mobius baseline.

### event.hitman — Hitman

ID: `event.hitman` · Shared: **chronicle-only**

Bounty board: a player posts a kill order on another character for a payment in currency items; hitmen take the contract, and killing the target pays out; limits on targets per customer, page size and same-team rules.
Sub-features: order posting · target limit · reward currency · announcements · saved target time.

| Chronicle | Present | Builds (evidence)                        | Difference |
| --------- | ------- | ---------------------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il               | —          |
| High Five | yes     | eternity, advanced, premium (not Mobius) | baseline   |
| Essence   | no      | mobius-ess                               | —          |

Entry points

- `eternity:core/java/l2e/gameserver/model/entity/events/Hitman.java`, `eternity:core/java/l2e/gameserver/model/actor/instance/L2HitmanInstance.java`, `eternity:core/dist/game/config/events/hitman_event.ini`
- `premium:scripts/scripts/events/Hitman/Hitman.java`

Packets: none dedicated.

Notes: absent from Mobius.

Known fixes: `hellas@8c53287c74` (2013-10-21, other) — Hitman Bounty * Fixed adena -> any item Id

### event.leprechaun — Leprechaun

ID: `event.leprechaun` · Shared: **chronicle-only**

A leprechaun NPC appears periodically in a region (optionally announced with nick and region); the first player to find and click it gets a configurable reward.
Sub-features: first-spawn delay · respawn interval · announce interval · reward item/count/chance.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | yes     | eternity                   | baseline   |
| Essence   | no      | mobius-ess                 | —          |

Entry points

- `eternity:core/java/l2e/gameserver/model/entity/events/Leprechaun.java`, `eternity:core/dist/game/config/events/leprechaun_event.ini`

Packets: none.

Notes: only one of the builds checked carries it.

### event.lakfi — Lakfi

ID: `event.lakfi` · Shared: **chronicle-only**

Lakfi is a small NPC that spawns for a limited time and "eats" adena given to it (above a minimum) with a chance to drop rewards; public forks implement it as an AI script `LafiLakfi`.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | yes     | advanced, premium          | AI script  |
| Essence   | no      | mobius-ess                 | —          |

Entry points

- `advanced:dist/gameserver/data/scripts/ai/LafiLakfi.java`
- `premium:scripts/scripts/ai/LafiLakfi.java`

Packets: none.

Notes: —

### event.aerial-cleft — Aerial Cleft

ID: `event.aerial-cleft` · Shared: **chronicle-only**

Gracia team battlefield on the Aerial Cleft: two teams, compressors/towers, gates and a cleft teleporter; players enter via the cleft-enter request and the client shows list and state packets. In Mobius HF only the zone data (`cleft.xml`) and the packet ids exist, with no event logic.
Sub-features: team assignment · compressor / tower points · gates · state / list packets.

| Chronicle | Present | Builds (evidence)                                              | Difference                                                                                   |
| --------- | ------- | -------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il                                     | —                                                                                            |
| High Five | differs | nx-l2 (zone data only); L2J Server game, advanced (full event) | nx-l2 has the zone and packet ids only; full event logic in L2J Server and the Eternity line |
| Essence   | no      | mobius-ess (zone data only)                                    | —                                                                                            |

Entry points

- `nx-l2:chronicles/high-five/datapack/data/zones/cleft.xml` (data only)
- `l2j-server:src/main/java/com/l2jserver/gameserver/model/cleft/{CleftMatch.java,CleftTeam.java,CleftParticipant.java,CleftTowerType.java}`, `l2j-server:src/main/java/com/l2jserver/gameserver/network/clientpackets/RequestExCleftEnter.java`

Packets

| Chronicle | Client → server                  | Server → client                                   |
| --------- | -------------------------------- | ------------------------------------------------- |
| High Five | `REQUEST_EX_CLEFT_ENTER` `D0:59` | `EX_CLEFT_LIST` `FE:94`, `EX_CLEFT_STATE` `FE:95` |

Notes: the high-five event class set in L2J Server is a reference for a clean implementation.

### event.balthus-festival — Balthus Festival

ID: `event.balthus-festival` · Shared: **chronicle-only**

Essence token festival: players receive Balthus tokens (daily supply item), spend them on a roll for rewards in tiers with a jackpot, with a consolation item and level limit; the event NPC (Sibi) is spawned in major towns.
Sub-features: token request · reward periods · jackpot broadcast · daily supply.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/BalthusEventManager.java`, `mobius-ess:dist/game/data/scripts/events/BalthusFestival/{BalthusFestival.java,config.xml,rewards.xml}`

Packets

| Chronicle | Client → server                      | Server → client                                                   |
| --------- | ------------------------------------ | ----------------------------------------------------------------- |
| Essence   | `EX_REQUEST_BALTHUS_TOKEN` `D0:011B` | `EX_BALTHUS_EVENT` `FE:01A4`, `EX_BALTHUS_JACKPOT_USER` `FE:01A5` |

Notes: window in `config.xml` is 30 06 2021 - 29 07 2021 (past).

### event.cross-event — Cross Event

ID: `event.cross-event` · Shared: **chronicle-only**

Essence board-like event: the player fills a field by placing items, receives normal and rare rewards, and can reset the board; reward lists are configured per holder (regular and advanced rewards).
Sub-features: data / info request · normal reward · rare reward · reset.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/CrossEventManager.java`, `mobius-ess:dist/game/data/scripts/events/CrossEvent/{CrossEvent.java,CrossEvent.xml,config.xml}`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/crossevent/ExCrossEventNoti.java`

Packets

| Chronicle | Client → server                                                                                                                                                                      | Server → client                 |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------- |
| Essence   | `EX_CROSS_EVENT_DATA` `D0:029B`, `EX_CROSS_EVENT_INFO` `D0:029C`, `EX_CROSS_EVENT_NORMAL_REWARD` `D0:029D`, `EX_CROSS_EVENT_RARE_REWARD` `D0:029E`, `EX_CROSS_EVENT_RESET` `D0:029F` | `EX_CROSS_EVENT_NOTI` `FE:037C` |

Notes: window 16 07 2024 - 16 08 2024.

### event.letter-collector — Letter Collector

ID: `event.letter-collector` · Shared: **chronicle-only**

Essence event in which letters dropped as event items are collected on a word board; completed words exchange for rewards; level range configurable.
Sub-features: word board · reward exchange · letter field reset · UI launcher.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/LetterCollectorManager.java`, `mobius-ess:dist/game/data/scripts/events/LetterCollector/{LetterCollector.java,config.xml,rewards.xml}`

Packets

| Chronicle | Client → server                             | Server → client                             |
| --------- | ------------------------------------------- | ------------------------------------------- |
| Essence   | `EX_LETTER_COLLECTOR_TAKE_REWARD` `D0:0191` | `EX_LETTER_COLLECTOR_UI_LAUNCHER` `FE:0240` |

Notes: window 30 06 2021 - 29 07 2029.

### event.secret-shop — Secret Shop

ID: `event.secret-shop` · Shared: **chronicle-only**

Essence festival "black market" game: the player spends tickets in a game window to win items from tiered reward lists; the event runs in a daily hour window and rewards a clan item for clan activity.
Sub-features: ticket game · tier lists · daily start / end hours · clan reward.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/SecretShopEventManager.java`, `mobius-ess:dist/game/data/scripts/events/SecretShop/{SecretShop.java,SecretShop.xml,config.xml}`

Packets

| Chronicle | Client → server                                                  | Server → client                                                                                                                                      |
| --------- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_FESTIVAL_BM_INFO` `D0:017A`, `EX_FESTIVAL_BM_GAME` `D0:017B` | `EX_FESTIVAL_BM_INFO` `FE:0223`, `EX_FESTIVAL_BM_ALL_ITEM_INFO` `FE:0224`, `EX_FESTIVAL_BM_TOP_ITEM_INFO` `FE:0225`, `EX_FESTIVAL_BM_GAME` `FE:0226` |

Notes: window 25 10 2023 - 01 11 2023.

### event.event-shrines — Event shrines

ID: `event.event-shrines` · Shared: **chronicle-only**

Switch that enables festival shrines for events that declare `enableShrines="true"` in their `config.xml` (Squash events); `EventShrineManager` only tracks the enabled flag, shrine NPC logic lives in the event scripts.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/EventShrineManager.java`

Packets: none.

Notes: tiny manager (49 lines); consider merging into the event engine on rewrite.

### event.balok-war — Battle with Balok

ID: `event.balok-war` · Shared: **chronicle-only**

Server-wide raid against Balok: the boss script tracks a global stage/status/points shared by all players, per-player contribution points (scorpion vs. regular monsters), a rank and a reward level; two battleground templates (normal and Kreia camp).
Sub-features: global points / stage · per-player rank · battleground instances · reward.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/BattleWithBalokManager.java`, `mobius-ess:dist/game/data/scripts/ai/bosses/Balok/Balok.java`, `mobius-ess:dist/game/data/spawns/Gludio/BalokBattleground.xml`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/balok/{ExBalrogWarGetReward.java,ExBalrogWarShowRanking.java,ExBalrogWarShowUI.java,ExBalrogWarTeleport.java}`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/balok/{BalrogWarGetReward.java,BalrogWarHud.java,BalrogWarShowRanking.java,BalrogWarShowUI.java}`

Packets

| Chronicle | Client → server                                                                                                                                 | Server → client                                                                                                                            |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `EX_BALROGWAR_GET_REWARD` `D0:023D`, `EX_BALROGWAR_SHOW_RANKING` `D0:023C`, `EX_BALROGWAR_SHOW_UI` `D0:023B`, `EX_BALROGWAR_TELEPORT` `D0:023A` | `EX_BALROGWAR_GET_REWARD` `FE:02F8`, `EX_BALROGWAR_HUD` `FE:02F9`, `EX_BALROGWAR_SHOW_RANKING` `FE:02F7`, `EX_BALROGWAR_SHOW_UI` `FE:02F6` |

Notes: the feature lives in `ai/bosses`, not in `events/`.

### event.event-match — Event match client packets

ID: `event.event-match` · Shared: **partial (HF+Ess)**

Client HUD for server-hosted event matches: match creation, team info and team lock, per-user info, spell info, score, a message line, observer list and GM test/manage windows. In the Mobius trees only the opcode constants and one writer class (`ExEventMatchMessage`) exist: no producer code sends the match packets.
Sub-features: match create / manage · team info and lock · user and spell info · score · message · observer.

| Chronicle | Present | Builds (evidence)                                                        | Difference                    |
| --------- | ------- | ------------------------------------------------------------------------ | ----------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il                                               | —                             |
| High Five | differs | nx-l2 (opcodes, `ExEventMatchMessage` writer only), l2j-server, eternity | packets exist, no match logic |
| Essence   | differs | mobius-ess (`ExEventMatchMessage` only)                                  | one packet, no match logic    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExEventMatchMessage.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExEventMatchMessage.java`
- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExPVPMatchUserDie.java`

Packets

| Chronicle | Client → server                               | Server → client                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| --------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| High Five | `REQUEST_EX_EVENT_MATCH_OBSERVER_END` `D0:77` | `EX_EVENT_MATCH_USER_INFO` `FE:02`, `EX_EVENT_MATCH_SPELLED_INFO` `FE:04`, `EX_EVENT_MATCH_FIRECRACKER` `FE:05`, `EX_EVENT_MATCH_TEAM_UNLOCKED` `FE:06`, `EX_EVENT_MATCH_GM_TEST` `FE:07`, `EX_EVENT_MATCH_LOCK_RESULT` `FE:0B`, `EX_EVENT_MATCH_LIST` `FE:0D`, `EX_EVENT_MATCH_OBSERVER` `FE:0E`, `EX_EVENT_MATCH_MESSAGE` `FE:0F`, `EX_EVENT_MATCH_SCORE` `FE:10`, `EX_EVENT_MATCH_TEAM_INFO` `FE:1C`, `EX_EVENT_MATCH_CREATE` `FE:1D`, `EX_EVENT_MATCH_MANAGE` `FE:30`, `ExPVPMatchUserDie` `FE:7F` |
| Essence   | —                                             | `EX_EVENT_MATCH_USER_INFO` `FE:02`, `EX_EVENT_MATCH_SPELLED_INFO` `FE:04`, `EX_EVENT_MATCH_FIRECRACKER` `FE:05`, `EX_EVENT_MATCH_GM_TEST` `FE:07`, `EX_EVENT_MATCH_LOCK_RESULT` `FE:0B`, `EX_EVENT_MATCH_LIST` `FE:0D`, `EX_EVENT_MATCH_OBSERVER` `FE:0E`, `EX_EVENT_MATCH_MESSAGE` `FE:0F`, `EX_EVENT_MATCH_SCORE` `FE:10`, `EX_EVENT_MATCH_TEAM_INFO` `FE:1C`, `EX_EVENT_MATCH_CREATE` `FE:1D`, `EX_EVENT_MATCH_MANAGE` `FE:30`                                                                      |

Notes: the writer class is unused in both trees; the packet family comes from the official client protocol. `ExPVPMatchUserDie` is written by the Underground Coliseum arena code (`minigame.underground-coliseum`).
