# Clan halls and fortresses

Clan-owned residences other than castles: clan halls, their functions, auctions and sieges, fortresses and fortress sieges.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### residence.clan-hall — Clan halls

ID: `residence.clan-hall` · Shared: **partial**

A clan owns a clan hall (by auction, siege or rent): hall doors, hall manager and doorman, teleport into the hall, rent
paid periodically from the clan warehouse, loss of the hall on non-payment.
Sub-features: ownership · rent/lease · doors · manager/doorman · hall zone · grades.

| Chronicle | Present | Builds (evidence)                                 | Difference                                                                                                                                      |
| --------- | ------- | ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, l2j-server | baseline: SQL-defined halls (`ClanHallTable`)                                                                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity              | same model + siegable halls                                                                                                                     |
| Essence   | differs | mobius-ess                                        | XML-defined halls (`mobius-ess:dist/game/data/residences/clanHalls/`, `ClanHallData`), types AUCTIONABLE/SIEGEABLE/OTHER, hall teleports in XML |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/residences/ClanHall.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/residences/AbstractResidence.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/ClanHallTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/{ClanHallManager,ClanHallDoorman}.java`, `nx-l2:chronicles/high-five/datapack/data/zones/clan_hall.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/residences/ClanHall.java`, `mobius-il:java/org/l2jmobius/gameserver/data/sql/ClanHallTable.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/residences/ClanHall.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ClanHallData.java`, `mobius-ess:dist/game/data/residences/clanHalls` (Gludin, Gludio, Goddard, Talking Island), `mobius-ess:dist/game/data/scripts/ai/others/{ClanHallManager,ClanHallDoorManager}`

Packets

| Chronicle | Client → server                                                             | Server → client                               |
| --------- | --------------------------------------------------------------------------- | --------------------------------------------- |
| Interlude | `RequestAllAgitInfo` (class present, not registered in the IL opcode table) | `ExShowAgitInfo` `F7`, `AgitDecoInfo` `F7`    |
| High Five | `RequestAllAgitInfo` `D0:3E`                                                | `ExShowAgitInfo` `FE:16`, `AgitDecoInfo` `FD` |
| Essence   | `RequestAllAgitInfo` `D0:3B`                                                | `ExShowAgitInfo` `FE:16`, `AgitDecoInfo` `FD` |

Notes: Essence moved hall NPC logic from instance classes to scripts.
Known fixes: `l2j-server@cd39fed4` (2008-01-14, crash-npe) — Revert Clanhall code NPE (Thanks Aik); `mobius@31015ab7` (2019-11-16, crash-npe) — Fixed infinite loop at clanhall siege. Contributed by Sahar.

### residence.functions — Residence functions

ID: `residence.functions` · Shared: **partial**

Owners buy timed functions for their residence: teleport, buffs, HP/MP/EXP regeneration, item creation, decorations;
each function charges a fee per period and lapses when unpaid.
Sub-features: teleport · support magic · HP/MP/EXP regen · item creation · curtains/platforms · fee and period.

| Chronicle | Present | Builds (evidence)                                             | Difference                                                                                                                                                               |
| --------- | ------- | ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis                                               | baseline: functions stored per clan hall in `ClanHall`                                                                                                                   |
| High Five | yes     | nx-l2, l2j-server, advanced (`ResidenceFunction`), lineage2ts | same; castle and fortress functions too                                                                                                                                  |
| Essence   | differs | mobius-ess                                                    | data-driven `mobius-ess:dist/game/data/ResidenceFunctions.xml` (`ResidenceFunctionsData`, `ResidenceFunctionTemplate`, `ResidenceFunctionType`) shared by all residences |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/residences/ClanHall.java` (inner function class), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/Castle.java` (castle functions), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/Fort.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/residences/ClanHall.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ResidenceFunctionsData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/residences/ResidenceFunction.java`, `mobius-ess:dist/game/data/ResidenceFunctions.xml`

Packets

| Chronicle | Client → server        | Server → client                                                 |
| --------- | ---------------------- | --------------------------------------------------------------- |
| Interlude | — (manager NPC dialog) | `AgitDecoInfo` `F7` (function levels shown as hall decorations) |
| High Five | — (manager NPC dialog) | `AgitDecoInfo` `FD` (function levels shown as hall decorations) |
| Essence   | — (manager NPC dialog) | `AgitDecoInfo` `FD` (function levels shown as hall decorations) |

Notes: the Essence model (template + function instance, one table for all residence types) is the cleaner design.
Known fixes: `l2jfrozen@f57bf2ea` (2011-07-23, crash-npe) — Fixed clan hall siege scripts, Fixed some minor exceptions, Added logs to discover Zones issues, Fi.

### residence.clan-hall-auction — Clan hall auction

ID: `residence.clan-hall-auction` · Shared: **partial**

Free auctionable halls are put up for auction; clans bid adena through the auctioneer; at the end the highest bidder
takes the hall, others get their bids back; the owner can resell.
Sub-features: auction list · bid / cancel bid · deposit · sale by owner · auction end task.

| Chronicle | Present | Builds (evidence)                  | Difference                                                                                                 |
| --------- | ------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (forks), l2jfrozen | baseline: auctioneer NPC (`Auctioneer` instance), `ClanHallAuction` 632 lines                              |
| High Five | yes     | nx-l2, l2j-server, sunrise         | same                                                                                                       |
| Essence   | differs | mobius-ess                         | rewritten: `ClanHallAuction` 187 lines, bidders table, auctioneer script; halls flagged AUCTIONABLE in XML |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/ClanHallAuctionManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/residences/ClanHallAuction.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/residences/AuctionableHall.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Auctioneer.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/ClanHallAuctionManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/residences/ClanHallAuction.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/ClanHallAuctionManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/residences/ClanHallAuction.java`, `mobius-ess:dist/game/data/scripts/ai/others/ClanHallAuctioneer/ClanHallAuctioneer.java`

Packets

| Chronicle | Client → server           | Server → client |
| --------- | ------------------------- | --------------- |
| Interlude | — (auctioneer NPC dialog) | —               |
| High Five | — (auctioneer NPC dialog) | —               |
| Essence   | — (auctioneer NPC dialog) | —               |

Notes: aCis public has no clan hall auction; the hl4p3x aCis custom fork adds one (`AuctionTable`, `AuctionManager` NPC).

### residence.clan-hall-siege — Clan hall sieges

ID: `residence.clan-hall-siege` · Shared: **partial (IL+HF)**

Conquerable clan halls are taken in their own sieges with special rules: Devastated Castle and Fortress of the Dead
(kill the boss), Fortress of Resistance (boss), Bandit Stronghold and Wild Beast Reserve (registration via quest,
flag fight, mini-boss), Rainbow Springs Chateau (mini-game).
Sub-features: siegable hall engine · per-hall scripts · registration · hall-specific rules.

| Chronicle | Present | Builds (evidence)                     | Difference                                                                                               |
| --------- | ------- | ------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, l2jfrozen, l2jbrasil, acis | baseline: same 6 halls in Mobius IL                                                                      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity  | same engine (`ClanHallSiegeEngine`)                                                                      |
| Essence   | no      | mobius-ess                            | engine and scripts absent (only a `SiegableHallZone` leftover in 04.2); clan halls type SIEGEABLE unused |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CHSiegeManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/clanhalls/{ClanHallSiegeEngine,SiegableHall,SiegeStatus}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/conquerablehalls/{BanditStronghold,DevastatedCastle,FortressOfResistance,FortressOfTheDead,RainbowSpringsChateau,WildBeastReserve}/`, `nx-l2:chronicles/high-five/datapack/config/ConquerableHallSiege.ini`
- `mobius-il:java/org/l2jmobius/gameserver/managers/CHSiegeManager.java`, `mobius-il:dist/game/data/scripts/conquerablehalls` (same 6 halls), `mobius-il:dist/game/config/ConquerableHallSiege.ini`

Packets

| Chronicle | Client → server                          | Server → client                           |
| --------- | ---------------------------------------- | ----------------------------------------- |
| Interlude | `RequestJoinSiege` `A4` (siegable halls) | `SiegeInfo` `C9` (sent by `SiegableHall`) |
| High Five | `RequestJoinSiege` `AD` (siegable halls) | `SiegeInfo` `C9` (sent by `SiegableHall`) |

Notes: L2Emu Essence carries a `ChSiegeManager` (legacy). L2jOrion and hl4p3x aCis forks add the CT2 siegable-hall engine to Interlude.

### residence.provisional-hall — Provisional clan halls

ID: `residence.provisional-hall` · Shared: **chronicle-only**

In Essence, a clan without a hall can rent one of the provisional halls on Talking Island from an NPC for a fixed adena
price and term, outside the auction and sieges.
Sub-features: rent · term · teleport to hall.

| Chronicle | Present | Builds (evidence) | Difference                                                 |
| --------- | ------- | ----------------- | ---------------------------------------------------------- |
| Interlude | no      | mobius-il         | —                                                          |
| High Five | no      | nx-l2             | —                                                          |
| Essence   | yes     | mobius-ess        | baseline (marked as a custom implementation in the source) |

Entry points

- `mobius-ess:dist/game/data/scripts/ai/others/ProvisionalHalls/ProvisionalHalls.java`, `mobius-ess:dist/game/data/teleporters/clanhall/TalkingIsland/ProvisionalHalls.xml`, `mobius-ess:dist/game/data/residences/clanHalls/TalkingIsland/*.xml`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Essence   | — (NPC dialog)  | —               |

Notes: —

### residence.fortress — Fortresses

ID: `residence.fortress` · Shared: **partial (HF+Ess)**

High Five: 21 fortresses owned by clans; the owner gets supply boxes, functions, a castle contract (independent or
vassal of a castle) and pays nothing for an independent fort up to a maximum ownership time. Essence keeps the fortress
engine for a single Orc Fortress with its own event-like siege.
Sub-features: ownership and max own time · supplies (`FortUpdater`) · castle contract · fortress functions · logistics/envoy NPCs.

| Chronicle | Present | Builds (evidence)                              | Difference                                                                                                                                                                 |
| --------- | ------- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2j-server-686500             | custom backports only (Mobius C6 Interlude, the L2jFrozen family); absent from Mobius IL (CT 0) and aCis, not retail Interlude                                             |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, advanced | baseline: 21 forts                                                                                                                                                         |
| Essence   | differs | mobius-ess                                     | one fort: the Orc Fortress (id 122), tracked separately as `region.orc-fortress`, with `mobius-ess:dist/game/config/OrcFortress.ini`, a HUD packet and its own NPC scripts |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/FortManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/Fort.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/FortUpdater.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/{FortManager,FortLogistics,FortDoorman}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/CastleAmbassador/`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/FortManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/Fort.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/OrcFortressConfig.java`, `mobius-ess:dist/game/config/OrcFortress.ini`, `mobius-ess:dist/game/data/scripts/ai/others/OrcFortress`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/orcfortress/OrcFortressSiegeInfoHUD.java`

Packets

| Chronicle | Client → server                                                    | Server → client                                                                                    |
| --------- | ------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------- |
| High Five | `RequestAllFortressInfo` `D0:3D`, `RequestFortressMapInfo` `D0:48` | `ExShowFortressInfo` `FE:15`, `ExShowFortressMapInfo` `FE:7D`                                      |
| Essence   | `RequestAllFortressInfo` `D0:3A`, `RequestFortressMapInfo` `D0:45` | `ExShowFortressInfo` `FE:15`, `ExShowFortressMapInfo` `FE:7E`, `OrcFortressSiegeInfoHUD` `FE:0251` |

Notes: Mobius C6 Interlude (`mobius-c6:dist/db_installer/sql/game/fort.sql` with 21 forts, `mobius-c6:dist/game/config/FortSiege.ini`) and the L2jFrozen family ship a fortress backport; it is not Interlude retail.
Known fixes: `l2j-server@cdfaf3a6` (2014-08-07, crash-npe) — BETA: Add NPE checks and log for RequestFortressMapInfo Packet:; `mobius@3208a6aa` (2021-02-08, crash-npe) — Fixed probable Fort NPE.

### residence.fortress-siege — Fortress siege

ID: `residence.fortress-siege` · Shared: **partial (HF+Ess)**

Attackers register at the fortress siege manager (or the suspicious merchant), kill the commanders, take the combat flag
and raise it on the flag pole to capture; guards and ballistas defend.
Sub-features: registration · countdown · commanders and guards · combat flag · flag pole capture · ballista ·
Essence Orc Fortress daily siege.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                                                                 |
| --------- | ------- | ------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis                      | custom backport only (Mobius C6 Interlude, the L2jFrozen family)                                                                                                           |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                                                                                                                                   |
| Essence   | differs | mobius-ess                           | `FortSiege` 1782 lines: the Orc Fortress daily siege on a timer (`OrcFortressTime`), HUD, Greg sentry / runners NPCs; this siege is a sub-feature of `region.orc-fortress` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/FortSiegeManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/FortSiege.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/siege/CombatFlag.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/FortSiegeGuardManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/FortCommander.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/{TakeFort,TakeFortStart}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/FortressSiegeManager/`, `nx-l2:chronicles/high-five/datapack/config/FortSiege.ini`; also: `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/FortSiegeGuardAI.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/siege/FortSiege.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/FortSiegeManager.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/targets/FortressFlagpole.java`, `mobius-ess:dist/game/config/FortSiege.ini`

Packets

| Chronicle | Client → server                    | Server → client                                                        |
| --------- | ---------------------------------- | ---------------------------------------------------------------------- |
| High Five | `RequestFortressSiegeInfo` `D0:3F` | `ExShowFortressSiegeInfo` `FE:17`                                      |
| Essence   | `RequestFortressSiegeInfo` `D0:3C` | `ExShowFortressSiegeInfo` `FE:17`, `OrcFortressSiegeInfoHUD` `FE:0251` |

Notes: —
Known fixes: `l2j-server@9541193a` (2009-07-13, crash-npe) — Fixed NPE on fort siege end (we should not remove elements from currently iterated over list).
