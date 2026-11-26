# Duels

One-on-one and party duels.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### duel.solo — Solo duel

ID: `duel.solo` · Shared: **engine**

A player challenges another to a duel; on acceptance a countdown starts, both fight under duel rules (no karma, state
restored afterwards); the duel ends on defeat (low HP), surrender, timeout or interference.
Sub-features: challenge / answer · countdown · duel rules · surrender · end conditions · state restore.

| Chronicle | Present | Builds (evidence)                                                               | Difference                                             |
| --------- | ------- | ------------------------------------------------------------------------------- | ------------------------------------------------------ |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2j-server, ares-melnkovmaxim, l2kt-cozmin91-kotlin | baseline                                               |
| High Five | yes     | nx-l2, l2j-server, sunrise                                                      | same                                                   |
| Essence   | yes     | mobius-ess                                                                      | same (`Duel` differs only by instance API and logging) |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/DuelManager.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/Duel.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/enums/player/DuelResult.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/network/clientpackets/{RequestDuelStart,RequestDuelAnswerStart,RequestDuelSurrender}.java`
- `mobius-il:java/org/l2jmobius/gameserver/managers/DuelManager.java`, `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/player/Duel.java`
- `mobius-ess:java/org/l2jmobius/gameserver/managers/DuelManager.java`, `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/player/Duel.java`

Packets

| Chronicle | Client → server                                                                              | Server → client                                                                                                             |
| --------- | -------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| Interlude | `RequestDuelStart` `D0:27`, `RequestDuelAnswerStart` `D0:28`, `RequestDuelSurrender` `D0:30` | `ExDuelAskStart` `FE:4B`, `ExDuelReady` `FE:4C`, `ExDuelStart` `FE:4D`, `ExDuelEnd` `FE:4E`, `ExDuelUpdateUserInfo` `FE:4F` |
| High Five | `RequestDuelStart` `D0:1B`, `RequestDuelAnswerStart` `D0:1C`, `RequestDuelSurrender` `D0:45` | `ExDuelAskStart` `FE:4C`, `ExDuelReady` `FE:4D`, `ExDuelStart` `FE:4E`, `ExDuelEnd` `FE:4F`, `ExDuelUpdateUserInfo` `FE:50` |
| Essence   | `RequestDuelStart` `D0:1B`, `RequestDuelAnswerStart` `D0:1C`, `RequestDuelSurrender` `D0:42` | `ExDuelAskStart` `FE:4D`, `ExDuelReady` `FE:4E`, `ExDuelStart` `FE:4F`, `ExDuelEnd` `FE:50`, `ExDuelUpdateUserInfo` `FE:51` |

Notes: the Essence `DuelistFury` effect is a class skill, not part of duels.
Known fixes: `l2j-server@3c2743be` (2007-09-14, exploit-dupe) — should fix duel mp cheats with ally/clan buffs (thanks nobody for testing); `sunrise@5f0cdc50` (2015-10-29, wrong-formula-value) — Fixed duel issues restore player stats(buffs, hp, cp etc) Fixed Quest wishing potion.

### duel.party — Party duel

ID: `duel.party` · Shared: **engine**

Party leaders challenge each other; on acceptance both parties are moved to a duel arena, fight until one party is
defeated or surrenders, then everyone returns to the original location with restored state.
Sub-features: party challenge · arena teleport · party defeat · return and restore.

| Chronicle | Present | Builds (evidence)                                                                                                                | Difference                                                      |
| --------- | ------- | -------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen                                                                                                       | baseline: teleport to fixed arena coordinates                   |
| High Five | yes     | nx-l2, l2j-server (instance `l2j-server-dp:src/main/resources/data/instances/PartyDuel.xml`), advanced (`PartyVsPartyDuelEvent`) | arena as instance                                               |
| Essence   | yes     | mobius-ess                                                                                                                       | arena as `Instance` object, 20 s start delay for solo duels too |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/entity/actor/holders/player/Duel.java` (`teleportPlayers`, `teleportBack`), `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/managers/DuelManager.java`
- `mobius-il:java/org/l2jmobius/gameserver/entity/actor/holders/player/Duel.java`
- `mobius-ess:java/org/l2jmobius/gameserver/entity/actor/holders/player/Duel.java`

Packets

| Chronicle | Client → server     | Server → client     |
| --------- | ------------------- | ------------------- |
| Interlude | same as `duel.solo` | same as `duel.solo` |
| High Five | same as `duel.solo` | same as `duel.solo` |
| Essence   | same as `duel.solo` | same as `duel.solo` |

Notes: L2-Advanced / L2JPremium model party duels as an event (`PartyVsPartyDuelEvent`).
Known fixes: `l2j-server@3c2743be` (2007-09-14, exploit-dupe) — should fix duel mp cheats with ally/clan buffs (thanks nobody for testing); `sunrise@5f0cdc50` (2015-10-29, wrong-formula-value) — Fixed duel issues restore player stats(buffs, hp, cp etc) Fixed Quest wishing potion.
