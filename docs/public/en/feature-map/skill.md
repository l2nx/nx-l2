# Skills

Skill engine: data, learning, casting, effects, buffs and debuffs, and the skill-based subsystems (cubics, transformations).

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### skill.engine — Skill engine

ID: `skill.engine` · Shared: **partial**

Server-side skill model: skill templates are loaded from XML per id+level (enchant routes as extra levels), with
operate type (active/passive/toggle/channeling), reuse and reuse groups, MP/HP/item consumption, cast/hit times,
effect lists and conditions attached to the template. Sub-features: XML loader · operate types · reuse groups ·
custom skills overlay · skill level/sub-level addressing.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                                                                                                    |
| --------- | ------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, mobius-c6            | Mobius IL uses the same `DocumentSkill` + `Skill` model as HF (`SkillData` byte-identical); 37 skill XML files                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline: `DocumentSkill` parses `<for>`/`<cond>`/effects; 95 skill XML files                                                                                                 |
| Essence   | differs | mobius-ess, mobius-ess, l2emu-ess                | Classic-style schema: `SkillData` is the parser itself (no `DocumentSkill`), effects split into start/pump/tick scopes, conditions are separate handlers, 371 skill XML files |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/SkillData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/util/DocumentSkill.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/Skill.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/SkillOperateType.java`, `nx-l2:chronicles/high-five/datapack/data/stats/skills/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/SkillData.java`, `mobius-il:java/org/l2jmobius/gameserver/util/DocumentSkill.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/skill/Skill.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SkillData.java` (1412 lines, own XML parser), `mobius-ess:java/org/l2jmobius/gameserver/mechanics/skill/Skill.java`, `mobius-ess:dist/game/data/stats/skills`

Packets

| Chronicle | Client → server         | Server → client                                                           |
| --------- | ----------------------- | ------------------------------------------------------------------------- |
| Interlude | `RequestSkillList` `3F` | `SkillList` `58`                                                          |
| High Five | `RequestSkillList` `50` | `SkillList` `5F`, `GMViewSkillInfo` `97`                                  |
| Essence   | `RequestSkillList` `50` | `SkillList` `5F`, `GMViewSkillInfo` `97`, `ExAlterSkillRequest` `FE:0110` |

Notes: aCis, L2jFrozen and L2Emu Essence keep the older `L2Skill` + `SkillTable` model with skill-type subclasses
(`L2SkillDrain`, `L2SkillElemental`, …); Mobius/L2J Server/L2jSunrise moved logic into effect handlers. Lineage2TS
(HF) stores skill data in SQLite generated from XML (`cli/source/datapack/transformer/`).

Known fixes: `l2j-server@09423ff8` (2010-03-20, wrong-formula-value) — Fix for #4486 New function in SkillTable: boolean isEnchantable(skillId) should be used for checking, instead of level.

### skill.cast — Skill casting

ID: `skill.cast` · Shared: **partial**

Using a skill: client request → checks (conditions, MP/HP/item cost, reuse, target, range, geodata) → cast with
hit/cool time → launch → effects; cancel by move/damage/stun; ground-targeted skills; channelized skills that tick
on their targets; skill cooldown sync to the client. Sub-features: cast/cancel · consumption · ground skills ·
channeling · cooldown sync (`RequestSkillCoolTime`) · stored cooldown on logout.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                                                 |
| --------- | ------- | -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, mobius-c6  | same Creature cast pipeline as HF; `RequestExMagicSkillUseGround` exists in Mobius IL (backport, not an IL client feature) |
| High Five | yes     | nx-l2, l2j-server, sunrise, lineage2ts | baseline: `Creature.doCast` + `SkillChannelizer`                                                                           |
| Essence   | differs | mobius-ess, l2emu-ess                  | `SkillCaster` object per cast with `SkillCastingType` (normal / second slot = double cast), auto-skill support             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestMagicSkillUse.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExMagicSkillUseGround.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestSkillCoolTime.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/SkillChannelizer.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/holders/SkillUseHolder.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/creature/MagicUseTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/creature/QueuedMagicUseTask.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestMagicSkillUse.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/skill/SkillChannelizer.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/creature/MagicUseTask.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/creature/QueuedMagicUseTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/skill/SkillCaster.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/skill/SkillCastingType.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestMagicSkillUse.java`

Packets

| Chronicle | Client → server                                                                                                                | Server → client                                                                                                                                                                            |
| --------- | ------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | `RequestMagicSkillUse` `2F`, `RequestExMagicSkillUseGround` `D0:2F`, `RequestSkillCoolTime` `9D`                               | `MagicSkillUse` `48`, `MagicSkillLaunched` `76`, `MagicSkillCanceled` `49`, `SetupGauge` `6D`, `SkillCoolTime` `C1`, `ActionFailed` `25`                                                   |
| High Five | `RequestMagicSkillUse` `39`, `RequestExMagicSkillUseGround` `D0:44`, `RequestSkillCoolTime` `A6`                               | `MagicSkillUse` `48`, `MagicSkillLaunched` `54`, `MagicSkillCanceled` `49`, `SetupGauge` `6B`, `SkillCoolTime` `C7`, `ActionFailed` `1F`                                                   |
| Essence   | `RequestMagicSkillUse` `39`, `RequestExMagicSkillUseGround` `D0:41`, `RequestSkillCoolTime` `A6`, `RequestMagicSkillList` `38` | `MagicSkillUse` `48`, `MagicSkillLaunched` `54`, `MagicSkillCanceled` `49`, `ExMagicSkillUseGround` `FE:02A8`, `ExShowChannelingEffect` `FE:E1`, `SkillCoolTime` `C7`, `ActionFailed` `1F` |

Notes: `SkillChannelizer` is near-identical in all three Mobius trees (2 / 74 diff lines). Essence `General.ini
EnableAutoSkill` ties casting to autoplay.

Known fixes: `l2j-server@934c0c12` (2015-09-14, crash-npe) — Fix NPE in Channelizer skills; `mobius@30b69e38` (2019-10-15, crash-npe) — Fixed skill channelizer NPE. Contributed by Sahar.

### skill.learn — Skill learning

ID: `skill.learn` · Shared: **partial**

Skill trees per class and per special kind; learning at an NPC (cost in SP, adena, spellbooks), auto-learn on level
up, removal on de-level, forgotten-scroll skills. Sub-features: class trees · fishing · pledge / sub-pledge ·
transform · collect · sub-class / transfer · special (noble, hero, GM) · auto-learn · spellbook cost.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                                                            |
| --------- | ------- | -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen             | `AcquireSkillType` = CLASS, FISHING, PLEDGE only; trees per 1st/2nd/3rd class + noble/hero/GM                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise, lineage2ts | baseline: CLASS, FISHING, PLEDGE, SUBPLEDGE, TRANSFORM, TRANSFER, SUBCLASS, COLLECT                                                   |
| Essence   | differs | mobius-ess, l2emu-ess                  | adds REVELATION, DUALCLASS, ALCHEMY kinds (most unused in Essence data); race-specific trees (Kamael, Sylph, Death Knight, Vanguard…) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/SkillTreeData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/holders/SkillLearn.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestAcquireSkill.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestAcquireSkillInfo.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/SkillList.java`, `nx-l2:chronicles/high-five/datapack/data/stats/players/skillTrees/`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/SkillTreeData.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/skill/enums/AcquireSkillType.java`, `mobius-il:dist/game/data/stats/players/skillTrees`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SkillTreeData.java` (2094 lines), `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestAcquireSkill.java`

Packets

| Chronicle | Client → server                                            | Server → client                                                                                                                                                                                                                                    |
| --------- | ---------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestAcquireSkillInfo` `6B`, `RequestAcquireSkill` `6C` | `AcquireSkillList` `8A`, `AcquireSkillInfo` `8B`, `AcquireSkillDone` `8E`, `SkillList` `58`, `PledgeSkillList` `FE:39`, `PledgeSkillListAdd` `FE:3A`                                                                                               |
| High Five | `RequestAcquireSkillInfo` `73`, `RequestAcquireSkill` `7C` | `AcquireSkillList` `90`, `AcquireSkillInfo` `91`, `AcquireSkillDone` `94`, `PledgeSkillList` `FE:3A`, `PledgeSkillListAdd` `FE:3B`, `ExSubPledgeSkillAdd` `FE:76`                                                                                  |
| Essence   | `RequestAcquireSkillInfo` `73`, `RequestAcquireSkill` `7C` | `AcquireSkillList` `90`, `AcquireSkillInfo` `91`, `AcquireSkillDone` `94`, `ExAcquireSkillInfo` `FE:FD`, `ExAcquireSkillResult` `FE:0343`, `ExAcquirableSkillListByClass` `FE:FB`, `ExNewSkillToLearnByLevelUp` `FE:FE`, `PledgeSkillList` `FE:3A` |

Notes: auto-learn config (`AutoLearnSkills`, `AutoLearnForgottenScrollSkills`, `DecreaseSkillOnDelevel`) is the same
in all three Mobius `Player.ini`.

Known fixes: `mobius@74f3eab7` (2017-09-15, crash-npe) — Fixed possible RequestAcquireSkillInfo NPE; `mobius@adb8f6cf` (2018-01-18, crash-npe) — Probable fix for RequestAcquireSkill NPE.

### skill.enchant — Skill enchanting

ID: `skill.enchant` · Shared: **partial**

Raising a 3rd-class skill above its max level through enchant routes. HF: normal / safe / untrain / route change,
SP + adena + Giant's Codex cost, chance by level. Essence: star-based enchant with charge points from items,
SP extraction, guaranteed enchant. Sub-features: routes · safe · untrain · change route · cost tables · logging.

| Chronicle | Present | Builds (evidence)                     | Difference                                                                                                                                                                           |
| --------- | ------- | ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | differs | mobius-il, acis, l2jfrozen, mobius-c6 | per-skill `EnchantSkillTreeData.xml`; only normal enchant (`RequestExEnchantSkill` + `Info`), no safe/untrain/route change                                                           |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity  | baseline: `EnchantSkillGroups.xml`, packets Enchant / Safe / Untrain / RouteChange / InfoDetail                                                                                      |
| Essence   | differs | mobius-ess, l2emu-ess                 | `SkillEnchantData.xml` (stars, charge exp from items), new packets: `RequestExSkillEnchantCharge`, `RequestExSpExtract*`, `RequestExtractSkillEnchant`, `RequestSkillEnchantConfirm` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/EnchantSkillGroupsData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/holders/EnchantSkillLearn.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExEnchantSkill.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExEnchantSkillSafe.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExEnchantSkillUntrain.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExEnchantSkillRouteChange.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/EnchantSkillTreeData.java`, `mobius-il:dist/game/data/EnchantSkillTreeData.xml`, `mobius-il:dist/game/data/scripts/handlers/bypass/npc/EnchantSkillList.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/SkillEnchantData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/newskillenchant/RequestExSkillEnchantCharge.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/skillenchantextract/RequestExtractSkillEnchant.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/skillenchantguarantee/RequestSkillEnchantConfirm.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                                                                                                                                         | Server → client                                                                                                                                                                                                                                                                                                                         |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestExEnchantSkillInfo` `D0:06`, `RequestExEnchantSkill` `D0:07`                                                                                                                                                                                                                                                                                    | `ExEnchantSkillList` `FE:17`, `ExEnchantSkillInfo` `FE:18`                                                                                                                                                                                                                                                                              |
| High Five | `RequestExEnchantSkillInfo` `D0:0E`, `RequestExEnchantSkillInfoDetail` `D0:46`, `RequestExEnchantSkill` `D0:0F`, `RequestExEnchantSkillSafe` `D0:32`, `RequestExEnchantSkillUntrain` `D0:33`, `RequestExEnchantSkillRouteChange` `D0:34`                                                                                                                | `ExEnchantSkillList` `FE:29`, `ExEnchantSkillInfo` `FE:2A`, `ExEnchantSkillInfoDetail` `FE:5E`, `ExEnchantSkillResult` `FE:A7`                                                                                                                                                                                                          |
| Essence   | `RequestExEnchantSkillInfo` `D0:0E`, `RequestExEnchantSkillInfoDetail` `D0:43`, `RequestExEnchantSkill` `D0:0F`, `RequestExSkillEnchantInfo` `D0:0271`, `RequestExSkillEnchantCharge` `D0:0272`, `RequestExSpExtractInfo` `D0:0268`, `RequestExSpExtractItem` `D0:0269`, `RequestExtractSkillEnchant` `D0:02AC`, `RequestSkillEnchantConfirm` `D0:02AD` | `ExEnchantSkillList` `FE:29`, `ExEnchantSkillInfo` `FE:2A`, `ExEnchantSkillInfoDetail` `FE:5F`, `ExEnchantSkillResult` `FE:A8`, `ExSkillEnchantInfo` `FE:033D`, `ExSkillEnchantCharge` `FE:033E`, `ExSpExtractInfo` `FE:0335`, `ExSpExtractItem` `FE:0336`, `ExExtractSkillEnchant` `FE:038C`, `ExRequestSkillEnchantConfirm` `FE:038D` |

Notes: Mobius IL keeps `SafeEnchantCostMultipiler` in `General.ini` although no safe-enchant packet exists (dead
option). L2J Server and L2jSunrise match the HF baseline.

Known fixes: `l2j-server@1d8ba70a` (2011-10-18, wrong-formula-value) — BETA: Fix for [4989], #5731:

### skill.effects — Effect engine

ID: `skill.effects` · Shared: **partial**

Effect handlers attached to skills: instant effects, continuous (abnormal) effects with ticks, pump/stat effects,
effect flags (stun, root, silence…), effect start/exit hooks. Sub-features: handler registry · instant/continuous ·
tick tasks · effect flags · stat-modifying effects.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                   |
| --------- | ------- | ------------------------------------ | -------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | same handler framework as HF; 146 handlers                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `AbstractEffect` + `EffectHandler`, 165 handlers; stats via `<for>` funcs in XML   |
| Essence   | differs | mobius-ess, l2emu-ess                | 464 handlers; stat changes are effect handlers (`AbstractStatEffect`, PvP/PvE bonus effects) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/effects/AbstractEffect.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/effects/EffectTickTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/effects/EffectFlag.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/EffectHandler.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/EffectMasterHandler.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/`
- `mobius-il:java/org/l2jmobius/gameserver/handler/EffectHandler.java`, `mobius-il:dist/game/data/scripts/handlers/EffectMasterHandler.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects`
- `mobius-ess:java/org/l2jmobius/gameserver/handler/EffectHandler.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects` (464 files), `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/instance/EffectPoint.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                                                                                                                                                                                   |
| --------- | --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `AbnormalStatusUpdate` `7F`, `ShortBuffStatusUpdate` `F4`, `StatusUpdate` `0E`                                                                                                                                                                                                                                    |
| High Five | —               | `AbnormalStatusUpdate` `85`, `ShortBuffStatusUpdate` `FA`, `ExChangeClientEffectInfo` `FE:C2`, `StatusUpdate` `18`                                                                                                                                                                                                |
| Essence   | —               | `AbnormalStatusUpdate` `85`, `ShortBuffStatusUpdate` `FA`, `ExAbnormalStatusUpdateFromTarget` `FE:E7`, `ExUserInfoAbnormalVisualEffect` `FE:0159`, `NpcInfoAbnormalVisualEffect` `4B`, `ExChangeClientEffectInfo` `FE:C4`; masks: `NpcInfoType.ABNORMALS` (bit `0x24`), `NpcInfoType.DISPLAY_EFFECT` (bit `0x16`) |

Notes: aCis / L2jFrozen / L2Emu Essence use `L2Effect` subclasses (`EffectBuff`, `EffectFear`…) instead of handlers.

Known fixes: `mobius@226d5593` (2022-01-20, exploit-dupe) — Fixed setImobilized exploit; `l2j-server-dp@4ebf41c4` (2013-05-19, wrong-formula-value) — BETA: Minor fixes to Damage Over time.

### skill.conditions — Conditions

ID: `skill.conditions` · Shared: **partial**

Conditions gating skill use and item/skill stat application: player level, race, class, zone, state (sitting,
moving, combat), weapon/armor kind, HP/MP/CP thresholds, target type, abnormal presence, logic and/or/not.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                    |
| --------- | ------- | -------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | XML condition tree (`mechanics/conditions`, 80 classes), same model as HF                                     |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: 88 `Condition*` classes built by `DocumentSkill`                                                    |
| Essence   | differs | mobius-ess                 | two layers: legacy `mechanics/conditions` (103, for items) + 130 skill-condition handlers (`*SkillCondition`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/Condition.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/` (88 files)
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/conditions`
- `mobius-ess:java/org/l2jmobius/gameserver/handler/SkillConditionHandler.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/skill/ISkillCondition.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/conditions`

Packets

| Chronicle | Client → server | Server → client                                             |
| --------- | --------------- | ----------------------------------------------------------- |
| Interlude | —               | `SystemMessage` `64` (failure reasons), `ActionFailed` `25` |
| High Five | —               | `SystemMessage` `62` (failure reasons), `ActionFailed` `1F` |
| Essence   | —               | `SystemMessage` `62` (failure reasons), `ActionFailed` `1F` |

Notes: L2Emu Essence keeps the HF-style condition tree (235 classes) rather than Mobius' handler split.

### skill.targets — Target types and affect area

ID: `skill.targets` · Shared: **partial**

How a skill picks its targets: single target, self, party, clan, area around caster/target, front/behind cones,
corpses, summons; area radius and affect limits.

| Chronicle | Present | Builds (evidence)                    | Difference                                                           |
| --------- | ------- | ------------------------------------ | -------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | 34 target handlers, same as HF                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: one handler per `TargetType` doing both selection and area |
| Essence   | differs | mobius-ess                           | split into 24 target types + 20 affect scopes + 11 affect objects    |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/TargetHandler.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/targets/` (34)
- `mobius-il:java/org/l2jmobius/gameserver/handler/TargetHandler.java`, `mobius-il:dist/game/data/scripts/handlers/skill/targets`
- `mobius-ess:java/org/l2jmobius/gameserver/handler/TargetHandler.java`, `mobius-ess:java/org/l2jmobius/gameserver/handler/AffectScopeHandler.java`, `mobius-ess:java/org/l2jmobius/gameserver/handler/AffectObjectHandler.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/targets/affectscope`, `mobius-ess:dist/game/data/scripts/handlers/skill/targets/affectobject`

Packets

| Chronicle | Client → server                                         | Server → client                         |
| --------- | ------------------------------------------------------- | --------------------------------------- |
| Interlude | — (targets come from `RequestMagicSkillUse` `2F` flags) | `MagicSkillLaunched` `76` (target list) |
| High Five | — (targets come from `RequestMagicSkillUse` `39` flags) | `MagicSkillLaunched` `54` (target list) |
| Essence   | — (targets come from `RequestMagicSkillUse` `39` flags) | `MagicSkillLaunched` `54` (target list) |

Notes: aCis has its own `handler/targethandlers/` (27 files); L2jFrozen resolves targets inside `L2Skill`.

Known fixes: `mobius@616f55bc` (2021-10-05, wrong-formula-value) — Fixed FAN & SQUARE effectscope not giving damage to unflagged targets. Contributed by LLiuKe; `l2j-server-dp@66619f2b` (2013-05-08, exploit-dupe) — BETA: Fixing skills without any affect limit set, it should be unlimited in that case.

### skill.abnormals — Buffs and debuffs

ID: `skill.abnormals` · Shared: **partial**

Active effects on a creature: abnormal type + level stacking, slot limits (buffs, dances/songs, triggered buffs,
debuffs), duration, store/restore on logout, dispel by the player (`RequestDispel`), cancel/steal, optional return
of cancelled buffs (Mobius custom `CancelReturn`). Sub-features: stacking · slot limits · persistence · dispel ·
cancel/steal · cancel return.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                 |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------------------------------ |
| Interlude | differs | mobius-il, acis, l2jfrozen           | `BuffInfo` identical to HF; no triggered-buff slot limit (`MaxTriggeredBuffAmount` absent) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `EffectList` + `BuffInfo`, buff/dance/trigger limits                             |
| Essence   | differs | mobius-ess                           | `EffectList` reworked (abnormal slots, `DispelSlotType`, hidden buffs), 1391 vs 1954 lines |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/creature/EffectList.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/BuffInfo.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/AbnormalType.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestDispel.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/CancelReturnConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/CancelReturn.ini`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/skill/BuffFinishTask.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/creature/EffectList.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/skill/BuffInfo.java`, `mobius-il:java/org/l2jmobius/gameserver/config/custom/CancelReturnConfig.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/skill/BuffFinishTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/creature/EffectList.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/skill/enums/DispelSlotType.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestDispel.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/skill/BuffFinishTask.java`

Packets

| Chronicle | Client → server         | Server → client                                                                                                                                                                                                     |
| --------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestDispel`         | `AbnormalStatusUpdate` `7F`, `ShortBuffStatusUpdate` `F4`, `PartySpelled` `EE`                                                                                                                                      |
| High Five | `RequestDispel` `D0:4B` | `AbnormalStatusUpdate` `85`, `ShortBuffStatusUpdate` `FA`, `PartySpelled` `F4`, `ExOlympiadSpelledInfo` `FE:7B`                                                                                                     |
| Essence   | `RequestDispel` `D0:48` | `AbnormalStatusUpdate` `85`, `ShortBuffStatusUpdate` `FA`, `PartySpelled` `F4`, `ExAbnormalStatusUpdateFromTarget` `FE:E7`, `ExUserInfoAbnormalVisualEffect` `FE:0159`; masks: `NpcInfoType.ABNORMALS` (bit `0x24`) |

Notes: `CancelReturnConfig` is byte-identical in all three Mobius trees (custom). Fork L2jOrion (hl4p3x, IL) adds its
own `CancelReturn`.

Known fixes: `mobius@cf38303a` (2020-07-20, exploit-dupe) — Fixed buff with herb stacking exploit; `mobius@e5b9a0b6` (2019-05-28, crash-npe) — BuffInfo cancel task NPE check.

### skill.charges-souls — Charges and souls

ID: `skill.charges-souls` · Shared: **partial**

Accumulated resources consumed by skills: force charges (Duelist/Gladiator-type), Kamael souls; gain, cap, spend,
reset after a timeout. Sub-features: charges · souls · reset tasks · conditions on count.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                         |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen           | charges only (no Kamael, no souls): `ResetChargesTask`, `ConditionPlayerCharges`   |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: charges + souls, `ResetChargesTask` / `ResetSoulsTask`                   |
| Essence   | yes     | mobius-ess, mobius-ess               | same tasks (`ResetChargesTask` identical); soul effects `FocusSouls`, `SoulEating` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/ResetChargesTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/ResetSoulsTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerCharges.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerSouls.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/ResetChargesTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/ResetChargesTask.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/ResetSoulsTask.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/FocusSouls.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/SoulEating.java`

Packets

| Chronicle | Client → server | Server → client                         |
| --------- | --------------- | --------------------------------------- |
| Interlude | —               | `EtcStatusUpdate` `F3` (charges)        |
| High Five | —               | `EtcStatusUpdate` `F9` (charges, souls) |
| Essence   | —               | `EtcStatusUpdate` `F9` (charges, souls) |

Notes: Essence also has momentum/special points (`FocusMomentum`, class point effects) that reuse the same charge
counter.

### skill.triggers — Triggered skills

ID: `skill.triggers` · Shared: **partial**

Skills cast automatically with a chance on an event: on attack, on crit, on being hit, on skill use, on evasion;
sources — passive skills, items, augmentations.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                     |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis            | 4 `TriggerSkillBy*` effects, same as HF                                                        |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: `TriggerSkillByAvoid / ByDamageDealt / ByDamageReceived / BySkill`                   |
| Essence   | differs | mobius-ess, l2emu-ess      | 19 trigger effects (by kill, HP %, stat, target type, magic type, dual/triple range, delayed…) |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/TriggerSkillBySkill.java` (+ `TriggerSkillByAvoid`, `TriggerSkillByDamageDealt`, `TriggerSkillByDamageReceived`)
- `mobius-il:dist/game/data/scripts/handlers/skill/effects/TriggerSkillByAvoid.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/TriggerSkillByDamageDealt.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/TriggerSkillByDamageReceived.java`, `mobius-il:dist/game/data/scripts/handlers/skill/effects/TriggerSkillBySkill.java`
- `mobius-ess:dist/game/data/scripts/handlers/skill/effects/TriggerSkill.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects` (`mobius-ess:dist/game/data/scripts/handlers/skill/effects/TriggerSkillBy*.java`, 15 effects)

Packets

| Chronicle | Client → server | Server → client                                 |
| --------- | --------------- | ----------------------------------------------- |
| Interlude | —               | `MagicSkillUse` `48`, `MagicSkillLaunched` `76` |
| High Five | —               | `MagicSkillUse` `48`, `MagicSkillLaunched` `54` |
| Essence   | —               | `MagicSkillUse` `48`, `MagicSkillLaunched` `54` |

Notes: aCis, L2jEternityWorld and L2Emu Essence use the older `ChanceSkillList` + `ChanceCondition` model; aCis fork
devjextreme (382) backports `ChanceSkillList`.

Known fixes: `l2j-server@dab45d26` (2009-05-15, crash-npe) — Fixed NPE in chance skills if target list empty.

### skill.cubics — Cubics

ID: `skill.cubics` · Shared: **partial**

Cubics summoned by skills: template per cubic id/level, lifetime, action interval, skill list with target selection
and conditions (HP thresholds, range), cubic mastery for several cubics.

| Chronicle | Present | Builds (evidence)          | Difference                                                                                                 |
| --------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen | `CubicData` identical to HF; 9 cubic XML files                                                             |
| High Five | yes     | nx-l2, l2j-server, sunrise | baseline: `CubicData` + `entity/cubic` conditions + `Cubic` instance                                       |
| Essence   | differs | mobius-ess                 | `entity/cubic/Cubic.java` reworked (no `entity/actor/instance/Cubic`), 28 cubic XML files, `UnsummonCubic` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/CubicData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/Cubic.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/cubic/`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/SummonCubic.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/cubics/CubicAction.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/cubics/CubicDisappear.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/cubics/CubicHeal.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/CubicData.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/Cubic.java`, `mobius-il:dist/game/data/stats/cubics`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/cubics/CubicAction.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/cubics/CubicDisappear.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/cubics/CubicHeal.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/CubicData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/cubic/Cubic.java`, `mobius-ess:dist/game/data/stats/cubics`

Packets

| Chronicle | Client → server | Server → client                                                                 |
| --------- | --------------- | ------------------------------------------------------------------------------- |
| Interlude | —               | `UserInfo` `04`, `CharInfo` `03` (cubic list), `MagicSkillUse` `48`             |
| High Five | —               | `UserInfo` `32`, `CharInfo` `31` (cubic list), `MagicSkillUse` `48`             |
| Essence   | —               | `ExUserInfoCubic` `FE:0158`, `CharInfo` `31` (cubic list), `MagicSkillUse` `48` |

Notes: aCis keeps `Cubic` + `CubicList` in Java without XML templates.

### skill.transformations — Transformations

ID: `skill.transformations` · Shared: **partial (HF+Ess)**

Player transformation into another form: template with stats, collision, skills and allowed actions; transform via
skill or scroll; restrictions (zones, sieges, mounts); cursed-weapon forms. Sub-features: templates · transform
skills/tree · untransform · zone restrictions.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                            |
| --------- | ------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen, mobius-c6            | — (only `OnPlayerTransform` event stub in Mobius IL)                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline: `TransformData` XML templates, transform skill tree                                         |
| Essence   | yes     | mobius-ess, l2emu-ess                            | same `Transform`/`TransformData` model; fewer forms, mount forms; `CanTransformInDominion` conditions |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/TransformData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/transform/Transform.java`, `nx-l2:chronicles/high-five/datapack/data/stats/transformations/`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/TransformData.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/transform/Transform.java`, `mobius-ess:dist/game/data/stats/transformations`

Packets

| Chronicle | Client → server                              | Server → client                                                                                                                                                 |
| --------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | `RequestActionUse` `56` (untransform action) | `UserInfo` `32`, `CharInfo` `31` (transformation id), `ExBasicActionList` `FE:5F`, `SkillList` `5F`                                                             |
| Essence   | `RequestActionUse` `56` (untransform action) | `UserInfo` `32`, `CharInfo` `31` (transformation id), `ExBasicActionList` `FE:60`, `SkillList` `5F`; masks: `NpcInfoType.TRANSFORMATION` (bit `0x17`) (summons) |

Notes: matches in aCis/L2jFrozen/IL roots are NPC "transform on death" AI scripts, not player transformations.
L2J Server CT2 datapack and old L2JTW keep transformations as one Java script per form.

Known fixes: `l2j-server-dp@2eac476d` (2008-10-29, wrong-formula-value) — JAVA: Transformations time corrections (bug with not autodispelling transformation when timer goes to 0 still remains).

### skill.skill-transfer — Skill transfer

ID: `skill.skill-transfer` · Shared: **chronicle-only**

HF: Holy-class (healer) skills are transferred to sub-classes/other characters via the transfer skill tree; reset
for a fee (`FeeDeleteTransferSkills`).

| Chronicle | Present | Builds (evidence)                    | Difference                                                       |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis                      | —                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                         |
| Essence   | no      | mobius-ess                           | — (`TRANSFER` tree kind remains in the enum, no data, no script) |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/features/SkillTransfer/SkillTransfer.java`, `nx-l2:chronicles/high-five/datapack/data/stats/players/skillTrees/transferSkillTree.xml`

Packets

| Chronicle | Client → server                                                            | Server → client                                  |
| --------- | -------------------------------------------------------------------------- | ------------------------------------------------ |
| High Five | `RequestAcquireSkillInfo` `73`, `RequestAcquireSkill` `7C` (kind TRANSFER) | `AcquireSkillList` `90`, `AcquireSkillInfo` `91` |

Notes: Mobius IL `Player.ini` still lists `FeeDeleteTransferSkills` (dead option).

Known fixes: `l2j-server-dp@f13d4930` (2014-04-18, wrong-formula-value) — BETA: Skill Transfer feature fixes.

### skill.alchemy — Alchemy

ID: `skill.alchemy` · Shared: **chronicle-only**

Alchemy skills and the mixing cube (Ertheia-era system): alchemy skill list, mixing ingredients into results,
conversion.

| Chronicle | Present | Builds (evidence)      | Difference                                                                                                               |
| --------- | ------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | mobius-il              | —                                                                                                                        |
| High Five | no      | nx-l2, l2j-server      | —                                                                                                                        |
| Essence   | differs | mobius-ess, mobius-ess | vestigial: only `RequestAlchemySkillList` → `ExAlchemySkillList`, gated on race Ertheia (absent in Essence); no mix cube |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestAlchemySkillList.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExAlchemySkillList.java`

Packets

| Chronicle | Client → server                     | Server → client                |
| --------- | ----------------------------------- | ------------------------------ |
| Essence   | `RequestAlchemySkillList` `D0:0100` | `ExAlchemySkillList` `FE:0177` |

Notes: no public Interlude/High Five/Essence build implements the mixing cube; the full system lives in Mobius
Ertheia+ trees (reference only). Public Essence fork Waytrel Vanguard adds `AlchemyCraftData` / `AlchemyResult`.
