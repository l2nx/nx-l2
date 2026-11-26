# Rankings

Character, PvP, clan and pet rankings and the bonuses tied to them.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### ranking.character — Character ranking

ID: `ranking.character` · Shared: **chronicle-only**

Server-wide ranking of characters by level, with filters by race and class, a per-character info window and a rank history. The server recomputes it on a timer (every 30 minutes in Essence) from the `characters` table, ignoring characters inactive for a configured time, and keeps a snapshot for the client.
Sub-features: top list · by race / by class · own rank info · rank history · periodic refresh.

| Chronicle | Present | Builds (evidence)            | Difference                                             |
| --------- | ------- | ---------------------------- | ------------------------------------------------------ |
| Interlude | no      | acis, l2jfrozen, mobius-il   | —                                                      |
| High Five | no      | nx-l2, l2j-server, mobius-hf | no ranking window in HF client; only forks add a board |
| Essence   | yes     | mobius-ess                   | baseline                                               |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/RankManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/{RequestRankingCharInfo.java,RequestRankingCharHistory.java,RequestRankingCharRankers.java}`

Packets

| Chronicle | Client → server                                                                                            | Server → client                                                                                            |
| --------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_RANKING_CHAR_INFO` `D0:0182`, `EX_RANKING_CHAR_HISTORY` `D0:0183`, `EX_RANKING_CHAR_RANKERS` `D0:0184` | `EX_RANKING_CHAR_INFO` `FE:022F`, `EX_RANKING_CHAR_HISTORY` `FE:0230`, `EX_RANKING_CHAR_RANKERS` `FE:0231` |

Notes: the same manager also serves PvP, pet, clan and Olympiad rankings (separate features / domains); the HF client has no ranking window.

### ranking.pvp — PvP ranking

ID: `ranking.pvp` · Shared: **chronicle-only**

Ranking by PvP kills with a list and own-info request, recomputed with the character ranking.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/RequestPvpRankingList.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/RankManager.java` (`SELECT_CHARACTERS_PVP`)

Packets

| Chronicle | Client → server                                                     | Server → client                                                     |
| --------- | ------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Essence   | `EX_PVP_RANKING_LIST` `D0:01C6`, `EX_PVP_RANKING_MY_INFO` `D0:01C5` | `EX_PVP_RANKING_LIST` `FE:0278`, `EX_PVP_RANKING_MY_INFO` `FE:0277` |

Notes: ranking fields: kills, deaths, PvP kills, class, clan.

### ranking.clan — Clan ranking

ID: `ranking.clan` · Shared: **chronicle-only**

Ranking of clans (level, reputation, members) with list and own-clan info.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/RequestPledgeRankingList.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/RankManager.java` (`SELECT_CLANS`)

Packets

| Chronicle | Client → server                                                           | Server → client                                                           |
| --------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Essence   | `EX_PLEDGE_RANKING_LIST` `D0:01F7`, `EX_PLEDGE_RANKING_MY_INFO` `D0:01F6` | `EX_PLEDGE_RANKING_LIST` `FE:02B3`, `EX_PLEDGE_RANKING_MY_INFO` `FE:02B2` |

Notes: —

### ranking.pet — Pet ranking

ID: `ranking.pet` · Shared: **chronicle-only**

Ranking of pets by experience/level with own-pet info.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/RequestPetRankingList.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/RankManager.java` (`SELECT_PETS`)

Packets

| Chronicle | Client → server                                                     | Server → client                                                     |
| --------- | ------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Essence   | `EX_PET_RANKING_LIST` `D0:01DA`, `EX_PET_RANKING_MY_INFO` `D0:01D9` | `EX_PET_RANKING_LIST` `FE:028E`, `EX_PET_RANKING_MY_INFO` `FE:028D` |

Notes: —

### ranking.power — Leader's Power

ID: `ranking.power` · Shared: **chronicle-only**

Buff of the ranking leader: the character on top of the character ranking can use "Leader's Power" once per cooldown. It spawns a statue (decoy) at the leader's position that periodically buffs nearby players with the leader-power skill; the buff-zone location is broadcast to everyone and kept in global variables. `RankingPowerManager` holds the state; the name is historical, it is not a power score.
Sub-features: leader power activation · buff-zone NPC position · global broadcast · cooldown.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/RankingPowerManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/ranking/RequestExRankingCharSpawnBuffzoneNpc.java`

Packets

| Chronicle | Client → server                                                                                   | Server → client                                                                          |
| --------- | ------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Essence   | `EX_RANKING_CHAR_SPAWN_BUFFZONE_NPC` `D0:0185`, `EX_RANKING_CHAR_BUFFZONE_NPC_POSITION` `D0:0186` | `EX_RANKING_BUFF_ZONE_NPC_INFO` `FE:0232`, `EX_RANKING_BUFF_ZONE_NPC_POSITION` `FE:0233` |

Notes: leader skill id 52018.

### ranking.gear-score — Gear score

ID: `ranking.gear-score` · Shared: **chronicle-only**

Numeric "power" of a character computed from equipment (enchant, augmentation, attributes, skills), used as a displayed value and as the points source for rankings. Evidence is a data table only (`GearScore.xml` in the L2Emu Samurai Essence datapack); no public code for the calculation was reviewed.

| Chronicle | Present | Builds (evidence)                                    | Difference                        |
| --------- | ------- | ---------------------------------------------------- | --------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il                           | —                                 |
| High Five | no      | nx-l2, l2j-server                                    | —                                 |
| Essence   | differs | l2emu-ess (data table only); Mobius Essence has none | rules table without reviewed code |

Entry points

- `l2emu-ess:dp/L2Emu-Essence-DP-542-main/data/GearScore.xml`

Packets: none.

Notes: no Mobius build computes it.

### ranking.top-bonuses — Ranking top bonuses

ID: `ranking.top-bonuses` · Shared: **chronicle-only**

Passive bonuses for the top of the character ranking: top-ranked players receive bonus skills through an AI script (`RankingSkillBonuses`).
Sub-features: passive rank bonus skills.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:dist/game/data/scripts/ai/others/RankingSkillBonuses.java`

Packets: none.

Notes: the active leader buff is a separate feature, `ranking.power`.
