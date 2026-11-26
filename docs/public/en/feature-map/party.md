# Party

Temporary player groups: membership, loot and experience sharing, matching rooms and command channels.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### party.membership — Party membership

ID: `party.membership` · Shared: **partial**

A leader invites a target into a party: request/answer dialogs, join, leave, expel, change leader, disband when one
member remains. Party size cap, invite refusal rules and the party status windows are part of it.
Sub-features: invite/answer · leave · expel · change leader · disband · member list windows · member position updates.

| Chronicle | Present | Builds (evidence)                                         | Difference                                                                                                                                                                                                              |
| --------- | ------- | --------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, l2j-server | cap 9 hardcoded (aCis `aCis_gameserver/java/net/sf/l2j/gameserver/network/clientpackets/RequestJoinParty.java:98`, Mobius IL `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java:215`) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, premium             | baseline; cap 9 hardcoded (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestJoinParty.java:195`)                                                                         |
| Essence   | yes     | mobius-ess                                                | cap from config `AltPartyMaxMembers`, default 7 (`mobius-ess:java/org/l2jmobius/gameserver/config/PlayerConfig.java:555`); extra client packet `RequestNewInvitePartyInquiry`                                           |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/Party.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/AbstractPlayerGroup.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestJoinParty,RequestAnswerJoinParty,RequestWithDrawalParty,RequestOustPartyMember,RequestChangePartyLeader}.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/groups/Party.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java`; aCis `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/group/Party.java`; L2jFrozen `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/model/L2Party.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/Party.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{RequestJoinParty,RequestNewInvitePartyInquiry}.java`; also: `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/request/AbstractRequest.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                    | Server → client                                                                                                                                                                                                                                                                                                                                                        |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestJoinParty` `29`, `RequestAnswerJoinParty` `2A`, `RequestWithDrawalParty` `2B`, `RequestOustPartyMember` `2C`, `RequestChangePartyLeader` `D0:04`                                           | `AskJoinParty` `39`, `JoinParty` `3A`, `PartySmallWindowAll` `4E`, `PartySmallWindowAdd` `4F`, `PartySmallWindowUpdate` `52`, `PartySmallWindowDelete` `51`, `PartySmallWindowDeleteAll` `50`, `PartyMemberPosition` `A7`, `PartySpelled` `EE`                                                                                                                         |
| High Five | `RequestJoinParty` `42`, `RequestAnswerJoinParty` `43`, `RequestWithDrawalParty` `44`, `RequestOustPartyMember` `45`, `RequestChangePartyLeader` `D0:0C`                                           | `AskJoinParty` `39`, `JoinParty` `3A`, `PartySmallWindowAll` `4E`, `PartySmallWindowAdd` `4F`, `PartySmallWindowUpdate` `52`, `PartySmallWindowDelete` `51`, `PartySmallWindowDeleteAll` `50`, `PartyMemberPosition` `BA`, `PartySpelled` `F4`, `DismissParty` `3E`, `OustPartyMember` `3C`, `SetDismissParty` `3F`, `SetOustPartyMember` `3D`, `WithdrawalParty` `3B` |
| Essence   | `RequestJoinParty` `42`, `RequestAnswerJoinParty` `43`, `RequestWithDrawalParty` `44`, `RequestOustPartyMember` `45`, `RequestChangePartyLeader` `D0:0C`, `RequestNewInvitePartyInquiry` `D0:021F` | `AskJoinParty` `39`, `JoinParty` `3A`, `PartySmallWindowAll` `4E`, `PartySmallWindowAdd` `4F`, `PartySmallWindowUpdate` `52`, `PartySmallWindowDelete` `51`, `PartySmallWindowDeleteAll` `50`, `PartyMemberPosition` `BA`, `PartySpelled` `F4`, `ExRequestNewInvitePartyInquiry` `FE:02DD`                                                                             |

Notes: Mobius HF/IL `Party` is one code lineage (IL is an HF backport); Essence `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/Party.java` is ~280 lines longer (tactical signs, exp types, configurable size).
Known fixes: `l2j-server@916d2993` (2009-03-25, crash-npe) — Party NPE fixes. Thx Kerberos :); `l2jfrozen@78d31a6f` (2011-08-22, crash-npe) — NPE fix thx Previous Fix L2Party protected methods.

### party.loot — Party loot distribution

ID: `party.loot` · Shared: **partial**

Leader sets one of five distribution modes; the change request is put to a vote of the members (`requestLootChange`);
spoil can be included in the rule. Items are handed out on drop by the chosen rule.
Sub-features: finders keepers · random · random incl. spoil · by turn · by turn incl. spoil · mode-change vote.

| Chronicle | Present | Builds (evidence)                                        | Difference                                                                                                                                                                                                                        |
| --------- | ------- | -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis (`ITEM_RANDOM`, `ITEM_ORDER`, `*_SPOIL`) | rule is chosen only at party creation (`RequestJoinParty` carries it, `AskJoinParty` shows it); no in-party change vote: `RequestPartyLootModification` / `AnswerPartyLootModification` are absent; aCis names the modes `ITEM_*` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity                     | baseline: `PartyDistributionType`, `RequestPartyLootModification`                                                                                                                                                                 |
| Essence   | yes     | mobius-ess                                               | identical enum (ids 0..4)                                                                                                                                                                                                         |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/PartyDistributionType.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/Party.java` (`distributeItem`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestPartyLootModification,AnswerPartyLootModification}.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/groups/PartyDistributionType.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/PartyDistributionType.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestPartyLootModification.java`

Packets

| Chronicle | Client → server                                                               | Server → client                                                                                              |
| --------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestJoinParty` `29` (carries the loot rule)                               | `AskJoinParty` `39` (shows the rule), `SystemMessage` `64`                                                   |
| High Five | `RequestPartyLootModification` `D0:78`, `AnswerPartyLootModification` `D0:79` | `SystemMessage` `62` (vote and result texts), `ExAskModifyPartyLooting` `FE:BF`, `ExSetPartyLooting` `FE:C0` |
| Essence   | `RequestPartyLootModification` `D0:76`, `AnswerPartyLootModification` `D0:77` | `SystemMessage` `62` (vote and result texts), `ExAskModifyPartyLooting` `FE:C1`, `ExSetPartyLooting` `FE:C2` |

Notes: from High Five on, the leader can change the rule inside a running party through a member vote (`ExAskModifyPartyLooting` / `ExSetPartyLooting`); in Interlude the rule is fixed when the party is created.

### party.exp-share — Party exp/sp bonus

ID: `party.exp-share` · Shared: **partial**

Exp and SP of a kill are split among party members in range by level share and multiplied by a bonus that grows with party
size (`BONUS_EXP_SP`, indexed by member count); level-gap filtering and the sqLevel share belong to the same formula.
Sub-features: size bonus table · level-gap filter · sqLevel split · exp-type config.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                                |
| --------- | ------- | -------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | acis, l2jfrozen (Mobius IL mirrors HF) | table starts 1.30 (2 members), 1.39, 1.50, 1.54, 1.58, 1.63, 1.67; Mobius IL instead carries the HF table |
| High Five | yes     | nx-l2, l2j-server, eternity            | baseline: {1.0, 1.10, 1.20, 1.30, 1.40, 1.50, 2.0, 2.10, 2.20}                                            |
| Essence   | yes     | mobius-ess                             | {1.0, 1.6, 1.65, 1.7, 1.8, 1.9, 2.0, 2.1, 2.2}; size 7; `PartyExpType` selectable by config               |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/Party.java` (`BONUS_EXP_SP` L81, `distributeXpAndSp`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/PartyExpType.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/groups/Party.java` (L73); aCis `acis:aCis_gameserver/java/net/sf/l2j/gameserver/model/group/Party.java` (L46); L2jFrozen `l2jfrozen:gameserver/head-src/com/l2jfrozen/gameserver/model/L2Party.java` (L63)
- `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/Party.java` (L87), `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/PartyExpType.java`

Packets

| Chronicle | Client → server | Server → client                                                 |
| --------- | --------------- | --------------------------------------------------------------- |
| Interlude | —               | `SystemMessage` `64`, `StatusUpdate` `0E` (EXP / SP attributes) |
| High Five | —               | `SystemMessage` `62`, `StatusUpdate` `18` (EXP / SP attributes) |
| Essence   | —               | `SystemMessage` `62`, `StatusUpdate` `18` (EXP / SP attributes) |

Notes: Mobius IL does not reproduce the Interlude party bonuses; use aCis / L2jFrozen as the Interlude baseline.
Known fixes: `l2j-server@916d2993` (2009-03-25, crash-npe) — Party NPE fixes. Thx Kerberos :); `l2jfrozen@78d31a6f` (2011-08-22, crash-npe) — NPE fix thx Previous Fix L2Party protected methods; `sunrise@e07ff15c` (2015-08-04, crash-npe) — Minor party null pointer Minor change for some broken calculations.

### party.matching — Party matching rooms

ID: `party.matching` · Shared: **partial**

A player opens a matching room (title, level range, loot rule); others browse the list or sit in a waiting list and request
to join; the owner accepts, the room has its own chat channel and can be dissolved.
Sub-features: room list · waiting list · owner management · room chat · (Essence) history and announce.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                            |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `PartyMatchRoom`, `PartyMatchRoomList`, `PartyMatchWaitingList`                                                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same classes as IL                                                                                                                    |
| Essence   | differs | mobius-ess                                    | `MatchingRoomManager` + `MatchingRoom` hierarchy (`PartyMatchingRoom`, `CommandChannelMatchingRoom`), `MatchingRoomHistory`, announce |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/matching/{PartyMatchRoom,PartyMatchRoomList,PartyMatchWaitingList}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestPartyMatchConfig,RequestPartyMatchList,RequestPartyMatchDetail,RequestListPartyMatchingWaitingRoom,RequestAskJoinPartyRoom,AnswerJoinPartyRoom,RequestDismissPartyRoom,RequestWithdrawPartyRoom,RequestOustFromPartyRoom,RequestExitPartyMatchingWaitingRoom}.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/groups/matching/*.java` (same names)
- `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/matching/*.java`, `mobius-ess:java/org/l2jmobius/gameserver/managers/MatchingRoomManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{RequestPartyMatchingHistory,RequestPartyMatchingAnnounce}.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                                                                            | Server → client                                                                                                                                                                                                                                            |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestPartyMatchConfig` `6F`, `RequestPartyMatchList` `70`, `RequestPartyMatchDetail` `71`, `RequestListPartyMatchingWaitingRoom` `D0:16`, `RequestAskJoinPartyRoom` `D0:14`, `AnswerJoinPartyRoom` `D0:15`, `RequestWithdrawPartyRoom` `D0:03`, `RequestOustFromPartyRoom` `D0:01`, `RequestDismissPartyRoom` `D0:02`, `RequestExitPartyMatchingWaitingRoom` `D0:17`                                                                                    | `PartyMatchDetail` `97`, `ListPartyWating` `96`, `ExPartyRoomMember` `FE:0E`, `ExManagePartyRoomMember` `FE:10`, `ExAskJoinPartyRoom` `FE:34`, `ExClosePartyRoom` `FE:0F`, `ExListPartyMatchingWaitingRoom` `FE:35`                                        |
| High Five | `RequestPartyMatchConfig` `7F`, `RequestPartyMatchList` `80`, `RequestPartyMatchDetail` `81`, `RequestListPartyMatchingWaitingRoom` `D0:31`, `RequestAskJoinPartyRoom` `D0:2F`, `AnswerJoinPartyRoom` `D0:30`, `RequestWithdrawPartyRoom` `D0:0B`, `RequestOustFromPartyRoom` `D0:09`, `RequestDismissPartyRoom` `D0:0A`, `RequestExitPartyMatchingWaitingRoom` `D0:25`                                                                                    | `PartyMatchDetail` `9D`, `ListPartyWating` `9C`, `ExPartyRoomMember` `FE:08`, `ExManagePartyRoomMember` `FE:0A`, `ExAskJoinPartyRoom` `FE:35`, `ExClosePartyRoom` `FE:09`, `ExListPartyMatchingWaitingRoom` `FE:36`                                        |
| Essence   | `RequestPartyMatchConfig` `7F`, `RequestPartyMatchList` `80`, `RequestPartyMatchDetail` `81`, `RequestListPartyMatchingWaitingRoom` `D0:31`, `RequestAskJoinPartyRoom` `D0:2F`, `AnswerJoinPartyRoom` `D0:30`, `RequestWithdrawPartyRoom` `D0:0B`, `RequestOustFromPartyRoom` `D0:09`, `RequestDismissPartyRoom` `D0:0A`, `RequestExitPartyMatchingWaitingRoom` `D0:25`, `RequestPartyMatchingHistory` `D0:011C`, `RequestPartyMatchingAnnounce` `D0:02D2` | `PartyRoomInfo` `9D`, `ExPartyRoomMember` `FE:08`, `ExPartyRoomAnnounce` `FE:03BC`, `ExRequestPartyMatchingHistory` `FE:01A6`, `ExAskJoinPartyRoom` `FE:35`, `ExClosePartyRoom` `FE:09`, `ExListPartyMatchingWaitingRoom` `FE:36`, `ListPartyWaiting` `9C` |

Notes: Essence reshapes the rooms into a `MatchingRoom` hierarchy (party rooms and command-channel rooms) with its own packet set.

### party.command-channel — Command channel

ID: `party.command-channel` · Shared: **partial**

A clan leader of pledge class ≥5 with the Clan Imperium skill (id 391) invites another party leader into a multi-party
channel; the channel has its own chat, raid-loot privileges at a configured size and info windows.
Sub-features: ask/accept · oust · leave/delete · show parties info · channel chat · loot privilege size.

| Chronicle | Present | Builds (evidence)                     | Difference                                                                                                                                                                                      |
| --------- | ------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil | invite rule class ≥5 + skill 391 (`mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestExAskJoinMPCC.java:150`)                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity  | same (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExAskJoinMPCC.java:128`)                                                                  |
| Essence   | differs | mobius-ess                            | same invite rule (`:150`) + command-channel matching rooms (`RequestEx*Mpcc*Room`), config `AltCommandChannelFriends` (`mobius-ess:java/org/l2jmobius/gameserver/config/PlayerConfig.java:558`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/groups/CommandChannel.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestExAskJoinMPCC,RequestExAcceptJoinMPCC,RequestExOustFromMPCC,RequestExMPCCShowPartyMembersInfo}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/{ChannelDelete,ChannelInfo,ChannelLeave}.java`; also: `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/attackable/CommandChannelTimer.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/groups/CommandChannel.java`, same client packets; also: `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/attackable/CommandChannelTimer.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/CommandChannel.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/matching/CommandChannelMatchingRoom.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/{RequestExJoinMpccRoom,RequestExManageMpccRoom,RequestExDismissMpccRoom,RequestExWithdrawMpccRoom,RequestExListMpccWaiting,RequestExMpccPartymasterList,RequestExOustFromMpccRoom}.java`; also: `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/attackable/CommandChannelTimer.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                                                                               | Server → client                                                                                                                                                                                                                                                                                                                                                                |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestExAskJoinMPCC` `D0:0D`, `RequestExAcceptJoinMPCC` `D0:0E`, `RequestExOustFromMPCC` `D0:0F`, `RequestExMPCCShowPartyMembersInfo` `D0:26`                                                                                                                                                                                                                                                               | `ExAskJoinMPCC` `FE:27`, `ExOpenMPCC` `FE:25`, `ExCloseMPCC` `FE:26`, `ExMultiPartyCommandChannelInfo` `FE:30`, `ExMPCCShowPartyMemberInfo` `FE:4A`                                                                                                                                                                                                                            |
| High Five | `RequestExAskJoinMPCC` `D0:06`, `RequestExAcceptJoinMPCC` `D0:07`, `RequestExOustFromMPCC` `D0:08`, `RequestExMPCCShowPartyMembersInfo` `D0:2D`                                                                                                                                                                                                                                                               | `ExAskJoinMPCC` `FE:1A`, `ExOpenMPCC` `FE:12`, `ExCloseMPCC` `FE:13`, `ExMultiPartyCommandChannelInfo` `FE:31`, `ExMPCCShowPartyMemberInfo` `FE:4B`, `ExSetMpccRouting` `FE:37`                                                                                                                                                                                                |
| Essence   | `RequestExAskJoinMPCC` `D0:06`, `RequestExAcceptJoinMPCC` `D0:07`, `RequestExOustFromMPCC` `D0:08`, `RequestExMPCCShowPartyMembersInfo` `D0:2D`, `RequestExJoinMpccRoom` `D0:5C`, `RequestExManageMpccRoom` `D0:5B`, `RequestExDismissMpccRoom` `D0:5E`, `RequestExWithdrawMpccRoom` `D0:5F`, `RequestExOustFromMpccRoom` `D0:5D`, `RequestExListMpccWaiting` `D0:5A`, `RequestExMpccPartymasterList` `D0:61` | `ExAskJoinMPCC` `FE:1A`, `ExOpenMPCC` `FE:12`, `ExCloseMPCC` `FE:13`, `ExMultiPartyCommandChannelInfo` `FE:31`, `ExMPCCShowPartyMemberInfo` `FE:4C`, `ExMPCCRoomInfo` `FE:9C`, `ExMPCCRoomMember` `FE:A0`, `ExManageMpccRoomMember` `FE:0A/FE:9F`, `ExDissmissMPCCRoom` `FE:9E`, `ExListMpccWaiting` `FE:9D`, `ExMPCCPartymasterList` `FE:A3`, `ExMPCCPartyInfoUpdate` `FE:5C` |

Notes: —
Known fixes: `l2j-server@43bc1854` (2011-04-17, exploit-dupe) — BETA: Exploit fix for removing party from channel that's not in yours! (thanks Nik and JIV); `sunrise@54facb73` (2015-10-23, wrong-formula-value) — Fixed for command channel low level chars getting exp.

### party.tactical-signs — Tactical signs

ID: `party.tactical-signs` · Shared: **chronicle-only**

Party members mark targets with tactical signs; marks are shown to all party members and a target can be selected by sign.
Sub-features: sign use · target by sign · cleanup on party leave.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                             |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen           | no class or packet found                                                                                               |
| High Five | no      | nx-l2, l2j-server, sunrise, eternity | no class or packet found in any public HF build                                                                        |
| Essence   | yes     | mobius-ess                           | `Party.applyTacticalSigns` (`mobius-ess:java/org/l2jmobius/gameserver/entity/groups/Party.java:434`), `ExTacticalSign` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/entity/groups/Party.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExTacticalSign.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/{TacticalSignUse,TacticalSignTarget}.java`

Packets

| Chronicle | Client → server                                                                   | Server → client            |
| --------- | --------------------------------------------------------------------------------- | -------------------------- |
| Essence   | `RequestActionUse` `56` (action handlers `TacticalSignUse`, `TacticalSignTarget`) | `ExTacticalSign` `FE:0101` |

Notes: there is no dedicated client packet; sign actions go through the player-action handler registry.
