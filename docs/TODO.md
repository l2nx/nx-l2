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
- [#42](https://github.com/l2nx/nx-l2/issues/42) E7/E8: cadence settings for periodic systems (`l2nx.engine.cadence.*`)
- [#43](https://github.com/l2nx/nx-l2/issues/43) E6 part 2: split `GameServer.start()` so the partition runtime starts
  between data load and port open
- [#44](https://github.com/l2nx/nx-l2/issues/44) Replay production incidents of the world loop
- [#45](https://github.com/l2nx/nx-l2/issues/45) Input runs against timers due up to one pass earlier (sub-tick order)
- [#48](https://github.com/l2nx/nx-l2/issues/48) Real-client checks left open after moving the login connection to Netty
- [#49](https://github.com/l2nx/nx-l2/issues/49) Move the stage 3 repositories into their feature slices
- [#51](https://github.com/l2nx/nx-l2/issues/51) Replace key-value variable stores with typed owner state
- [#53](https://github.com/l2nx/nx-l2/issues/53) Community board post cache loads a topic twice under a race
- [#54](https://github.com/l2nx/nx-l2/issues/54) Dimensional Rift state races between player actions and its jump task
- [#55](https://github.com/l2nx/nx-l2/issues/55) Pet.destroyItem never sends the disappeared message
- [#56](https://github.com/l2nx/nx-l2/issues/56) Ended long-time event items on the ground come back after a restart
- [#57](https://github.com/l2nx/nx-l2/issues/57) Mail keeps its cash-on-delivery price after its attachments are destroyed
- [#58](https://github.com/l2nx/nx-l2/issues/58) Castle circlets stay with the old owner from the second change of owner on
- [#59](https://github.com/l2nx/nx-l2/issues/59) A clan hall sale auction without bids leaves its row behind
- [#60](https://github.com/l2nx/nx-l2/issues/60) Event item cleanup can delete rows of a container that loads during it
- [#61](https://github.com/l2nx/nx-l2/issues/61) Castle.setOwner throws for a castle without a territory
- [#62](https://github.com/l2nx/nx-l2/issues/62) AutoSpawnDataTest sometimes sees no groups in the full test run
- [#64](https://github.com/l2nx/nx-l2/issues/64) Bidders lose 10% when a clan hall sale is cancelled by the seller's dissolution
- [#66](https://github.com/l2nx/nx-l2/issues/66) Siege flags of a dissolved clan stay until the siege ends
- [#68](https://github.com/l2nx/nx-l2/issues/68) E0: load harness in a separate process, gate of the E6 cutover ([spec 009](specs/009-load-harness.md))
- [#70](https://github.com/l2nx/nx-l2/issues/70) AdminPForge: forge a client packet through the player's adapter
- [#71](https://github.com/l2nx/nx-l2/issues/71) Load scenario replaying recorded sessions
- [#72](https://github.com/l2nx/nx-l2/issues/72) Castle siege as a load scenario
- [#73](https://github.com/l2nx/nx-l2/issues/73) E10: move send-time side effects of legacy server packets into slice handlers
- [#74](https://github.com/l2nx/nx-l2/issues/74) GrandBossDataTest fails depending on test order
- [#76](https://github.com/l2nx/nx-l2/issues/76) Server list shows no character count for the account

## Done

- [#75](https://github.com/l2nx/nx-l2/issues/75) REUSE licences check fails on main: ten files without SPDX information
- [#47](https://github.com/l2nx/nx-l2/issues/47) Game server's connection pool sized to fit `max_connections` ([spec 006](specs/006-database.md)).
- [#18](https://github.com/l2nx/nx-l2/issues/18) Target architecture of the game code: package layout, tick model, ECS
  ([spec 003](specs/003-engine-core/spec.md)).
- [#46](https://github.com/l2nx/nx-l2/issues/46) Timer wheel: a handler re-arming an overdue timer at its own deadline
  starves later timers ([execution.md §4](specs/003-engine-core/execution.md#4-timers)).
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
