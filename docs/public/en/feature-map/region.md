# Regional systems

Area scripts and region-bound content: Hellbound, Gracia seeds, Four Sepulchers, Ketra and Varka, Dragon Valley, blood altars, Orc Fortress.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### region.area-scripts — Area scripts

ID: `region.area-scripts` · Shared: **partial**

Catalog `ai/areas`: one folder per location with scripts of that location (teleporters, guards, special monsters, location
NPCs). Each script is a `Script` subclass registered at boot; data (HTML, spawns) sits beside it.
Sub-features: location teleporters · guards · special monster behaviour · location NPC dialogs.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                     |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | 16 area folders, 26 `.java` scripts (Mobius IL)                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: 30 folders, 134 `.java` scripts (Gracia, Hellbound, Imperial Tomb, …)                |
| Essence   | yes     | mobius-ess                 | 27 folders, 49 `.java` scripts; new areas (Frost Castle, Garden of Authority, Orc Barracks, …) |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/**`, data beside it in `nx-l2:chronicles/high-five/datapack/data/scripts/ai/areas/**`
- `mobius-il:dist/game/data/scripts/ai/areas/**`
- `mobius-ess:dist/game/data/scripts/ai/areas/**`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: the catalog is mapped as one engine feature (see the legend); individual scripts are not features.

### region.hellbound — Hellbound

ID: `region.hellbound` · Shared: **partial (HF+Ess)**

The Hellbound island has a trust level that rises as players kill monsters and complete quests; the island stage (0-11) changes
with trust and unlocks NPCs, spawns, zones and the Tower of Infinitum; stage data comes from XML; the island entry needs a
quest (or a config bypass); GM and voiced commands show and set the status.
Sub-features: trust points · island stages · stage spawns · stage NPCs/instances (Demon Prince, Ranku floors) · entry quest ·
status commands.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                            |
| --------- | ------- | -------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis            | —                                                                                                                     |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: `HellboundEngine`, trust points, spawns, instances                                                          |
| Essence   | differs | mobius-ess                 | island as a hunting area only: zone, `Aizen`, `Deiman`, `Satina`, `Ryuminir`, citadel teleport; no trust/stage engine |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/Hellbound/{HellboundEngine,HellboundLoader,HellboundPointData,HellboundSpawns}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/Hellbound/AI/**`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/Hellbound/Instances/**`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Hellbound.java`, `nx-l2:chronicles/high-five/datapack/data/scripts/ai/areas/Hellbound/{hellboundTrustPoints,hellboundSpawns}.xml`, `nx-l2:chronicles/high-five/datapack/data/spawns/Hellbound/Hellbound.xml`, `nx-l2:chronicles/high-five/datapack/config/Custom/HellboundStatus.ini`
- `mobius-ess:dist/game/data/scripts/ai/areas/Hellbound/{Aizen,Deiman,Satina,Ryuminir,IvoryTowerTeleportZones}.java`, `mobius-ess:dist/game/data/scripts/ai/areas/Hellbound/CitadelTeleport/*`, `mobius-ess:dist/game/data/zones/hellbound.xml`

Packets

| Chronicle | Client → server | Server → client                                  |
| --------- | --------------- | ------------------------------------------------ |
| High Five | —               | — (stage state is server-side; NPC dialogs only) |
| Essence   | —               | —                                                |

Notes: the Essence hunting-area scripts arrived in 05.2 (`Aizen`, `Deiman`, `Satina`, `Ryuminir`, `HellboundMessenger`) and
06.3 (`CitadelTeleport`); they are not the HF engine.

### region.gracia-seeds — Gracia seeds

ID: `region.gracia-seeds` · Shared: **chronicle-only**

Gracia seeds (Seed of Destruction, Seed of Infinity, Seed of Annihilation): each seed cycles through stages driven by kill
counters (Ekimus, Tiat) and timers; stage state controls which instance (Hall of Erosion/Suffering/Heart of Infinity, attack
or defence) is open; Energy Seeds and Star Stones are the loot loop.
Sub-features: SoD stages · SoI stages · SoA (annihilation) · kill counters · attack/defence instances · energy seeds.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | mobius-il, acis            | —          |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline   |
| Essence   | no      | mobius-ess                 | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/{SeedOfDestructionManager,SeedOfInfinityManager}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/Gracia/{GraciaLoader,AI/EnergySeeds,AI/SeedOfAnnihilation/**,instances/**}`, `nx-l2:chronicles/high-five/datapack/config/GraciaSeeds.ini`, `nx-l2:chronicles/high-five/datapack/data/spawns/Gracia/**`

Packets

| Chronicle | Client → server | Server → client                         |
| --------- | --------------- | --------------------------------------- |
| High Five | —               | — (instance entry and NPC dialogs only) |

Notes: the seed instances themselves belong to the reflection domain.

### region.four-sepulchers — Four Sepulchers

ID: `region.four-sepulchers` · Shared: **partial (IL+HF)**

Four Sepulchers (Imperial Tomb): the party enters the tomb during timed entry windows, clears four halls of a chosen sepulcher,
and the cycle (entry, warm-up, attack, cool-down) repeats; managers announce the phase and spawn the room monsters.
Sub-features: cycle timers (entry, warm-up, attack, cool-down) · room spawns · manager NPC announcements.

| Chronicle | Present | Builds (evidence)                                    | Difference                                                        |
| --------- | ------- | ---------------------------------------------------- | ----------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`FourSepulchersManager`), l2jfrozen | same idea; aCis/L2jFrozen keep the HTML pages under `sepulchers/` |
| High Five | yes     | nx-l2, l2j-server, sunrise                           | baseline (script + `FourSepulchers.xml` + manager spawn list)     |
| Essence   | no      | mobius-ess                                           | —                                                                 |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/ImperialTomb/FourSepulchers/FourSepulchers.java`, `nx-l2:chronicles/high-five/datapack/data/scripts/ai/areas/ImperialTomb/FourSepulchers/FourSepulchers.xml`, `nx-l2:chronicles/high-five/datapack/data/spawns/Goddard/FourSepulchersManagers.xml`
- `mobius-il:dist/game/data/scripts/ai/areas/ImperialTomb/FourSepulchers/FourSepulchers.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |

Notes: —

Known fixes: `mobius@302842ff` (2018-04-13, crash-npe) — Probable NPE fix for FourSepulchersManager; `l2j-server@14267378` (2008-08-18, other) — Improved FourSepulchersManager error handling. (Fixes #2969); `sunrise@35c767e7` (2015-04-14, other) — Clean up some loggers Removed some duplicate code from FourSepulchersManager Fixed some logic mistakes in FourSepulchersManager.

### region.ketra-varka — Ketra and Varka alliance

ID: `region.ketra-varka` · Shared: **partial (IL+HF)**

Ketra Orcs and Varka Silenos outposts: killing members of one side raises the alliance level with it and lowers the other;
alliance levels unlock support NPCs (buffs, soul crystal, teleports), and an opposing player is attacked.
Sub-features: alliance level quests · support NPC (Ketra, Varka) · hostile behaviour between sides · outpost teleporters.

| Chronicle | Present | Builds (evidence)          | Difference                                |
| --------- | ------- | -------------------------- | ----------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline in IL (22 script files per side) |
| High Five | yes     | nx-l2, l2j-server, sunrise | same                                      |
| Essence   | no      | mobius-ess                 | —                                         |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/KetraOrcOutpust/KetraOrcSupport/KetraOrcSupport.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/VarkaSilenosBarracks/VarkaSilenosSupport/VarkaSilenosSupport.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/VarkaKetra.java`, `nx-l2:chronicles/high-five/datapack/data/teleporters/others/{Ketra,Varka}.xml`
- `mobius-il:dist/game/data/scripts/ai/areas/KetraOrcOutpust/**`, `mobius-il:dist/game/data/scripts/ai/areas/VarkaSilenosBarracks/**`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |

Notes: the alliance quests (levels) belong to the quest domain.

### region.dragon-valley — Dragon Valley

ID: `region.dragon-valley` · Shared: **partial (HF+Ess)**

Dragon Valley hunting area with its own monster behaviour (spoil reaction, summons, spawn animation), Dragon Vortex raid spawn via a vortex NPC and item, the Separated Soul teleporter and a Lair of Antharas script.
Sub-features: valley monster AI · Dragon Vortex raids · Separated Soul teleport · Lair of Antharas script.

| Chronicle | Present | Builds (evidence)          | Difference                                                                         |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis            | —                                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: `DragonValley`, `DragonVortex`, `SeparatedSoul`, `LairOfAntharas`        |
| Essence   | differs | mobius-ess                 | area scripts only (`CaveMaiden`, `DragonValleySaturdaySpawn`, `Fellow`); no vortex |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/DragonValley/{DragonValley,LairOfAntharas}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/DragonValley/DragonVortex/DragonVortex.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/areas/DragonValley/SeparatedSoul/SeparatedSoul.java`, `nx-l2:chronicles/high-five/datapack/data/spawns/Giran/DragonValley.xml`
- `mobius-ess:dist/game/data/scripts/ai/areas/DragonValley/{CaveMaiden,DragonValleySaturdaySpawn}.java`, `mobius-ess:dist/game/data/scripts/ai/areas/DragonValley/Fellow/Fellow.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: —

Known fixes: `l2j-server-dp@003573ea` (2012-10-19, wrong-formula-value) — Fixed despawn delay for Raid Bosses spawned by Dragon Vortex.

### region.blood-altars — Blood altars

ID: `region.blood-altars` · Shared: **chronicle-only**

Blood altars in every town: an altar switches between an alive and a dead state on a timer (with a configured spawn chance), a progress counter is kept per altar, and a group of destruction bosses spawns at the altar; altar status, progress and change time persist in a database table.
Sub-features: altar per town (17 scripts) · alive/dead NPC groups · progress/status · destruction boss spawn group · persisted altar state · GM control.

| Chronicle | Present | Builds (evidence)                  | Difference                                        |
| --------- | ------- | ---------------------------------- | ------------------------------------------------- |
| Interlude | no      | mobius-il, acis                    | —                                                 |
| High Five | yes     | eternity, sunrise (data), advanced | absent from nx-l2 (Mobius HF has no blood altars) |
| Essence   | no      | mobius-ess                         | —                                                 |

Entry points

- `eternity:server_scripts/java/l2e/scripts/ai/npc/BloodAltars/*BloodAltar.java` (17 files, one per town)
- `sunrise-data:L2J_SunriseProject_Data/dist/game/data/scripts/ai/npc/BloodAltars/AdenBloodAltar.java` and siblings
- `advanced:src/main/java/l2f/gameserver/instancemanager/BloodAltarManager.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| High Five | —               | —               |

Notes: nx-l2 (Mobius HF) does not contain blood altars; the public L2jEternityWorld, L2jSunrise and L2_Advanced builds do.

### region.orc-fortress — Orc Fortress (Essence)

ID: `region.orc-fortress` · Shared: **chronicle-only**

Essence server event of the Orc Fortress: a scheduled event (enable flag and start time in `OrcFortress.ini`) with gatekeeper, sentry, logistic officer and runner NPCs.
Sub-features: schedule (`OrcFortressEnable`, `OrcFortressTime`) · daily siege on the timer (`FortSiege`) · siege HUD · gate keeper · sentries · logistic officer · runners · single fortress (id 122).

| Chronicle | Present | Builds (evidence) | Difference                                          |
| --------- | ------- | ----------------- | --------------------------------------------------- |
| Interlude | no      | mobius-il         | —                                                   |
| High Five | no      | nx-l2             | —                                                   |
| Essence   | yes     | mobius-ess        | baseline: one fortress, daily siege, own HUD packet |

Entry points

- `mobius-ess:dist/game/config/OrcFortress.ini` (`OrcFortressEnable`, `OrcFortressTime`), `mobius-ess:java/org/l2jmobius/gameserver/config/OrcFortressConfig.java`, `mobius-ess:dist/game/data/scripts/ai/others/OrcFortress/{GateKeeper,GregSentry,LogisticOfficer,RunnersOrcs}.java`, `mobius-ess:dist/game/data/scripts/ai/areas/OrcBarracks/{Kerr,Tarba}`

Packets

| Chronicle | Client → server                                                      | Server → client                                                                                                    |
| --------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Essence   | `RequestAllFortressInfo` `D0:3A`, `RequestFortressSiegeInfo` `D0:3C` | `ExShowFortressSiegeInfo` `FE:17`, `OrcFortressSiegeInfoHUD` `FE:0251` (catalog name `ExAdenFortressSiegeHudInfo`) |

Notes: the Orc Fortress siege is a sub-feature of this ID (there is no separate residence ID for it); the generic fortress mechanics stay in `residence.fortress` and `residence.fortress-siege`, where Essence is `differs`.
