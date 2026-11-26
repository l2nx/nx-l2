# Seven Signs

The Seven Signs cycle, Festival of Darkness, Dimensional Rift, catacombs and Mammon.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### seven-signs.cycle — Seven Signs cycle

ID: `seven-signs.cycle` · Shared: **partial (IL+HF)**

Every two weeks players join Dawn or Dusk at priests, collect seal stones and vote for one of three seals (Avarice,
Gnosis, Strife); after the competition period the winning cabal owns seals for the seal-validation week, which changes
NPC spawns, catacomb access, shop prices and castle respawn rules.
Sub-features: registration (Dawn/Dusk, ticket fee) · seal stone contribution · seal voting · period switch ·
seal effects · record of seven signs.

| Chronicle | Present | Builds (evidence)                                                               | Difference                                                                    |
| --------- | ------- | ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server, ares-melnkovmaxim, l2kt-cozmin91-kotlin | baseline                                                                      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                                            | same mechanic                                                                 |
| Essence   | no      | mobius-ess                                                                      | only names left: Seven Signs dungeons and raid bosses without the cabal cycle |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/sevensigns/SevenSigns.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/SignsPriest.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/SevenSignsRecord.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestSSQStatus.java`, `nx-l2:chronicles/high-five/datapack/data/zones/ssq.xml`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/sevensigns/SevenSigns.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/{SignsPriest,DawnPriest,DuskPriest}.java`

Packets

| Chronicle | Client → server         | Server → client                              |
| --------- | ----------------------- | -------------------------------------------- |
| Interlude | `RequestSSQStatus` `C7` | `SSQStatus` `F5`                             |
| High Five | `RequestSSQStatus` `C8` | `SSQStatus` `FB`, `SSQInfo` `73` (sky color) |

Notes: Mobius IL/HF use the Seal of Strife only for respawn location.

### seven-signs.festival — Festival of Darkness

ID: `seven-signs.festival` · Shared: **partial (IL+HF)**

During the competition period, parties of one cabal enter level-banded festival arenas, kill waves of monsters for a
fixed time and offer blood; the best score per band earns seal stones and festival points for the cabal.
Sub-features: level bands · party requirements · spawn waves and chests · scoring · festival points to cabal.

| Chronicle | Present | Builds (evidence)                      | Difference                                                  |
| --------- | ------- | -------------------------------------- | ----------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server | baseline                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise             | same                                                        |
| Essence   | no      | mobius-ess                             | — (Essence `FestivalBm*` packets are the secret-shop event) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/sevensigns/SevenSignsFestival.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/{FestivalGuide,FestivalMonster}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/Festival.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/sevensigns/SevenSignsFestival.java`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc/Festival.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | — (NPC bypass)  | —               |
| High Five | — (NPC bypass)  | —               |

Notes: —
Known fixes: `l2j-server@ec94fe47` (2008-10-15, crash-npe) — A likely fix for #2792 leader quits festival. Also fixes #2510 NPE.

### seven-signs.dimensional-rift — Dimensional Rift

ID: `seven-signs.dimensional-rift` · Shared: **partial (IL+HF)**

A party pays dimensional fragments to enter the rift of its level band; it is moved between random rooms on a timer,
fights rift invaders, may meet Anakim/Lilith room bosses, and can be pushed out of the rift.
Sub-features: entry by fragments · level bands · room jumps · boss room · party leave/kick.

| Chronicle | Present | Builds (evidence)                      | Difference                                                |
| --------- | ------- | -------------------------------------- | --------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server | baseline                                                  |
| High Five | yes     | nx-l2, l2j-server                      | same                                                      |
| Essence   | no      | mobius-ess                             | — (`HeavenlyRift` in Tower of Insolence is other content) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/DimensionalRiftManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/sevensigns/{DimensionalRift,DimensionalRiftRoom}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/RiftInvader.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/Rift.java`, `nx-l2:chronicles/high-five/datapack/data/DimensionalRift.xml`
- `mobius-il:java/org/l2jmobius/gameserver/managers/DimensionalRiftManager.java`, `mobius-il:dist/game/data/DimensionalRift.xml`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | — (NPC bypass)  | —               |
| High Five | — (NPC bypass)  | —               |

Notes: the L2jone (aCis) fork adds the rift back to aCis.

### seven-signs.catacombs-mammon — Catacombs, necropolises and Mammon

ID: `seven-signs.catacombs-mammon` · Shared: **partial**

Catacombs and necropolises are entered through gatekeepers; in IL/HF access during seal validation depends on the
player's cabal and the winning side; the Merchant and Blacksmith of Mammon move between them on a schedule. Essence
opens three Seven Signs dungeons daily on a timer without any cabal check.
Sub-features: dungeon gatekeepers · cabal access rules · Mammon merchant/blacksmith spawns · Essence timed opening.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                         |
| --------- | ------- | -------------------------- | -------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | baseline: Mammon via auto-spawn                                                                    |
| High Five | yes     | nx-l2, l2j-server          | + `BlackMarketeerOfMammon` script                                                                  |
| Essence   | differs | mobius-ess (since 07.3)    | `SevenSignsDungeons`: daily opening at 21:55, Patriots / Forbidden Path / Witch dungeons, no cabal |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/BlackMarketeerOfMammon/BlackMarketeerOfMammon.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminMammon.java`, `nx-l2:chronicles/high-five/datapack/data/spawns/Catacombs/*.xml`
- `mobius-il:java/org/l2jmobius/gameserver/entity/spawns/AutoSpawnHandler.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminMammon.java`
- `mobius-ess:dist/game/data/scripts/ai/areas/SevenSignsDungeons/SevenSignsDungeons.java`, `mobius-ess:dist/game/data/scripts/ai/bosses/SevenSignsRBs/SevenSignsRBs.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | — (NPC dialog)  | —               |
| High Five | — (NPC dialog)  | —               |
| Essence   | — (NPC dialog)  | —               |

Notes: —
Known fixes: `l2j-server-dp@5958d1bb` (2015-01-26, crash-npe) — Minor NPE fix for Black Marketeer of Mammon.
