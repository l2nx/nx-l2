# Account and login

Everything before a character appears in the world: the login server, the login-to-game link and account protection.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### account.login — Account login

ID: `account.login` · Shared: **engine**

Login server handshake (RSA key exchange, Blowfish session), credential check, optional auto-creation of an unknown
account, account/IP ban check, GameGuard auth stub; on success the client gets LoginOk and the server list.
Sub-features: RSA/Blowfish handshake · auto-create account · ban check (account, IP) · failed-attempt IP ban ·
GameGuard reply · command-line login (Ess).

| Chronicle | Present | Builds (evidence)                                                           | Difference                                                                                                         |
| --------- | ------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim, mobius-c6 | baseline (Mobius `LoginController` identical in IL/HF/Ess)                                                         |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, premium, lineage2ts                   | same as IL                                                                                                         |
| Essence   | yes     | mobius-ess (09.2, 04.2)                                                     | `RequestAuthLogin`: command-line-only login mode; wrong password → `ACCESS_FAILED` instead of `USER_OR_PASS_WRONG` |

Entry points

- `nx-l2:login/server/src/main/java/app/l2nx/login/controller/LoginController.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/network/clientpackets/RequestAuthLogin.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/LoginServer.java`
- `mobius-il:java/org/l2jmobius/loginserver/controller/LoginController.java`, `mobius-il:java/org/l2jmobius/loginserver/network/clientpackets/RequestAuthLogin.java`
- `mobius-ess:java/org/l2jmobius/loginserver/controller/LoginController.java`, `mobius-ess:java/org/l2jmobius/loginserver/network/clientpackets/RequestAuthLogin.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                    | Server → client                                                                                                                                                                                                  |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestAuthLogin` `00`, `AuthGameGuard` `07`, `RequestServerLogin` `02`; game: `ProtocolVersion` `00`, `AuthLogin` `08`, `GameGuardReply` `CA`                                                                                    | `Init` `00`, `GGAuth` `0B`, `LoginOk` `03`, `LoginFail` `01`, `PlayOk` `07`, `PlayFail` `06`; game: `KeyPacket` `00`, `GameGuardQuery` `F9`                                                                      |
| High Five | `RequestAuthLogin` `00`, `AuthGameGuard` `07`, `RequestServerLogin` `02`; game: `ProtocolVersion` `0E`, `AuthLogin` `2B`, `GameGuardReply` `CB`                                                                                    | `Init` `00`, `GGAuth` `0B`, `LoginOk` `03`, `LoginFail` `01`, `PlayOk` `07`, `PlayFail` `06`; game: `KeyPacket` `2E`, `GameGuardQuery` `74`                                                                      |
| Essence   | `RequestAuthLogin` `00`, `AuthGameGuard` `07`, `RequestServerLogin` `02`; game: `ProtocolVersion` `0E`, `AuthLogin` `2B`, `GameGuardReply` `CB`, `RequestCmdLogin` `0B`, `RequestPIAgreementCheck` `0E`, `RequestPIAgreement` `0F` | `Init` `00`, `GGAuth` `0B`, `LoginOk` `03`, `LoginFail` `01`, `PlayOk` `07`, `PlayFail` `06`; game: `KeyPacket` `2E`, `GameGuardQuery` `74`, `LoginOtpFail` `0D`, `PIAgreementCheck` `11`, `PIAgreementAck` `12` |

Notes: `AutoCreateAccounts = True` by default in all three Mobius `dist/login/config/Server.ini`. Independent rewrites
(L2dotNET C#, L2Interlude C#, L2kt Kotlin, L2Server Kotlin, a C++ login server) implement only this handshake and
the server list — useful as protocol references, not as behaviour references.

Known fixes: `sunrise@3be47552` (2015-04-27, exploit-dupe) — Fixed one exploit just in case someone tries to use patched system and create account with strange characters; `l2j-server@fd444433` (2007-01-06, exploit-dupe) — Next Fix for account hacking - i hoop this will solve problem :].

### account.server-list — Server list

ID: `account.server-list` · Shared: **partial**

After login the client requests the list of game servers: id, address, status (up/down), online/max players,
PvP flag, age marker, brackets, last-played server, characters per server.
Sub-features: status/online/limit · age marker · last server · characters on server · characters pending deletion.

| Chronicle | Present | Builds (evidence)                                                | Difference                                                                 |
| --------- | ------- | ---------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline                                                                   |
| High Five | yes     | nx-l2, l2j-server, sunrise, lineage2ts                           | same packet as Mobius IL                                                   |
| Essence   | yes     | mobius-ess                                                       | no "characters pending deletion" block; trailing field `0xA4` instead of 0 |

Entry points

- `nx-l2:login/server/src/main/java/app/l2nx/login/network/serverpackets/ServerList.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/network/clientpackets/RequestServerList.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/GameServerListener.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/admin/AdminLogin.java`
- `mobius-il:java/org/l2jmobius/loginserver/network/serverpackets/ServerList.java`, `mobius-il:dist/game/data/scripts/handlers/chat/commands/admin/AdminLogin.java`
- `mobius-ess:java/org/l2jmobius/loginserver/network/serverpackets/ServerList.java`, `mobius-ess:dist/game/data/scripts/handlers/chat/commands/admin/AdminLogin.java`

Packets

| Chronicle | Client → server          | Server → client   |
| --------- | ------------------------ | ----------------- |
| Interlude | `RequestServerList` `05` | `ServerList` `04` |
| High Five | `RequestServerList` `05` | `ServerList` `04` |
| Essence   | `RequestServerList` `05` | `ServerList` `04` |

Notes: wire format is client-protocol specific; the server-side model (status, limits, chars per server) is shared.

Known fixes: `l2j-server@61afecfc` (2014-02-14, crash-npe) — BETA: Fixed an NPE in `GameServerThread`.

### account.gameserver-link — Login ↔ game server link

ID: `account.gameserver-link` · Shared: **engine**

Internal protocol between login and game servers: game server registers with its hex id, sends player auth requests
(session keys), online/logout notifications and status changes; login can kick a player on request.
Sub-features: GS registration by hexid · PlayerAuthRequest / session keys · PlayerInGame / PlayerLogout · kick ·
server status · change access level · change password relay.

| Chronicle | Present | Builds (evidence)                             | Difference                                    |
| --------- | ------- | --------------------------------------------- | --------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | baseline                                      |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same (Mobius `GameServerThread` identical)    |
| Essence   | yes     | mobius-ess                                    | same (`LoginServerThread` differs by 3 lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/LoginServerThread.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/loginserverpackets/send/PlayerAuthRequest.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/GameServerThread.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/network/gameserverpackets/receive/GameServerAuth.java`
- `mobius-il:java/org/l2jmobius/gameserver/LoginServerThread.java`, `mobius-il:java/org/l2jmobius/loginserver/GameServerThread.java`
- `mobius-ess:java/org/l2jmobius/gameserver/LoginServerThread.java`, `mobius-ess:java/org/l2jmobius/loginserver/GameServerThread.java`

Packets

| Chronicle | Client → server                                                                                                                                                                                                                  | Server → client                                                                                                                             |
| --------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | GS → LS: `AuthRequest` `01`, `BlowFishKey` `00`, `PlayerAuthRequest` `05`, `PlayerInGame` `02`, `PlayerLogout` `03`, `ServerStatus` `06`, `ChangeAccessLevel` `04`, `PlayerTracert` `07`, `ReplyCharacters` `08`, `TempBan` `0A` | LS → GS: `InitLS` `00`, `AuthResponse` `02`, `PlayerAuthResponse` `03`, `KickPlayer` `04`, `LoginServerFail` `01`, `RequestCharacters` `05` |
| High Five | GS → LS: `AuthRequest` `01`, `BlowFishKey` `00`, `PlayerAuthRequest` `05`, `PlayerInGame` `02`, `PlayerLogout` `03`, `ServerStatus` `06`, `ChangeAccessLevel` `04`, `PlayerTracert` `07`, `ReplyCharacters` `08`, `TempBan` `0A` | LS → GS: `InitLS` `00`, `AuthResponse` `02`, `PlayerAuthResponse` `03`, `KickPlayer` `04`, `LoginServerFail` `01`, `RequestCharacters` `05` |
| Essence   | GS → LS: `AuthRequest` `01`, `BlowFishKey` `00`, `PlayerAuthRequest` `05`, `PlayerInGame` `02`, `PlayerLogout` `03`, `ServerStatus` `06`, `ChangeAccessLevel` `04`, `PlayerTracert` `07`, `ReplyCharacters` `08`, `TempBan` `0A` | LS → GS: `InitLS` `00`, `AuthResponse` `02`, `PlayerAuthResponse` `03`, `KickPlayer` `04`, `LoginServerFail` `01`, `RequestCharacters` `05` |

Notes: protocol is internal and chronicle-independent — the clearest `engine/` candidate of the group.

Known fixes: `l2j-server@ca426668` (2006-12-04, exploit-dupe) — Fix for LS->GS hlapex bypass exploit; `l2j-server@61afecfc` (2014-02-14, crash-npe) — BETA: Fixed an NPE in `GameServerThread`; `mobius@60856fe5` (2019-04-15, crash-npe) — Fixed probable GameServerThread NPE.

### account.duplicate-login — Duplicate login

ID: `account.duplicate-login` · Shared: **engine**

A second login to an account that is already online kicks the existing session (login server sends KickPlayer to
the game server, client gets AccountKicked / "account in use").
Sub-features: kick old session · AccountKicked reasons · same-account guard on game server.

| Chronicle | Present | Builds (evidence)                                                | Difference |
| --------- | ------- | ---------------------------------------------------------------- | ---------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas, ares-melnkovmaxim | baseline   |
| High Five | yes     | nx-l2, sunrise, eternity, premium, lineage2ts                    | same       |
| Essence   | yes     | mobius-ess                                                       | same       |

Entry points

- `nx-l2:login/server/src/main/java/app/l2nx/login/network/serverpackets/AccountKicked.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/enums/AccountKickedReason.java`, `nx-l2:login/server/src/main/java/app/l2nx/login/controller/LoginController.java`
- `mobius-il:java/org/l2jmobius/loginserver/network/serverpackets/AccountKicked.java`
- `mobius-ess:java/org/l2jmobius/loginserver/network/serverpackets/AccountKicked.java`

Packets

| Chronicle | Client → server | Server → client                                                                  |
| --------- | --------------- | -------------------------------------------------------------------------------- |
| Interlude | —               | `AccountKicked` `02` (login); LS → GS `KickPlayer` `04`; game `ServerClose` `26` |
| High Five | —               | `AccountKicked` `02` (login); LS → GS `KickPlayer` `04`; game `ServerClose` `20` |
| Essence   | —               | `AccountKicked` `02` (login); LS → GS `KickPlayer` `04`; game `ServerClose` `20` |

Notes: per-IP/HWID window limits ("max N clients per PC") are not part of this feature in Mobius; public HF builds do
not have them either.

Known fixes: `l2j-server@52088764` (2007-06-06, wrong-formula-value) — Fixed a LoginServer issue were a client would try to log on an account with a random password just to disconnect that account from GS (Password mus.

### account.secondary-password — Secondary password (PIN)

ID: `account.secondary-password` · Shared: **partial (HF+Ess)**

Character-selection PIN: create, verify, change; account is blocked for a period after N wrong attempts.
Sub-features: create · verify · change · attempt limit + ban time · strong-password rule.

| Chronicle | Present | Builds (evidence)                                         | Difference                                        |
| --------- | ------- | --------------------------------------------------------- | ------------------------------------------------- |
| Interlude | no      | mobius-il, acis, l2jfrozen                                | client has no PIN window                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, premium, lineage2ts | baseline                                          |
| Essence   | yes     | mobius-ess (09.2, 04.2)                                   | same (`SecondaryPasswordAuth` differs by 4 lines) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/security/SecondaryPasswordAuth.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/data/xml/SecondaryAuthData.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/serverpackets/Ex2ndPasswordCheck.java`
- `mobius-ess:java/org/l2jmobius/gameserver/security/SecondaryPasswordAuth.java`

Packets

| Chronicle | Client → server                                                                                              | Server → client                                                                               |
| --------- | ------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------- |
| High Five | `RequestEx2ndPasswordCheck` `D0:93`, `RequestEx2ndPasswordVerify` `D0:94`, `RequestEx2ndPasswordReq` `D0:95` | `Ex2ndPasswordCheck` `FE:E5`, `Ex2ndPasswordVerify` `FE:E6`, `Ex2ndPasswordAck` `FE:E7`       |
| Essence   | `RequestEx2ndPasswordCheck` `D0:A7`, `RequestEx2ndPasswordVerify` `D0:A8`, `RequestEx2ndPasswordReq` `D0:A9` | `Ex2ndPasswordCheck` `FE:0106`, `Ex2ndPasswordVerify` `FE:0107`, `Ex2ndPasswordAck` `FE:0108` |

Notes: freejia highfive (fork of L2J Server) adds a community-board UI to change the PIN (`CCPSecondaryPassword`) and
a login-side `ChangeSecondaryPassword` packet.

### account.password-change — In-game password change

ID: `account.password-change` · Shared: **engine**

Custom voiced command `.changepassword`: validates old/new password in game, relays the change to the login server
over the GS link and reports the result.
Sub-features: voiced command · validation · login relay (ChangePassword / ChangePasswordResponse).

| Chronicle | Present | Builds (evidence)                                  | Difference                        |
| --------- | ------- | -------------------------------------------------- | --------------------------------- |
| Interlude | yes     | mobius-il (custom), acis-180-devastated            | baseline (custom, off by default) |
| High Five | yes     | nx-l2 (custom), l2j-server-dp, sunrise, lineage2ts | same                              |
| Essence   | yes     | mobius-ess (custom)                                | same (1 line)                     |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/chat/commands/voiced/ChangePassword.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/custom/PasswordChangeConfig.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/loginserverpackets/send/ChangePassword.java`
- `mobius-il:dist/game/data/scripts/handlers/chat/commands/voiced/ChangePassword.java`
- `mobius-ess:dist/game/data/scripts/handlers/chat/commands/voiced/ChangePassword.java`

Packets

| Chronicle | Client → server                                      | Server → client                       |
| --------- | ---------------------------------------------------- | ------------------------------------- |
| Interlude | voiced command (chat); GS → LS `ChangePassword` `0B` | LS → GS `ChangePasswordResponse` `06` |
| High Five | voiced command (chat); GS → LS `ChangePassword` `0B` | LS → GS `ChangePasswordResponse` `06` |
| Essence   | voiced command (chat); GS → LS `ChangePassword` `0B` | LS → GS `ChangePasswordResponse` `06` |

Notes: not a retail mechanic in any chronicle; present only as custom.

### account.account-lock — Account binding to IP/HWID

ID: `account.account-lock` · Shared: **chronicle-only**

Player binds the account to the current IP (or hardware id): login from any other address is refused until the
binding is removed. Sub-features: bind IP · bind HWID · unbind · allowed list.

| Chronicle | Present | Builds (evidence) | Difference                            |
| --------- | ------- | ----------------- | ------------------------------------- |
| Interlude | no      | mobius-il, acis   | —                                     |
| High Five | yes     | eternity (custom) | baseline: IP binding (`ProtectionIP`) |
| Essence   | no      | mobius-ess        | —                                     |

Entry points

- `eternity:core/java/l2e/gameserver/model/entity/ProtectionIP.java`

Packets

| Chronicle | Client → server       | Server → client |
| --------- | --------------------- | --------------- |
| High Five | voiced command / html | —               |

Notes: absent from Mobius and L2J Server; nx-l2 has no counterpart.

### account.variables — Account variables

ID: `account.variables` · Shared: **engine**

Key-value storage shared by all characters of an account (`account_gsdata`): flags, counters, one-time rewards.

| Chronicle | Present | Builds (evidence)                                | Difference                                  |
| --------- | ------- | ------------------------------------------------ | ------------------------------------------- |
| Interlude | yes     | mobius-il, mobius-c6                             | baseline                                    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity, lineage2ts | same                                        |
| Essence   | yes     | mobius-ess                                       | more predefined keys (13 lines), same store |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/mechanics/variables/AccountVariables.java`
- `mobius-il:java/org/l2jmobius/gameserver/mechanics/variables/AccountVariables.java`
- `mobius-ess:java/org/l2jmobius/gameserver/mechanics/variables/AccountVariables.java`

Packets

| Chronicle | Client → server | Server → client |
| --------- | --------------- | --------------- |
| all       | —               | —               |

Notes: aCis and L2jFrozen have no account-level store.
