# Clan

Permanent player communities: creation, membership, ranks and privileges, subunits, levels and reputation, skills, crests, wars, warehouse, notice and recruitment.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### clan.create-dissolve — Create and dissolve a clan

ID: `clan.create-dissolve` · Shared: **partial**

A player founds a clan (name validation, level requirement, wait time after leaving/dissolving) and the leader can
schedule dissolution with a delay; level-ups and some actions are blocked while dissolution is pending.
Sub-features: create · name check · create penalty · scheduled dissolve · cancel dissolve.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                                      |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | via the village-master NPC dialog; level ≥10 (Mobius IL `mobius-il:java/org/l2jmobius/gameserver/data/sql/ClanTable.java:167`)                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same NPC path (`VillageMaster`); level ≥10 (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/ClanTable.java:154`); penalty days `DaysBeforeCreateAClan` (default 10) |
| Essence   | differs | mobius-ess                                    | client dialog `RequestCreatePledge` / `ExShowCreatePledge`; level check commented out (`mobius-ess:java/org/l2jmobius/gameserver/data/sql/ClanTable.java:172-174`)                              |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/ClanTable.java` (`createClan`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/VillageMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/village_master/ClanMaster/ClanMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminPledge.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/sql/ClanTable.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/VillageMaster.java`, `mobius-il:dist/game/data/scripts/village_master/ClanMaster/ClanMaster.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/sql/ClanTable.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestCreatePledge.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExShowCreatePledge.java`, `mobius-ess:dist/game/data/scripts/village_master/ClanMaster/ClanMaster.java`

Packets

| Chronicle | Client → server                           | Server → client                                                                                     |
| --------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` (NPC dialog) | `PledgeShowMemberListAll` `53`, `PledgeInfo` `83`, `PledgeStatusChanged` `CD`, `SystemMessage` `64` |
| High Five | `RequestBypassToServer` `23` (NPC dialog) | `PledgeShowMemberListAll` `5A`, `PledgeInfo` `89`, `PledgeStatusChanged` `CD`, `SystemMessage` `62` |
| Essence   | `RequestCreatePledge` `D0:014D`           | `ExShowCreatePledge` `FE:01EE`, `PledgeShowMemberListAll` `5A`, `PledgeInfo` `89`                   |

Notes: —

### clan.membership — Clan membership

ID: `clan.membership` · Shared: **partial**

Leader or a member with the privilege invites a player (request/answer dialogs); members leave or are expelled; each action
puts a join/invite penalty on the actor; member lists and info windows are served to members and non-members.
Sub-features: invite/answer · leave · expel · join/invite penalty · member list · member info · pledge info.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline; join penalty days config `DaysBeforeJoinAClan` (HF default 1, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/PlayerConfig.java:482`) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | baseline (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java:889` penalty check)                                                    |
| Essence   | yes     | mobius-ess                                    | + invite by name (`RequestClanAskJoinByName`); `RequestJoinPledge` differs by ~40 lines from IL                                                                           |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/{Clan,ClanMember}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestJoinPledge,RequestAnswerJoinPledge,RequestWithdrawalPledge,RequestOustPledgeMember,RequestPledgeMemberList,RequestPledgeMemberInfo,RequestPledgeInfo,RequestPledgeExtendedInfo}.java`; also: `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/ClanHandler.java`
- same names under `mobius-il:java/org/l2jmobius/gameserver`; Mobius Essence: same + `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestClanAskJoinByName.java`; also: `mobius-il:dist/game/data/scripts/handlers/chat/commands/voiced/ClanHandler.java`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/voiced/ClanHandler.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                             | Server → client                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestJoinPledge` `24`, `RequestAnswerJoinPledge` `25`, `RequestWithdrawalPledge` `26`, `RequestOustPledgeMember` `27`, `RequestPledgeMemberList` `3C`, `RequestPledgeMemberInfo` `D0:1D`, `RequestPledgeInfo` `66`, `RequestPledgeExtendedInfo` `67`                                     | `AskJoinPledge` `32`, `JoinPledge` `33`, `PledgeShowMemberListAll` `53`, `PledgeShowMemberListAdd` `55`, `PledgeShowMemberListUpdate` `54`, `PledgeShowMemberListDelete` `56`, `PledgeShowMemberListDeleteAll` `82`, `PledgeReceiveMemberInfo` `FE:3D`, `PledgeInfo` `83`, `PledgeShowInfoUpdate` `88`, `PledgeStatusChanged` `CD`                                                                                                                                                                              |
| High Five | `RequestJoinPledge` `26`, `RequestAnswerJoinPledge` `27`, `RequestWithdrawalPledge` `28`, `RequestOustPledgeMember` `29`, `RequestPledgeMemberList` `4D`, `RequestPledgeMemberInfo` `D0:16`, `RequestPledgeInfo` `65`, `RequestPledgeExtendedInfo` `66`                                     | `AskJoinPledge` `2C`, `JoinPledge` `2D`, `PledgeShowMemberListAll` `5A`, `PledgeShowMemberListAdd` `5C`, `PledgeShowMemberListUpdate` `5B`, `PledgeShowMemberListDelete` `5D`, `PledgeShowMemberListDeleteAll` `88`, `PledgeReceiveMemberInfo` `FE:3E`, `PledgeInfo` `89`, `PledgeShowInfoUpdate` `8E`, `PledgeStatusChanged` `CD`, `CreatePledge` `2B`, `DismissPledge` `37`, `OustPledgeMember` `35`, `PledgeExtendedInfo` `8A`, `SetDismissPledge` `38`, `SetOustPledgeMember` `36`, `WithdrawalPledge` `34` |
| Essence   | `RequestJoinPledge` `26`, `RequestAnswerJoinPledge` `27`, `RequestWithdrawalPledge` `28`, `RequestOustPledgeMember` `29`, `RequestPledgeMemberList` `4D`, `RequestPledgeMemberInfo` `D0:16`, `RequestPledgeInfo` `65`, `RequestPledgeExtendedInfo` `66`, `RequestClanAskJoinByName` `D0:BA` | `AskJoinPledge` `2C`, `JoinPledge` `2D`, `PledgeShowMemberListAll` `5A`, `PledgeShowMemberListAdd` `5C`, `PledgeShowMemberListUpdate` `5B`, `PledgeShowMemberListDelete` `5D`, `PledgeShowMemberListDeleteAll` `88`, `PledgeReceiveMemberInfo` `FE:3F`, `PledgeInfo` `89`, `PledgeShowInfoUpdate` `8E`, `PledgeStatusChanged` `CD`, `ExPledgeClassicRaidInfo` `FE:01D7`                                                                                                                                         |

Notes: —

### clan.ranks-privileges — Ranks and privileges

ID: `clan.ranks-privileges` · Shared: **engine**

Members get a power grade (rank) with a privilege bitmask (invite, expel, manage ranks, warehouse, war, crest, castle/clan hall
rights …); the clan leader edits grades and assigns them to members. Pledge class (social rank) depends on clan level and role.
Sub-features: power grades · privilege mask · member power info · pledge class.

| Chronicle | Present | Builds (evidence)                             | Difference                                                  |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same                                                        |
| Essence   | yes     | mobius-ess                                    | `ClanPrivileges` identical to Mobius IL (0 differing lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/ClanPrivileges.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestPledgePower,RequestPledgePowerGradeList,RequestPledgeSetMemberPowerGrade,RequestPledgeMemberPowerInfo}.java`
- Mobius IL / Essence: same file names under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                                                                                                                      | Server → client                                                                            |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Interlude | `RequestPledgePower` `C0`, `RequestPledgePowerGradeList` `D0:1A`, `RequestPledgeSetMemberPowerGrade` `D0:1C`, `RequestPledgeMemberPowerInfo` `D0:1B` | `ManagePledgePower` `30`, `PledgePowerGradeList` `FE:3B`, `PledgeReceivePowerInfo` `FE:3C` |
| High Five | `RequestPledgePower` `CC`, `RequestPledgePowerGradeList` `D0:13`, `RequestPledgeSetMemberPowerGrade` `D0:15`, `RequestPledgeMemberPowerInfo` `D0:14` | `ManagePledgePower` `2A`, `PledgePowerGradeList` `FE:3C`, `PledgeReceivePowerInfo` `FE:3D` |
| Essence   | `RequestPledgePower` `CC`, `RequestPledgePowerGradeList` `D0:13`, `RequestPledgeSetMemberPowerGrade` `D0:15`, `RequestPledgeMemberPowerInfo` `D0:14` | `ManagePledgePower` `2A`, `PledgePowerGradeList` `FE:3D`, `PledgeReceivePowerInfo` `FE:3E` |

Notes: —
Known fixes: `mobius@3eeaa99c` (2021-08-26, wrong-formula-value) — Fix clan sometimes not updating when member privileges change. Contributed by nasseka.

### clan.subunits — Clan subunits

ID: `clan.subunits` · Shared: **partial**

A clan has up to seven sub-units: Academy (id -1), two Royal Guards (100, 200) and four Orders of Knights (1001, 1002, 2001, 2002),
with a leader/captain per unit, member limits by clan level and academy sponsorship (graduate / sponsor reputation).
Sub-features: academy · royal guard · knights · unit leaders · reorganize members · sponsor assignment.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                     |
| --------- | ------- | --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | same unit ids; units available by clan level (IL clan max level 8)                                                                                             |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | baseline (`Clan.SUBUNIT_*`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java:111-123`)                                 |
| Essence   | yes     | mobius-ess                                    | same unit ids (`mobius-ess:java/org/l2jmobius/gameserver/entity/clan/Clan.java:122-134`); `ExSubPledgeSkillAdd` for unit skills; per-level limits not compared |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestPledgeSetAcademyMaster,RequestPledgeReorganizeMember}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/village_master/ClanMaster/ClanMaster.java` (unit creation dialogs)
- Mobius IL / Essence: same under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                                                  | Server → client                                                                                        |
| --------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestPledgeSetAcademyMaster` `D0:19`, `RequestPledgeReorganizeMember` `D0:24` | `PledgeReceiveSubPledgeCreated` `FE:3F`, `PledgeShowMemberListAdd` `55`                                |
| High Five | `RequestPledgeSetAcademyMaster` `D0:12`, `RequestPledgeReorganizeMember` `D0:2C` | `PledgeReceiveSubPledgeCreated` `FE:40`, `PledgeShowMemberListAdd` `5C`                                |
| Essence   | `RequestPledgeSetAcademyMaster` `D0:12`, `RequestPledgeReorganizeMember` `D0:2C` | `PledgeReceiveSubPledgeCreated` `FE:41`, `PledgeShowMemberListAdd` `5C`, `ExSubPledgeSkillAdd` `FE:77` |

Notes: —

### clan.level-reputation — Clan level and reputation

ID: `clan.level-reputation` · Shared: **partial**

Clan level rises by paying a fixed requirement (SP, adena, quest items, reputation) at the village master (HF/IL) or by accumulating
clan experience (Essence). Reputation points are earned from sieges, wars, PvP and raid kills and spent on clan skills.
Sub-features: level-up cost table · clan reputation · reputation-for-items trade · max level.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                           |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | levels 0→8 (Mobius IL `Clan.levelUpClan` `case 0..7`)                                                                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | levels 0→11 (`Clan.levelUpClan` `case 0..10`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java:1995`); level 1 costs 20000 SP + 650000 adena |
| Essence   | differs | mobius-ess                                    | level by clan exp from `mobius-ess:dist/game/data/ClanLevelData.xml` (0..11, e.g. level 1 = 100 exp, 11 = 20 000 000 exp); `Clan.addExp`                                             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java` (`levelUpClan`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/VillageMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/ClanTrader/ClanTrader.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/clan/Clan.java`; Mobius Essence: `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ClanLevelData.java`, `mobius-ess:dist/game/data/ClanLevelData.xml`, `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/Clan.java` (L3199-3206)

Packets

| Chronicle | Client → server | Server → client                                                               |
| --------- | --------------- | ----------------------------------------------------------------------------- |
| Interlude | NPC dialog      | `PledgeStatusChanged` `CD`, `PledgeShowInfoUpdate` `88`, `SystemMessage` `64` |
| High Five | NPC dialog      | `PledgeStatusChanged` `CD`, `PledgeShowInfoUpdate` `8E`, `SystemMessage` `62` |
| Essence   | —               | `ExPledgeLevelUp` `FE:01DF`, `PledgeShowInfoUpdate` `8E`                      |

Notes: the HF 9–11 levels are in Mobius HF code but not in aCis/L2jFrozen (Interlude cap is 8).
Known fixes: `l2j-server@91a1e119` (2006-10-11, wrong-formula-value) — Should fix the clan leveling bugs. That's not perfect but at least better than now ;).

### clan.skills — Clan skills

ID: `clan.skills` · Shared: **partial**

Clan skills (pledge skills, sub-pledge/squad skills) are learned by the leader with reputation points, apply to online members
and are listed in the skill window.
Sub-features: pledge skills · sub-pledge skills · reputation cost · skill list packets.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                          |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline                                                                                                            |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | baseline (`Clan` keeps `_subPledgeSkills`)                                                                          |
| Essence   | yes     | mobius-ess                                    | + `ExSubPledgeSkillAdd`; same pledge-skill maps in `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/Clan.java` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{PledgeSkillList,PledgeSkillListAdd}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestAcquireSkill.java`
- Mobius IL / Essence: same under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                            | Server → client                                                                        |
| --------- | ---------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Interlude | `RequestAcquireSkillInfo` `6B`, `RequestAcquireSkill` `6C` | `PledgeSkillList` `FE:39`, `PledgeSkillListAdd` `FE:3A`                                |
| High Five | `RequestAcquireSkillInfo` `73`, `RequestAcquireSkill` `7C` | `PledgeSkillList` `FE:3A`, `PledgeSkillListAdd` `FE:3B`                                |
| Essence   | `RequestAcquireSkillInfo` `73`, `RequestAcquireSkill` `7C` | `PledgeSkillList` `FE:3A`, `PledgeSkillListAdd` `FE:3B`, `ExSubPledgeSkillAdd` `FE:77` |

Notes: —

### clan.crest — Clan and alliance crests

ID: `clan.crest` · Shared: **partial**

Clan crest (16×12), large crest (for sieges) and alliance crest are uploaded by the leader, stored and shown to other clients on demand.
Essence adds crest presets chosen from a catalogue.
Sub-features: clan crest · large crest · alliance crest · presets (Essence).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                              |
| --------- | ------- | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `CrestTable`, `Crest` (Mobius IL `mobius-il:java/org/l2jmobius/gameserver/entity/clan/Crest.java` identical to Essence) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | baseline (`CrestType`)                                                                                                  |
| Essence   | differs | mobius-ess                                    | + `RequestGetPledgeCrestPreset`, `RequestSetPledgeCrestPreset` (package `newcrest`)                                     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/CrestTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Crest.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/enums/CrestType.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestPledgeCrest,RequestSetPledgeCrest,RequestExPledgeCrestLarge,RequestExSetPledgeCrestLarge}.java`
- same under `mobius-il:java/org/l2jmobius/gameserver`; Mobius Essence: same + `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/newcrest/{RequestGetPledgeCrestPreset,RequestSetPledgeCrestPreset}.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                        | Server → client                                                                |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Interlude | `RequestPledgeCrest` `68`, `RequestSetPledgeCrest` `53`, `RequestExPledgeCrestLarge` `D0:10`, `RequestExSetPledgeCrestLarge` `D0:11`                                                                                   | `PledgeCrest` `6C`, `ExPledgeEmblem` `FE:28`                                   |
| High Five | `RequestPledgeCrest` `67`, `RequestSetPledgeCrest` `09`, `RequestExPledgeCrestLarge` `D0:10`, `RequestExSetPledgeCrestLarge` `D0:11`                                                                                   | `PledgeCrest` `6A`, `ExPledgeEmblem` `FE:1B`, `SetPledgeCrest` `69`            |
| Essence   | `RequestPledgeCrest` `67`, `RequestSetPledgeCrest` `09`, `RequestExPledgeCrestLarge` `D0:10`, `RequestExSetPledgeCrestLarge` `D0:11`, `RequestGetPledgeCrestPreset` `D0:0266`, `RequestSetPledgeCrestPreset` `D0:0265` | `PledgeCrest` `6A`, `ExPledgeEmblem` `FE:1B`, `GetPledgeCrestPreset` `FE:0333` |

Notes: alliance crest packets live in `alliance.crest`.
Known fixes: `l2j-server@ce8ae8ae` (2006-10-20, crash-npe) — Crests fix for reloading, avoid wrong crest displaying and rare client crash; `l2j-server@a2281cd9` (2010-05-05, other) — fix: save crest id to database after removing the crest.

### clan.war — Clan wars

ID: `clan.war` · Shared: **partial**

A leader of a clan of level ≥3 with enough members declares war on another clan; the target confirms (mutual) or the declaration stays
one-sided; either side can ask for peace or surrender; war kills give reputation; the war list is shown to members.
Sub-features: declare · reply · stop · surrender · war list · member-count requirement.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                                          |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | level ≥3 and `AltClanMembersForWar` (Mobius IL `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestStartPledgeWar.java`)                                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestStartPledgeWar.java:54,75`)                                                                   |
| Essence   | differs | mobius-ess                                    | `ClanWar` object with states `DECLARATION`, `BLOOD_DECLARATION`, `MUTUAL`, `WIN`, `LOSS`, `TIE`; timers 7 / 5 / 21 days (`mobius-ess:java/org/l2jmobius/gameserver/entity/clan/ClanWar.java:41-43`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestStartPledgeWar,RequestReplyStartPledgeWar,RequestStopPledgeWar,RequestReplyStopPledgeWar,RequestSurrenderPledgeWar,RequestReplySurrenderPledgeWar,RequestPledgeWarList}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/ClanWarsList.java`
- same under `mobius-il:java/org/l2jmobius/gameserver`; Mobius Essence: same + `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/ClanWar.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/enums/ClanWarState.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                         | Server → client                                                                                                                                                                               |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestStartPledgeWar` `4D`, `RequestReplyStartPledgeWar` `4E`, `RequestStopPledgeWar` `4F`, `RequestReplyStopPledgeWar` `50`, `RequestSurrenderPledgeWar` `51`, `RequestReplySurrenderPledgeWar` `52`, `RequestPledgeWarList` `D0:1E` | `StartPledgeWar` `65`, `StopPledgeWar` `67`, `SurrenderPledgeWar` `69`, `PledgeReceiveWarList` `FE:3E`                                                                                        |
| High Five | `RequestStartPledgeWar` `03`, `RequestReplyStartPledgeWar` `04`, `RequestStopPledgeWar` `05`, `RequestReplyStopPledgeWar` `06`, `RequestSurrenderPledgeWar` `07`, `RequestReplySurrenderPledgeWar` `08`, `RequestPledgeWarList` `D0:17` | `StartPledgeWar` `63`, `StopPledgeWar` `65`, `SurrenderPledgeWar` `67`, `PledgeReceiveWarList` `FE:3F`, `ReplyStartPledgeWar` `64`, `ReplyStopPledgeWar` `66`, `ReplySurrenderPledgeWar` `68` |
| Essence   | `RequestStartPledgeWar` `03`, `RequestReplyStartPledgeWar` `04`, `RequestStopPledgeWar` `05`, `RequestReplyStopPledgeWar` `06`, `RequestSurrenderPledgeWar` `07`, `RequestReplySurrenderPledgeWar` `08`, `RequestPledgeWarList` `D0:17` | `StartPledgeWar` `63`, `StopPledgeWar` `65`, `SurrenderPledgeWar` `67`, `PledgeReceiveWarList` `FE:40`, `ExEnemyKillLog` `FE:0371`                                                            |

Notes: Mobius HF has no `ClanWar` class; the war list is a set of clan ids inside `Clan` (`warList`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java:204`).
Known fixes: `l2j-server@b1fdd174` (2007-06-15, crash-npe) — Fixing a NPE in PledgeReceiveWarList; `l2jfrozen@9a49feee` (2011-02-23, other) — Fixed message for request clan war (Thanks Strike for report).

### clan.warehouse — Clan warehouse

ID: `clan.warehouse` · Shared: **engine**

The clan has a shared warehouse; access is gated by privileges (view / take). Items are deposited and withdrawn at a warehouse NPC;
transfers are logged.
Sub-features: deposit · withdraw · privilege check · item log.

| Chronicle | Present | Builds (evidence)                             | Difference                    |
| --------- | ------- | --------------------------------------------- | ----------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `ClanWarehouse` container     |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same                          |
| Essence   | yes     | mobius-ess                                    | same (bypass `ClanWarehouse`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/itemcontainer/ClanWarehouse.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/ClanWarehouse.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/itemcontainer/ClanWarehouse.java`; Mobius Essence: `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/ClanWarehouse.java`

Packets

| Chronicle | Client → server                                                                                | Server → client                                             |
| --------- | ---------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| Interlude | `SendWareHouseDepositList` `31`, `SendWareHouseWithDrawList` `32` (shared with item.warehouse) | `WareHouseDepositList` `41`, `WareHouseWithdrawalList` `42` |
| High Five | `SendWareHouseDepositList` `3B`, `SendWareHouseWithDrawList` `3C` (shared with item.warehouse) | `WareHouseDepositList` `41`, `WareHouseWithdrawalList` `42` |
| Essence   | `SendWareHouseDepositList` `3B`, `SendWareHouseWithDrawList` `3C` (shared with item.warehouse) | `WareHouseDepositList` `41`, `WareHouseWithdrawalList` `42` |

Notes: container and packets are shared with `item.warehouse`; here only the clan-specific rules.

### clan.notice — Clan notice

ID: `clan.notice` · Shared: **partial**

A text notice that is shown to members on login; the leader edits and enables it. In Interlude/High Five it is edited in the
Community Board (`_bbsclan_clannotice_edit`), in Essence in the clan window.
Sub-features: edit · enable/disable · show on enter world.

| Chronicle | Present | Builds (evidence)    | Difference                                                                                                                                                                                                                                 |
| --------- | ------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, l2jfrozen | Community Board edit (`mobius-il:dist/game/data/scripts/handlers/bypass/communityboard/ClanBoard.java:112`), table `clan_notices`; not in aCis                                                                                             |
| High Five | yes     | nx-l2, eternity      | same (`nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/ClanBoard.java:80-84`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java:938,963`) |
| Essence   | differs | mobius-ess           | `RequestExPledgeV3SetAnnounce`, `RequestExPledgeV3Info`; max length 8192 (`mobius-ess:java/org/l2jmobius/gameserver/entity/clan/Clan.java:177`)                                                                                            |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/ClanBoard.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/clan/Clan.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/EnterWorld.java`
- `mobius-il:dist/game/data/scripts/handlers/bypass/communityboard/ClanBoard.java`; Mobius Essence: `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pledgeV3/{RequestExPledgeV3SetAnnounce,RequestExPledgeV3Info}.java`

Packets

| Chronicle | Client → server                                                             | Server → client                |
| --------- | --------------------------------------------------------------------------- | ------------------------------ |
| Interlude | `RequestBBSwrite` `22`, `RequestBypassToServer` `21` (CB)                   | Community Board output packets |
| High Five | `RequestBBSwrite` `24`, `RequestBypassToServer` `23` (CB)                   | Community Board output packets |
| Essence   | `RequestExPledgeV3SetAnnounce` `D0:01CD`, `RequestExPledgeV3Info` `D0:01C8` | `ExPledgeV3Info` `FE:0279`     |

Notes: Mobius IL and HF edit the notice from the Community Board (`_bbsclan_clannotice_edit`) and show it in `EnterWorld`; Essence moves it into the clan window with the `pledgeV3` packets.

### clan.recruitment — Clan recruitment board

ID: `clan.recruitment` · Shared: **chronicle-only**

A recruitment board: clans publish recruitment cards (focus, level range, text), players browse/search and apply, applicants wait in
a waiting list; leaders accept; a "sign in for open joining" mode.
Sub-features: recruit board search/detail · application · waiting list · draft list · open joining.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                                         |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | no class found                                                                                                                     |
| High Five | no      | nx-l2, l2j-server, sunrise | not in Mobius HF; L2jEternityWorld has only an NPC service (`eternity:server_scripts/java/l2e/scripts/services/ClanSearcher.java`) |
| Essence   | yes     | mobius-ess                 | `ClanEntryManager`, `entity/clan/entry/{PledgeApplicantInfo,PledgeRecruitInfo,PledgeWaitingInfo}`, `ClanEntryStatus`               |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/ClanEntryManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/entry/*.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestPledge{RecruitBoardAccess,RecruitBoardSearch,RecruitBoardDetail,RecruitInfo,RecruitApplyInfo,WaitingApply,WaitingApplied,WaitingList,WaitingUser,WaitingUserAccept,DraftListApply,DraftListSearch,SignInForOpenJoiningMethod}.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | Server → client                                                                                                                                                                                                                                                                                                                                                       |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestPledgeRecruitBoardAccess` `D0:D6`, `RequestPledgeRecruitBoardSearch` `D0:D5`, `RequestPledgeRecruitBoardDetail` `D0:D7`, `RequestPledgeRecruitInfo` `D0:D4`, `RequestPledgeRecruitApplyInfo` `D0:DF`, `RequestPledgeWaitingApply` `D0:D8`, `RequestPledgeWaitingApplied` `D0:D9`, `RequestPledgeWaitingList` `D0:DA`, `RequestPledgeWaitingUser` `D0:DB`, `RequestPledgeWaitingUserAccept` `D0:DC`, `RequestPledgeDraftListApply` `D0:DE`, `RequestPledgeDraftListSearch` `D0:DD`, `RequestPledgeSignInForOpenJoiningMethod` `D0:0111` | `ExPledgeRecruitInfo` `FE:0140`, `ExPledgeRecruitBoardSearch` `FE:0142`, `ExPledgeRecruitBoardDetail` `FE:0143`, `ExPledgeRecruitApplyInfo` `FE:0141`, `ExPledgeWaitingList` `FE:0145`, `ExPledgeWaitingListApplied` `FE:0144`, `ExPledgeWaitingUser` `FE:0146`, `ExPledgeWaitingListAlarm` `FE:0148`, `ExPledgeDraftListSearch` `FE:0147`, `ExPledgeCount` `FE:013E` |

Notes: —
Known fixes: `mobius@04e87661` (2022-10-25, other) — Fixed player join clan with penalty from clan entry application. Contributed by Enryu.

### clan.bonus — Clan bonuses (Essence)

ID: `clan.bonus` · Shared: **chronicle-only**

Clan members receive login/hunting bonuses from a reward table tied to clan level; rewards are claimed in the bonus window and the
daily mark resets.
Sub-features: bonus window · reward list · claim · mark reset.

| Chronicle | Present | Builds (evidence) | Difference                                                                        |
| --------- | ------- | ----------------- | --------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis   | —                                                                                 |
| High Five | no      | nx-l2, l2j-server | —                                                                                 |
| Essence   | yes     | mobius-ess        | `ClanRewardBonus`, `ClanRewardType`, `mobius-ess:dist/game/config/ClanReward.xml` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/ClanRewardBonus.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/clan/enums/ClanRewardType.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pledgebonus/{RequestPledgeBonusOpen,RequestPledgeBonusReward,RequestPledgeBonusRewardList}.java`

Packets

| Chronicle | Client → server                                                                                                    | Server → client                                                                                                                   |
| --------- | ------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestPledgeBonusOpen` `D0:0122`, `RequestPledgeBonusReward` `D0:0124`, `RequestPledgeBonusRewardList` `D0:0123` | `ExPledgeBonusOpen` `FE:01AF`, `ExPledgeBonusList` `FE:01B0`, `ExPledgeBonusUpdate` `FE:01B2`, `ExPledgeBonusMarkReset` `FE:01B1` |

Notes: —

### clan.donation — Clan donations (Essence)

ID: `clan.donation` · Shared: **chronicle-only**

Members donate to the clan in exchange for clan coins/experience; the window shows the donation state and contribution ranking.
Sub-features: donation request · donation info · contribution list.

| Chronicle | Present | Builds (evidence) | Difference                                   |
| --------- | ------- | ----------------- | -------------------------------------------- |
| Interlude | no      | mobius-il, acis   | —                                            |
| High Five | no      | nx-l2, l2j-server | —                                            |
| Essence   | yes     | mobius-ess        | package `pledgedonation`, `ExPledgeCoinInfo` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pledgedonation/{RequestExPledgeDonationInfo,RequestExPledgeDonationRequest,RequestExPledgeContributionList}.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/variables/ClanVariables.java`

Packets

| Chronicle | Client → server                                                                                                                  | Server → client                                                                                                                           |
| --------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestExPledgeDonationInfo` `D0:01F3`, `RequestExPledgeDonationRequest` `D0:01F4`, `RequestExPledgeContributionList` `D0:01F5` | `ExPledgeDonationInfo` `FE:02AF`, `ExPledgeCoinInfo` `FE:02B6`, `ExPledgeContributionList` `FE:02B1`, `ExPledgeDonationRequest` `FE:02B0` |

Notes: Weekly contribution counters are kept in the per-clan variable store `ClanVariables` (table `clan_variables`, Essence only).
Known fixes: `mobius@b25618c9` (2021-12-17, other) — Fixed clan contribution points decreasing clan exp. Contributed by nasseka.

### clan.enemies — Clan enemy list (Essence)

ID: `clan.enemies` · Shared: **chronicle-only**

A clan registers other clans as enemies (a list independent of wars) shown in the clan window.
Sub-features: register · delete · list.

| Chronicle | Present | Builds (evidence) | Difference         |
| --------- | ------- | ----------------- | ------------------ |
| Interlude | no      | mobius-il, acis   | —                  |
| High Five | no      | nx-l2             | —                  |
| Essence   | yes     | mobius-ess        | package `pledgeV3` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/pledgeV3/{RequestExPledgeEnemyRegister,RequestExPledgeEnemyDelete,RequestExPledgeEnemyInfoList}.java`

Packets

| Chronicle | Client → server                                                                                                            | Server → client                   |
| --------- | -------------------------------------------------------------------------------------------------------------------------- | --------------------------------- |
| Essence   | `RequestExPledgeEnemyRegister` `D0:01CA`, `RequestExPledgeEnemyDelete` `D0:01CB`, `RequestExPledgeEnemyInfoList` `D0:01C9` | `ExPledgeEnemyInfoList` `FE:027A` |

Notes: —
Known fixes: `mobius@e33c0959` (2023-02-17, other) — Fixed declaring and removing clan wars.
