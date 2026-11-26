# Rewards and activities

Daily missions, attendance, achievements and other reward systems.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### reward.daily-missions — Daily missions

ID: `reward.daily-missions` · Shared: **chronicle-only**

Per-character missions with a progress counter and reward items, reset daily / weekly / monthly or one-time: kill N monsters in a level band (daily hunting), reach a level, finish a quest, fish, join a clan, enchant a henna, use an item, take part in an Olympiad match or castle siege, kill a boss, login on weekend / month. A handler class per mission type reports progress; the client shows the list in the to-do / daily-mission window and a completed mission is claimed with one request.
Sub-features: hunting (daily / weekly) · level-up · quest · fishing · clan join · olympiad · siege · boss kill · weekend / monthly login · reward items per class group.

| Chronicle | Present | Builds (evidence)          | Difference                                                     |
| --------- | ------- | -------------------------- | -------------------------------------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —                                                              |
| High Five | no      | nx-l2, l2j-server          | Mobius HF has no daily-mission feature                         |
| Essence   | yes     | mobius-ess                 | baseline: 14 handlers, 90 reward entries in `DailyMission.xml` |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/DailyMissionData.java`, `mobius-ess:dist/game/data/scripts/handlers/dailymissions/PurgeRewardDailyMissionHandler.java`, `mobius-ess:dist/game/data/DailyMission.xml`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/dailymission/RequestOneDayRewardReceive.java`

Packets

| Chronicle | Client → server                       | Server → client                                                                                                                                                         |
| --------- | ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_ONE_DAY_RECEIVE_REWARD` `D0:0120` | `EX_ONE_DAY_REWARD_LIST` `FE:01A9`, `EX_ONE_DAY_REWARD_INFO` `FE:01AA`, `EX_TODOLIST_RECOMMAND` `FE:01AB`, `EX_TODOLIST_INZONE` `FE:01AC`, `EX_TODOLIST_HTML` `FE:01AD` |

Notes: handler names: Boss, EnchantHenna, Fishing, JoinClan, Level, LoginMonth, LoginWeekend, Monster, Olympiad, PurgeReward, Quest, Siege, Spirit, UseItem.

### reward.mission-level — Mission Level

ID: `reward.mission-level` · Shared: **chronicle-only**

Seasonal mission-pass: XP earned from missions fills levels (maxLevel 30 in the shipped season), each level unlocks a free and a bonus reward; the player can pay to jump a level; seasons are identified by year-month.
Sub-features: season table · exp table per level · reward list · level jump.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/MissionLevel.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/dailymission/RequestMissionLevelReceiveReward.java`, `mobius-ess:dist/game/data/MissionLevel.xml`

Packets

| Chronicle | Client → server                                                                                                                | Server → client                          |
| --------- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- |
| Essence   | `EX_MISSION_LEVEL_REWARD_LIST` `D0:0237`, `EX_MISSION_LEVEL_RECEIVE_REWARD` `D0:0238`, `EX_MISSION_LEVEL_JUMP_LEVEL` `D0:0239` | `EX_MISSION_LEVEL_REWARD_LIST` `FE:02F5` |

Notes: source XML warns that EXP for levels 4-30 is not retail-exact (TODO in data file).

### reward.attendance — Attendance rewards

ID: `reward.attendance` · Shared: **chronicle-only**

Monthly attendance calendar: one reward per day when the player logs in and presses the check (or on a popup), with a delay after login, optional premium/VIP-only, shared across account or per character, loop or real-calendar matching.
Sub-features: attendance check · item list · popup on login · premium / VIP gating · cycle start day · real-day matching.

| Chronicle | Present | Builds (evidence)          | Difference                               |
| --------- | ------- | -------------------------- | ---------------------------------------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —                                        |
| High Five | no      | nx-l2, l2j-server          | —                                        |
| Essence   | yes     | mobius-ess                 | baseline (disabled by default in config) |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/AttendanceRewardData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/attendance/RequestVipAttendanceCheck.java`, `mobius-ess:dist/game/config/AttendanceRewards.ini`, `mobius-ess:dist/game/data/AttendanceRewards.xml`

Packets

| Chronicle | Client → server                                                                                                                                                                           | Server → client                                                                                                                                               |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_REQUEST_ACCOUNT_ATTENDANCE_INFO` `D0:FC`, `EX_REQUEST_ACCOUNT_ATTENDANCE_REWARD` `D0:FD`, `EX_REQUEST_VIP_ATTENDANCE_ITEMLIST` `D0:0106`, `EX_REQUEST_VIP_ATTENDANCE_CHECK` `D0:0107` | `EX_ACCOUNT_ATTENDANCE_INFO` `FE:0175`, `EX_VIP_ATTENDANCE_ITEMLIST` `FE:017F`, `EX_CONFIRM_VIP_ATTENDANCE_CHECK` `FE:0180`, `EX_NOTIFY_ATTENDANCE` `FE:030A` |

Notes: `EnableAttendanceRewards = False`, `AttendanceRewardDelay = 30` in shipped config.

### reward.achievement-box — Achievement box

ID: `reward.achievement-box` · Shared: **chronicle-only**

Essence "steady box": the character earns achievement points (generic and PvP kills) and fills box slots; each slot can be opened to roll a reward, then claimed.
Sub-features: slot unlock · box open · reward claim · PvP points.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/steadybox/RequestSteadyOpenBox.java`, `mobius-ess:java/org/l2jmobius/gameserver/config/AchievementBoxConfig.java`, `mobius-ess:dist/game/config/AchievementBox.ini`

Packets

| Chronicle | Client → server                                                                                                                   | Server → client                                                                                                                                 |
| --------- | --------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `EX_STEADY_BOX_LOAD` `D0:01D5`, `EX_STEADY_OPEN_SLOT` `D0:01D6`, `EX_STEADY_OPEN_BOX` `D0:01D7`, `EX_STEADY_GET_REWARD` `D0:01D8` | `EX_STEADY_BOX_UI_INIT` `FE:0289`, `EX_STEADY_ALL_BOX_UPDATE` `FE:028A`, `EX_STEADY_ONE_BOX_UPDATE` `FE:028B`, `EX_STEADY_BOX_REWARD` `FE:028C` |

Notes: defaults `EnabledAchievementBox = True`, `PointsForReward = 1000`, `PointsForPvpReward = 5`.

### reward.achievements — Achievements

ID: `reward.achievements` · Shared: **chronicle-only**

Server-side achievement list: categories with counters (kills, PvP, quests, level, etc.), progress saved per character, rewards granted on completion, shown by an NPC or a voiced command.
Sub-features: categories · counters · NPC window · voiced command · rewards.

| Chronicle | Present | Builds (evidence)                                  | Difference                           |
| --------- | ------- | -------------------------------------------------- | ------------------------------------ |
| Interlude | no      | acis, l2jfrozen, mobius-il                         | —                                    |
| High Five | yes     | eternity, advanced, premium (not Mobius)           | `AchievementsManager` + NPC instance |
| Essence   | no      | mobius-ess (box variant: `reward.achievement-box`) | —                                    |

Entry points

- `eternity:core/java/l2e/gameserver/model/entity/mods/AchievementsManager.java`, `eternity:core/java/l2e/gameserver/model/actor/instance/L2AchievementsInstance.java`, `eternity:core/java/l2e/gameserver/model/entity/mods/base/Achievement.java`

Packets: none.

Notes: absent from Mobius.

### reward.payback — Payback

ID: `reward.payback` · Shared: **chronicle-only**

Essence event that returns part of spent currency: the player's purchases of the event coin accumulate, and rewards (multisell lists) can be claimed within a level range until the end time.
Sub-features: payback list · give reward · UI launcher.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/managers/events/PaybackManager.java`

Packets

| Chronicle | Client → server                                                 | Server → client                                                                                     |
| --------- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| Essence   | `EX_PAYBACK_LIST` `D0:0175`, `EX_PAYBACK_GIVE_REWARD` `D0:0176` | `EX_PAYBACK_LIST` `FE:021C`, `EX_PAYBACK_GIVE_REWARD` `FE:021D`, `EX_PAYBACK_UI_LAUNCHER` `FE:021E` |

Notes: fields: coin id, min / max level, multisell ids, end time.

### reward.stream-rewards — Stream rewards

ID: `reward.stream-rewards` · Shared: **chronicle-only**

Rewards for streaming the server on Twitch: a voiced command checks the player's stream (minimum viewers, game name, server name in the title) and, if it passes, grants configured items at a minimum interval.
Sub-features: stream check · viewer minimum · title and game match · item reward.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | yes     | premium                    | baseline   |
| Essence   | no      | mobius-ess                 | —          |

Entry points

- `premium:java/premium/gameserver/kara/twitch/TwitchManager.java`

Packets: none.

Notes: calls the Twitch API through a bundled client library.
