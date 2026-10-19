# 001 — nx-l2: project design

**Date:** 2026-10-17
**Related repos:** `nx-physics/docs/specs/001-spatial-engine.md` (spatial engine, opened by this project),
`nx-gs-adapter` (L2NX adapter, built in)

## 1. Goal

An open-source Lineage 2 server built on L2J Mobius, with the L2NX adapter built in. One repository holds every
chronicle (High Five, Essence, Interlude), the login server, the network stack and the shared libraries.

It serves three purposes:

1. **Adapter test bed.** Every adapter release is verified end to end against a live server.
2. **Ready-made build.** Anyone who wants to run a server gets one with L2NX integration out of the box.
3. **Community development.** The engine is maintained in the open, not by a single person.

Positioning follows Mobius: a reference implementation for education and research. The public test server is the
project's demo and is non-commercial.

The project home is `l2nx.<tld>` (default `l2nx.dev`); the test server lives on a subdomain (`play.`). It is not
hosted on `l2nx.top`.

## 2. Legal and licensing

### Licenses

The goal: a build anyone may distribute and modify in any way, closed forks included. Licenses are assigned per
file, as Mobius does it: its own files are MIT, the files inherited from L2J are GPLv3.

| Component                     | License                            | Why                                                                 |
| ----------------------------- | ---------------------------------- | ------------------------------------------------------------------- |
| New and rewritten files       | Apache 2.0                         | one license with the adapter and `nx-physics`; patent grant         |
| Mobius MIT files              | MIT                                | kept; MIT and Apache 2.0 combine freely                             |
| Mobius GPLv3 files            | GPLv3                              | cannot be relicensed; they go away only by a rewrite                |
| `nx-l2` build as a whole      | GPLv3 while any GPLv3 file remains | one GPLv3 file binds the combined work                              |
| `nx-physics`, `nx-gs-adapter` | Apache 2.0                         | any emulator can adopt them, closed ones included; GPLv3-compatible |

The Mobius HF tree imported at the start holds 905 GPLv3 and 990 MIT Java files in the core, plus 927 GPLv3 scripts.

**GPLv3 is pushed out, not kept** ([#1](https://github.com/l2nx/nx-l2/issues/1)):

- **`libs/*` and `engine/*` hold no GPLv3 file.** They are Apache 2.0 / MIT from the first commit and can be
  published as permissive artifacts for any emulator. A Mobius GPLv3 file needed there is rewritten first; until
  then it stays in its chronicle.
- **A chronicle module turns permissive when its last GPLv3 file is rewritten.** CI prints the GPLv3 file count
  per module from the SPDX headers (§2, REUSE).
- **While GPLv3 files remain, the build is distributed under GPLv3:** anyone may distribute and modify it, with
  the source. Running a server without handing the build to anyone is not distribution and carries no obligation.
- **Inbound = outbound:** a contribution comes under the license of the file it touches, so no relicensing right
  is needed for the GPLv3 exit. Whether a CLA or a DCO enforces this is decided in [#7](https://github.com/l2nx/nx-l2/issues/7).

Mobius' "for education" wording is a fair-use argument and a project policy, not a license term. Neither GPLv3 nor
MIT restricts use, and GPLv3 §7/§10 forbid adding restrictions.

### Authorship and headers

Every file carries an SPDX line (`SPDX-License-Identifier: Apache-2.0`, `MIT`, `GPL-3.0-or-later` as the upstream
header states), following the [REUSE](https://reuse.software) specification. CI runs `reuse lint`: a file without
a license fails the build. The GPLv3 count per module comes from the same data.

- **Files taken from Mobius keep their Mobius header and license.** This holds even after heavy changes: both MIT
  and GPLv3 require the copyright notice to be preserved.
- **A modified Mobius file adds a line:** `Copyright (c) 2026 L2NX contributors`. Its license does not change.
- **A file written from scratch carries only the project header, Apache 2.0.** Renaming packages or variables is
  not a rewrite. A rewrite of a GPLv3 file is done from the behaviour, not by editing the GPLv3 text.
- **`NOTICE` names the upstream:** "based on L2J Mobius", with the upstream commit the import started from.
- **Package rename is allowed:** `org.l2jmobius` becomes the package of the module the file lands in (§4), and
  the headers stay.

### Originality policy

The code base is public L2J Mobius, refactored in place. Game mechanics are checked against private reference
servers to find the best behaviour.

- No decompiled, leaked or proprietary code.
- **Reference servers are consulted for behaviour only.** What a mechanic does is re-implemented from scratch on
  top of Mobius; no code, data or structure is copied from them.
- **Own code with a single author and no L2J-derived parts may be brought in.** The first case is the Netty
  transport that becomes `libs/network` (stage 3).
- **The L2NX adapter host is written from scratch** against the `nx-gs-adapter` API (stage 4).

### Assets

The repository holds none of the following:

- the game client;
- geodata;
- baked navmesh (`.nav`, `.los`).

Tools are shipped instead, and every operator prepares data from their own client.

### Project files

| File              | Content                                                |
| ----------------- | ------------------------------------------------------ |
| `LICENSE`         | Apache 2.0, the project license; points to `LICENSES/` |
| `LICENSES/`       | every license text in use, REUSE layout                |
| `NOTICE`          | upstream attribution                                   |
| `CONTRIBUTING.md` | originality policy, inbound = outbound, header rules   |
| `README.md`       | positioning, disclaimer, takedown contact              |

Contributions come under the license of the file they touch (inbound = outbound). A CLA or DCO, enforced by a PR
bot, records this. The bot is not needed while only the owner and contracted developers commit; it is enabled
before the repository goes public or accepts its first external PR, whichever comes first ([#7](https://github.com/l2nx/nx-l2/issues/7)).

## 3. Repositories

| Repo            | Content                                                 | Visibility |
| --------------- | ------------------------------------------------------- | ---------- |
| `nx-l2`         | monorepo: libs, engine, login, chronicles with datapack | public     |
| `nx-physics`    | spatial engine + `navtools` / `unrtools`                | public     |
| `nx-gs-adapter` | L2NX adapter                                            | public     |

All three live in the `l2nx` GitHub organisation.

### Monorepo now, split later

`nx-l2` starts as a monorepo. Without geodata, one chronicle is roughly 0.1–0.5 GB of data, so three chronicles
make about 1 GB of working tree; contributors who need one chronicle use `git clone --filter=blob:none` plus
sparse checkout.

The reason is the shared game core: it is extracted by diffing HF and Essence (§4, stage 8), a long refactoring
that moves code across the shared ↔ chronicle boundary. Across repositories every such move would cost a release
and a bump per consumer.

A chronicle moves to its own repository `nx-l2-<chronicle>` when either holds:

- `engine/core` has gone through releases without breaking API changes after stage 8;
- the chronicle has its own maintainer.

The move is mechanical because a chronicle is one self-contained directory (§4):
`git filter-repo --subdirectory-filter chronicles/<chronicle>` keeps its history, and project dependencies on
`libs/*` and `engine/*` become dependencies on published artifacts. [#6](https://github.com/l2nx/nx-l2/issues/6).

## 4. Module layout

```
libs/                 knows nothing about Lineage 2; reusable by any server
  commons             app.l2nx.commons     framework-free utilities
  network             app.l2nx.network     Netty transport (framing, PacketCipher, server/client)
  db                  app.l2nx.db          connection pool, Liquibase mechanics
  telemetry           app.l2nx.telemetry   Micrometer
engine/               Lineage 2 mechanics shared by every chronicle
  geo                 app.l2nx.engine.geo      movement / pathfinding / line-of-sight SPI + classic geodata
  adapter             app.l2nx.engine.adapter  L2NX adapter host
  core                app.l2nx.engine.core     created at stage 8: what HF and Essence share
login/
  server              app.l2nx.login       login logic, protocol variants, GS-link; plain Java
  boot                app.l2nx.login.boot  Spring Boot launcher
chronicles/
  high-five/
    server            app.l2nx.highfive          the chronicle's game server; plain Java
    scripts           app.l2nx.highfive.scripts  quests, NPC AI, events
    datapack                                     XML / SQL
    boot              app.l2nx.highfive.boot     Spring Boot launcher
  essence/            app.l2nx.essence     stage 8, same four modules
  interlude/          app.l2nx.interlude   stage 9, same four modules
tools/                data preparation from the operator's own client
```

What goes where:

- **`libs/`** holds infrastructure with no game knowledge. Login and every game server use it.
- **`engine/`** knows _how_: moving, seeing a target, talking to L2NX.
- **`chronicles/<chronicle>/server`** knows _what_ the chronicle is: classes, damage formulas, enchanting,
  olympiad, the chronicle's client packets. Today almost all Mobius code lands here; it moves into `engine/`
  only once two chronicles prove it identical.
- **`scripts`** is a delivery unit of its own (§4.1).
- **`boot`** is the only place that knows Spring Boot: `main`, `application.yaml`, wiring, lifecycle, graceful
  shutdown, the distribution and the Docker image. A chronicle's `boot` assembles `server`, `scripts`,
  `datapack`, `engine/*` and `libs/*` into one runnable server.

Rules:

- **Dependencies point down only.** `chronicles/* → engine/* → libs/*`, `login → libs/*`. Inside a chronicle,
  `scripts → server` and `boot → server, scripts`. `engine` does not know about chronicles, `libs` does not know
  about `engine`, chronicles do not depend on each other. ArchUnit and the Gradle module boundaries enforce this.
- **Spring Boot is present only in `*/boot` modules.** Every other module is plain Java; ArchUnit forbids
  `org.springframework` imports outside `boot`.
- **The engine provides mechanisms, the chronicle provides rules.** Formulas, classes, skills, enchanting and
  game systems belong to the chronicle. The engine exposes extension points: SPI, handler registries.
- **The engine is not extracted up front.** With HF alone, `engine/` takes only `geo` and `adapter`. The shared
  game core is extracted when Essence arrives, by comparing the two Mobius trees. An empty `engine/core` is not
  created ahead of time.
- **A datapack lives in its chronicle's directory.** Code and data change in one commit, so they cannot drift
  apart.
- **The package follows the path.** A module's root package is listed above; a new module picks its package by
  the same rule.
- **Gradle includes chronicles by property.** `-Pchronicles=high-five,essence` limits the build; the default is
  every chronicle.

### 4.1. Scripts as a delivery unit

`scripts` is released and versioned on its own (tag `high-five-scripts/vX.Y`), with a README that lists the
quests and NPC behaviour it covers. It is meant to become a recognisable unit that other builds take.

- **Now:** scripts compile against the chronicle's `server` (`Quest`, `Npc`, `Player`, as in Mobius). Builds on
  top of `nx-l2` (forks, operator customisations) consume the artifact as is.
- **Target:** a stable script API that other emulators implement, so the scripts run outside `nx-l2`. [#5](https://github.com/l2nx/nx-l2/issues/5).
- **Cost of a compiled module:** Mobius' runtime compilation of script sources and reloading a quest without a
  restart are gone. An operator changes scripts through their own build or a module on top.

## 5. Stack

| Concern  | Choice                                                               |
| -------- | -------------------------------------------------------------------- |
| Java     | 25, matching Mobius                                                  |
| Build    | Gradle Kotlin DSL, version catalog, no committed jars                |
| Host     | Spring Boot in `*/boot` only; every other module is plain code       |
| Network  | Netty for every connection: client↔game, client↔login, login↔game |
| Database | MariaDB (Mobius native), schema managed by Liquibase                 |
| Metrics  | Micrometer                                                           |
| Docs     | English, as an international open-source project                     |

## 6. L2NX integration

- **`engine/adapter` hosts the adapter:** `ServiceLoader` discovery, lifecycle, JDBC source, commands and events.
- **A chronicle supplies only chronicle data through an SPI:** class and item mapping, hook points.
- **Integration is active only when platform credentials are configured.** Without them the server runs as a
  plain emulator.
- **The engine tracks the latest adapter release.** An adapter release triggers:
  1. a bump in `nx-l2`;
  2. a build;
  3. a test server rollout;
  4. the adapter e2e suite.

## 7. Spatial engine

- **`engine/geo` defines an SPI for movement, pathfinding and line of sight.**
- **The default implementation is classic L2J geodata.** Geodata files are downloaded separately, as with
  Mobius.
- **`nx-physics` is the second implementation.** Its model (actor, position, speed, decision) is already
  game-agnostic. The navmesh is baked by the server operator with `navtools` from their own client.

## 8. Login and multi-protocol

### Login

- **One login server for every chronicle.** Accounts are shared. Each client sees only the game servers of its own
  chronicle in the server list.
- **One port per login protocol variant.** On the login connection the server speaks first (`Init` with the RSA
  key and Blowfish), so the client version is unknown at that point. Each listening port is bound to one variant
  and its codec; the client or launcher points at its port. There is no detection heuristic, and the server-list
  filter follows from the port.
- **Layout inside `login/server`:** `protocol/<variant>/` (codec, packets) next to the shared domain: accounts,
  bans, sessions, GS-link.
- **GS-link login↔game** runs on Netty and is the same for every chronicle. A game server reports its chronicle
  when it registers.

### Game server

Inside a chronicle, the packet codec is selected by the client protocol version: the client speaks first
(`ProtocolVersion`), so the choice is deterministic. Codecs live in `server/.../network/protocol/<version>/`. The
game stays one; only the clients differ. This comes after the test server is live (stage 7).

### Edge gateway

An edge gateway (the client wire terminated in a separate process, the game runtime headless behind S2S) is not
part of the layout. If it is needed, it becomes a top-level
`edge/` directory. [#4](https://github.com/l2nx/nx-l2/issues/4).

## 9. Test server

The test server:

- runs on our infrastructure (Komodo), as a separate stack;
- is protected by SAC anti-cheat;
- is non-commercial: no donations, no item sales;
- may wipe characters on releases;
- is deployed by chronicle tag, and the adapter e2e suite runs against it.

## 10. Upstream

- **`docs/provenance.md`** records the Mobius commit the import started from and the import rules.
- **Upstream fixes are ported by hand** (cherry-pick with adaptation) once the structure diverges. This is the
  accepted cost of the fork.

## 11. Stages

Every stage ends with a server that builds and a client that logs in and plays. No stage breaks the game.

| #   | Stage                                                                           | Done when                                                                 |
| --- | ------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 0   | Repository and legal                                                            | `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, README disclaimer                 |
| 1   | Import HF with history (subtree split of Mobius), Gradle build                  | server starts locally, an HF client logs in and plays                     |
| 2   | Host shell                                                                      | `boot` modules, `application.yaml`, Liquibase, metrics, graceful shutdown |
| 3   | Netty network                                                                   | own Netty transport in `libs/network`, all three connections              |
| 4   | L2NX built in                                                                   | latest adapter, commands and events work                                  |
| 5   | Public test server `play.l2nx.<tld>`                                            | public access, adapter e2e in CI                                          |
| 6   | Geo SPI + open `nx-physics`                                                     | two implementations, movement on navmesh                                  |
| 7   | HF multi-protocol                                                               | newer clients log in to HF                                                |
| 8   | Essence + shared core extraction ([#2](https://github.com/l2nx/nx-l2/issues/2)) | second chronicle; `engine/` grows by real overlap                         |
| 9   | Interlude ([#3](https://github.com/l2nx/nx-l2/issues/3))                        | third chronicle                                                           |

Stage 1 lands the Mobius tree directly in the §4 layout: game server code in `chronicles/high-five/server`,
scripts in `chronicles/high-five/scripts`, data in `chronicles/high-five/datapack`, the login server in
`login/server`.

Each stage gets its own spec in `docs/specs/` before implementation.

## 12. Defaults taken without explicit confirmation

These are recommendations from the design discussion. Each one can be overridden before stage 0.

1. **Domain:** `l2nx.dev`.
2. **Host:** Spring Boot.
3. **Database:** MariaDB, not Postgres.

## 13. Deferred

- Pushing GPLv3 out of the build — [#1](https://github.com/l2nx/nx-l2/issues/1).
- Essence chronicle and shared core extraction — [#2](https://github.com/l2nx/nx-l2/issues/2).
- Interlude chronicle — [#3](https://github.com/l2nx/nx-l2/issues/3).
- Edge gateway / horizontal scale-out — [#4](https://github.com/l2nx/nx-l2/issues/4).
- Stable script API for other emulators — [#5](https://github.com/l2nx/nx-l2/issues/5).
- Moving a chronicle into its own repository — [#6](https://github.com/l2nx/nx-l2/issues/6).
- CLA or DCO enforcement before external contributions — [#7](https://github.com/l2nx/nx-l2/issues/7).
