# Community Board

The Community Board (Alt+B) window: routing engine, forums and the custom service shell.

Paths: `<build-key>:<path>` — `<path>` is relative to the root of the build that the key names; the key table (project, source URL, commit, license) is in the [README](README.md). `nx-l2:chronicles/high-five/...` paths are in this repository. Opcodes (`XX` or `XX:YYYY` for extended packets) come from the packet catalogue: Interlude — Mobius IL protocol, High Five — protocol 267/273, Essence — Mobius Essence 09.2 RoseVain protocol.

### community.board-engine — Community Board engine

ID: `community.board-engine` · Shared: **engine**

The Alt+B window: `RequestBypassToServer`/`RequestBBSwrite` carry `_bbs…` commands that `CommunityBoardHandler` routes to board handlers
(home, clan, friends, mail, memo, region, favorites, homepage, drop search); pages are HTML sent with `ShowBoard`.
Sub-features: router · home · favorites · memo · region list · homepage · drop search.

| Chronicle | Present | Builds (evidence)                             | Difference                                                                                                                                                    |
| --------- | ------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | 9 board handlers in `handlers/bypass/communityboard`                                                                                                          |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same 9 handlers; `EnableCommunityBoard` (`nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/config/GeneralConfig.java:288`)                   |
| Essence   | yes     | mobius-ess                                    | same 9 handlers; `EnableCommunityBoard` (`mobius-ess:java/org/l2jmobius/gameserver/config/GeneralConfig.java:285`); client availability of Alt+B not verified |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/handler/CommunityBoardHandler.java`, `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/{HomeBoard,ClanBoard,FriendsBoard,MailBoard,MemoBoard,RegionBoard,FavoriteBoard,HomepageBoard,DropSearchBoard}.java`
- Mobius IL / Essence: same under `mobius-il:java/org/l2jmobius/gameserver`/`mobius-il:dist/game/data/scripts` and `mobius-ess:java/org/l2jmobius/gameserver`/`mobius-ess:dist/game/data/scripts`

Packets

| Chronicle | Client → server                                                               | Server → client  |
| --------- | ----------------------------------------------------------------------------- | ---------------- |
| Interlude | `RequestShowBoard` `57`, `RequestBBSwrite` `22`, `RequestBypassToServer` `21` | `ShowBoard` `6E` |
| High Five | `RequestShowBoard` `5E`, `RequestBBSwrite` `24`, `RequestBypassToServer` `23` | `ShowBoard` `7B` |
| Essence   | `RequestShowBoard` `5E`, `RequestBBSwrite` `24`, `RequestBypassToServer` `23` | `ShowBoard` `7B` |

Notes: —
Known fixes: `l2j-server@77e7d408` (2007-08-27, crash-npe) — npe fixes: olympiad, getCommunityPage; `mobius@f613b983` (2021-09-22, exploit-dupe) — Fixed probable community board post bypass exploit.

### community.forums — Forums

ID: `community.forums` · Shared: **engine**

Forum / topic / post managers behind the Community Board for clan and memo boards; data in forum tables.
Sub-features: forum tree · topics · posts · clan forum · memo.

| Chronicle | Present | Builds (evidence)                             | Difference                                                      |
| --------- | ------- | --------------------------------------------- | --------------------------------------------------------------- |
| Interlude | yes     | mobius-il, acis, l2jfrozen, l2jbrasil, hellas | managers `Forums/Topic/Post/BaseBBSManager`; extra `BB/Mail`    |
| High Five | yes     | nx-l2, l2j-server, sunrise, eternity          | same managers (`communitybbs/Manager`), `BB/{Forum,Post,Topic}` |
| Essence   | yes     | mobius-ess                                    | same as HF                                                      |

Entry points

- `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/communitybbs/Manager/{BaseBBSManager,ForumsBBSManager,TopicBBSManager,PostBBSManager}.java`, `nx-l2:chronicles/high-five/server/src/main/java/app/l2nx/highfive/communitybbs/BB/{Forum,Post,Topic}.java`
- Mobius IL / Essence: same under `mobius-il:java/org/l2jmobius/gameserver` / `mobius-ess:java/org/l2jmobius/gameserver`

Packets

| Chronicle | Client → server        | Server → client  |
| --------- | ---------------------- | ---------------- |
| Interlude | `RequestBBSwrite` `22` | `ShowBoard` `6E` |
| High Five | `RequestBBSwrite` `24` | `ShowBoard` `7B` |
| Essence   | `RequestBBSwrite` `24` | `ShowBoard` `7B` |

Notes: —

### community.custom-services — Custom Community Board shell

ID: `community.custom-services` · Shared: **engine**

Mobius custom: an alternative home page of the board with paid services (teleport, buffs, heal, multisells, delevel, premium) configured by
`Custom/CommunityBoard.ini`; each service belongs to its own domain, only the shell is described here.
Sub-features: custom home · currency and prices · peace-zone / combat / karma restrictions · premium.

| Chronicle | Present | Builds (evidence)   | Difference                                                                           |
| --------- | ------- | ------------------- | ------------------------------------------------------------------------------------ |
| Interlude | yes     | mobius-il (custom)  | `Custom/CommunityBoard.ini`: same key set as Essence                                 |
| High Five | yes     | nx-l2 (custom)      | `nx-l2:chronicles/high-five/datapack/config/Custom/CommunityBoard.ini`: same key set |
| Essence   | yes     | mobius-ess (custom) | same key set                                                                         |

Entry points

- `nx-l2:chronicles/high-five/scripts/src/main/java/app/l2nx/highfive/scripts/handlers/bypass/communityboard/HomeBoard.java`, `nx-l2:chronicles/high-five/datapack/config/Custom/CommunityBoard.ini`
- `mobius-il:dist/game/data/scripts/handlers/bypass/communityboard/HomeBoard.java`, `mobius-il:dist/game/config/Custom/CommunityBoard.ini`
- `mobius-ess:dist/game/data/scripts/handlers/bypass/communityboard/HomeBoard.java`, `mobius-ess:dist/game/config/Custom/CommunityBoard.ini`

Packets

| Chronicle | Client → server              | Server → client  |
| --------- | ---------------------------- | ---------------- |
| Interlude | `RequestBypassToServer` `21` | `ShowBoard` `6E` |
| High Five | `RequestBypassToServer` `23` | `ShowBoard` `7B` |
| Essence   | `RequestBypassToServer` `23` | `ShowBoard` `7B` |

Notes: —
Known fixes: `mobius@21e0829e` (2021-02-23, wrong-formula-value) — Fixed delevel from community board. Contributed by CostyKiller; `l2j-server@4af58f77` (2007-01-04, other) — Some CommunityBoard fixes but this really need to be rewritten.
