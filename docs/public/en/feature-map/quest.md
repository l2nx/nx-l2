# Quests

Scripted quests: the quest engine, quest script catalog, quest list UI and the new-character tutorial.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### quest.engine — Quest engine

ID: `quest.engine` · Shared: **partial**

Scripted quests as state machines: `Quest` (event/talk/kill/attack/skill/spawn/zone hooks registered per NPC id), `QuestState` (CREATED / STARTED / COMPLETED, `cond` and memo-state variables persisted per character), quest timers, quest items (separate inventory slot group, removed on abort), drops tied to quest state, repeatable vs one-time exit, party-member selection for shared kills. The same hook-registration model exists in every version; Essence adds dialog types, quest conditions and the new quest-list hooks on top.
Sub-features: quest states · cond / memo-state · quest timers · quest items and drops · party quest helpers · quest events on the listener bus · restart-time (daily quests).

| Chronicle | Present | Builds (evidence)            | Difference                                                                 |
| --------- | ------- | ---------------------------- | -------------------------------------------------------------------------- |
| Interlude | yes     | acis, l2jfrozen, mobius-il   | same state model; QuestState API and script language differ per family     |
| High Five | yes     | nx-l2, l2j-server, mobius-hf | baseline (`mechanics/script/Quest.java`, ~5.2k lines)                      |
| Essence   | yes     | mobius-ess                   | adds `QuestCondition`, `QuestDialogType`, `newquestdata` goal/reward model |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/script/{Quest.java,QuestState.java,QuestTimer.java,State.java,QuestType.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/script/timers/TimerHolder.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/script/Quest.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/script/timers/TimerHolder.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/script/{Quest.java,QuestState.java,QuestCondition.java,QuestDialogType.java}`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/script/timers/TimerHolder.java`

Packets

| Chronicle | Client → server                                       | Server → client                                                                                                                                                                                              |
| --------- | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `REQUEST_QUEST_LIST` `63`, `REQUEST_QUEST_ABORT` `64` | `QUEST_LIST` `80`, `GM_VIEW_QUEST_INFO` `93`, `EX_SHOW_QUEST_MARK` `FE:1A`, `EX_SHOW_QUEST_INFO` `FE:19`                                                                                                     |
| High Five | `REQUEST_QUEST_LIST` `62`, `REQUEST_QUEST_ABORT` `63` | `QUEST_LIST` `86`, `EX_QUEST_ITEM_LIST` `FE:C6`, `EX_QUEST_NPC_LOG_LIST` `FE:C5`, `EX_NPC_QUEST_HTML_MESSAGE` `FE:8D`, `GM_VIEW_QUEST_INFO` `99`, `EX_SHOW_QUEST_MARK` `FE:21`, `EX_SHOW_QUEST_INFO` `FE:20` |
| Essence   | `REQUEST_QUEST_LIST` `62`, `REQUEST_QUEST_ABORT` `63` | `QUEST_LIST` `86`, `EX_QUEST_ITEM_LIST` `FE:C8`, `EX_QUEST_NPC_LOG_LIST` `FE:C7`, `EX_NPC_QUEST_HTML_MESSAGE` `FE:8E`, `GM_VIEW_QUEST_INFO` `99`, `EX_SHOW_QUEST_MARK` `FE:21`, `EX_SHOW_QUEST_INFO` `FE:20` |

Notes: Mobius IL `Quest.java` (5.6k lines) is larger than the HF one (5.3k) and Essence (6.5k); the three are one lineage, so equal logic does not prove IL behaviour — aCis and L2jFrozen keep a separate, smaller quest class per family.

Known fixes: `l2jfrozen@089b038c33` (2011-04-02, exploit-dupe) — Quest State fix, Exploit (cast over sell action) maybe fixed, Multisell distance fixed; `l2j-server@f9fceeca5d` (2010-01-07, crash-npe) — Fix for //reload quests NPE, same as before. No, its not recommended to use on live!; `sunrise@3629062547` (2016-05-29, crash-npe) — Fixed some null points cause of python quests

### quest.scripts — Quest script catalog

ID: `quest.scripts` · Shared: **partial**

Per-chronicle catalog of quest scripts (one class per quest, optionally an HTML dialog folder), loaded by the script engine; engine feature is `quest.engine`, individual quests are not listed. Content differs by chronicle: Interlude has the original C5 list, High Five adds Gracia/Freya/Kamael quests (Q10xxx Gracia series, Seven Signs, Pailaka, Hellbound), Essence replaces the old list with its own series.
Sub-features: class-transfer quests (profession trials, sagas) · starter / newbie quests · daily and repeatable quests · instance-entry quests · Seven Signs quest line.

| Chronicle | Present | Builds (evidence)                                   | Difference                                                            |
| --------- | ------- | --------------------------------------------------- | --------------------------------------------------------------------- |
| Interlude | yes     | acis (~340), l2jfrozen (~370), mobius-il (344 dirs) | original C5 set; no Gracia quests                                     |
| High Five | yes     | nx-l2 (513 dirs), l2j-server-dp (~527)              | baseline; adds Kamael, Gracia, Freya, Seven Signs, per-race tutorials |
| Essence   | differs | mobius-ess (301 dirs)                               | `Q1xxxx` newbie/class-change series, daily quests via daily missions  |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/quests/` (513 quest folders)
- `mobius-il:dist/game/data/scripts/quests`
- `mobius-ess:dist/game/data/scripts/quests`

Packets: none of its own (uses the quest engine packets).

Notes: forks of the surveyed builds mostly differ in datapack HTML only; none adds a new quest mechanism to the engine.

### quest.ui — Quest list

ID: `quest.ui` · Shared: **engine**

Player opens the quest list (`QUEST_LIST` with per-quest state/cond flags), aborts a quest from the list, and the server pushes quest markers (`?`/`!`) on NPCs and tracks the active-quest count; abort wipes the quest state and quest items. Same flow in all three versions; Essence additionally renders the list through the new quest UI (`quest.essence-quest-ui`).
Sub-features: list · abort · quest marker / mini-map mark · NPC quest-log list.

| Chronicle | Present | Builds (evidence)            | Difference                                         |
| --------- | ------- | ---------------------------- | -------------------------------------------------- |
| Interlude | yes     | acis, l2jfrozen, mobius-il   | baseline                                           |
| High Five | yes     | nx-l2, l2j-server, mobius-hf | adds `EX_QUEST_NPC_LOG_LIST`, `EX_QUEST_ITEM_LIST` |
| Essence   | yes     | mobius-ess                   | same handlers, packet ids differ                   |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestQuestList.java,RequestQuestAbort.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/QuestList.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/QuestList.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestQuestList.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestQuestAbort.java`

Packets

| Chronicle | Client → server                                                                                 | Server → client                                                                                                                              |
| --------- | ----------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `REQUEST_QUEST_LIST` `63`, `REQUEST_QUEST_ABORT` `64`                                           | `QUEST_LIST` `80`, `EX_SHOW_QUEST_MARK` `FE:1A`, `EX_SHOW_QUEST_INFO` `FE:19`                                                                |
| High Five | `REQUEST_QUEST_LIST` `62`, `REQUEST_QUEST_ABORT` `63`, `REQUEST_ADD_EXPAND_QUEST_ALARM` `D0:7D` | `QUEST_LIST` `86`, `EX_QUEST_ITEM_LIST` `FE:C6`, `EX_QUEST_NPC_LOG_LIST` `FE:C5`, `EX_SHOW_QUEST_MARK` `FE:21`, `EX_SHOW_QUEST_INFO` `FE:20` |
| Essence   | `REQUEST_QUEST_LIST` `62`, `REQUEST_QUEST_ABORT` `63`, `REQUEST_ADD_EXPAND_QUEST_ALARM` `D0:7B` | `QUEST_LIST` `86`, `EX_QUEST_ITEM_LIST` `FE:C8`, `EX_QUEST_NPC_LOG_LIST` `FE:C7`, `EX_SHOW_QUEST_MARK` `FE:21`, `EX_SHOW_QUEST_INFO` `FE:20` |

Notes: Shared is `engine` on handler logic only; the list payload layout differs by protocol.

Known fixes: `mobius@9e341d2429` (2017-08-26, wrong-formula-value) — Solve wrong quest state show in the window.

### quest.essence-quest-ui — Essence quest UI

ID: `quest.essence-quest-ui` · Shared: **chronicle-only**

Essence client shows a dedicated quest window: acceptable-quest list by level/class, accept and complete from the window, teleport to the quest start location, quest notifications, and a daily-quest list. Data comes from `NewQuestData.xml` (387 quests in RoseVain: id, type, start/end NPC, location ids, class conditions, pre-quest, item rewards, goal count and goal text), not from the script alone.
Sub-features: acceptable list · accept / cancel / complete from UI · quest teleport · notification (all) · quest zone selection · daily quest list.

| Chronicle | Present | Builds (evidence)          | Difference |
| --------- | ------- | -------------------------- | ---------- |
| Interlude | no      | acis, l2jfrozen, mobius-il | —          |
| High Five | no      | nx-l2, l2j-server          | —          |
| Essence   | yes     | mobius-ess                 | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/quest/RequestExQuestAccept.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/xml/NewQuestData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/script/newquestdata/{NewQuest.java,NewQuestGoal.java,NewQuestCondition.java,NewQuestReward.java}`, `mobius-ess:dist/game/data/NewQuestData.xml` (`RequestExQuestUI`, `RequestExQuestTeleport`)

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                  | Server → client                                                                                                                                                                                                                                                                                                          |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Essence   | `EX_QUEST_UI` `D0:026F`, `EX_QUEST_ACCEPTABLE_LIST` `D0:0270`, `EX_QUEST_ACCEPT` `D0:026B`, `EX_QUEST_CANCEL` `D0:026C`, `EX_QUEST_COMPLETE` `D0:026D`, `EX_QUEST_TELEPORT` `D0:026A`, `EX_QUEST_NOTIFICATION_ALL` `D0:026E`, `EX_SELECTED_QUEST_ZONEID` `D0:FF` | `EX_QUEST_UI` `FE:033A`, `EX_QUEST_ACCEPTABLE_LIST` `FE:033C`, `EX_QUEST_DIALOG` `FE:0337`, `EX_QUEST_NOTIFICATION` `FE:0338`, `EX_QUEST_NOTIFICATION_ALL` `FE:0339`, `EX_QUEST_ACCEPTABLE_ALARM` `FE:033B`, `EX_DYNAMIC_QUEST` `FE:EA`, `EX_COMPLETED_DAILY_QUEST_LIST` `FE:0200`, `EX_COMPLETED_DAILY_QUEST` `FE:0201` |

Notes: Mobius Main (SamuraiCrow) has the same packet set; no HF/IL equivalent exists.

### quest.tutorial — Tutorial

ID: `quest.tutorial` · Shared: **partial**

New-character tutorial: tutorial quest (255) drives HTML dialog windows, client-event hooks (sit, attack, pick-up, level) and question marks; a bypass `TutorialClose` closes it. High Five splits it into per-race tutorial quests (201-209) after Q255; Interlude has Q255 plus `Q999_T0Tutorial`; Essence keeps only the packet/bypass layer and a client-side hint id packet (no Q255 script).
Sub-features: tutorial quest · client events · question mark · link / pass-command handlers · close.

| Chronicle | Present | Builds (evidence)            | Difference                                               |
| --------- | ------- | ---------------------------- | -------------------------------------------------------- |
| Interlude | yes     | acis, l2jfrozen, mobius-il   | Q255 + T0 tutorial                                       |
| High Five | yes     | nx-l2, l2j-server, mobius-hf | + per-race tutorial quests Q201-Q209, `EX_TUTORIAL_LIST` |
| Essence   | differs | mobius-ess                   | packets only; `EX_TUTORIAL_SHOW_ID`, no Q255 script      |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/quests/Q00255_Tutorial/Q00255_Tutorial.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/quests/Dummy/Q00201_HumanFighterTutorial.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/TutorialClose.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/{ExCaptureOrc.java,ExSearchOrc.java}`
- `mobius-il:dist/game/data/scripts/quests/Q00255_Tutorial`, `mobius-il:dist/game/data/scripts/quests/Q00999_T0Tutorial/Q00999_T0Tutorial.java`, `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/{ExCaptureOrc.java,ExShowSlideshowKamael.java}`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/npc/TutorialClose.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExSearchOrc.java`

Packets

| Chronicle | Client → server                                                                                                                                                                               | Server → client                                                                                                                                                                                                    |
| --------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `REQUEST_TUTORIAL_LINK_HTML` `7B`, `REQUEST_TUTORIAL_PASS_CMD_TO_SERVER` `7C`, `REQUEST_TUTORIAL_QUESTION_MARK` `7D`, `REQUEST_TUTORIAL_CLIENT_EVENT` `7E`                                    | `TUTORIAL_SHOW_HTML` `A0`, `TUTORIAL_SHOW_QUESTION_MARK` `A1`, `TUTORIAL_ENABLE_CLIENT_EVENT` `A2`, `TUTORIAL_CLOSE_HTML` `A3`, `EX_SEARCH_ORC` `FE:44`, `EX_SHOW_SLIDESHOW_KAMAEL` `FE:5B`                        |
| High Five | `REQUEST_TUTORIAL_LINK_HTML` `85`, `REQUEST_TUTORIAL_PASS_CMD_TO_SERVER` `86`, `REQUEST_TUTORIAL_QUESTION_MARK` `87`, `REQUEST_TUTORIAL_CLIENT_EVENT` `88`, `REQUEST_EX_SET_TUTORIAL` `D0:1D` | `TUTORIAL_SHOW_HTML` `A6`, `TUTORIAL_SHOW_QUESTION_MARK` `A7`, `TUTORIAL_ENABLE_CLIENT_EVENT` `A8`, `TUTORIAL_CLOSE_HTML` `A9`, `EX_TUTORIAL_LIST` `FE:6B`, `EX_SEARCH_ORC` `FE:45`                                |
| Essence   | `REQUEST_TUTORIAL_LINK_HTML` `85`, `REQUEST_TUTORIAL_PASS_CMD_TO_SERVER` `86`, `REQUEST_TUTORIAL_QUESTION_MARK` `87`, `REQUEST_TUTORIAL_CLIENT_EVENT` `88`                                    | `TUTORIAL_SHOW_HTML` `A6`, `TUTORIAL_SHOW_QUESTION_MARK` `A7`, `TUTORIAL_ENABLE_CLIENT_EVENT` `A8`, `TUTORIAL_CLOSE_HTML` `A9`, `EX_TUTORIAL_LIST` `FE:6C`, `EX_TUTORIAL_SHOW_ID` `FE:01E6`, `EX_ORC_MOVE` `FE:46` |

Notes: Q255 listens to `OnPlayerSit`, `OnCreatureAttacked`, `OnPlayerItemPickup`, `OnPlayerLevelChanged` events on the listener bus.

Known fixes: `mobius@318e0f6685` (2025-04-15, crash-npe) — Fixed tutorial packet event NPE. Contributed by sant11h.; `l2j-server-dp@1d538d4b03` (2017-05-07, missing-check) — Allow AlternateClassMaster to work without AllowClassMasters
