# Administration

GM commands grouped by purpose, access levels, GM chat and petitions.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### admin.access-levels — Access levels and command rights

ID: `admin.access-levels` · Shared: **engine**

Each account has an access level; `AdminCommands.xml` maps every admin command to the minimum level, with optional confirmation for dangerous commands; GM actions are audited to a log.
Sub-features: access-level table · per-command rights · confirm-dangerous · GM audit log · PcCondOverride (GM bypass of game conditions) · change access level.

| Chronicle | Present | Builds (evidence)                             | Difference |
| --------- | ------- | --------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise          | baseline   |
| Essence   | yes     | mobius-ess, l2emu-ess                         | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/AdminData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/holders/AccessLevel.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/util/GMAudit.java`, `nx-l2:chronicles/high-five/datapack/config/{AccessLevels.xml,AdminCommands.xml}`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminChangeAccessLevel.java`
- `mobius-il:java/org/l2jmobius/gameserver/data/xml/AdminData.java`, `mobius-il:java/org/l2jmobius/log/filter/GMAuditFilter.java`

Packets

| Chronicle | Client → server              | Server → client |
| --------- | ---------------------------- | --------------- |
| Interlude | `RequestBypassToServer` `21` | —               |
| High Five | `RequestBypassToServer` `23` | —               |
| Essence   | `RequestBypassToServer` `23` | —               |

Notes: `GMAudit` log writes through the logging filter in IL and Essence; nx-l2 keeps `util/GMAudit.java`.

Known fixes: `l2j-server@2f57492f8f` (2012-11-11, other) — BETA: Fixing issue with GM names and logs. * Reported by: DareStrike; `lineage21-l2jenergy@46b3f7ef89` (2019-09-25, other) — fix logs for GMAudit

### admin.panel — Admin panel

ID: `admin.panel` · Shared: **engine**

The GM panel: command index, help pages and HTML screens for category menus.
Sub-features: main menu · help pages · HTML viewer · server info page.

| Chronicle | Present | Builds (evidence)                 | Difference |
| --------- | ------- | --------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen        | —          |
| High Five | yes     | nx-l2, l2j-server, eternity       | baseline   |
| Essence   | yes     | mobius-ess (+`AdminMissingHtmls`) | extra tool |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminAdmin.java,AdminMenu.java,AdminHelp.java,AdminHtml.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminMenu.java`

Packets

| Chronicle | Client → server                                       | Server → client       |
| --------- | ----------------------------------------------------- | --------------------- |
| Interlude | `RequestBypassToServer` `21`, `RequestGmCommand` `6E` | `NpcHtmlMessage` `0F` |
| High Five | `RequestBypassToServer` `23`, `RequestGmCommand` `7E` | `NpcHtmlMessage` `19` |
| Essence   | `RequestBypassToServer` `23`, `RequestGmCommand` `7E` | `NpcHtmlMessage` `19` |

Notes: HTML pages live under `data/html/admin` in each tree (not enumerated).

Known fixes: `l2j-server-dp@789188d4c8` (2012-06-16, crash-npe) — BETA: Fix for NoSuchElementException in reload command, thanks DareStrike for report.; `l2jdevs-data@abad9ae393` (2019-06-23, crash-npe) — Better Rework AdminMenu class and fix StringIndexOutOfBoundsException; `sunrise-data@b33d49b8ae` (2016-12-17, crash-npe) — Minor admin menu changes Minor npe fix

### admin.character-tools — Character commands

ID: `admin.character-tools` · Shared: **engine**

Edit a target character: level / exp / SP, class, repair, teleport to / go to, speed, invisibility, invulnerability.
Sub-features: edit-char menu · level · exp/sp · repair char · teleport/goto · hide · invul · gm-speed.

| Chronicle | Present | Builds (evidence)                             | Difference |
| --------- | ------- | --------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise          | baseline   |
| Essence   | yes     | mobius-ess                                    | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminEditChar.java,AdminLevel.java,AdminExpSp.java,AdminRepairChar.java,AdminTeleport.java,AdminGoto.java,AdminHide.java,AdminInvul.java,AdminGmSpeed.java}`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminSuperHaste.java`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminEditChar.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminSuperHaste.java`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/admin/AdminSuperHaste.java`

Packets

| Chronicle | Client → server              | Server → client                                                                                                                                                       |
| --------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `GMViewCharacterInfo` `8F`, `GMViewPledgeInfo` `90`, `GMViewSkillInfo` `91`                                                                                           |
| High Five | `RequestBypassToServer` `23` | `GMViewCharacterInfo` `95`, `GMViewPledgeInfo` `96`, `GMViewSkillInfo` `97`, `GMHide` `93`, `GM_VIEW_MAGIC_INFO` `98`                                                 |
| Essence   | `RequestBypassToServer` `23` | `GMViewCharacterInfo` `95`, `GMViewPledgeInfo` `96`, `GMViewSkillInfo` `97`, `GMHide` `93`, `ExGMViewVitalityEffectInfo` `FE:0151`, `ExGMViewCharacterInfo` `FE:0156` |

Notes: Essence adds `AdminVitality` equivalents inside the vitality system (see `character.vitality`).

Known fixes: `l2j-server-dp@8982aac8ce` (2011-12-13, exploit-dupe) — BETA/STABLE: Fix for visual glitch of setsex, setclass commands by VlLight.; `sunrise-data@b33d49b8ae` (2016-12-17, crash-npe) — Minor admin menu changes Minor npe fix; `mobius@3de6ee7291` (2022-06-08, crash-npe) — Fixed probable admin_setclass NPE.

### admin.item-tools — Item commands

ID: `admin.item-tools` · Shared: **partial**

Create and destroy items, enchant, set attribute (element) and augmentation on a target.
Sub-features: create item · destroy items · enchant · element · augment.

| Chronicle | Present | Builds (evidence)           | Difference                        |
| --------- | ------- | --------------------------- | --------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen  | no `AdminElement` (no attributes) |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline                          |
| Essence   | yes     | mobius-ess                  | —                                 |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminCreateItem.java,AdminDestroyItems.java,AdminEnchant.java,AdminElement.java,AdminAugment.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminCreateItem.java`

Packets

| Chronicle | Client → server              | Server → client                                                             |
| --------- | ---------------------------- | --------------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `InventoryUpdate` `27`, `ItemList` `1B`                                     |
| High Five | `RequestBypassToServer` `23` | `InventoryUpdate` `21`, `ItemList` `11`, `ExAttributeEnchantResult` `FE:61` |
| Essence   | `RequestBypassToServer` `23` | `InventoryUpdate` `21`, `ItemList` `11`, `ExAttributeEnchantResult` `FE:62` |

Notes: `AdminElement` is HF-only versus IL in the Mobius trees.

Known fixes: `l2j-server-dp@ca813cae6c` (2009-06-01, wrong-formula-value) — Fix admin create item for long count, thx janiii

### admin.npc-spawn-tools — NPC and spawn commands

ID: `admin.npc-spawn-tools` · Shared: **engine**

Spawn and delete NPCs, mob groups and the NPC editor.
Sub-features: spawn · delete · summon · fake players · NPC editor (aCis/l2e).

| Chronicle | Present | Builds (evidence)                             | Difference |
| --------- | ------- | --------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —          |
| High Five | yes     | nx-l2, l2j-server, eternity                   | baseline   |
| Essence   | yes     | mobius-ess                                    | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminSpawn.java,AdminSummon.java,AdminFakePlayers.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminSpawn.java`

Packets

| Chronicle | Client → server              | Server → client                     |
| --------- | ---------------------------- | ----------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `NpcInfo` `16`, `DeleteObject` `12` |
| High Five | `RequestBypassToServer` `23` | `NpcInfo` `0C`, `DeleteObject` `08` |
| Essence   | `RequestBypassToServer` `23` | `NpcInfo` `0C`, `DeleteObject` `08` |

Notes: `AdminFakePlayers` exists in all three Mobius trees (fake players are a Mobius custom).

Known fixes: `l2jfrozen@d15dc240d0` (2011-05-24, crash-npe) — [CORE] Admin spawn update, exception fixed.

### admin.skill-effect-tools — Skill and effect commands

ID: `admin.skill-effect-tools` · Shared: **partial**

Grant or remove skills, apply buffs and effects, transform, heal, kill, resurrect.
Sub-features: skills · buffs · effects · transform · heal · kill · res.

| Chronicle | Present | Builds (evidence)           | Difference          |
| --------- | ------- | --------------------------- | ------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen  | no `AdminTransform` |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline            |
| Essence   | yes     | mobius-ess                  | —                   |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminSkill.java,AdminBuffs.java,AdminEffects.java,AdminTransform.java,AdminHeal.java,AdminKill.java,AdminRes.java}`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/admin/AdminTransform.java`

Packets

| Chronicle | Client → server              | Server → client                                                                          |
| --------- | ---------------------------- | ---------------------------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `SkillList` `58`, `AbnormalStatusUpdate` `7F`, `MagicSkillUse` `48`, `StatusUpdate` `0E` |
| High Five | `RequestBypassToServer` `23` | `SkillList` `5F`, `AbnormalStatusUpdate` `85`, `MagicSkillUse` `48`, `StatusUpdate` `18` |
| Essence   | `RequestBypassToServer` `23` | `SkillList` `5F`, `AbnormalStatusUpdate` `85`, `MagicSkillUse` `48`, `StatusUpdate` `18` |

Notes: transformations do not exist as a separate GM tool in IL trees.

Known fixes: `mobius@1df749fff3` (2022-01-14, crash-npe) — AdminKill handler null target fix. Contributed by CostyKiller.; `l2j-server-dp@8a6f620d70` (2009-08-26, crash-npe) — JAVA: NPE fix in AdminBuff, thanks janiii again :P; `sunrise-data@b33d49b8ae` (2016-12-17, crash-npe) — Minor admin menu changes Minor npe fix

### admin.punishment-tools — Punishment commands

ID: `admin.punishment-tools` · Shared: **partial**

Ban, jail, kick, disconnect, chat ban, HWID ban from the GM console.
Sub-features: punishment (ban/jail/chat) · kick · disconnect · HWID admin.

| Chronicle | Present | Builds (evidence)           | Difference     |
| --------- | ------- | --------------------------- | -------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen  | no `AdminHwid` |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline       |
| Essence   | yes     | mobius-ess                  | —              |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminPunishment.java,AdminKick.java,AdminDisconnect.java,AdminHwid.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminPunishment.java`

Packets

| Chronicle | Client → server                                             | Server → client                       |
| --------- | ----------------------------------------------------------- | ------------------------------------- |
| Interlude | `RequestBypassToServer` `21`                                | `ServerClose` `26`, `LeaveWorld` `7E` |
| High Five | `RequestBypassToServer` `23`, `RequestHardwareInfo` `D0:96` | `ServerClose` `20`, `LeaveWorld` `84` |
| Essence   | `RequestBypassToServer` `23`, `RequestHardwareInfo` `D0:AF` | `ServerClose` `20`, `LeaveWorld` `84` |

Notes: unblock-IP tool (`UnblockIp`) is an Eternity addition.

Known fixes: `l2jfrozen@761ad31c84` (2011-04-16, other) — - Fixed //kick name. Now you can kick also players in Offline Mode.

### admin.server-tools — Server commands

ID: `admin.server-tools` · Shared: **engine**

Restart / shutdown with countdown, reload data and configs, server statistics, online list, announcements.
Sub-features: shutdown/restart · reload · server info · online · announcements.

| Chronicle | Present | Builds (evidence)           | Difference |
| --------- | ------- | --------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen  | —          |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline   |
| Essence   | yes     | mobius-ess                  | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminShutdown.java,AdminReload.java,AdminServerInfo.java,AdminOnline.java,AdminAnnouncements.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminReload.java`

Packets

| Chronicle | Client → server                           | Server → client                                                       |
| --------- | ----------------------------------------- | --------------------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21`, `Say2` `38` | `CreatureSay` `4A`, `ExShowScreenMessage` `FE:38`, `ServerClose` `26` |
| High Five | `RequestBypassToServer` `23`              | `CreatureSay` `4A`, `ExShowScreenMessage` `FE:39`, `ServerClose` `20` |
| Essence   | `RequestBypassToServer` `23`              | `CreatureSay` `4A`, `ExShowScreenMessage` `FE:39`, `ServerClose` `20` |

Notes: no aCis equivalent for `AdminShutdown` by file name by file name (aCis wires it inside `AdminAdmin`).

Known fixes: `l2jfrozen@ed6fab4f36` (2011-02-22, crash-npe) — Some improvements/fixes on "Hidden Java Exceptions" management; `hellas@c79b75dfc2` (2012-11-26, wrong-formula-value) — Minor fixes in reload menu and some sql files Adding admin account user:admin pass:admin; `mobius@8c63798e84` (2022-10-18, wrong-formula-value) — Fixed server info deamon thread count.

### admin.residence-tools — Residence and siege commands

ID: `admin.residence-tools` · Shared: **partial**

Castles, forts, clan halls, sieges, territory war and manor from the GM console.
Sub-features: castle siege · fort siege · clan-hall siege · territory war · manor · Essence castle/clan-hall/olympiad tools.

| Chronicle | Present | Builds (evidence)           | Difference                                                                                                                              |
| --------- | ------- | --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen  | `AdminSiege`, `AdminCHSiege`, `AdminManor`                                                                                              |
| High Five | yes     | nx-l2, l2j-server, eternity | adds `AdminFortSiege`, `AdminTerritoryWar`                                                                                              |
| Essence   | differs | mobius-ess                  | has `AdminFortSiege`, `AdminManor`, `AdminCastle`, `AdminClanHall`, `AdminOlympiad`; no `AdminSiege`/`AdminTerritoryWar`/`AdminCHSiege` |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminSiege.java,AdminFortSiege.java,AdminCHSiege.java,AdminTerritoryWar.java,AdminManor.java}`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/admin/{AdminCastle.java,AdminClanHall.java,AdminOlympiad.java}`

Packets

| Chronicle | Client → server              | Server → client                         |
| --------- | ---------------------------- | --------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `NpcHtmlMessage` `0F`, `SiegeInfo` `C9` |
| High Five | `RequestBypassToServer` `23` | `NpcHtmlMessage` `19`, `SiegeInfo` `C9` |
| Essence   | `RequestBypassToServer` `23` | `NpcHtmlMessage` `19`, `SiegeInfo` `C9` |

Notes: `AdminOlympiad` is Essence-only among Mobius trees.

Known fixes: `l2j-server@b462763dcd` (2007-06-08, crash-npe) — Little Cleanup, fix possible NPE, possible fix for strange bug with ClanHallManager (And log line added).; `sunrise-data@e529ba1e13` (2015-12-16, wrong-formula-value) — Fixed some times dark choir npcs fall into the ground Fixed admin command set siege time; `l2j-server-dp@27d0af8678` (2009-05-29, other) — fix //clanhall command without params or with a non number param, thx brutus

### admin.world-tools — World commands

ID: `admin.world-tools` · Shared: **engine**

Geodata inspection, zones, doors, fences, path nodes, regions.
Sub-features: geodata · zone / zone build · door control · fence · path node · regions.

| Chronicle | Present | Builds (evidence)           | Difference |
| --------- | ------- | --------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen  | —          |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline   |
| Essence   | yes     | mobius-ess                  | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminGeodata.java,AdminZone.java,AdminZoneBuild.java,AdminDoorControl.java,AdminFence.java,AdminPathNode.java,AdminRegions.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminGeodata.java`

Packets

| Chronicle | Client → server              | Server → client                                      |
| --------- | ---------------------------- | ---------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `ExServerPrimitive` `FE:24`, `DoorStatusUpdate` `4D` |
| High Five | `RequestBypassToServer` `23` | `ExServerPrimitive` `FE:11`, `DoorStatusUpdate` `4D` |
| Essence   | `RequestBypassToServer` `23` | `ExServerPrimitive` `FE:11`, `DoorStatusUpdate` `4D` |

Notes: no zone editor in Mobius or Eternity (not found by name in public roots).

Known fixes: `l2j-server@d440e0a1c3` (2007-01-23, missing-check) — GeoData: typo fix, support for GeoDataBugs (will be used be L2jGeoEditor) & Los check for player casting :P; `mobius@809dc770a8` (2018-03-31, missing-check) — Fixed wrong zone check html break position.

### admin.content-tools — Content commands

ID: `admin.content-tools` · Shared: **partial**

Quests, events, reflections (instances), grand bosses, Gracia seeds, Hellbound, Mammon.
Sub-features: quest · show quests · events · instance / instance-zone · grand boss · Gracia seeds · Hellbound · Mammon.

| Chronicle | Present | Builds (evidence)           | Difference                                |
| --------- | ------- | --------------------------- | ----------------------------------------- |
| Interlude | differs | mobius-il, acis, l2jfrozen  | no Gracia seeds, no Hellbound; has Mammon |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline                                  |
| Essence   | differs | mobius-ess                  | no Mammon, Gracia seeds, Hellbound        |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminQuest.java,AdminShowQuests.java,AdminEvents.java,AdminInstance.java,AdminInstanceZone.java,AdminGrandBoss.java,AdminGraciaSeeds.java,AdminHellbound.java,AdminMammon.java}`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/admin/AdminInstance.java`

Packets

| Chronicle | Client → server              | Server → client                                                          |
| --------- | ---------------------------- | ------------------------------------------------------------------------ |
| Interlude | `RequestBypassToServer` `21` | `NpcHtmlMessage` `0F`, `ExShowQuestMark` `FE:1A`, `GMViewQuestInfo` `93` |
| High Five | `RequestBypassToServer` `23` | `NpcHtmlMessage` `19`, `ExShowQuestMark` `FE:21`, `GMViewQuestInfo` `99` |
| Essence   | `RequestBypassToServer` `23` | `NpcHtmlMessage` `19`, `ExShowQuestMark` `FE:21`, `GMViewQuestInfo` `99` |

Notes: `AdminGrandBoss` is present in IL, HF, Essence.

Known fixes: `l2j-server-dp@de842932d4` (2014-05-22, wrong-formula-value) — BETA: Fixing minor typo in AdminQuest handler that was showing wrong QuestEventType types registered by specific quest.; `mobius@7f02780e63` (2020-04-06, other) — Fixed AdminGrandBoss handler after revision 7084 changes.

### admin.clan-tools — Clan commands

ID: `admin.clan-tools` · Shared: **engine**

Manage clans and alliances: create, level, reputation, members.
Sub-features: clan admin · pledge admin.

| Chronicle | Present | Builds (evidence)                             | Difference |
| --------- | ------- | --------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500 | —          |
| High Five | yes     | nx-l2, l2j-server, eternity                   | baseline   |
| Essence   | yes     | mobius-ess                                    | —          |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminClan.java,AdminPledge.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminPledge.java`

Packets

| Chronicle | Client → server              | Server → client                                                                      |
| --------- | ---------------------------- | ------------------------------------------------------------------------------------ |
| Interlude | `RequestBypassToServer` `21` | `PledgeShowInfoUpdate` `88`, `PledgeShowMemberListAll` `53`, `GMViewPledgeInfo` `90` |
| High Five | `RequestBypassToServer` `23` | `PledgeShowInfoUpdate` `8E`, `PledgeShowMemberListAll` `5A`, `GMViewPledgeInfo` `96` |
| Essence   | `RequestBypassToServer` `23` | `PledgeShowInfoUpdate` `8E`, `PledgeShowMemberListAll` `5A`, `GMViewPledgeInfo` `96` |

Notes: —

Known fixes: `mobius@b25618c95c` (2021-12-17, other) — Fixed clan contribution points decreasing clan exp. Contributed by nasseka.

### admin.economy-tools — Economy commands

ID: `admin.economy-tools` · Shared: **partial**

Shops, game points, PC Cafe points, premium accounts, Prime (Essence) points.
Sub-features: shop · game points · PC-cafe points · premium · Essence prime points.

| Chronicle | Present | Builds (evidence)           | Difference                                             |
| --------- | ------- | --------------------------- | ------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen  | `AdminGamePoints`, `AdminPcCafePoints`, `AdminPremium` |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline                                               |
| Essence   | differs | mobius-ess                  | `AdminPrimePoints` instead of `AdminGamePoints`        |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminShop.java,AdminGamePoints.java,AdminPcCafePoints.java,AdminPremium.java}`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/admin/AdminPrimePoints.java`

Packets

| Chronicle | Client → server              | Server → client                                          |
| --------- | ---------------------------- | -------------------------------------------------------- |
| Interlude | `RequestBypassToServer` `21` | `ExPCCafePointInfo` `FE:31`                              |
| High Five | `RequestBypassToServer` `23` | `ExPCCafePointInfo` `FE:32`, `ExBrExtraUserInfo` `FE:DA` |
| Essence   | `RequestBypassToServer` `23` | `ExPCCafePointInfo` `FE:32`, `ExBrExtraUserInfo` `FE:DC` |

Notes: premium (`.premium`) is an optional Mobius custom in all three trees.

### admin.diagnostics — Diagnostics

ID: `admin.diagnostics` · Shared: **partial**

Debugging and verification tools: packet forge, debug toggles, test, scan, search, camera, fight calculator.
Sub-features: packet forge · debug · test · scan · search · camera · fight calculator.

| Chronicle | Present | Builds (evidence)           | Difference                                      |
| --------- | ------- | --------------------------- | ----------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen  | baseline set incl. `AdminSearch`, `AdminCamera` |
| High Five | yes     | nx-l2, l2j-server, eternity | baseline (`AdminSearch` present)                |
| Essence   | differs | mobius-ess                  | no `AdminSearch`                                |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminPForge.java,AdminDebug.java,AdminTest.java,AdminScan.java,AdminSearch.java,AdminCamera.java,AdminFightCalculator.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/SnoopQuit.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/Snoop.java`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminPForge.java`, `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/SnoopQuit.java`, `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/Snoop.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/SnoopQuit.java`, `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/Snoop.java`

Packets

| Chronicle | Client → server                                                            | Server → client                                         |
| --------- | -------------------------------------------------------------------------- | ------------------------------------------------------- |
| Interlude | `SendBypassBuildCmd` `5B`, `RequestBypassToServer` `21`, `SNOOP_QUIT` `AB` | `SpecialCamera` `C7`, `NormalCamera` `C8`, `SNOOP` `D5` |
| High Five | `SendBypassBuildCmd` `74`, `RequestBypassToServer` `23`, `SNOOP_QUIT` `B4` | `SpecialCamera` `D6`, `NormalCamera` `D7`, `SNOOP` `DB` |
| Essence   | `SendBypassBuildCmd` `74`, `RequestBypassToServer` `23`, `SNOOP_QUIT` `B4` | `SpecialCamera` `D6`, `NormalCamera` `D7`, `SNOOP` `DB` |

Notes: `AdminSearch` is in the IL and HF Mobius trees but not in Essence; `AdminCamera`, `AdminPForge`, `AdminTest`, `AdminScan`, `AdminFightCalculator`, `AdminDebug` are in all three.

Known fixes: `mobius@0984cf59f8` (2020-01-30, other) — Fixed AdminTest commands falling through next commands.

### admin.gm-chat — GM chat and GM list

ID: `admin.gm-chat` · Shared: **engine**

GM-only chat channel and the list of online GMs shown to players.
Sub-features: GM chat · GM list · target say.

| Chronicle | Present | Builds (evidence)                       | Difference        |
| --------- | ------- | --------------------------------------- | ----------------- |
| Interlude | yes     | mobius-il, l2jfrozen, l2j-server-686500 | —                 |
| High Five | yes     | nx-l2, l2j-server, eternity             | baseline          |
| Essence   | yes     | mobius-ess                              | `GMList` c2s name |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/{AdminGmChat.java,AdminGm.java,AdminTargetSay.java}`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminGmChat.java`

Packets

| Chronicle | Client → server                   | Server → client                                |
| --------- | --------------------------------- | ---------------------------------------------- |
| Interlude | `RequestGmList` `81`, `Say2` `38` | `GMViewCharacterInfo` `8F`, `CreatureSay` `4A` |
| High Five | `RequestGmList` `8B`              | `GMViewCharacterInfo` `95`, `Say2` `4A`        |
| Essence   | `RequestGmList` `8B`              | `GMViewCharacterInfo` `95`, `Say2` `4A`        |

Notes: no `GmChat` file name matched in aCis; it exists in Ares, L2jFrozen, L2J-IL and Hellas.

Known fixes: `l2j-server-dp@6ffff1ccc6` (2009-05-29, other) — Fix for GM chat (thx janiii, brutus); `l2j-server-dp@446c7dceb8` (2010-06-29, other) — JAVA: fix //snoop <name>, thx JIV

### admin.petition — Petitions

ID: `admin.petition` · Shared: **partial**

Players file petitions; GMs queue, accept, chat and close them; players can rate the result.
Sub-features: submit · cancel · queue and accept · petition chat · feedback/vote · Essence web petition alarm.

| Chronicle | Present | Builds (evidence)                                     | Difference                                                           |
| --------- | ------- | ----------------------------------------------------- | -------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | baseline                                                             |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | adds feedback (`PetitionVote`)                                       |
| Essence   | differs | mobius-ess                                            | web petition (`ExRequestShowPetition`, `ExResponseWebPetitionAlarm`) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/PetitionManager.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminPetition.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/channels/ChatPetition.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/PetitionManager.java`

Packets

| Chronicle | Client → server                                                                                                                                             | Server → client                                                |
| --------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| Interlude | `RequestPetition` `7F`, `RequestPetitionCancel` `80`                                                                                                        | —                                                              |
| High Five | `RequestPetition` `89`, `RequestPetitionCancel` `8A`, `RequestPetitionFeedback` `C9`, `RequestShowNewUserPetition` `D0:70`                                  | `PetitionVote` `FC`, `ExShowPetitionHtml` `FE:B1`              |
| Essence   | `RequestPetition` `89`, `RequestPetitionCancel` `8A`, `RequestPetitionFeedback` `C9`, `ExRequestShowPetition` `D0:6E`, `ExResponseWebPetitionAlarm` `D0:E1` | `PetitionVote` `FC`, `ExNotifyWebPetitionReplyAlarm` `FE:014C` |

Notes: `PetitionManager` is 287 normalized lines in nx-l2, 334 (IL) and 351 (Essence) apart.
