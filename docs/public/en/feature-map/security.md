# Anti-cheat and protection

Flood protection, punishments, HWID, captcha, bot detection and client guards.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

## Features

### security.flood-protector — Flood protector

ID: `security.flood-protector` · Shared: **engine**

Per-action rate limits (use item, chat, bypass, multisell, drop, enchant, subclass, ...): an action repeated inside the configured interval is dropped, optionally logged and punished.
Sub-features: per-action interval · log flooding · punishment type/limit/time.

| Chronicle | Present | Builds (evidence)                                     | Difference |
| --------- | ------- | ----------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | baseline   |
| Essence   | yes     | mobius-ess, l2emu-ess                                 | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/util/{FloodProtectors.java,FloodProtectorAction.java,FloodProtectorSettings.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/FloodProtectorConfig.java`, `nx-l2:chronicles/high-five/datapack/config/FloodProtector.ini`
- `mobius-il:java/org/l2jmobius/gameserver/util/FloodProtectors.java`, `mobius-il:dist/game/config/FloodProtector.ini`

Packets

| Chronicle | Client → server                                                                                                                      | Server → client |
| --------- | ------------------------------------------------------------------------------------------------------------------------------------ | --------------- |
| Interlude | `RequestBypassToServer` `21`, `UseItem` `14`, `Say2` `38`, `MultiSellChoose` `A7`, `RequestDropItem` `12`, `RequestEnchantItem` `58` | —               |
| High Five | `RequestBypassToServer` `23`, `UseItem` `19`, `Say2` `49`, `MultiSellChoose` `B0`, `RequestDropItem` `17`, `RequestEnchantItem` `5F` | —               |
| Essence   | `RequestBypassToServer` `23`, `UseItem` `19`, `Say2` `49`, `MultiSellChoose` `B0`, `RequestDropItem` `17`, `RequestEnchantItem` `5F` | —               |

Notes: the config schema repeats `<Action>Interval / LogFlooding / PunishmentLimit / PunishmentType / PunishmentTime` per action. The file layout is the same in all three Mobius trees.

Known fixes: `l2j-server@a535fa89e3` (2008-01-23, crash-npe) — floodprotector npe (wasn't harmful); `l2jfrozen@2adda58026` (2011-03-04, other) — Some floodprotector changes,Chat message,and player remove from world fix.I hope now lees errors will be shown in console.

### security.punishment — Punishments

ID: `security.punishment` · Shared: **engine**

Unified punishment store: ban, chat ban, party ban, jail applied to an account, character, IP or HWID, with an expiry; handlers enforce them at login, chat and party time.
Sub-features: punishment types · affect targets · expiry task · handlers (ban, chat ban, jail) · persistence.

| Chronicle | Present | Builds (evidence)                                | Difference                                                |
| --------- | ------- | ------------------------------------------------ | --------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis (`PunishmentType` only)          | L2jFrozen / L2J-IL: no unified manager found by file name |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise, lineage2ts | baseline                                                  |
| Essence   | yes     | mobius-ess                                       | —                                                         |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/PunishmentManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/punishment/{PunishmentAffect.java,PunishmentType.java,PunishmentTask.java}`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/punishments/{BanHandler.java,ChatBanHandler.java,JailHandler.java}`
- `mobius-il:java/org/l2jmobius/gameserver/managers/PunishmentManager.java`

Packets

| Chronicle | Client → server            | Server → client                 |
| --------- | -------------------------- | ------------------------------- |
| Interlude | `Logout` `09`, `Say2` `38` | `ServerClose` `26`              |
| High Five | `Logout` `00`, `Say2` `49` | `ServerClose` `20`, `Say2` `4A` |
| Essence   | `Logout` `00`, `Say2` `49` | `ServerClose` `20`, `Say2` `4A` |

Notes: `PunishmentAffect { ACCOUNT, CHARACTER, IP, HWID }` and `PunishmentType { BAN, CHAT_BAN, PARTY_BAN, JAIL }` are identical in the IL, HF and Essence Mobius trees (IL declares the HWID affect although it has no hardware-info packet).

Known fixes: `l2j-server@330fea8001` (2013-06-07, wrong-formula-value) — BETA: Fixing little typos in punishment system using wrong database types to store/restore key.

### security.hwid — HWID

ID: `security.hwid` · Shared: **partial (HF+Ess)**

The client sends hardware information after login; the server stores it per account/session and uses it for HWID bans and per-HWID limits.
Sub-features: collect · store · ban · admin tool.

| Chronicle | Present | Builds (evidence)                                       | Difference                          |
| --------- | ------- | ------------------------------------------------------- | ----------------------------------- |
| Interlude | no      | mobius-il (holder class only), acis, l2jfrozen          | no hardware-info packet             |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise, advanced, premium | baseline                            |
| Essence   | yes     | mobius-ess, l2emu-ess                                   | packet renamed `ExUserHardwareInfo` |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/RequestHardWareInfo.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/holders/ClientHardwareInfoHolder.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminHwid.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/RequestHardWareInfo.java`

Packets

| Chronicle | Client → server               | Server → client |
| --------- | ----------------------------- | --------------- |
| High Five | `RequestHardwareInfo` `D0:96` | —               |
| Essence   | `RequestHardwareInfo` `D0:AF` | —               |

Notes: IL carries `ClientHardwareInfoHolder` and the `HWID` punishment affect but no receiving packet, so HWID is inert there. Interlude-family builds that have HWID (Ares, aCis forks, L2jMega forks) add it as custom `hwid/` packages.

### security.dualbox — Multi-client limit

ID: `security.dualbox` · Shared: **partial**

Optional limit on simultaneous clients from one IP: per-IP caps in the world, in Olympiad, in events, for offline play and offline traders; whitelist.
Sub-features: players per IP · Olympiad per IP · event per IP · offline play per IP · whitelist.

| Chronicle | Present | Builds (evidence)                    | Difference |
| --------- | ------- | ------------------------------------ | ---------- |
| Interlude | differs | mobius-il (custom)                   | —          |
| High Five | differs | nx-l2 (custom), l2j-server, eternity | baseline   |
| Essence   | differs | mobius-ess (custom)                  | —          |

`differs` = Mobius custom, off by default (`DualboxCheckMaxPlayersPerIP = 0`).

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/DualboxCheckConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/DualboxCheck.ini`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/AntiFeedManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/CharacterSelect.java`
- `mobius-il:dist/game/config/Custom/DualboxCheck.ini`

Packets

| Chronicle | Client → server                           | Server → client |
| --------- | ----------------------------------------- | --------------- |
| Interlude | `CharacterSelect` `0D`, `EnterWorld` `03` | —               |
| High Five | `CharacterSelect` `12`, `EnterWorld` `11` | —               |
| Essence   | `CharacterSelect` `12`, `EnterWorld` `11` | —               |

Notes: L2J-HF and Eternity have one file each matching the idea (by name only).

Known fixes: `l2j-server@decfd7d63a` (2016-03-31, exploit-dupe) — Dualbox white list Fix #111

### security.bot-report — Bot report

ID: `security.bot-report` · Shared: **partial (HF+Ess)**

Players report a suspected bot via a client action; points accrue per reporter and target, a configured punishment list applies at thresholds.
Sub-features: report action · per-day points · punishment table · same-clan block.

| Chronicle | Present | Builds (evidence)                                          | Difference                           |
| --------- | ------- | ---------------------------------------------------------- | ------------------------------------ |
| Interlude | no      | mobius-il, acis, l2jfrozen                                 | —                                    |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise, lineage2ts, advanced | baseline (repository layer in nx-l2) |
| Essence   | yes     | mobius-ess                                                 | report is a user-action handler      |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/BotReportTable.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/repository/social/BotReportRepository.java`, `nx-l2:chronicles/high-five/datapack/config/BotReportPunishments.xml`
- `mobius-ess:dist/game/data/scripts/handlers/actions/user/BotReport.java`, `mobius-ess:dist/game/config/BotReportPunishments.xml`

Packets

| Chronicle | Client → server                              | Server → client |
| --------- | -------------------------------------------- | --------------- |
| High Five | `RequestActionUse` `56`, `TradeRequest` `1A` | —               |
| Essence   | `RequestActionUse` `56`, `TradeRequest` `1A` | —               |

Notes: nx-l2 stores reports through a repository (`repository/social`), Mobius through `BotReportTable`.

Known fixes: `mobius@20fa7fd698` (2019-11-15, crash-npe) — Fixed BotReportTable NPE.; `l2j-server@bec9a3ada2` (2013-09-10, other) — [BETA] Bot repport fixes and adaptations. Reported by: UnAfraid, lucan

### security.captcha — Captcha

ID: `security.captcha` · Shared: **partial**

A kill counter (or an admin command) triggers a captcha challenge; a wrong or missing answer within the timeout leads to a punishment.
Sub-features: kill-counter trigger · validation time · attempts · punishment · refresh image.

| Chronicle | Present | Builds (evidence)                 | Difference                                         |
| --------- | ------- | --------------------------------- | -------------------------------------------------- |
| Interlude | differs | mobius-il (custom)                | HTML/image challenge (`ImageData`)                 |
| High Five | differs | nx-l2 (custom), advanced, premium | baseline (HTML/image challenge)                    |
| Essence   | differs | mobius-ess (custom), l2emu-ess    | dedicated client packets, generated texture blocks |

`differs` = Mobius custom, off by default (`EnableCaptcha = False`).

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/CaptchaManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/captcha/{Captcha.java,ImageData.java}`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/CaptchaConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/Captcha.ini`
- `mobius-ess:java/org/l2jmobius/gameserver/network/clientpackets/captcha/RequestCaptchaAnswer.java`, `mobius-ess:java/org/l2jmobius/gameserver/mechanics/captcha/CaptchaGenerator.java`

Packets

| Chronicle | Client → server                                                | Server → client                                               |
| --------- | -------------------------------------------------------------- | ------------------------------------------------------------- |
| Essence   | `ExCaptchaAnswer` `D0:010F`, `ExRefreshCaptchaImage` `D0:0110` | `ExCaptchaImage` `FE:018C`, `ExCaptchaAnswerResult` `FE:018D` |

Notes: Advanced and Premium (HF) also ship their own captcha.

### security.bot-detection — Bot detection

ID: `security.bot-detection` · Shared: **partial**

Heuristic switch against walker bots on the player side.
Sub-features: walker-bot protection config.

| Chronicle | Present | Builds (evidence)                                 | Difference |
| --------- | ------- | ------------------------------------------------- | ---------- |
| Interlude | differs | mobius-il (custom), l2jfrozen (`AntiBot`), hellas | —          |
| High Five | differs | nx-l2 (custom), eternity, sunrise, lineage2ts     | baseline   |
| Essence   | differs | mobius-ess (custom)                               | —          |

`differs` = Mobius custom, off by default (`L2WalkerProtection = False`).

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/WalkerBotProtectionConfig.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/WalkerBotProtection.ini`
- `mobius-il:dist/game/config/Custom/WalkerBotProtection.ini`

Packets: none dedicated.

Notes: Mobius ships only the walker-protection switch; richer anti-bot mods exist as separate files in L2jFrozen (`AntiBot.properties`), Eternity and Sunrise.

### security.packet-guard — Protocol protection

ID: `security.packet-guard` · Shared: **engine**

GameGuard query/reply exchange at login (optionally enforced).
Sub-features: GameGuard query/reply.

| Chronicle | Present | Builds (evidence)                                     | Difference |
| --------- | ------- | ----------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | baseline   |
| Essence   | yes     | mobius-ess, l2emu-ess                                 | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/GameGuardReply.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/ExRequestHackShield.java`
- `mobius-il:java/org/l2jmobius/gameserver/network/clientpackets/GameGuardReply.java`, `mobius-il:java/org/l2jmobius/gameserver/network/serverpackets/ExRequestHackShield.java`
- `mobius-ess:java/org/l2jmobius/gameserver/network/serverpackets/ExRequestHackShield.java`

Packets

| Chronicle | Client → server       | Server → client                                         |
| --------- | --------------------- | ------------------------------------------------------- |
| Interlude | `GameGuardReply` `CA` | `GameGuardQuery` `F9`, `EX_REQUEST_HACK_SHIELD` `FE:48` |
| High Five | `GameGuardReply` `CB` | `GameGuardQuery` `74`, `EX_REQUEST_HACK_SHIELD` `FE:49` |
| Essence   | `GameGuardReply` `CB` | `GameGuardQuery` `74`, `EX_REQUEST_HACK_SHIELD` `FE:4A` |

Notes: no connection rate limiting or unauthenticated-socket cap was found in the three Mobius trees (nx-l2 included); the only throttling is `security.flood-protector`.

### security.client-guard — Client guard

ID: `security.client-guard` · Shared: **partial (IL+HF)**

Server half of a client-side protection pack: key derivation for the client handshake, the HWID option (`UseClientHWID`), a per-HWID character limit (`MaxChars`), automatic ban-list cleaning and a punishment ladder for detected bots. Disabled by default (`GuardEnabled = False`).
Sub-features: handshake key · HWID option · per-HWID character limit · ban cleaning.

| Chronicle | Present | Builds (evidence)        | Difference                   |
| --------- | ------- | ------------------------ | ---------------------------- |
| Interlude | yes     | ares-melnkovmaxim (Ares) | SmartGuard package with menu |
| High Five | yes     | advanced                 | baseline                     |
| Essence   | no      | mobius-ess               | —                            |

Entry points

- `advanced:src/main/java/l2f/gameserver/hwid/SmartGuard.java`, `advanced:dist/gameserver/config/SmartGuard.properties`
- `ares-melnkovmaxim:src/src/ru/agecold/gameserver/network/smartguard/SmartGuard.java`, `ares-melnkovmaxim:src/src/ru/agecold/gameserver/network/smartguard/menu/SmartGuardMenu.java`

Packets: none.

Notes: needs the matching client-side module; the protocol-level guards are `security.packet-guard`.

### security.illegal-action — Illegal actions

ID: `security.illegal-action` · Shared: **engine**

Suspicious packets and rule violations raise an illegal-action report with a configured punishment (log, kick, ban, jail) and a GM notice.
Sub-features: illegal-player-action task · punishment per violation.

| Chronicle | Present | Builds (evidence)                                     | Difference |
| --------- | ------- | ----------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server-686500, hellas | —          |
| High Five | yes     | nx-l2, l2j-server, eternity, sunrise                  | baseline   |
| Essence   | yes     | mobius-ess, l2emu-ess                                 | —          |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/tasks/player/IllegalPlayerActionTask.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/Player.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/tasks/player/IllegalPlayerActionTask.java`

Packets: none dedicated (raised from handlers of enchant, bypass, use-item packets).

Notes: present by class name in every sampled root of all three versions.

Known fixes: `lineage21-l2jenergy@1cc11dba30` (2020-08-31, other) — Removing custom class FastStringBuilder Fixing invalid DefaultPunish configuration options; `l2j-server@f9ec03dfc2` (2020-08-30, other) — Fixing invalid DefaultPunish configuration options
