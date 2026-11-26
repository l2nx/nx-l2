# Alliance

Unions of clans under a leader clan: creation, membership and crest.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### alliance.create-dissolve — Create and dissolve an alliance

ID: `alliance.create-dissolve` · Shared: **partial**

The leader of a clan of level ≥5 creates an alliance (name), becomes its leader clan and can dissolve it when no other clan remains;
re-creation after dissolving is limited by a wait time.
Sub-features: create · name check · dissolve · wait time after dissolve.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                      |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | create through the alliance-master NPC dialog; dissolve by `RequestDismissAlly` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same (`AllianceMaster` script + `RequestDismissAlly`)                           |
| Essence   | differs | mobius-ess                                    | 09.2 keeps `AllianceMaster`; 10.1 adds the client packet `AllyCreate`           |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/village_master/AllianceMaster/AllianceMaster.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestDismissAlly.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/ClanTable.java`
- `mobius-il:dist/game/data/scripts/village_master/AllianceMaster/AllianceMaster.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestDismissAlly.java`
- `mobius-ess:dist/game/data/scripts/village_master/AllianceMaster/AllianceMaster.java`; Mobius Essence 10.1: `mobius-ess-10-1:java/org/l2jmobius/gameserver/network/clientpackets/AllyCreate.java`

Packets

| Chronicle | Client → server                                               | Server → client                                                 |
| --------- | ------------------------------------------------------------- | --------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` (NPC), `RequestDismissAlly` `86` | `SystemMessage` `64`, `PledgeInfo` `83`                         |
| High Five | `RequestBypassToServer` `23` (NPC), `RequestDismissAlly` `90` | `SystemMessage` `62`, `PledgeInfo` `89`, `DismissAlliance` `AD` |
| Essence   | `RequestDismissAlly` `90`, `AllyCreate` (Essence 10.1 only)   | `SystemMessage` `62`, `PledgeInfo` `89`                         |

Notes: `AllyCreate` exists only in the 10.1 folder of the monorepo; the 09.2 folder used as Essence baseline lacks it.
Known fixes: `l2j-server@7eaf0232` (2007-03-03, crash-npe) — isAllyExists NPE fix, thanks Seires.

### alliance.membership — Alliance membership

ID: `alliance.membership` · Shared: **partial**

The alliance leader invites another clan leader; a clan leaves voluntarily or is expelled; join/leave penalties apply; the member
clan count is capped by config; ally info windows are served.
Sub-features: invite/answer · leave · dismiss clan · wait times · clan cap · alliance info.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                            |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | cap `AltMaxNumOfClansInAlly` default 3 (aCis `aCis_gameserver/java/net/sf/l2j/Config.java:458`, Mobius IL `mobius-il:java/org/l2jmobius/gameserver/config/PlayerConfig.java:550`)     |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | baseline (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/PlayerConfig.java:489`, same default 3); wait times `DaysBeforeJoinAllyWhenLeaved` / `…Dismissed` |
| Essence   | yes     | mobius-ess                                    | same config key; handler sources differ from IL by 4–19 lines per packet (`RequestJoinAlly` 19)                                                                                       |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestJoinAlly,RequestAnswerJoinAlly,AllyLeave,AllyDismiss,RequestAllyInfo}.java`
- Mobius IL / Essence: same names under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                                                                                    | Server → client                                                                                                                                                                                                                                                   |
| --------- | ------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestJoinAlly` `82`, `RequestAnswerJoinAlly` `83`, `AllyLeave` `84`, `AllyDismiss` `85`, `RequestAllyInfo` `8E` | `AskJoinAlly` `A8`, `PledgeInfo` `83`, `PledgeShowMemberListAll` `53`, `AllianceInfo` `B4`                                                                                                                                                                        |
| High Five | `RequestJoinAlly` `8C`, `RequestAnswerJoinAlly` `8D`, `AllyLeave` `8E`, `AllyDismiss` `8F`, `RequestAllyInfo` `2E` | `AskJoinAlly` `BB`, `PledgeInfo` `89`, `PledgeShowMemberListAll` `5A`, `AllianceInfo` `B5`, `JoinAlliance` `BC`, `OustAllianceMemberPledge` `AC`, `ReplyStartAllianceWar` `C3`, `ReplyStopAllianceWar` `C5`, `SurrenderAllianceWar` `C6`, `WithdrawAlliance` `AB` |
| Essence   | `RequestJoinAlly` `8C`, `RequestAnswerJoinAlly` `8D`, `AllyLeave` `8E`, `AllyDismiss` `8F`, `RequestAllyInfo` `2E` | `AskJoinAlly` `BB`, `PledgeInfo` `89`, `PledgeShowMemberListAll` `5A`, `AllianceInfo` `B5`                                                                                                                                                                        |

Notes: —
Known fixes: `l2j-server-dp@9c53295b` (2005-09-13, other) — Fix for clan/alliance quests to work with latest engine

### alliance.crest — Alliance crest

ID: `alliance.crest` · Shared: **engine**

The alliance leader uploads an emblem that is shown next to the alliance name; clients request it by id.
Sub-features: upload · request.

| Chronicle | Present | Builds (evidence)                             | Difference                                               |
| --------- | ------- | --------------------------------------------- | -------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same                                                     |
| Essence   | yes     | mobius-ess                                    | same (shares `Crest` / `CrestTable` with the clan crest) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestSetAllyCrest,RequestAllyCrest}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/AllyCrest.java`
- Mobius IL / Essence: same under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                     | Server → client                           |
| --------- | --------------------------------------------------- | ----------------------------------------- |
| Interlude | `RequestSetAllyCrest` `87`, `RequestAllyCrest` `88` | `AllyCrest` `AE`                          |
| High Five | `RequestSetAllyCrest` `91`, `RequestAllyCrest` `92` | `AllyCrest` `AF`, `SetAllianceCrest` `AE` |
| Essence   | `RequestSetAllyCrest` `91`, `RequestAllyCrest` `92` | `AllyCrest` `AF`                          |

Notes: —
Known fixes: `l2j-server@e2025c46` (2008-01-12, other) — Fix: Broken AllyCrests; `mobius@ae99349c` (2021-12-15, other) — Fixed ally and pledge crest display. Contributed by nasseka.
