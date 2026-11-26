# Combat

Combat: attacks, damage formulas, the stat engine and everything that decides the outcome of a hit.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### combat.attack — Physical attack

ID: `combat.attack` · Shared: **partial**

Auto-attack: attack request on a target, range and geodata check, attack speed → hit timing, hit/miss/crit/shield
per hit, weapon specifics (bow/crossbow arrows and reuse, dual-weapon double hit, polearm multi-target), attack
interruption by movement. Sub-features: request validation · hit scheduling · bow reuse/arrows · dual/pole hits ·
attack-to-move check.

| Chronicle | Present | Builds (evidence)                      | Difference                                                                                                  |
| --------- | ------- | -------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, mobius-c6  | same as HF (`AttackRequest` 17 diff lines, `HitTask`)                                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, lineage2ts | baseline: `Creature.doAttack` + per-hit `HitTask`                                                           |
| Essence   | differs | mobius-ess, l2emu-ess                  | hits scheduled through `CreatureAttackTaskManager` (no `HitTask`); request packet identical (16 diff lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/AttackRequest.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Creature.java` (doAttack), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/creature/HitTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/Attack.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/AttackRequest.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/creature/HitTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/AttackRequest.java`, `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/CreatureAttackTaskManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Creature.java`

Packets

| Chronicle | Client → server                                                      | Server → client                                                                                                                           |
| --------- | -------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `AttackRequest` `0A`, `Action` `04` (shift-less click on attackable) | `Attack` `05`, `AutoAttackStart` `2B`, `AutoAttackStop` `2C`, `ActionFailed` `25`, `SetupGauge` `6D`                                      |
| High Five | `AttackRequest` `01`, `Action` `1F`                                  | `Attack` `33`, `AutoAttackStart` `25`, `AutoAttackStop` `26`, `ActionFailed` `1F`, `SetupGauge` `6B`                                      |
| Essence   | `AttackRequest` `01`, `Action` `1F`                                  | `Attack` `33`, `AutoAttackStart` `25`, `AutoAttackStop` `26`, `ActionFailed` `1F`, `ExMagicAttackInfo` `FE:FC`, `ExDamagePopUp` `FE:0392` |

Notes: close to `engine` — client side and validation are shared; only the hit scheduler differs. aCis has its own
`AttackRequest` with the same checks.

Known fixes: `sunrise@20637e46` (2015-04-01, exploit-dupe) — added delay for some packets Fixed a nasty exploit using del key to increase attack speed.

### combat.formulas — Damage formulas

ID: `combat.formulas` · Shared: **partial**

Combat math: physical/magic damage, critical rate and damage, blow (dagger) success and damage, skill success
(land rate) with level difference and resist, shield block rate, reflect, vampiric drain, cancel success, lethal
strikes, PvP/PvE modifiers. Sub-features: phys / magic / blow damage · crit · land rate · shield · reflect ·
vampiric · lethal · level penalty for NPCs.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                                                                                     |
| --------- | ------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen, mobius-c6            | HF formulas minus elemental bonus and NPC level-difference penalties (244 diff lines vs HF)                                                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline: single `Formulas` class (2184 lines), `SkillDmgPenaltyForLvLDifferences` (4 steps)                                                                   |
| Essence   | differs | mobius-ess, l2emu-ess                            | Classic/Essence math: stat-driven PvP/PvE bonuses, skill crit, `MaxPSkillCritRate`, 8-step NPC level penalty, `CalculateMagicSuccessBySkillMagicLevel = False` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/Formulas.java`, `nx-l2:chronicles/high-five/datapack/config/NPC.ini` (level penalties)
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/Formulas.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/Formulas.java`, `mobius-ess:dist/game/config/Player.ini` (`MaxPSkillCritRate`, `VampiricAttackWorkWithSkills`)

Packets

| Chronicle | Client → server | Server → client                                                                                                                         |
| --------- | --------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `Attack` `05` (hit flags: crit, shield, miss, soulshot), `SystemMessage` `64` (damage, resist, crit)                                    |
| High Five | —               | `Attack` `33` (hit flags), `SystemMessage` `62`                                                                                         |
| Essence   | —               | `Attack` `33` (hit flags), `ExMagicAttackInfo` `FE:FC` (crit / overhit / resist marks), `ExDamagePopUp` `FE:0392`, `SystemMessage` `62` |

Notes: aCis keeps a compact `skills/Formulas.java`; L2Emu Essence ships `FormulasCalculator` on the old L2J model.
Formula classes are the most divergent code between builds of the same chronicle.

Formula inputs vs the public forum write-up «Математика и механики игры» (MasterWork, HF base with custom math —
https://mw5.community/topic/208162-matematika-i-mehaniki-igry/; reference only, checked against code):

- Magic crit damage: forum ×2.5 PvP / ×3 PvE — matches Mobius IL and HF (`damage *= attacker.isPlayer() &&
target.isPlayer() ? 2.5 : 3`, then `MAGIC_CRIT_DMG` stat); Essence replaces it with `calcCritDamage` /
  `calcCritDamageAdd` + `calculatePvpPveBonus` (stat-driven).
- Magic crit chance: forum `mod_wit * 5 * mod_per`, cap 25 %; Mobius HF/Essence cap `MaxMCritRate = 200` (per mille,
  20 %); Essence also clamps the skill rate to `[30, MAX_MCRIT_RATE]`.
- Shield: forum block chance `shieldRate * mod_DEX`, perfect block `2 * mod_dex` %; Mobius HF uses DEX for block
  chance but a flat `AltPerfectShieldBlockRate = 10` % for perfect block; Essence uses CON for block chance.
- Magic resist (full / partial): forum `0.5 * mod_per + level_diff` / `5 * mod_per + level_diff`; Mobius HF
  `calcMagicSuccess` uses `1.3 ^ (targetLevel − attackerLevel or skill magic level)` plus an NPC level-penalty table
  (`SkillChancePenaltyForLvLDifferences`) — a different curve.
- Stat formulas (pAtk = weapon × lvl × STR, mAtk = weapon × lvl² × INT², pDef = (4 + armor) × lvl, mDef = acc × lvl × MEN)
  match the HF/IL `Func*` chain.

Known fixes: `mobius@df5b49e2` (2019-10-04, crash-npe) — Fixed an NPE with skills that have critRatePos. Contributed by Sahar.

### combat.stats-engine — Stat engine

ID: `combat.stats-engine` · Shared: **partial**

Calculation of creature stats from base values: base stats (STR/DEX/CON/INT/WIT/MEN) bonuses, stat functions from
items, skills, effects and hennas (add/mul/set, ordered), per-stat calculators, stat caps. Sub-features: base stat
tables · function ordering · calculators per creature · caps.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                   |
| --------- | ------- | ------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | same as HF (`Calculator` byte-identical, `Stat` 7 diff lines)                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `Calculator` + `AbstractFunction`/`FuncTemplate` from XML `<for>` blocks                                           |
| Essence   | differs | mobius-ess, l2emu-ess                | no `Calculator`: `Stat` enum (482 lines) with 29 stat finalizers in `mechanics/stats/finalizers`; effects add stats directly |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/Stat.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/Calculator.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/BaseStat.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/functions/`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/stat/CreatureStat.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/Calculator.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/Stat.java`, `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/functions`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/Stat.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/finalizers`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/stat/CreatureStat.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/stat/PlayerStat.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                                                                                                                                                                               |
| --------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `UserInfo` `04`, `StatusUpdate` `0E`, `CharInfo` `03` (speeds)                                                                                                                                                                                                                                                |
| High Five | —               | `UserInfo` `32`, `StatusUpdate` `18`, `CharInfo` `31` (speeds)                                                                                                                                                                                                                                                |
| Essence   | —               | `UserInfo` `32` masks `BASE_STATS` (bit `0x02`), `MAX_HPCPMP` (bit `0x03`), `STATS` (bit `0x08`), `SPEED` (bit `0x0B`), `MULTIPLIER` (bit `0x0C`), `STAT_POINTS` (bit `0x19`), `STAT_ABILITIES` (bit `0x1A`); `StatusUpdate` `18`; `NpcInfoType.ATK_CAST_SPEED` (bit `0x07`), `SPEED_MULTIPLIER` (bit `0x08`) |

Notes: aCis / L2jFrozen / L2Emu Essence use the older `Stats` enum + `Func*` classes (same idea as HF).

Known fixes: `mobius@b9178def` (2020-06-30, wrong-formula-value) — Fixed movement multiplier float value not increasing.

### combat.status — HP/MP/CP status

ID: `combat.status` · Shared: **engine**

Current HP/MP/CP of creatures: applying damage (CP first for players), death, regeneration task by interval,
status listeners that receive HP bar updates, restore on resurrection. Sub-features: damage application · CP layer ·
regen · death · status listeners.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                      |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | `CreatureStatus` byte-identical to HF                                           |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                                        |
| Essence   | yes     | mobius-ess, l2emu-ess                | same model; adds HP-change event dispatch and HP-block abnormal (80 diff lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/status/CreatureStatus.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/status/PlayerStatus.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/status/CreatureStatus.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/status/PlayerStatus.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/status/CreatureStatus.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/status/PlayerStatus.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/npc/MpRewardTask.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                                                                                        |
| --------- | --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `StatusUpdate` `0E`, `Die` `06`, `Revive` `07`, `PartySmallWindowUpdate` `52`, `PetStatusUpdate` `B5`, `ExRegenMax` `FE:01`                                                                                            |
| High Five | —               | `StatusUpdate` `18`, `Die` `00`, `Revive` `01`, `PartySmallWindowUpdate` `52`, `PetStatusUpdate` `B6`, `ExRegenMax` `FE:01`                                                                                            |
| Essence   | —               | `StatusUpdate` `18`, `Die` `00`, `ExDieInfo` `FE:021F`, `Revive` `01`, `PetStatusUpdate` `B6`; masks `UserInfoType.CURRENT_HPMPCP_EXP_SP` (bit `0x04`), `NpcInfoType.CURRENT_HP/MP`, `MAX_HP/MP`, `ExRegenMax` `FE:01` |

Notes: strongest `engine` candidate in the group.

### combat.elemental-attributes — Elemental attributes in combat

ID: `combat.elemental-attributes` · Shared: **partial (HF+Ess)**

Fire/water/wind/earth/holy/dark attack and defence values from items and skills; damage bonus from attack vs
defence difference; attribute stones/crystals on items (insertion itself belongs to `equipment.*`).

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                                          |
| --------- | ------- | ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen           | — (`item_attributes` table in Mobius IL is augmentation, not elements)                                                              |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `Elementals` item holder + `ElementalAttributeData`, bonus in `Formulas`                                                  |
| Essence   | differs | mobius-ess, l2emu-ess                | `AttributeType`, `AttributeFinalizer`, `AttackAttribute`/`DefenceAttribute` effects; elemental spirits add a second elemental layer |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ElementalAttributeData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/holders/Elementals.java`, `nx-l2:chronicles/high-five/datapack/data/ElementalAttributeData.xml`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/Formulas.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/EnchantAttribute.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ElementalAttributeData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/finalizers/AttributeFinalizer.java`, `mobius-ess:dist/game/config/AttributeSystem.ini`, `mobius-ess:dist/game/data/scripts/handlers/items/EnchantAttribute.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                    |
| --------- | --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | —               | `UserInfo` `32` (attack/defence attributes), `ExBaseAttributeCancelResult` `FE:75`, `ExAttributeEnchantResult` `FE:61`                             |
| Essence   | —               | `UserInfo` `32` masks `ELEMENTALS` (bit `0x09`), `ATK_ELEMENTAL` (bit `0x0E`), `ATT_SPIRITS` (bit `0x17`); `ExElementalSpiritAttackType` `FE:0332` |

Notes: aCis has `L2SkillElemental` (IL elemental _skills_, not the item attribute system).

### combat.traits — Traits and resistances

ID: `combat.traits` · Shared: **partial**

Weapon traits (sword, blunt, dagger, bow…) and debuff traits (stun, sleep, hold, poison, bleed, derangement…):
attack trait of the skill vs target vulnerability/resistance; trait invulnerability.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                               |
| --------- | ------- | ------------------------------------------------ | -------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il                                        | `TraitType` backported from HF (75 vs 78 lines); aCis/L2jFrozen have no trait system (resists via stats) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline: `TraitType` enum, `DefenceTrait`/`AttackTrait` effects                                         |
| Essence   | differs | mobius-ess, l2emu-ess                            | extended `TraitType` (138 lines, more weapon/race traits)                                                |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/TraitType.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/Formulas.java` (trait bonus)
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/TraitType.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/TraitType.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/AttackTrait.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/DefenceTrait.java`

Packets

| Chronicle | Client → server | Server → client                                            |
| --------- | --------------- | ---------------------------------------------------------- |
| Interlude | —               | — (server-side only; resist shows as `SystemMessage` `64`) |
| High Five | —               | —                                                          |
| Essence   | —               | —                                                          |

Notes: Lineage2TS has an extra `TraitRegistry` for AI.

### combat.hit-conditions — Position and time modifiers

ID: `combat.hit-conditions` · Shared: **engine**

Hit and damage bonuses by attacker position relative to the target (front, side, back), height difference and day
or night, from `hitConditionBonus.xml`.

| Chronicle | Present | Builds (evidence)                                | Difference                                   |
| --------- | ------- | ------------------------------------------------ | -------------------------------------------- |
| Interlude | yes     | mobius-il, l2jfrozen, mobius-c6                  | `HitConditionBonusData` byte-identical to HF |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline                                     |
| Essence   | yes     | mobius-ess, l2emu-ess                            | same logic (diff = licence header only)      |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/HitConditionBonusData.java`, `nx-l2:chronicles/high-five/datapack/data/stats/hitConditionBonus.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/HitConditionBonusData.java`, `mobius-il:dist/game/data/stats/hitConditionBonus.xml`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/HitConditionBonusData.java`, `mobius-ess:dist/game/data/stats/hitConditionBonus.xml`

Packets

| Chronicle | Client → server | Server → client      |
| --------- | --------------- | -------------------- |
| Interlude | —               | — (server-side only) |
| High Five | —               | —                    |
| Essence   | —               | —                    |

Notes: aCis has no data file (positional bonus hardcoded in formulas).

### combat.shots — Soulshots and spiritshots

ID: `combat.shots` · Shared: **partial**

Shots boosting the next attack or cast: soulshot, spiritshot, blessed spiritshot, beast (pet) shots, fishing shots;
grade check, consumption per hit, auto-use toggle. Sub-features: shot types · auto shots · beast shots · grade
matching · shot power bonus.

| Chronicle | Present | Builds (evidence)                    | Difference                                                                                                |
| --------- | ------- | ------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | same handlers as HF (`RequestAutoSoulShot` 4 diff lines)                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `SoulShots`, `SpiritShot`, `BlessedSpiritShot`, `BeastSoulShot`, `BeastSpiritShot`, `FishShots` |
| Essence   | differs | mobius-ess                           | adds blessed soulshots, shot power from `ShotsBonus` stat/finalizer, auto-shot rework (231 lines)         |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/SoulShots.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/SpiritShot.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/BlessedSpiritShot.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/BeastSoulShot.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestAutoSoulShot.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/item/enums/ShotType.java`
- `mobius-il:dist/game/data/scripts/handlers/items/SoulShots.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestAutoSoulShot.java`
- `mobius-ess:dist/game/data/scripts/handlers/items/BlessedSoulShots.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/ShotsBonus.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/finalizers/ShotsBonusFinalizer.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestAutoSoulShot.java`

Packets

| Chronicle | Client → server                               | Server → client                                                 |
| --------- | --------------------------------------------- | --------------------------------------------------------------- |
| Interlude | `RequestAutoSoulShot` `D0:05`, `UseItem` `14` | `ExAutoSoulShot` `FE:12`, `MagicSkillUse` `48` (shot animation) |
| High Five | `RequestAutoSoulShot` `D0:0D`, `UseItem` `19` | `ExAutoSoulShot` `FE:0C`, `MagicSkillUse` `48`                  |
| Essence   | `RequestAutoSoulShot` `D0:0D`, `UseItem` `19` | `ExAutoSoulShot` `FE:0C`, `MagicSkillUse` `48`                  |

Notes: —

Known fixes: `l2j-server@35cdabb7` (2006-10-13, crash-npe) — Fix for possible NPE (thanks arlas).

### combat.attack-stance — Combat stance

ID: `combat.attack-stance` · Shared: **engine**

Entering combat on attack or being attacked, the 15-second combat timer, leaving combat (auto-attack stop),
restrictions while in combat (logout, teleport, some actions).

| Chronicle | Present | Builds (evidence)                    | Difference                                                    |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | byte-identical to HF                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline: `AttackStanceTaskManager`                           |
| Essence   | yes     | mobius-ess, l2emu-ess                | same; on exit also clears summon damage-taken (25 diff lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/taskmanagers/AttackStanceTaskManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/taskmanagers/AttackStanceTaskManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/taskmanagers/AttackStanceTaskManager.java`

Packets

| Chronicle | Client → server | Server → client                               |
| --------- | --------------- | --------------------------------------------- |
| Interlude | —               | `AutoAttackStart` `2B`, `AutoAttackStop` `2C` |
| High Five | —               | `AutoAttackStart` `25`, `AutoAttackStop` `26` |
| Essence   | —               | `AutoAttackStart` `25`, `AutoAttackStop` `26` |

Notes: —

Known fixes: `l2j-server@4f5d56e5` (2013-08-25, crash-npe) — BETA: Fixing NPE in Attack Stance task.

### combat.targeting — Targeting

ID: `combat.targeting` · Shared: **partial**

Selecting a target by click (`Action`), shift-click info, cancelling the target (Esc), target-of-target display,
target updates to observers. Sub-features: select · cancel · target display · Essence target action menu.

| Chronicle | Present | Builds (evidence)                    | Difference                                                    |
| --------- | ------- | ------------------------------------ | ------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen           | same as HF (`Action` 21, `RequestTargetCanceld` 5 diff lines) |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity | baseline                                                      |
| Essence   | differs | mobius-ess                           | adds `RequestTargetActionMenu` (target from the context menu) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/Action.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestTargetCanceld.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/ai/Action.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/click/PlayerClick.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/actions/shiftclick/PlayerShiftClick.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/Action.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestTargetCanceld.java`, `mobius-il:dist/game/data/scripts/handlers/actions/click/PlayerClick.java`, `mobius-il:dist/game/data/scripts/handlers/actions/shiftclick/PlayerShiftClick.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/Action.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestTargetActionMenu.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/click/PlayerClick.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/shiftclick/PlayerShiftClick.java`

Packets

| Chronicle | Client → server                                                               | Server → client                                                                              |
| --------- | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Interlude | `Action` `04`, `RequestTargetCanceld` `37`                                    | `MyTargetSelected` `A6`, `TargetSelected` `29`, `TargetUnselected` `2A`, `ActionFailed` `25` |
| High Five | `Action` `1F`, `RequestTargetCanceld` `48`                                    | `MyTargetSelected` `B9`, `TargetSelected` `23`, `TargetUnselected` `24`, `ActionFailed` `1F` |
| Essence   | `Action` `1F`, `RequestTargetCanceld` `48`, `RequestTargetActionMenu` `D0:FE` | `MyTargetSelected` `B9`, `TargetSelected` `23`, `TargetUnselected` `24`, `ActionFailed` `1F` |

Notes: close to `engine`; Essence only adds one entry packet.

Known fixes: `l2j-server@1c6c0093` (2009-09-23, exploit-dupe) — Invisible characters can be directly attacked or interacted only by GMs. Block possible packet exploit here. Also players can't send friend invite.

### combat.class-balance — Class balance

ID: `combat.class-balance` · Shared: **engine** (custom)

Server-side multipliers of damage, defence, crit chance/damage per class, separately for PvP and PvE, physical vs
magical, auto-attack vs skill. Not an official mechanic; a configurable custom.

| Chronicle | Present | Builds (evidence)                                                                   | Difference                                                                                 |
| --------- | ------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il (custom), l2jfrozen / Mobius C6 (`ClassDamageManager`), hellas (Balancer) | Mobius config identical to HF except class array size (137)                                |
| High Five | yes     | nx-l2 (custom)                                                                      | baseline: `ClassBalance.ini` → `ClassBalanceConfig` → applied in `Formulas`/`CreatureStat` |
| Essence   | yes     | mobius-ess (custom)                                                                 | same config; class array sized to Essence class ids; applied via PvP/PvE stats             |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/ClassBalanceConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/ClassBalance.ini`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/Formulas.java` (28 uses), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/stat/CreatureStat.java`
- `mobius-il:java/org/l2jmobius/gameserver/config/custom/ClassBalanceConfig.java`, `mobius-il:dist/game/config/Custom/ClassBalance.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/config/custom/ClassBalanceConfig.java`, `mobius-ess:dist/game/config/Custom/ClassBalance.ini`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| Interlude | —               | —               |
| High Five | —               | —               |
| Essence   | —               | —               |

Notes: public forks add GUI/admin balancers: L2jSunrise Purity (`ClassBalanceManager`, `AdminClassBalance`,
`ClassBalanceBBSManager`), L2jMega (`BalancerEdit`, `OlyClassDamageManager`), L2jOne l2fusion (`ClassBalanceData`),
L2jOrion (`classDamages.ini`), L2 Retail Engine (`custom/balancer`, Swing GUI).
