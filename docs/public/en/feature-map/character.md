# Character

Character life cycle, progression by level and class, and character state that is not tied to equipment.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### character.create — Character creation

ID: `character.create` · Shared: **partial**

Lobby flow: the client asks for the creation screen, sends race/class/sex/appearance and a name; the server
validates the name (length, pattern, forbidden words, uniqueness), checks race/class/sex rules, creates the character
from the player template, gives starting equipment, shortcuts, location and optional title.
Sub-features: name validation · race/class/sex rules · initial equipment · initial shortcuts · starting location ·
starting title · race ban (custom) · name-availability pre-check (Ess).

| Chronicle | Present | Builds (evidence)                                                           | Difference                                                                                                                                                |
| --------- | ------- | --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim, mobius-c6 | 5 races, no Kamael                                                                                                                                        |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                            | baseline: + Kamael (sex locked by class)                                                                                                                  |
| Essence   | differs | mobius-ess                                                                  | + Death Knight, Sylph, Vanguard, Assassin, High Elf with per-class sex/appearance locks; `RequestCharacterNameCreatable` pre-check; per-race enable flags |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/CharacterCreate.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/InitialEquipmentData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/InitialShortcutData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/StartingTitleConfig.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/CharacterCreate.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/CharacterCreate.java` (race/class locks ~:193–325), `mobius-ess:java/org/l2jmobius/gameserver/config/custom/AllowedPlayerRacesConfig.java`

Packets

| Chronicle | Client → server                                                                      | Server → client                                                                                                                     |
| --------- | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `NewCharacter` `0E`, `CharacterCreate` `0B`                                          | `NewCharacterSuccess` `17`, `CharCreateOk` `19`, `CharCreateFail` `1A`, `CharSelectionInfo` `13`                                    |
| High Five | `NewCharacter` `13`, `CharacterCreate` `0C`                                          | `NewCharacterSuccess` `0D`, `CharCreateOk` `0F`, `CharCreateFail` `10`, `CharSelectionInfo` `09`                                    |
| Essence   | `NewCharacter` `13`, `CharacterCreate` `0C`, `RequestCharacterNameCreatable` `D0:AA` | `NewCharacterSuccess` `0D`, `CharCreateOk` `0F`, `CharCreateFail` `10`, `CharSelectionInfo` `09`, `ExIsCharNameCreatable` `FE:010C` |

Notes: `InitialEquipmentData` is identical in all three Mobius trees; the difference is race/class rules and data.
Starting location / title / allowed races are Mobius customs present in all three.

Known fixes: `l2j-server@fc23f7b5` (2009-04-21, exploit-dupe) — Exploit fix (different characters with same name); `l2j-server@e839c613` (2006-11-01, crash-npe) — Fix for server crash when injecting invalid classID on character create; `mobius@deaefac8` (2018-04-05, crash-npe) — Fixed character creation hang the client.

### character.delete-restore — Character deletion and restore

ID: `character.delete-restore` · Shared: **engine**

Character is marked for deletion and removed after N days; in that window it can be restored. Clan leaders (and
clan members, depending on config) cannot be deleted.
Sub-features: deletion timer · clan restrictions · restore window · immediate delete when the timer is 0.

| Chronicle | Present | Builds (evidence)                                                | Difference                                       |
| --------- | ------- | ---------------------------------------------------------------- | ------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline (`DeleteCharAfterDays = 7`)             |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | same                                             |
| Essence   | yes     | mobius-ess                                                       | same logic; fail reasons as enum; default 3 days |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/CharacterDelete.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/CharacterRestore.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/CharacterDelete.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/CharacterDelete.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/enums/CharacterDeleteFailType.java`

Packets

| Chronicle | Client → server                                 | Server → client                                                           |
| --------- | ----------------------------------------------- | ------------------------------------------------------------------------- |
| Interlude | `CharacterDelete` `0C`, `CharacterRestore` `62` | `CharDeleteSuccess` `23`, `CharDeleteFail` `24`, `CharSelectionInfo` `13` |
| High Five | `CharacterDelete` `0D`, `CharacterRestore` `7B` | `CharDeleteSuccess` `1D`, `CharDeleteFail` `1E`, `CharSelectionInfo` `09` |
| Essence   | `CharacterDelete` `0D`, `CharacterRestore` `7B` | `CharDeleteSuccess` `1D`, `CharDeleteFail` `1E`, `CharSelectionInfo` `09` |

Notes: `CharacterRestore` is identical in the three Mobius trees.

### character.select-enter — Character selection and entering the world

ID: `character.select-enter` · Shared: **partial**

Lobby list, character selection, then EnterWorld: restore effects, skills, items, pet/servitor, clan and party
state, mail/friend notifications, welcome messages, and every per-version system that hooks on login.
Sub-features: lobby list · CharacterSelect · EnterWorld restore · GM login state · welcome / announcements · back to
lobby (`RequestGotoLobby`).

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                     |
| --------- | ------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline core                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | + vitality, Nevit, mail, fame, PIN hooks (`EnterWorld` +116 lines vs IL)       |
| Essence   | yes     | mobius-ess                                                       | + `ExEnterWorld`; many Essence systems initialised on login (+450 lines vs HF) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/EnterWorld.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/CharacterSelect.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/CharSelectionInfo.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/EnterWorld.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExEnterWorld.java`

Packets

| Chronicle | Client → server                                                       | Server → client                                                                          |
| --------- | --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| Interlude | `CharacterSelect` `0D`, `EnterWorld` `03`, `RequestGotoLobby`         | `CharSelectionInfo` `13`, `CharSelected` `15`, `UserInfo` `04`                           |
| High Five | `CharacterSelect` `12`, `EnterWorld` `11`, `RequestGotoLobby` `D0:36` | `CharSelectionInfo` `09`, `CharSelected` `0B`, `UserInfo` `32`                           |
| Essence   | `CharacterSelect` `12`, `EnterWorld` `11`, `RequestGotoLobby` `D0:33` | `CharSelectionInfo` `09`, `CharSelected` `0B`, `UserInfo` `32`, `ExEnterWorld` `FE:01C8` |

Notes: EnterWorld is the main coupling point — login-time systems are called from it directly. A shared module needs
an "on enter world" hook (Mobius already fires `OnPlayerLogin` listeners for part of it).

Known fixes: `sunrise@d03b2d16` (2016-06-03, exploit-dupe) — Safety precaution just in case of exploit while char select; `l2j-server@f9c18f13` (2010-01-22, crash-npe) — fix ClassCastException in EnterWorld at notifySponsorOrApprentice(), thx Gnacik.

### character.logout-restart — Logout and restart

ID: `character.logout-restart` · Shared: **engine**

Logout and restart to the lobby are refused in combat stance, during enchant, class change, olympiad, festival or
while trading; otherwise the character leaves the world or switches to offline trade / offline play.
Sub-features: combat / state restrictions · offline trade switch · offline play switch · restart to lobby.

| Chronicle | Present | Builds (evidence)                                                                             | Difference                                                        |
| --------- | ------- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim, l2ex-elixir, l2shablya-rust | baseline                                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                                              | same                                                              |
| Essence   | yes     | mobius-ess                                                                                    | same rules, collected into `Player.canLogout()` instead of inline |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/Logout.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestRestart.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/Disconnection.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestRestart.java`, `mobius-il:java/org/l2jmobius/gameserver/network/Disconnection.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestRestart.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Player.java` (`canLogout`), `mobius-ess:java/org/l2jmobius/gameserver/network/Disconnection.java`

Packets

| Chronicle | Client → server                      | Server → client                                                                                |
| --------- | ------------------------------------ | ---------------------------------------------------------------------------------------------- |
| Interlude | `Logout` `09`, `RequestRestart` `46` | `LeaveWorld` `7E`, `RestartResponse` `5F`, `CharSelectionInfo` `13`, `ExRestartClient` `FE:47` |
| High Five | `Logout` `00`, `RequestRestart` `57` | `LeaveWorld` `84`, `RestartResponse` `71`, `CharSelectionInfo` `09`, `ExRestartClient` `FE:48` |
| Essence   | `Logout` `00`, `RequestRestart` `57` | `LeaveWorld` `84`, `RestartResponse` `71`, `CharSelectionInfo` `09`, `ExRestartClient` `FE:49` |

Notes: the Essence `canLogout()` shape is the better one for a shared module.

Known fixes: `sunrise@5a4175a0` (2015-03-23, missing-check) — Fix for dual box checks when exit game; `sunrise@c090b6ef` (2016-05-27, missing-check) — Fixed missing checks for no restart zones.

### character.templates — Race and class templates

ID: `character.templates` · Shared: **partial**

Per-class base data (base stats, HP/MP/CP per level, collision, starting location), the class tree (parent class,
tier, race) and class categories used by conditions.
Sub-features: player template · class list (tree) · class categories.

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                |
| --------- | ------- | ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline: 5 races, 3 professions                                                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | + Kamael classes; loaders identical to IL                                                                 |
| Essence   | differs | mobius-ess                                                       | + Essence races/classes; `PlayerTemplateData` reads extra fields (+32 lines); class list loader identical |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/PlayerTemplateData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ClassListData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/CategoryData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/templates/PlayerTemplate.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/enums/player/PlayerClass.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/PlayerTemplateData.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/PlayerTemplateData.java`

Packets

| Chronicle | Client → server | Server → client                                                        |
| --------- | --------------- | ---------------------------------------------------------------------- |
| all       | —               | — (server data; class id goes out in `UserInfo` / `CharSelectionInfo`) |

Notes: loaders are near-identical; the difference is data (class ids, categories).

### character.essence-classes — Essence-only races and classes

ID: `character.essence-classes` · Shared: **chronicle-only**

Races and classes that exist only in Essence, with own mechanics: Death Knight, Sylph, Vanguard (mount), Assassin
(assassination points), High Elf, Warg; class-gated equipment and skills.
Sub-features: per-class resource (assassination points etc.) · Vanguard mount · class gating · creation locks.

| Chronicle | Present | Builds (evidence)                         | Difference |
| --------- | ------- | ----------------------------------------- | ---------- |
| Interlude | no      | mobius-il                                 | —          |
| High Five | no      | nx-l2                                     | —          |
| Essence   | yes     | mobius-ess (09.2; Sylph quests from 04.2) | baseline   |

Entry points

- `mobius-ess:dist/game/data/scripts/handlers/skill/effects/ModifyAssassinationPoints.java`, `mobius-ess:dist/game/data/scripts/quests/Q10114_WeSylphs/Q10114_WeSylphs.java`, `mobius-ess:java/org/l2jmobius/gameserver/data/enums/CategoryType.java` (`*_ALL_CLASS`)

Packets

| Chronicle | Client → server | Server → client                      |
| --------- | --------------- | ------------------------------------ |
| Essence   | —               | `UserInfo` `32` bit `VANGUARD_MOUNT` |

Notes: `UserInfoType.VANGUARD_MOUNT` is the per-class UserInfo bit.

### character.base-stats — Base stats

ID: `character.base-stats` · Shared: **partial**

STR/DEX/CON/INT/WIT/MEN and their bonus tables feeding derived stats (HP, MP, attack, speed, crit, cast speed,
resistances). Sub-features: base values per template · stat bonus table · conditions on base stats.

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                                          |
| --------- | ------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline (6 stats)                                                                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | same (`BaseStat` identical to IL)                                                                                                   |
| Essence   | differs | mobius-ess                                                       | same 6 stats; `BaseStat` rewritten (bonus via `Stat.STAT_*` + finalizers); + allocated points and elixirs (`character.stat-points`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/stats/BaseStat.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerBaseStats.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/stats/BaseStat.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/stats/BaseStat.java`

Packets

| Chronicle | Client → server | Server → client                                                                                              |
| --------- | --------------- | ------------------------------------------------------------------------------------------------------------ |
| Interlude | —               | `UserInfo` `04` (full packet)                                                                                |
| High Five | —               | `UserInfo` `32` (full packet)                                                                                |
| Essence   | —               | `UserInfo` `32` bits `BASE_STATS`, `STATS`, `ExUserBoostStat` `FE:024C`, `ExUserViewInfoParameter` `FE:02AE` |

Notes: Essence 09.2 has no LUC/CHA — the enum holds the six classic stats.

### character.stat-points — Essence stat points

ID: `character.stat-points` · Shared: **chronicle-only**

From level 76 a character gets one point per level (plus points from elixirs) to add to STR/DEX/CON/INT/WIT/MEN;
points can be reset. Sub-features: points per level · elixir points · allocation · reset.

| Chronicle | Present | Builds (evidence)       | Difference                                       |
| --------- | ------- | ----------------------- | ------------------------------------------------ |
| Interlude | no      | mobius-il               | —                                                |
| High Five | no      | nx-l2                   | —                                                |
| Essence   | yes     | mobius-ess (09.2, 04.2) | baseline: points = (level − 75) + elixirs − used |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/stats/ExSetStatusBonus.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/stats/ExResetStatusBonus.java`

Packets

| Chronicle | Client → server                                              | Server → client                                                     |
| --------- | ------------------------------------------------------------ | ------------------------------------------------------------------- |
| Essence   | `ExSetStatusBonus` `D0:0192`, `ExResetStatusBonus` `D0:0193` | `UserInfo` `32` bits `STAT_POINTS`, `STAT_ABILITIES`, `ELIXIR_USED` |

Notes: stored as player variables `STAT_POINTS`, `STAT_STR`…, `ELIXIRS_AVAILABLE`; elixir cap raised by
`Stat.ELIXIR_USAGE_LIMIT`.

### character.experience — Experience and level

ID: `character.experience` · Shared: **partial**

Exp/SP gain, level-up and de-level, exp loss on death by level table, max level per chronicle; custom: dynamic exp
rates, de-level NPC, `.expgain` toggle.
Sub-features: exp table · level up / delevel · death exp loss · dynamic rates (custom) · de-level NPC (custom) ·
exp-gain toggle (custom).

| Chronicle | Present | Builds (evidence)                                                             | Difference                                                                                  |
| --------- | ------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim, l2py-python | max level 80 (81 rows)                                                                      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                              | baseline: max 85 (87 rows)                                                                  |
| Essence   | differs | mobius-ess                                                                    | max 100; per-level `trainingRate` (training camp); `DelevelMinimum`; table loader +25 lines |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ExperienceData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/ExperienceLossData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/DelevelManagerConfig.java`, `nx-l2:chronicles/high-five/datapack/data/stats/players/experience.xml`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/ExperienceData.java`, `mobius-il:dist/game/data/scripts/custom/DelevelManager/DelevelManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ExperienceData.java`, `mobius-ess:dist/game/data/stats/players/experience.xml`

Packets

| Chronicle | Client → server | Server → client                             |
| --------- | --------------- | ------------------------------------------- |
| Interlude | —               | `UserInfo` `04`                             |
| High Five | —               | `UserInfo` `32`                             |
| Essence   | —               | `UserInfo` `32` bit `CURRENT_HPMPCP_EXP_SP` |

Notes: `ExperienceLossData` and the `DelevelManager` custom are identical in all three Mobius trees. L2jMaster 2020
(HF fork) adds a `DeLevelNpc`.

### character.death-revive — Death and resurrection

ID: `character.death-revive` · Shared: **partial**

On death the player picks a restart point (town, clan hall, castle, fortress, siege flag, fixed / festival, jail);
exp is lost by table; resurrection skills/items restore part of it.
Sub-features: restart-point choice · death exp penalty · resurrection exp restore · fixed resurrection (items) · paid
resurrection (Ess) · timed-hunting-zone restart (Ess).

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                                                             |
| --------- | ------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline restart points                                                                                                                                |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | + fortress / territory points (`RequestRestartPoint` +67 lines vs IL)                                                                                  |
| Essence   | differs | mobius-ess                                                       | + paid in-place resurrection (free N times, then item/adena by level), timed hunting zone restart, death-penalty protection effects (+311 lines vs HF) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestRestartPoint.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestRestartPoint.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestRestartPoint.java` (case 9 — resurrect by payment; case 25 — timed hunting), `mobius-ess:dist/game/data/scripts/handlers/skill/effects/ResurrectionFeeModifier.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/ProtectDeathPenalty.java`

Packets

| Chronicle | Client → server            | Server → client                                            |
| --------- | -------------------------- | ---------------------------------------------------------- |
| Interlude | `RequestRestartPoint` `6D` | `Die` `06`, `Revive` `07`                                  |
| High Five | `RequestRestartPoint` `7D` | `Die` `00`, `Revive` `01`                                  |
| Essence   | `RequestRestartPoint` `7D` | `Die` `00`, `Revive` `01`, `ExFieldDieLimitTime` `FE:0331` |

Notes: agathion resurrection (case 6) and "Adventurer's Song" (case 7) are TODO in Mobius Essence.

### character.class-change — Profession change

ID: `character.class-change` · Shared: **partial**

1st–3rd profession through quests and village masters, optional custom Class Master (NPC or on level-up), change
coupons and the Essence class-change window.
Sub-features: profession quests · village masters · Class Master (custom, prices, auto-popup) · class-change coupon
(Ess) · class-change UI (Ess) · awakened-class packet (Ess).

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                                              |
| --------- | ------- | ---------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline: quests + village masters; Class Master custom                                                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | same model; Class Master +136 lines (Kamael, tier rules)                                                                                |
| Essence   | differs | mobius-ess                                                       | client window (`ExRequestClassChange`, `ExClassChangeUiOpen`), `ClassChangeCoupon` item, `SecondClassChange`/`ThirdClassChange` scripts |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/VillageMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/ClassMaster/ClassMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/village_master/`
- `mobius-il:dist/game/data/scripts/ai/others/ClassMaster/ClassMaster.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/VillageMaster.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/classchange/ExRequestClassChange.java`, `mobius-ess:dist/game/data/scripts/handlers/items/ClassChangeCoupon.java`, `mobius-ess:dist/game/data/scripts/ai/others/SecondClassChange.java`, `mobius-ess:dist/game/data/scripts/ai/others/ThirdClassChange.java`

Packets

| Chronicle | Client → server                                                                                                                                | Server → client                                                                                                                        |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | NPC html (`RequestBypassToServer` `21`)                                                                                                        | `UserInfo` `04`                                                                                                                        |
| High Five | NPC html (`RequestBypassToServer` `23`)                                                                                                        | `UserInfo` `32`                                                                                                                        |
| Essence   | `ExRequestClassChange` `D0:0165`, `ExRequestClassChangeVerifying` `D0:0166`, `ExClassChange` `D0:02C9`, `RequestChangeToAwakenedClass` `D0:A3` | `ExClassChangeUiOpen` `FE:03B1`, `ExClassChangeSetAlarm` `FE:0209`, `ExClassChangeFail` `FE:03B2`, `ExChangeToAwakenedClass` `FE:0100` |

Notes: village-master scripts are a script catalogue (`village_master/`), not counted per script.

Known fixes: `mobius@b1c1c173` (2026-06-01, exploit-dupe) — Probable ClassMaster exploit fix.

### character.subclass — Subclasses

ID: `character.subclass` · Shared: **partial (IL+HF)**

Add, switch and cancel subclasses at village masters after the Fate's Whisper / Mimir's Elixir quests; subclass
level limits; skill validation when switching.
Sub-features: add · change · cancel · quest requirements · race/class restrictions · subclass skill validator (custom).

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                   |
| --------- | ------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline (`MaxSubclass = 3`)                                                                 |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | + Kamael rules, certification hooks                                                          |
| Essence   | no      | mobius-ess                                                       | code kept (dual-class field, `SubclassType`) but disabled: `MaxSubclass = 0` in `Player.ini` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/SubClassHolder.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/instance/VillageMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/Validators/SubClassSkills.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/player/SubClassHolder.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/instance/VillageMaster.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Player.java` (`addSubClass(..., isDualClass)`), `mobius-ess:dist/game/config/Player.ini` (`MaxSubclass = 0`)

Packets

| Chronicle | Client → server                         | Server → client        |
| --------- | --------------------------------------- | ---------------------- |
| Interlude | NPC html (`RequestBypassToServer` `21`) | `UserInfo` `04`        |
| High Five | NPC html (`RequestBypassToServer` `23`) | `UserInfo` `32`        |
| Essence   | —                                       | `ExSubjobInfo` `FE:EB` |

Notes: there are no dedicated client packets — the flow is NPC html + `UserInfo` refresh.

### character.subclass-certification — Subclass certification

ID: `character.subclass-certification` · Shared: **chronicle-only**

On a subclass at levels 65/70/75/80 the player gets certificates exchanged for certification skills usable on the
main class; certification can be reset.
Sub-features: certificate grant by level · certification skills · reset · certified-class quests.

| Chronicle | Present | Builds (evidence)                                   | Difference |
| --------- | ------- | --------------------------------------------------- | ---------- |
| Interlude | no      | mobius-il, acis                                     | —          |
| High Five | yes     | nx-l2, l2j-server-dp, sunrise, eternity, lineage2ts | baseline   |
| Essence   | no      | mobius-ess                                          | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/SubclassCertification/SubclassCertification.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/quests/Q00064_CertifiedBerserker/` (certified-class quests)

Packets

| Chronicle | Client → server                         | Server → client |
| --------- | --------------------------------------- | --------------- |
| High Five | NPC html (`RequestBypassToServer` `23`) | `UserInfo` `32` |

Notes: Essence matches only `MonumentOfHeroes-heroCertification*.html` — hero, not subclass certification.

Known fixes: `l2j-server@d73c850d` (2014-03-04, exploit-dupe) — BETA: Fix for subclass certification exploit; `l2j-server-dp@5bdb8181` (2013-05-31, wrong-formula-value) — BETA: Fixing one more issue with SubclassCertification:.

### character.noblesse — Noblesse

ID: `character.noblesse` · Shared: **partial**

Noblesse status: obtained by the noblesse quest chain (or from a custom NPC), grants noblesse skills, olympiad access
and noblesse teleports.
Sub-features: noblesse quest · noblesse skills (Blessing etc.) · Noblesse Master NPC (custom) · noblesse teleports.

| Chronicle | Present | Builds (evidence)                                                           | Difference                                                                             |
| --------- | ------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim, mobius-c6 | baseline                                                                               |
| High Five | yes     | nx-l2, l2j-server-dp, sunrise, eternity, lineage2ts                         | same                                                                                   |
| Essence   | differs | mobius-ess                                                                  | status, `NoblesseBless` and Noblesse Master custom kept; IL/HF quest chain not checked |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/custom/NoblessMaster/NoblessMaster.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/NoblesseBless.java`
- `mobius-il:dist/game/data/scripts/custom/NoblessMaster/NoblessMaster.java`
- `mobius-ess:dist/game/data/scripts/custom/NoblessMaster/NoblessMaster.java`

Packets

| Chronicle | Client → server  | Server → client |
| --------- | ---------------- | --------------- |
| Interlude | NPC html / quest | `UserInfo` `04` |
| High Five | NPC html / quest | `UserInfo` `32` |
| Essence   | NPC html / quest | `UserInfo` `32` |

Notes: the `NoblessMaster` custom differs by ≤5 lines across the three Mobius trees.

### character.rebirth — Rebirth

ID: `character.rebirth` · Shared: **engine**

Custom: at max level a character resets to a low level for a reward / stat bonus; rebirth count is limited.
Sub-features: level reset · reward per rebirth · rebirth limit · NPC bypass.

| Chronicle | Present | Builds (evidence)                                                               | Difference |
| --------- | ------- | ------------------------------------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il (custom), mobius-c6, l2jfrozen; forks L2jFrozen Shyla, l2jorion-exvo1 | custom     |
| High Five | yes     | nx-l2 (custom); fork facabek l2h5java25 (own `RebirthSystem`)                   | custom     |
| Essence   | yes     | mobius-ess (custom)                                                             | custom     |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/RebirthManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/RebirthConfig.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/npc/Rebirth.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/RebirthManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/RebirthManager.java`

Packets

| Chronicle | Client → server                         | Server → client |
| --------- | --------------------------------------- | --------------- |
| Interlude | NPC html (`RequestBypassToServer` `21`) | `UserInfo` `04` |
| High Five | NPC html (`RequestBypassToServer` `23`) | `UserInfo` `32` |
| Essence   | NPC html (`RequestBypassToServer` `23`) | `UserInfo` `32` |

Notes: Mobius `RebirthManager` differs a lot between trees (1009 / 1123 / 1059 lines, ~700 changed lines each) — the
idea is shared, the code is not. Not a retail mechanic.

### character.rename-recolor — Rename, sex change, name and title colour

ID: `character.rename-recolor` · Shared: **partial (HF+Ess)**

Change character name (forced rename after a name clash, or a paid service), change sex, nickname/title colour via an
item. Sub-features: forced rename (`ExNeedToChangeName`) · rename service · sex change · nickname colour item ·
nickname emote (Ess).

| Chronicle | Present | Builds (evidence)                                | Difference                                       |
| --------- | ------- | ------------------------------------------------ | ------------------------------------------------ |
| Interlude | no      | mobius-il, acis                                  | no client support; only custom services in packs |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline: forced rename + nickname colour item   |
| Essence   | yes     | mobius-ess                                       | + nickname emote                                 |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestExChangeName.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestChangeNicknameColor.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/items/NicknameColor.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestChangeNicknameEmote.java`

Packets

| Chronicle | Client → server                                                                                                                             | Server → client                                                                                                                 |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —                                                                                                                                           | `NicknameChanged` `CC`                                                                                                          |
| High Five | `RequestExChangeName` `D0:3B`, `RequestChangeNicknameColor` `D0:4F`, `RequestResetNickname` `D0:50`                                         | `ExNeedToChangeName` `FE:69`, `ExRequestChangeNicknameColor` `FE:83`, `NicknameChanged` `CC`                                    |
| Essence   | `RequestExChangeName` `D0:38`, `RequestChangeNicknameColor` `D0:4C`, `RequestChangeNicknameEmote` `D0:0208`, `RequestResetNickname` `D0:4D` | `ExNeedToChangeName` `FE:6A`, `ExRequestChangeNicknameColor` `FE:84`, `ExChangeNicknameEmote` `FE:02C6`, `NicknameChanged` `CC` |

Notes: rename / sex change as a paid service exists only as custom NPCs or community-board services; L2jMega (IL
fork) logs name changes (`ChangeNameLog`).

### character.title — Title

ID: `character.title` · Shared: **engine**

Player or clan leader sets a title; custom PvP-count title colours (see `pvp.rewards-titles`).
Sub-features: set own title · clan-given title · title colour custom.

| Chronicle | Present | Builds (evidence)                                                | Difference                             |
| --------- | ------- | ---------------------------------------------------------------- | -------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline                               |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | same                                   |
| Essence   | yes     | mobius-ess                                                       | same (`RequestGiveNickName` identical) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestGiveNickName.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/PvpTitleColorConfig.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestGiveNickName.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestGiveNickName.java`

Packets

| Chronicle | Client → server            | Server → client                         |
| --------- | -------------------------- | --------------------------------------- |
| Interlude | `RequestGiveNickName` `55` | `UserInfo` `04`, `CharInfo` `03`        |
| High Five | `RequestGiveNickName` `0B` | `UserInfo` `32`, `CharInfo` `31`        |
| Essence   | `RequestGiveNickName` `0B` | `UserInfo` `32`, `ExCharInfo` `FE:0255` |

Notes: —

Known fixes: `l2j-server@1c130bcf` (2013-09-24, crash-npe) — BETA: `RequestGiveNickName` packet fixes.

### character.beauty-shop — Beauty shop

ID: `character.beauty-shop` · Shared: **chronicle-only**

Paid hair style, face and hair colour from a client menu; reset to the original look.
Sub-features: style list · register style · reset.

| Chronicle | Present | Builds (evidence)       | Difference |
| --------- | ------- | ----------------------- | ---------- |
| Interlude | no      | mobius-il               | —          |
| High Five | no      | nx-l2, l2j-server       | —          |
| Essence   | yes     | mobius-ess (09.2, 04.2) | baseline   |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/BeautyShopData.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/beautyshop/BeautyData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestRegistBeauty.java`

Packets

| Chronicle | Client → server                                                                                                                    | Server → client                                                                                                                       |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `RequestShowBeautyList` `D0:CB`, `RequestRegistBeauty` `D0:CC`, `NotifyExitBeautyShop` `D0:E2`, `RequestShowResetShopList` `D0:CE` | `ExShowBeautyMenu` `FE:0135`, `ExResponseBeautyList` `FE:0136`, `ExBeautyItemList` `FE:017A`, `ExResponseBeautyRegistReset` `FE:0137` |

Notes: —

### character.styles — Character styles

ID: `character.styles` · Shared: **chronicle-only**

Essence style collection: register unlocked styles, select the active one per category, mark favourites.
Sub-features: style list · register · select · favourites · style items.

| Chronicle | Present | Builds (evidence) | Difference                |
| --------- | ------- | ----------------- | ------------------------- |
| Interlude | no      | mobius-il         | —                         |
| High Five | no      | nx-l2             | —                         |
| Essence   | yes     | mobius-ess        | baseline (absent in 04.2) |

Entry points

- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/CharacterStylesData.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/characterstyle/ExRequestCharacterStyleSelect.java`

Packets

| Chronicle | Client → server                                                                                                                                                                    | Server → client                                                                                                                                                                             |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Essence   | `ExRequestCharacterStyleList` `D0:02CD`, `ExRequestCharacterStyleRegister` `D0:02CE`, `ExRequestCharacterStyleSelect` `D0:02CF`, `ExRequestCharacterStyleUpdateFavorite` `D0:02D0` | `ExCharacterStyleList` `FE:03B7`, `ExCharacterStyleRegister` `FE:03B8`, `ExCharacterStyleSelect` `FE:03B9`, `ExCharacterStyleUpdateFavorite` `FE:03BA`, `ExCharacterStyleUseItem` `FE:03B6` |

Notes: —

### character.recommendation — Recommendations

ID: `character.recommendation` · Shared: **partial**

Players recommend each other a limited number of times per day; recommendations show on the character and (HF+)
give a hunting exp bonus that drains over time.
Sub-features: give recommendation · daily reset · recommendation bonus (HF+) · bonus timer.

| Chronicle | Present | Builds (evidence)                                | Difference                                                        |
| --------- | ------- | ------------------------------------------------ | ----------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen, l2jbrasil, hellas    | `RequestEvaluate`; have/left counters only, no bonus window       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline: `RequestVoteNew`, `ExVoteSystemInfo`, `RecoBonus` exp   |
| Essence   | yes     | mobius-ess                                       | same packets; `RecoBonus` holder removed, bonus via tasks/effects |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/RecoBonus.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/RecoGiveTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExVoteSystemInfo.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/RecoBonusTaskEnd.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestEvaluate.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExVoteSystemInfo.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/GiveRecommendation.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/RecoBonusTaskEnd.java`

Packets

| Chronicle | Client → server          | Server → client            |
| --------- | ------------------------ | -------------------------- |
| Interlude | `RequestEvaluate` `B9`   | `UserInfo` `04`            |
| High Five | `RequestVoteNew` `D0:7E` | `ExVoteSystemInfo` `FE:C9` |
| Essence   | `RequestVoteNew` `D0:7C` | `ExVoteSystemInfo` `FE:CB` |

Notes: Mobius IL still carries the HF `RecoBonus` class (shared code), but the client protocol is the IL one.

### character.vitality — Vitality / Sayha's Grace

ID: `character.vitality` · Shared: **partial (HF+Ess)**

Points accumulated offline / spent while hunting give an exp/SP bonus; restored by items, skills, peace zones and
clan hall functions. Essence calls it Sayha's Grace and adds limited Sayha items and hunt-pass support.
Sub-features: points and levels · exp/SP multiplier · consumption by kill · recovery (offline, items, CH) · raid bonus ·
Sayha items (Ess) · hunt-pass Sayha support (Ess).

| Chronicle | Present | Builds (evidence)                                | Difference                                                                                                                     |
| --------- | ------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| Interlude | no      | mobius-il, acis, l2jfrozen                       | Mobius IL carries dormant HF code, `EnableVitality = False`; no client packet                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline (`EnableVitality = True`, 20 000 start points)                                                                        |
| Essence   | differs | mobius-ess                                       | Sayha's Grace: 35 000 start points, `ExVitalityEffectInfo`, `LimitedSayha` items, peace-zone recovery effect, hunt-pass toggle |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/VitalityTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExVitalityPointInfo.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/VitalityPointUp.java`
- `mobius-ess:dist/game/data/scripts/handlers/items/LimitedSayha.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/VitalityExpRate.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/RecoverVitalityInPeaceZone.java`

Packets

| Chronicle | Client → server                  | Server → client                                                                                                                         |
| --------- | -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| High Five | —                                | `ExVitalityPointInfo` `FE:A0`                                                                                                           |
| Essence   | `HuntpassSayhasToggle` `D0:0227` | `ExVitalityPointInfo` `FE:A1`, `ExVitalityEffectInfo` `FE:0119`, `HuntPassSayhasSupportInfo` `FE:02E2`; `UserInfo` `32` bit `VITA_FAME` |

Notes: Essence UserInfo bit `VITA_FAME` carries vitality and fame.

Known fixes: `lineage2ts@324a42d3` (2023-07-24, wrong-formula-value) — adding admin commans for changing vitality - fixing issues with vitality being incorrectly calculated - using a bit different vitality formula (sim.

### character.nevit-blessing — Nevit's blessing

ID: `character.nevit-blessing` · Shared: **chronicle-only**

Hunting fills Nevit's points; at the cap Nevit's Advent gives a timed exp bonus; Nevit's Hourglass items and the
Nevit's Herald announcer. Sub-features: advent points · advent timer · hunting bonus · hourglass items · herald NPC.

| Chronicle | Present | Builds (evidence)                                | Difference                                                         |
| --------- | ------- | ------------------------------------------------ | ------------------------------------------------------------------ |
| Interlude | no      | mobius-il                                        | —                                                                  |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline                                                           |
| Essence   | no      | mobius-ess                                       | only the `ExNevitAdvent*` packet classes remain; no `Nevit` holder |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/Nevit.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/NevitsHerald/NevitsHerald.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/skill/effects/NevitsHourglass.java`

Packets

| Chronicle | Client → server | Server → client                                                                                          |
| --------- | --------------- | -------------------------------------------------------------------------------------------------------- |
| High Five | —               | `ExNevitAdventEffect` `FE:E0`, `ExNevitAdventPointInfoPacket` `FE:DF`, `ExNevitAdventTimeChange` `FE:E1` |

Notes: the Essence `PartyHuntingBonusXp` effect is a different mechanic (party bonus).

Known fixes: `l2jdevs-data@38f03149` (2018-03-02, missing-check) — Fix Nevits Herald text missing when Antharas get killed.

### character.fame — Fame

ID: `character.fame` · Shared: **partial (HF+Ess)**

Personal fame earned in siege / fortress / territory zones over time (and from some quests/items), spent at fame
NPCs. Sub-features: zone fame task · cap · fame for dead / flagged · fame NPC · fame items/effects.

| Chronicle | Present | Builds (evidence)                                | Difference                                                                        |
| --------- | ------- | ------------------------------------------------ | --------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il, acis                                  | Mobius IL carries dormant `FameTask` (shared code); no IL source of fame          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | baseline (cap 100 000, `FameManager` NPC)                                         |
| Essence   | differs | mobius-ess                                       | `EnableFameSystem` switch; `MaxPersonalFamePoints = 0` default; `GiveFame` effect |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/FameTask.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/ai/others/FameManager/FameManager.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/FameTask.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/GiveFame.java`, `mobius-ess:dist/game/config/Player.ini` (Fame section ~:565)

Packets

| Chronicle | Client → server | Server → client                 |
| --------- | --------------- | ------------------------------- |
| High Five | —               | `UserInfo` `32`                 |
| Essence   | —               | `UserInfo` `32` bit `VITA_FAME` |

Notes: no dedicated packets; fame is shown through UserInfo (Ess bit `VITA_FAME`).

### character.weight — Weight and overweight

ID: `character.weight` · Shared: **engine**

Carried weight vs weight limit (CON-based); overweight levels slow the character and block regeneration; skills and
effects raise the limit. Sub-features: weight limit formula · overweight levels · limit effects.

| Chronicle | Present | Builds (evidence)                                | Difference                                                             |
| --------- | ------- | ------------------------------------------------ | ---------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, hellas               | baseline                                                               |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | same                                                                   |
| Essence   | yes     | mobius-ess                                       | same; `WeightLimit` / `WeightPenalty` effects, `ExUserInfoInvenWeight` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/conditions/ConditionPlayerWeight.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Player.java` (weight penalty)
- `mobius-ess:dist/game/data/scripts/handlers/skill/effects/WeightLimit.java`, `mobius-ess:dist/game/data/scripts/handlers/skill/effects/WeightPenalty.java`

Packets

| Chronicle | Client → server | Server → client                   |
| --------- | --------------- | --------------------------------- |
| Interlude | —               | `UserInfo` `04`                   |
| High Five | —               | `UserInfo` `32`                   |
| Essence   | —               | `ExUserInfoInvenWeight` `FE:0167` |

Notes: —

### character.basic-actions — Basic actions

ID: `character.basic-actions` · Shared: **partial**

Sit/stand, walk/run, social animations and the action bar: the client sends an action id, the server dispatches it.
Sub-features: sit/stand · walk/run · social actions · action list.

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                 |
| --------- | ------- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | separate `RequestSocialAction`; `RequestActionUse` switch (674 lines)                                      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | baseline: one `RequestActionUse` switch (1316 lines), `ExBasicActionList`                                  |
| Essence   | differs | mobius-ess                                                       | data-driven: `ActionData` XML + per-action handlers (`handlers/actions/user`), `RequestActionUse` 97 lines |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestActionUse.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/SitDownTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/StandUpTask.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/RequestSocialAction.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/SitDownTask.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/StandUpTask.java`
- `mobius-ess:java/org/l2jmobius/gameserver/data/xml/ActionData.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/SocialAction.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/RunWalk.java`, `mobius-ess:dist/game/data/scripts/handlers/actions/user/SitStand.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/SitDownTask.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/tasks/player/StandUpTask.java`

Packets

| Chronicle | Client → server                                                                                     | Server → client                                                                                                 |
| --------- | --------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestActionUse` `45`, `RequestSocialAction` `1B`, `ChangeMoveType2` `1C`, `ChangeWaitType2` `1D` | `ChangeWaitType` `2F`, `ChangeMoveType` `2E`, `SocialAction` `2D`, `ChairSit` `E1`                              |
| High Five | `RequestActionUse` `56`                                                                             | `ChangeWaitType` `29`, `ChangeMoveType` `28`, `SocialAction` `27`, `ExBasicActionList` `FE:5F`, `ChairSit` `ED` |
| Essence   | `RequestActionUse` `56`                                                                             | `ChangeWaitType` `29`, `ChangeMoveType` `28`, `SocialAction` `27`, `ExBasicActionList` `FE:60`, `ChairSit` `ED` |

Notes: the Essence handler-per-action layout is the target shape for a shared module; pet/summon actions from the
same switch belong to `pet.*`.

### character.info-broadcast — Character info and broadcast

ID: `character.info-broadcast` · Shared: **partial**

The player's own full info (UserInfo) and the view of other players (CharInfo), relation flags (PvP, clan war,
party), broadcast to known players; cached relations and the char-name table.
Sub-features: UserInfo · CharInfo · RelationChanged + relation cache · broadcast to known list · char name/id table.

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                                                   |
| --------- | ------- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | full packets, no masks                                                                                       |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts                 | baseline: + `ExBrExtraUserInfo`                                                                              |
| Essence   | differs | mobius-ess                                                       | `UserInfo` with `UserInfoType` component mask; `ExCharInfo` replaces `CharInfo`; split `ExUserInfo*` packets |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/UserInfo.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/CharInfo.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/cache/RelationCache.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/sql/CharInfoTable.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/UserInfo.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/UserInfo.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/enums/UserInfoType.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExCharInfo.java`

Packets

| Chronicle | Client → server | Server → client                                                                                                                                                                                                                                                                                                                       |
| --------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | —               | `UserInfo` `04`, `CharInfo` `03`, `RelationChanged` `CE`                                                                                                                                                                                                                                                                              |
| High Five | —               | `UserInfo` `32`, `CharInfo` `31`, `ExBrExtraUserInfo` `FE:DA`, `RelationChanged` `CE`                                                                                                                                                                                                                                                 |
| Essence   | —               | `UserInfo` `32` (`UserInfoType` mask), `ExCharInfo` `FE:0255`, `RelationChanged` `CE`, `ExUserInfoAbnormalVisualEffect` `FE:0159`, `ExUserInfoCubic` `FE:0158`, `ExUserInfoEquipSlot` `FE:0157`, `ExUserInfoInvenWeight` `FE:0167`, `ExUserInfoFishing` `FE:015A`, `ExUserInfoAddMDefend` `FE:034C`, `ExUserInfoAddPDefend` `FE:034B` |

Notes: Essence `UserInfoType` bits: RELATION, BASIC_INFO, BASE_STATS, MAX_HPCPMP, CURRENT_HPMPCP_EXP_SP, ENCHANTLEVEL,
APPAREANCE, STATUS, STATS, ELEMENTALS, POSITION, SPEED, MULTIPLIER, COL_RADIUS_HEIGHT, ATK_ELEMENTAL, CLAN, SOCIAL,
VITA_FAME, SLOTS, MOVEMENTS, COLOR, INVENTORY_LIMIT, TRUE_HERO, ATT_SPIRITS, RANKING, STAT_POINTS, STAT_ABILITIES,
ELIXIR_USED, VANGUARD_MOUNT. A feature that changes one of them should send only its bit.

Known fixes: `mobius@08b3c72d` (2018-05-19, crash-npe) — Fixed TrapInfo NPE. Contributed by Sahar; `mobius@287a5b30` (2018-05-30, crash-npe) — Fixed possible AbstractNpcInfo NPE. Contributed by Sahar.

### character.variables — Character variables

ID: `character.variables` · Shared: **engine**

Per-character key-value store (`character_variables`) for feature flags and counters.

| Chronicle | Present | Builds (evidence)                                | Difference                                        |
| --------- | ------- | ------------------------------------------------ | ------------------------------------------------- |
| Interlude | yes     | mobius-il, mobius-c6                             | baseline                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | same (2 lines)                                    |
| Essence   | yes     | mobius-ess                                       | same store, many more predefined keys (+90 lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/variables/PlayerVariables.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/variables/PlayerVariables.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/variables/PlayerVariables.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| all       | —               | —               |

Notes: the predefined key list is effectively a registry of features; in a shared module keys should live with their
feature.

Known fixes: `l2j-server@d3d9b0cd` (2013-04-28, missing-check) — BETA: Fixing minor problem with PlayerVariables for some reason it stores booleans as 0/1 instead of false/true.

### character.birthday — Birthday gift

ID: `character.birthday` · Shared: **partial (HF+Ess)**

Character birthday: on the anniversary of the character creation date the player receives a gift by in-game mail; on
login the client is told the days left until the birthday (or that a gift was sent). Sub-features: gift mail ·
login notification · `/mybirthday` user command · birthday-gated shop products (Essence) · birthday NPC and hat exchange (HF).

| Chronicle | Present | Builds (evidence) | Difference                                                                                                                                                                                         |
| --------- | ------- | ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | no      | mobius-il         | — (only the unrelated `Q00432_BirthdayPartySong` quest)                                                                                                                                            |
| High Five | yes     | nx-l2             | baseline: daily-reset task mails `AltBirthdayGift` to characters whose anniversary fell in the last 24 h; Alegria NPC (32600) exchanges the event hat                                              |
| Essence   | differs | mobius-ess        | birthday counter (`checkBirthDay`, `getBirthdays`), `MyBirthday` command, `MailType.BIRTHDAY`, prime-shop min/max birthday filters; no gift-mail task, `ExBirthdayPopup` is defined but never sent |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/events/CharacterBirthday/CharacterBirthday.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CharacterBirthdayManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/EnterWorld.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExBirthdayPopup.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/user/MyBirthday.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/Player.java`, `mobius-ess:dist/game/data/scripts/handlers/chat/commands/user/MyBirthday.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExBirthdayPopup.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/enums/MailType.java`, `mobius-ess:dist/game/config/General.ini`

Packets

| Chronicle | Client → server | Server → client                                                         |
| --------- | --------------- | ----------------------------------------------------------------------- |
| High Five | —               | `ExBirthdayPopup` `FE:8F` (defined, send commented out in `EnterWorld`) |
| Essence   | —               | `ExBirthdayPopup` `FE:90` (defined, never sent)                         |

Notes: config keys `AltBirthdayGift`, `AltBirthdayMailSubject`, `AltBirthdayMailText` exist in both `General.ini` files; defaults differ (gift item 22187 in HF, 72078 in Essence).
