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
- [#14](https://github.com/l2nx/nx-l2/issues/14) Migrate logging to the SLF4J API
- [#15](https://github.com/l2nx/nx-l2/issues/15) Adopt Lombok for boilerplate
- [#16](https://github.com/l2nx/nx-l2/issues/16) Finalize the JDK: distribution, version and flags
- [#17](https://github.com/l2nx/nx-l2/issues/17) Move game and login settings to Spring Boot configuration
- [#20](https://github.com/l2nx/nx-l2/issues/20) Time dilation for overloaded partitions
- [#21](https://github.com/l2nx/nx-l2/issues/21) Parallel open world if one partition exceeds its budget

## Done

- [#18](https://github.com/l2nx/nx-l2/issues/18) Target architecture of the game code: package layout, tick model, ECS
  ([spec 003](specs/003-engine-core/spec.md)).
- [#8](https://github.com/l2nx/nx-l2/issues/8) Publish project docs on l2nx.dev: the `/l2/` section, from
  `docs/public/{en,ru}/` (spec 001 §1).
- Ownership of the reference L2NX host layer: not ours; the adapter host is written from scratch against the
  `nx-gs-adapter` API (spec 001 §2, stage 5).
