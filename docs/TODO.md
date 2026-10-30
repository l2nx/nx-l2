# TODO

Short index of deferred work. Details, discussion and decisions live in the linked GitHub issue; this file only
lists what is open.

## Open

- [#1](https://github.com/l2nx/nx-l2/issues/1) Push GPLv3 out of the build
- [#2](https://github.com/l2nx/nx-l2/issues/2) Essence chronicle and shared core extraction
- [#3](https://github.com/l2nx/nx-l2/issues/3) Interlude chronicle
- [#4](https://github.com/l2nx/nx-l2/issues/4) Edge gateway / horizontal scale-out
- [#5](https://github.com/l2nx/nx-l2/issues/5) Stable script API for other emulators
- [#6](https://github.com/l2nx/nx-l2/issues/6) Move a chronicle into its own repository
- [#7](https://github.com/l2nx/nx-l2/issues/7) CLA or DCO before external contributions
- [#9](https://github.com/l2nx/nx-l2/issues/9) Review texts shipped in the datapack
- [#10](https://github.com/l2nx/nx-l2/issues/10) Mirror the repository outside GitHub
- [#11](https://github.com/l2nx/nx-l2/issues/11) Release the High Five scripts as their own artifact
- [#13](https://github.com/l2nx/nx-l2/issues/13) PostgreSQL support in the adapter CDC engine
- [#16](https://github.com/l2nx/nx-l2/issues/16) Finalize the JDK: distribution, version and flags
- [#17](https://github.com/l2nx/nx-l2/issues/17) Move game and login settings to Spring Boot configuration
- [#19](https://github.com/l2nx/nx-l2/issues/19) Run both servers in Compose with an end-to-end login test
- [#20](https://github.com/l2nx/nx-l2/issues/20) Time dilation for overloaded partitions
- [#21](https://github.com/l2nx/nx-l2/issues/21) Parallel open world if one partition exceeds its budget
- [#22](https://github.com/l2nx/nx-l2/issues/22) Choose an XML library for the datapack loaders
- [#27](https://github.com/l2nx/nx-l2/issues/27) Client protocol 140 over High Five content
- [#28](https://github.com/l2nx/nx-l2/issues/28) Client protocol 557 over High Five content
- [#31](https://github.com/l2nx/nx-l2/issues/31) Not every effect should freeze while its owner is offline
- [#40](https://github.com/l2nx/nx-l2/issues/40) Dropped items lose augmentation and elemental attributes across a restart
- [#41](https://github.com/l2nx/nx-l2/issues/41) E10: inject beans instead of the static getInstance() bridges
- [#42](https://github.com/l2nx/nx-l2/issues/42) E8: cadence setting of the visibility periodic system (`ai-think`, `player-movement` and `npc-movement` have theirs in `l2nx.ai.*` and `l2nx.movement.*`)
- [#44](https://github.com/l2nx/nx-l2/issues/44) Record and replay production incidents of the world loop (recorder and replay built together)
- [#45](https://github.com/l2nx/nx-l2/issues/45) Input runs against timers due up to one pass earlier (sub-tick order)
- [#49](https://github.com/l2nx/nx-l2/issues/49) Move the stage 3 repositories into their feature slices
- [#51](https://github.com/l2nx/nx-l2/issues/51) Replace key-value variable stores with typed owner state
- [#53](https://github.com/l2nx/nx-l2/issues/53) Community board post cache loads a topic twice under a race
- [#55](https://github.com/l2nx/nx-l2/issues/55) Pet.destroyItem never sends the disappeared message
- [#56](https://github.com/l2nx/nx-l2/issues/56) Ended long-time event items on the ground come back after a restart
- [#57](https://github.com/l2nx/nx-l2/issues/57) Mail keeps its cash-on-delivery price after its attachments are destroyed
- [#58](https://github.com/l2nx/nx-l2/issues/58) Castle circlets stay with the old owner from the second change of owner on
- [#59](https://github.com/l2nx/nx-l2/issues/59) A clan hall sale auction without bids leaves its row behind
- [#61](https://github.com/l2nx/nx-l2/issues/61) Castle.setOwner throws for a castle without a territory
- [#64](https://github.com/l2nx/nx-l2/issues/64) Bidders lose 10% when a clan hall sale is cancelled by the seller's dissolution
- [#66](https://github.com/l2nx/nx-l2/issues/66) Siege flags of a dissolved clan stay until the siege ends
- [#68](https://github.com/l2nx/nx-l2/issues/68) E0: load harness in a separate process, gate of the E6 cutover ([spec 009](specs/009-load-harness.md))
- [#69](https://github.com/l2nx/nx-l2/issues/69) JDBC runs on the world loop until write-behind (E4)
- [#70](https://github.com/l2nx/nx-l2/issues/70) AdminPForge: forge a client packet through the player's adapter
- [#71](https://github.com/l2nx/nx-l2/issues/71) Load scenario replaying recorded sessions
- [#72](https://github.com/l2nx/nx-l2/issues/72) Castle siege as a load scenario
- [#73](https://github.com/l2nx/nx-l2/issues/73) E10: move send-time side effects of legacy server packets into slice handlers
- [#74](https://github.com/l2nx/nx-l2/issues/74) GrandBossDataTest fails depending on test order
- [#76](https://github.com/l2nx/nx-l2/issues/76) Server list shows no character count for the account
- [#77](https://github.com/l2nx/nx-l2/issues/77) Fishing with 17 lures has no guts-check period
- [#78](https://github.com/l2nx/nx-l2/issues/78) SystemMessage writes the declared parameter count even when it skips a null parameter
- [#79](https://github.com/l2nx/nx-l2/issues/79) Alliance chat ignores the block list
- [#80](https://github.com/l2nx/nx-l2/issues/80) Restart into offline trade disconnects the trader just created when OfflineDisconnectSameAccount is on
- [#82](https://github.com/l2nx/nx-l2/issues/82) Aborting an alt-mode craft during its final delay still finishes it
- [#83](https://github.com/l2nx/nx-l2/issues/83) PostRepositoryTest sometimes cannot get a JDBC connection
- [#84](https://github.com/l2nx/nx-l2/issues/84) Game server loses its login registration (hexid.txt) on every installDist
- [#88](https://github.com/l2nx/nx-l2/issues/88) Layered rate limit for the login and game client protocols
- [#89](https://github.com/l2nx/nx-l2/issues/89) Harden the network connection lifecycle: late init, idle, close reasons, write errors, shutdown deadline
- [#90](https://github.com/l2nx/nx-l2/issues/90) Clean up libs/network: module boundaries, shared L2 pipeline, login transport leftovers and statics
- [#91](https://github.com/l2nx/nx-l2/issues/91) Port the first client slice end to end before growing the port framework
- [#93](https://github.com/l2nx/nx-l2/issues/93) Make the login side of the GS-link thread-safe and keep database work off the stream
- [#94](https://github.com/l2nx/nx-l2/issues/94) Harden GS-link failure handling: reconnect classification, malformed messages, shutdown, buffering, tests
- [#97](https://github.com/l2nx/nx-l2/issues/97) Bound the world lifecycle waits and merge the two game server lifecycles
- [#98](https://github.com/l2nx/nx-l2/issues/98) Decide which Errors from a loop task stop the server
- [#99](https://github.com/l2nx/nx-l2/issues/99) Keep the loop out of overload spirals: coalesce missed fixed-rate runs, time-budget the deadlines phase
- [#101](https://github.com/l2nx/nx-l2/issues/101) Check ownership at runtime on the world state mutators
- [#102](https://github.com/l2nx/nx-l2/issues/102) Close the gaps in the architecture rules: scheduled work, randomness and slice layout gates
- [#103](https://github.com/l2nx/nx-l2/issues/103) Cut engine loop overhead and attribute heavy tasks in the meters
- [#104](https://github.com/l2nx/nx-l2/issues/104) Set database statement, lock and connection timeouts and resize the pool for the single-writer loop
- [#105](https://github.com/l2nx/nx-l2/issues/105) Update memory only after commit in mail and clan destruction
- [#106](https://github.com/l2nx/nx-l2/issues/106) Save a character in one transaction
- [#107](https://github.com/l2nx/nx-l2/issues/107) Enforce the repository handle contract and issue handles only on demand
- [#108](https://github.com/l2nx/nx-l2/issues/108) Database config and value hygiene: default password, changelog paths, blank legacy text
- [#109](https://github.com/l2nx/nx-l2/issues/109) Move alliance, couple and range rules into schema constraints
- [#110](https://github.com/l2nx/nx-l2/issues/110) Test repository transactions and published database meters through Spring
- [#111](https://github.com/l2nx/nx-l2/issues/111) Make the load gate trustworthy: outcome accounting, comparability, timeouts in latency, statistics, validity limits
- [#113](https://github.com/l2nx/nx-l2/issues/113) Test the p267 login storm against the real login server
- [#114](https://github.com/l2nx/nx-l2/issues/114) Load harness cleanup: p267 adapter measurement, speculative bot model, fail-fast CLI
- [#115](https://github.com/l2nx/nx-l2/issues/115) Finish the bug classes of the Mobius batch: static config snapshots and equals/hashCode gaps
- [#116](https://github.com/l2nx/nx-l2/issues/116) Small legacy fixes: fort contract reset, Quest logging, ended event item scan, captcha dedup
- [#117](https://github.com/l2nx/nx-l2/issues/117) Isolate and de-mock the Mobius batch tests
- [#118](https://github.com/l2nx/nx-l2/issues/118) Bring specs and comments in line with the code
- [#119](https://github.com/l2nx/nx-l2/issues/119) Add slice and domain-event to the glossary and settle term mismatches
- [#121](https://github.com/l2nx/nx-l2/issues/121) GameServerTest openPort release test fails with BindException under parallel load
- [#122](https://github.com/l2nx/nx-l2/issues/122) E7: defer world membership changes to a sync point instead of snapshots
- [#123](https://github.com/l2nx/nx-l2/issues/123) Loop wakes up to 15 ms late on Windows and counts on-time timers as overdue
- [#126](https://github.com/l2nx/nx-l2/issues/126) Default-locale case conversion breaks identifiers under a Turkish locale

## Done

- [#112](https://github.com/l2nx/nx-l2/issues/112) Read server meters through Prometheus and record the actual meter window
- [#92](https://github.com/l2nx/nx-l2/issues/92) Bind PlayOk to the game server the player selected
- [#95](https://github.com/l2nx/nx-l2/issues/95) Split account and registration logic out of GameServerLink
- [#96](https://github.com/l2nx/nx-l2/issues/96) Hash account passwords with a salted adaptive algorithm
- [#120](https://github.com/l2nx/nx-l2/issues/120) Flaky tools/load tests assert timing-dependent exact counts
- [#85](https://github.com/l2nx/nx-l2/issues/85) LoadCommandRunnerTest fails when no roaming action falls into the short steady window
- [#62](https://github.com/l2nx/nx-l2/issues/62) AutoSpawnDataTest sometimes sees no groups in the full test run
- [#60](https://github.com/l2nx/nx-l2/issues/60) Event item cleanup can delete rows of a container that loads during it
- [#54](https://github.com/l2nx/nx-l2/issues/54) Dimensional Rift state races between player actions and its jump task
- [#43](https://github.com/l2nx/nx-l2/issues/43) E6 part 2: split `GameServer.start()` so the partition runtime starts
  between data load and port open
- [#48](https://github.com/l2nx/nx-l2/issues/48) Real-client checks left open after moving the login connection to Netty
- [#81](https://github.com/l2nx/nx-l2/issues/81) NetworkServer close leaves a connection accepted during shutdown open forever
- [#75](https://github.com/l2nx/nx-l2/issues/75) REUSE licences check fails on main: ten files without SPDX information
- [#47](https://github.com/l2nx/nx-l2/issues/47) Game server's connection pool sized to fit `max_connections` ([spec 006](specs/006-database.md)).
- [#18](https://github.com/l2nx/nx-l2/issues/18) Target architecture of the game code: package layout, tick model, ECS
  ([spec 003](specs/003-engine-core/spec.md)).
- [#8](https://github.com/l2nx/nx-l2/issues/8) Publish project docs on l2nx.dev: the `/l2/` section, from
  `docs/public/{en,ru}/` (spec 001 §1).
- Ownership of the reference L2NX host layer: not ours; the adapter host is written from scratch against the
  `nx-gs-adapter` API (spec 001 §2, stage 5).
- [#14](https://github.com/l2nx/nx-l2/issues/14) Migrate logging to the SLF4J API ([spec 007](specs/007-java-conventions.md)).
- [#15](https://github.com/l2nx/nx-l2/issues/15) Adopt Lombok for boilerplate ([spec 007](specs/007-java-conventions.md)).
- [#23](https://github.com/l2nx/nx-l2/issues/23) Temporary login ban turns into a permanent account ban (fixed by stage 3,
  [spec 006](specs/006-database.md)).
- [#24](https://github.com/l2nx/nx-l2/issues/24) Login fails with NPE on an accounts_ipauth row without a type (fixed by stage 3,
  [spec 006](specs/006-database.md)).
- [#50](https://github.com/l2nx/nx-l2/issues/50) Dimensional Rift runs world changes on its own java.util.Timer threads
- [#25](https://github.com/l2nx/nx-l2/issues/25) Weapon damage_range with 2 or 3 parts breaks item loading
- [#26](https://github.com/l2nx/nx-l2/issues/26) Seed of Infinity logs the open duration as the close date
- [#36](https://github.com/l2nx/nx-l2/issues/36) Grand boss status lookup throws for a boss missing from the datapack
- [#37](https://github.com/l2nx/nx-l2/issues/37) Hero processing throws when a character's clan is missing
- [#29](https://github.com/l2nx/nx-l2/issues/29) WorldObject overrides equals without hashCode
- [#52](https://github.com/l2nx/nx-l2/issues/52) AbstractResidence overrides equals without hashCode
- [#33](https://github.com/l2nx/nx-l2/issues/33) Creating a second academy is never reported, and the academy leader differs between memory and database
- [#34](https://github.com/l2nx/nx-l2/issues/34) Editing a community board post by index can hit the wrong post
- [#38](https://github.com/l2nx/nx-l2/issues/38) A siegable clan hall without a loaded owner clan fails to load
- [#39](https://github.com/l2nx/nx-l2/issues/39) A fort whose owner clan is not loaded fails to load
- [#35](https://github.com/l2nx/nx-l2/issues/35) A long-time event deletes item rows that may still be loaded
- [#65](https://github.com/l2nx/nx-l2/issues/65) Inventory.MAX_ADENA freezes the config value at class initialisation
- [#63](https://github.com/l2nx/nx-l2/issues/63) ClanHallAuctionTest passes only after other tests initialise shared state
- [#32](https://github.com/l2nx/nx-l2/issues/32) A dissolved clan keeps its castle and clan hall in memory until restart
