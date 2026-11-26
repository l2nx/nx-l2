# Social

Social ties between players: friends, block list, mail contacts, social actions, couples and mentoring.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### social.friends — Friends

ID: `social.friends` · Shared: **partial**

Players invite each other as friends (request/answer), see online status, remove friends and send private messages through the friend list.
Essence adds friend memos and a detail window.
Sub-features: invite/answer · list with status · remove · friend message · memo and detail (Essence).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                        |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline: `RequestFriendInvite` / `RequestAnswerFriendInvite` / `RequestFriendDel` / `RequestFriendList` / `RequestSendFriendMsg` |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same + `RequestExFriendListExtended`                                                                                              |
| Essence   | differs | mobius-ess                                    | package `friend`: + `RequestFriendDetailInfo`, `RequestUpdateFriendMemo`; `RequestFriendInvite` ~80 lines apart from IL           |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestFriendInvite,RequestAnswerFriendInvite,RequestFriendDel,RequestFriendList,RequestSendFriendMsg,RequestExFriendListExtended}.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/FriendsBoard.java`
- same under `mobius-il:java/org/l2jmobius/gameserver`; Mobius Essence: `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/friend/*.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                    | Server → client                                                                                                                                                                                             |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestFriendInvite` `5E`, `RequestAnswerFriendInvite` `5F`, `RequestFriendDel` `61`, `RequestFriendList` `60`, `RequestSendFriendMsg` `CC`                                                                       | `FriendAddRequest` `7D`, `FriendPacket` `FA`, `FriendList` `FA`, `FriendStatusPacket` `FC`, `L2Friend` `FB`, `L2FriendSay` `FD`                                                                             |
| High Five | `RequestFriendInvite` `77`, `RequestAnswerFriendInvite` `78`, `RequestFriendDel` `7A`, `RequestFriendList` `79`, `RequestSendFriendMsg` `6B`, `RequestExFriendListExtended` `D0:87`                                | `FriendAddRequest` `83`, `FriendPacket` `76`, `FriendList` `58/75`, `FriendStatusPacket` `77`, `L2Friend` `76`, `L2FriendSay` `78`, `FriendListExtended` `58`                                               |
| Essence   | `RequestFriendInvite` `77`, `RequestAnswerFriendInvite` `78`, `RequestFriendDel` `7A`, `RequestFriendList` `79`, `RequestSendFriendMsg` `6B`, `RequestFriendDetailInfo` `D0:95`, `RequestUpdateFriendMemo` `D0:96` | `FriendAddRequest` `83`, `FriendAddRequestResult` `55`, `FriendList` `58`, `FriendRemove` `57`, `FriendStatus` `59`, `L2Friend` `76`, `L2FriendList` `75`, `L2FriendSay` `78`, `ExFriendDetailInfo` `FE:ED` |

Notes: —
Known fixes: `l2j-server@95e322fb` (2007-03-23, crash-npe) — ClassCastException Fixes for Friendlist NPE's Fix (Sora88)

### social.block-list — Block list

ID: `social.block-list` · Shared: **partial**

A player blocks another so that private messages and invites are rejected; the list is shown and edited with a block/unblock command and window.
Essence adds block memos and detail.
Sub-features: block/unblock · list · block-all mode · memo and detail (Essence).

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                    |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | `RequestBlock`, `BlockList`                                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same                                                                                          |
| Essence   | differs | mobius-ess                                    | + `RequestBlockMemo`, `RequestBlockDetailInfo`; server `BlockListPacket`, `ExBlockDetailInfo` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/BlockList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestBlock.java`
- Mobius IL / Essence: same under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`; Essence: `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/friend/{RequestBlockMemo,RequestBlockDetailInfo}.java`

Packets

| Chronicle | Client → server                                                                     | Server → client                                           |
| --------- | ----------------------------------------------------------------------------------- | --------------------------------------------------------- |
| Interlude | `RequestBlock` `A0`                                                                 | `BlockList` (not in the opcode table) (system texts)      |
| High Five | `RequestBlock` `A9`                                                                 | `BlockList` `D5` (system texts)                           |
| Essence   | `RequestBlock` `A9`, `RequestBlockMemo` `D0:97`, `RequestBlockDetailInfo` `D0:0127` | `BlockListPacket` `D5`, `ExBlockDetailInfo` `FE:ED/FE:F0` |

Notes: `BlockList` differs by 17 lines between Mobius IL and Essence.

### social.contact-list — Mail contact list

ID: `social.contact-list` · Shared: **partial (HF+Ess)**

A contact list (up to 100 names) used as the address book of the mail window; add, delete, show.
Sub-features: add · delete · show · size limit 100.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                                               |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen | Mobius IL has a `ContactList` holder referenced by `Player` but no packets (dead code)                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/ContactList.java:96` limit 100) |
| Essence   | yes     | mobius-ess                 | same packets                                                                                                                             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/ContactList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestExAddContactToContactList,RequestExDeleteContactFromContactList,RequestExShowContactList}.java`
- same names under `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server                                                                                                                 | Server → client             |
| --------- | ------------------------------------------------------------------------------------------------------------------------------- | --------------------------- |
| High Five | `RequestExAddContactToContactList` `D0:84`, `RequestExDeleteContactFromContactList` `D0:85`, `RequestExShowContactList` `D0:86` | `ExShowContactList` `FE:D3` |
| Essence   | `RequestExAddContactToContactList` `D0:82`, `RequestExDeleteContactFromContactList` `D0:83`, `RequestExShowContactList` `D0:84` | `ExShowContactList` `FE:D5` |

Notes: —

### social.social-actions — Social actions

ID: `social.social-actions` · Shared: **partial**

The client sends a social action (greeting, victory, dance, etc.); the server validates it (not in combat/stance) and broadcasts `SocialAction`
to nearby players; NPCs play random animations via a task manager.
Sub-features: player social action · multi-social (couple) action · NPC random animation.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                                                   |
| --------- | ------- | --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | dedicated client packet `RequestSocialAction` (Mobius IL)                                                                                                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | no `RequestSocialAction` class; broadcast in `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestActionUse.java:1219-1220`, multi-action `:1424` |
| Essence   | differs | mobius-ess                                    | player action handler `SocialAction` (script `mobius-ess:dist/game/data/scripts/handlers/actions/user/SocialAction.java`)                                                                    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestActionUse.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/SocialAction.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/RandomAnimationTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestSocialAction.java`; Mobius Essence: `mobius-ess:dist/game/data/scripts/handlers/actions/user/SocialAction.java`

Packets

| Chronicle | Client → server            | Server → client     |
| --------- | -------------------------- | ------------------- |
| Interlude | `RequestSocialAction` `1B` | `SocialAction` `2D` |
| High Five | `RequestActionUse` `56`    | `SocialAction` `27` |
| Essence   | `RequestActionUse` `56`    | `SocialAction` `27` |

Notes: HF and Essence send social actions through the generic action packet; Interlude has the separate packet.
Known fixes: `hellas@ebf02d7e` (2013-06-07, wrong-formula-value) — 2 Npes Fixed No social actions when you are dead fixed critical power.

### social.wedding — Wedding and couples (Mobius custom)

ID: `social.wedding` · Shared: **engine**

Mobius custom: engagement, ceremony at a wedding NPC, couple status with teleport to the spouse, divorce; optional infidelity punishment.
Sub-features: engage · ceremony · teleport to spouse · divorce · formal wear · infidelity punish.

| Chronicle | Present | Builds (evidence)                                              | Difference                                                       |
| --------- | ------- | -------------------------------------------------------------- | ---------------------------------------------------------------- |
| Interlude | yes     | mobius-il (custom), acis, l2jbrasil, hellas, l2j-server-686500 | `CoupleManager`, `Couple`, wedding NPC                           |
| High Five | yes     | nx-l2 (custom), l2j-server, eternity                           | `CoupleManager`, `Couple` identical to Essence; voiced `Wedding` |
| Essence   | yes     | mobius-ess (custom)                                            | `CoupleManager` and `Couple` have 0 differing lines vs Mobius IL |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CoupleManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/Couple.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/events/Wedding/Wedding.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/Wedding.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/Wedding.ini`; also: `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/holders/RequestPartner.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/CoupleManager.java`, `mobius-il:dist/game/data/scripts/custom/events/Wedding/Wedding.java`, `mobius-il:dist/game/config/Custom/Wedding.ini`; Mobius Essence: same under `mobius-ess:java/org/l2jmobius/gameserver` / `mobius-ess:dist/game/data/scripts` / `mobius-ess:dist/game/config`; also: `mobius-il:java/org/l2jmobius/gameserver/network/holders/RequestPartner.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/holders/RequestPartner.java`

Packets

| Chronicle | Client → server                             | Server → client                                    |
| --------- | ------------------------------------------- | -------------------------------------------------- |
| Interlude | `RequestSocialAction` `1B` (couple actions) | `SocialAction` `2D`                                |
| High Five | `AnswerCoupleAction` `D0:7A`                | `ExAskCoupleAction` `FE:BB` (`SystemMessage` `62`) |
| Essence   | `AnswerCoupleAction` `D0:78`                | `ExAskCoupleAction` `FE:BD` (`SystemMessage` `62`) |

Notes: `AnswerCoupleAction` exists in nx-l2 and Essence packet lists, absent from Mobius IL.
Known fixes: `l2j-server@b5ccdb3b` (2007-07-15, missing-check) — Wedding fixes by Ahmed: Some preventions for .gotolove -Duel -Event -Festival Grammar/Text Cleanup.

### social.mentoring — Mentoring (Essence)

ID: `social.mentoring` · Shared: **chronicle-only**

A veteran player (mentor) takes up to a few mentees; the pair gets bonuses, the mentor earns mentor coins when mentees level; the waiting list lets mentees find mentors.
Sub-features: add/confirm mentee · waiting list · cancel · mentor coins · mentor-target skills.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                         |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | mobius-il, acis, l2jfrozen           | —                                                                                                                  |
| High Five | no      | nx-l2, l2j-server, sunrise, eternity | Lineage2TS has only a lore document (`lineage2ts:cli/documents/lore/mentoring-system.md`), no implementation found |
| Essence   | yes     | mobius-ess                           | `MentorManager`, `mobius-ess:dist/game/config/MentorCoins.xml`, skill target `MyMentor`                            |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/MentorManager.java`, `mobius-ess:dist/game/config/MentorCoins.xml`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/mentoring/{RequestMenteeAdd,ConfirmMenteeAdd,RequestMenteeWaitingList,RequestMentorCancel,RequestMentorList}.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/targets/MyMentor.java`

Packets

| Chronicle | Client → server                                                                                                                                        | Server → client                                                                  |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
| Essence   | `RequestMenteeAdd` `D0:B8`, `ConfirmMenteeAdd` `D0:B5`, `RequestMenteeWaitingList` `D0:B9`, `RequestMentorCancel` `D0:B6`, `RequestMentorList` `D0:B7` | `ExMentorAdd` `FE:011D`, `ExMentorList` `FE:011C`, `ListMenteeWaiting` `FE:011E` |

Notes: —

### social.user-watcher — User watcher (Essence)

ID: `social.user-watcher` · Shared: **chronicle-only**

A player adds other characters to a watch list and gets login/logout notifications and their status (the "surveillance" window).
Sub-features: add · delete · target list · status updates.

| Chronicle | Present | Builds (evidence) | Difference             |
| --------- | ------- | ----------------- | ---------------------- |
| Interlude | no      | mobius-il, acis   | —                      |
| High Five | no      | nx-l2             | —                      |
| Essence   | yes     | mobius-ess        | package `surveillance` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/surveillance/{ExRequestUserWatcherAdd,ExRequestUserWatcherDelete,ExRequestUserWatcherTargetList}.java`

Packets

| Chronicle | Client → server                                                                                                         | Server → client                                                            |
| --------- | ----------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Essence   | `ExRequestUserWatcherAdd` `D0:01EB`, `ExRequestUserWatcherDelete` `D0:01EC`, `ExRequestUserWatcherTargetList` `D0:01EA` | `ExUserWatcherTargetList` `FE:02A2`, `ExUserWatcherTargetStatus` `FE:02A3` |

Notes: —
