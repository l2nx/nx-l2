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

The project home is `l2nx.dev`; it hosts the documentation only: the section `/l2/`, published from
`docs/public/{en,ru}/` of this repository whenever those pages change on `main`. The
test server site lives on a separate domain, chosen at stage 6; it is not hosted on `l2nx.dev` or `l2nx.top`.

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

The Mobius HF tree imported at stage 1 (upstream commit in [`docs/provenance.md`](../provenance.md)) holds 898 GPLv3
and 1005 MIT Java files in the core, plus 924 GPLv3 and 367 MIT scripts.

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

Every file carries an SPDX license identifier (`Apache-2.0`, `MIT`, `GPL-3.0-or-later` as the upstream
header states), following the [REUSE](https://reuse.software) specification. CI runs `reuse lint`: a file without
a license fails the build. The GPLv3 count per module comes from the same data.

- **Files taken from Mobius keep their Mobius header and license.** This holds even after heavy changes: both MIT
  and GPLv3 require the copyright notice to be preserved. The import inserts the SPDX identifier into the existing
  header and leaves the upstream text as is.
- **Mobius files without a header** (datapack XML/HTML, SQL, configs) are labelled `MIT`, "L2J Mobius
  contributors", in `REUSE.toml`. Basis: upstream states it is moving to MIT and that the header governs each
  file; it makes no statement for headerless files. The owner chose MIT over the conservative GPLv3 reading.
- **A substantively modified Mobius file adds an SPDX block** with `2026 L2NX contributors` and the upstream
  license identifier (CONTRIBUTING.md). Its license does not change. Mechanical changes (package rename, SPDX
  insertion) add no block.
- **A file written from scratch carries only the project header, Apache 2.0.** Renaming packages or variables is
  not a rewrite. A rewrite of a GPLv3 file is done from the behaviour, not by editing the GPLv3 text.
- **`NOTICE` names the upstream:** "based on L2J Mobius", with the upstream commit the import started from.
- **Package rename is allowed:** `org.l2jmobius` becomes the package of the module the file lands in (§4), and
  the headers stay.

### Legal protection

[`LEGAL.md`](../../LEGAL.md) is the public legal notice. A disclaimer alone does not stop a takedown: a platform acts on a
formal notice. What protects the project is that the repository holds no protected third-party material, that this
can be shown quickly in a counter-notice, and that nothing about the project looks commercial.

- **Interoperability basis:** EU Directive 2009/24/EC Art. 5(3) and 6, _SAS Institute v World Programming_ (C-406/10);
  US 17 U.S.C. § 102(b), § 1201(f), _Sega v. Accolade_, _Sony v. Connectix_.
- **Non-commercial project:** the project sells nothing, takes no donations, carries no advertising. The licenses still
  allow commercial use; the project only does not encourage or support it. Educational purpose is a policy, not a
  license term.
- **Genre-neutral naming of rights holders:** the game is named only where the project cannot be described without it;
  no publisher is named, the trademark notice is generic. Same precaution as the L2NX platform legal texts.
- **Own takedown channel:** `support@l2nx.app`, subject "Notice" / "Counter-notice", the same notice elements as the
  platform's notice-and-takedown policy. Answering a rights holder directly removes only the affected material instead
  of the whole repository.
- **Provable provenance:** REUSE headers on every file, `NOTICE`, `docs/provenance.md`, the originality policy.
- **Datapack texts** are the main remaining risk: numbers, formulas and identifiers are functional data, texts may be
  protected expression ([#9](https://github.com/l2nx/nx-l2/issues/9)).
- **A mirror outside GitHub** keeps the project alive during a takedown ([#10](https://github.com/l2nx/nx-l2/issues/10)).

### Originality policy

The code base is public L2J Mobius, refactored in place. Game mechanics are checked against private reference
servers to find the best behaviour.

- No decompiled, leaked or proprietary code.
- **Reference servers are consulted for behaviour only.** What a mechanic does is re-implemented from scratch on
  top of Mobius; no code, data or structure is copied from them.
- **Own code with a single author and no L2J-derived parts may be brought in.** The first case is the Netty
  transport that becomes `libs/network` (stage 4).
- **The L2NX adapter host is written from scratch** against the `nx-gs-adapter` API (stage 5).

### Assets

The repository holds none of the following:

- the game client;
- geodata;
- baked navmesh (`.nav`, `.los`).

Tools are shipped instead, and every operator prepares data from their own client.

### Project files

| File                 | Content                                              |
| -------------------- | ---------------------------------------------------- |
| `LICENSE`            | Apache 2.0 text, verbatim; the project license       |
| `LICENSES/`          | every license text in use, REUSE layout              |
| `REUSE.toml`         | license and copyright for files without a header     |
| `NOTICE`             | upstream attribution                                 |
| `CONTRIBUTING.md`    | originality policy, inbound = outbound, header rules |
| `README.md`          | positioning, short disclaimer, contacts              |
| `LEGAL.md`           | legal notice: purpose, interoperability, takedown    |
| `docs/provenance.md` | upstream commit, import mapping, labelling rules     |

The MIT and GPLv3 texts land in `LICENSES/` together with the stage 1 import (`reuse download MIT
GPL-3.0-or-later`): `reuse lint` rejects a license text no file uses. `NOTICE` lands with the same import, since it
names the upstream commit.

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

The reason is the shared game core: it is extracted by diffing HF and Essence (§4, stage 9), a long refactoring
that moves code across the shared ↔ chronicle boundary. Across repositories every such move would cost a release
and a bump per consumer.

A chronicle moves to its own repository `nx-l2-<chronicle>` when either holds:

- `engine/core` has gone through releases without breaking API changes after stage 9;
- the chronicle has its own maintainer.

The move is mechanical because a chronicle is one self-contained directory (§4):
`git filter-repo --subdirectory-filter chronicles/<chronicle>` keeps its history, and project dependencies on
`libs/*` and `engine/*` become dependencies on published artifacts. [#6](https://github.com/l2nx/nx-l2/issues/6).

## 4. Module layout

```
libs/                 knows nothing about Lineage 2; reusable by any server
  commons             app.l2nx.commons     utilities
  network             app.l2nx.network     Netty transport (framing, PacketCipher, server/client)
  db                  app.l2nx.db          connection pool, Liquibase mechanics
  telemetry           app.l2nx.telemetry   Micrometer
engine/               Lineage 2 mechanics shared by every chronicle
  geo                 app.l2nx.engine.geo      movement / pathfinding / line-of-sight SPI + classic geodata
  adapter             app.l2nx.engine.adapter  L2NX adapter host
  core                app.l2nx.engine.core     partition runtime (spec 003); later what HF and Essence share
login/
  server              app.l2nx.login       login logic, protocol variants, GS-link, tools; sql/
  boot                app.l2nx.login.boot  launcher; runtime files (config, data) in src/dist
chronicles/
  high-five/
    server            app.l2nx.highfive          the chronicle's game server
    scripts           app.l2nx.highfive.scripts  quests, NPC AI, events, handlers; Java only
    datapack                                     data/ (incl. data/scripts html/xml), config/, sql/
    boot              app.l2nx.highfive.boot     launcher; runtime files in src/dist
  essence/            app.l2nx.essence     stage 9, same four modules
  interlude/          app.l2nx.interlude   stage 10, same four modules
tools/                data preparation from the operator's own client
build-logic/          Gradle convention plugins
```

What goes where:

- **`libs/`** holds infrastructure with no game knowledge. Login and every game server use it.
- **`engine/`** knows _how_: moving, seeing a target, talking to L2NX.
- **`chronicles/<chronicle>/server`** knows _what_ the chronicle is: classes, damage formulas, enchanting,
  olympiad, the chronicle's client packets. Today almost all Mobius code lands here; it moves into `engine/`
  only once two chronicles prove it identical.
- **`scripts`** is a delivery unit of its own (§4.1).
- **`boot`** is the assembly module: the Spring Boot application class, `application.yaml`, the distribution
  and the Docker image. A chronicle's `boot` assembles `server`, `scripts`, `datapack`, `engine/*` and `libs/*`
  into one runnable server; it is the only module that sees `server` and `scripts` together. Its distribution
  has the Mobius `dist/` layout (`./config`, `./data`), and `run` starts from the installed distribution. The
  login distribution also carries the account and game server registration tools. Runtime details:
  [spec 002](002-spring-boot-runtime.md).

Rules:

- **Dependencies point down only.** `chronicles/* → engine/* → libs/*`, `login → libs/*`. Inside a chronicle,
  `scripts → server` and `boot → server, scripts`. `engine` does not know about chronicles, `libs` does not know
  about `engine`, chronicles do not depend on each other. The Gradle module boundaries enforce this: a module
  compiles only against the modules it declares.
- **Spring, Lombok and other de-facto standards are allowed in every module,** game logic included. The code
  is not kept framework-free; wiring and the layout of the game code: [spec 003](003-engine-core/wiring-layout.md).
- **The engine provides mechanisms, the chronicle provides rules.** Formulas, classes, skills, enchanting and
  game systems belong to the chronicle. The engine exposes extension points: SPI, handler registries.
- **The engine is not extracted up front.** With HF alone, `engine/` takes `geo`, `adapter` and `core`, where
  `core` holds only the chronicle-agnostic runtime of [spec 003](003-engine-core/spec.md): partition loops, clock,
  identity, sessions, replication and persistence mechanisms. Shared game mechanics are extracted when Essence
  arrives, by comparing the two Mobius trees.
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
- **Release:** stage 1 builds `scripts` as a module; the separate release and its README come later ([#11](https://github.com/l2nx/nx-l2/issues/11)).
- **Cost of a compiled module:** Mobius' runtime compilation of script sources is gone; a change to script code
  needs a rebuild. An operator changes scripts through their own build or a module on top. Reloading without a
  restart stays, on the loaded classes: `//quest_reload` replaces the quest with a new instance of its class (a class
  without a public no-argument constructor runs its script's `main` again), `//script_load` and
  `//reload handler|effect` run a script's `main` again. Instances start with fresh state; static fields keep their
  values, because the classes are not loaded anew.

How the server finds scripts (`server` cannot depend on `scripts`, so discovery goes through the classpath):

- **`ScriptEngine` scans `app.l2nx.highfive.scripts` with ClassGraph** and calls the
  `public static void main(String[])` of each top-level class, sorted by name, as Mobius did after compiling.
  `handlers.MasterHandler` and `handlers.EffectMasterHandler` run first. A script is named relative to that package
  (`quests.QuestMasterHandler`); admin commands also accept the Mobius path form (`quests/QuestMasterHandler.java`).
- **`config/Scripts.xml` keeps its meaning:** an excluded folder or file name matches a package segment or a
  simple class name; `include` inside an excluded folder re-admits a class.
- **Script data stays on disk** in `datapack/data/scripts/<path>/`, the path Mobius used, so `HtmCache` and the
  core are unchanged. `Quest.getPath()` derives `<path>` from the class name: the `app.l2nx.highfive.scripts.`
  prefix is cut, the dots become `/`, the class name is dropped.

## 5. Stack

| Concern  | Choice                                                                             |
| -------- | ---------------------------------------------------------------------------------- |
| Java     | 25 LTS (Mobius targets 27; the imported tree compiles on 25 as is)                 |
| Build    | Gradle Kotlin DSL, version catalog, no committed jars but the Gradle wrapper       |
| Local DB | `compose.yaml`: database (`l2nx-db`); servers from Gradle or the `servers` profile |
| Host     | Spring Boot 4.1 (spec 002)                                                         |
| Network  | Netty for every connection: client↔game, client↔login, login↔game                  |
| Database | PostgreSQL from stage 3 (§5.1); MariaDB with the imported SQL until then           |
| Metrics  | Micrometer, Actuator, Prometheus scrape                                            |
| Logging  | Log4j2 (async)                                                                     |
| Docs     | English; the public pages on `l2nx.dev` also in Russian                            |

### 5.1. Database

The database is chosen by what a game server needs from it, not by what Mobius was written for. Game state lives
in the JVM; the database persists it: a character is loaded at login, its changes are written back, and the data
must survive a crash. Analytics is not its job: the platform receives the data through the adapter.

The load, measured on a production snapshot of a large L2J-derived server (57k characters over its lifetime):

- **Small.** About 0.7 GB and 13M rows; the whole database fits in memory.
- **Many small writes.** A synchronous UPDATE per item change, upserts of character and quest variables, a periodic
  autosave of the whole character, DELETE + INSERT pairs for skills and shortcuts. Reads happen mostly at login and
  at boot.
- **Low concurrency.** A pool of 30 connections.
- **The main risk is a lost or duplicated item.** The imported code uses no transactions; character saves and item
  transfers become atomic as the code is reworked.

**PostgreSQL** fits this best:

- **Integrity in the schema:** CHECK and foreign keys, strict types with no silent truncation, partial unique
  indexes. Case-insensitive character and account names become a real UNIQUE constraint (`citext` or a
  nondeterministic collation); today they rely on the table collation alone.
- **Durability per transaction:** an item transfer commits synchronously, a routine autosave with
  `SET LOCAL synchronous_commit = off`. InnoDB only has a global setting.
- **Transactional DDL:** a failed Liquibase migration rolls back whole, instead of leaving an operator's server
  with a half-applied schema.
- **`jsonb` and arrays** for the structured values the imported schema keeps as strings.
- **Logical replication** (pgoutput) for change capture.

The case for InnoDB is cheap in-place UPDATEs and a clustered primary key. A PostgreSQL UPDATE writes a new row
version that vacuum later reclaims, and its indexes are rewritten when an indexed column changes. At this size
(everything in memory) and write rate the difference does not matter; `fillfactor` and autovacuum settings cover
the hot tables (`items`, character variables).

Rejected: SQLite (login and several game servers share one database), distributed SQL such as CockroachDB or
YugabyteDB (a network round trip on every write, nothing to gain at this size), key-value stores (no schema, no
constraints).

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
game stays one; only the clients differ. This comes after the test server is live (stage 8).

### Edge gateway

An edge gateway (the client wire terminated in a separate process, the game runtime headless behind S2S) is not
part of the layout. If it is needed, it becomes a top-level
`edge/` directory. [#4](https://github.com/l2nx/nx-l2/issues/4).

## 9. Test server

The test server:

- runs on our infrastructure (Komodo), as a separate stack;
- is protected by SAC anti-cheat;
- is non-commercial: no donations, no item sales, no advertising, no links to commercial servers;
- has its own site on a separate domain, with rules and the `LEGAL.md` disclaimer; it uses no publisher's name,
  trademark or artwork;
- limits registration (invites or a capped list) so it stays a test bed, not a game service;
- may wipe characters on releases;
- is deployed by chronicle tag, and the adapter e2e suite runs against it.

## 10. Upstream

- **[`docs/provenance.md`](../provenance.md)** records the Mobius commit the import started from, the path
  mapping, what was stripped and the labelling rules.
- **Upstream fixes are ported by hand** (cherry-pick with adaptation) once the structure diverges. This is the
  accepted cost of the fork.

## 11. Stages

Every stage ends with a server that builds and a client that logs in and plays. No stage breaks the game.

| #   | Stage                                                                           | Done when                                                                                       |
| --- | ------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| 0   | Repository and legal                                                            | `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, README disclaimer                                       |
| 1   | Import HF with history (`git filter-repo` of Mobius), Gradle build              | server starts locally, an HF client logs in and plays                                           |
| 2   | Spring Boot runtime ([spec 002](002-spring-boot-runtime.md))                    | Spring Boot hosts both servers, Actuator and metrics, graceful shutdown, boot smoke tests in CI |
| 3   | PostgreSQL (§5.1)                                                               | Liquibase baseline written for PostgreSQL, the SQL ported and covered by Testcontainers tests   |
| 4   | Netty network                                                                   | own Netty transport in `libs/network`, all three connections                                    |
| 5   | L2NX built in ([#13](https://github.com/l2nx/nx-l2/issues/13))                  | latest adapter, commands and events work                                                        |
| 6   | Public test server                                                              | public access, adapter e2e in CI                                                                |
| 7   | Geo SPI + open `nx-physics`                                                     | two implementations, movement on navmesh                                                        |
| 8   | HF multi-protocol                                                               | newer clients log in to HF                                                                      |
| 9   | Essence + shared core extraction ([#2](https://github.com/l2nx/nx-l2/issues/2)) | second chronicle; `engine/` grows by real overlap                                               |
| 10  | Interlude ([#3](https://github.com/l2nx/nx-l2/issues/3))                        | third chronicle                                                                                 |

Stage 1 lands the Mobius tree directly in the §4 layout and the `app.l2nx.*` packages: `git filter-repo` rewrites
both historical HF paths of Mobius into the target paths, so file history runs unbroken from 2016 without
`--follow`. Mapping and stripped files: [`docs/provenance.md`](../provenance.md).

Stage 3 replaces the schema in one step: the Liquibase baseline is written for PostgreSQL rather than ported from
the imported MySQL DDL, so the schema is written once. The port covers case-insensitive name and login checks
(today they rely on the `_ci` collation), the stricter type handling of pgjdbc (`setBoolean` into TINYINT columns,
`setString` into numeric ones), the MySQL-only statements (`REPLACE INTO`, `ON DUPLICATE KEY UPDATE`, `IF()`,
backticks), the data types PostgreSQL lacks (UNSIGNED, MEDIUMINT, ENUM) and empty-string defaults that stand
for no value (`gameservers.host`), which become NULL. Keys are UUIDv7 and object ids on the wire become handles
allocated on load ([spec 003, identity](003-engine-core/persistence.md#1-persistent-identity)). The adapter's
change capture reads only MySQL and MariaDB today; its PostgreSQL path
([#13](https://github.com/l2nx/nx-l2/issues/13)) is needed before stage 5.

A stage updates the living docs (this spec, `docs/provenance.md`, README). A new spec is created only for a
feature with its own lifecycle.

## 12. Defaults taken without explicit confirmation

These are recommendations from the design discussion. Each one can be overridden before stage 0.

1. **Host:** Spring Boot.

## 13. Deferred

- Pushing GPLv3 out of the build — [#1](https://github.com/l2nx/nx-l2/issues/1).
- Essence chronicle and shared core extraction — [#2](https://github.com/l2nx/nx-l2/issues/2).
- Interlude chronicle — [#3](https://github.com/l2nx/nx-l2/issues/3).
- Edge gateway / horizontal scale-out — [#4](https://github.com/l2nx/nx-l2/issues/4).
- Stable script API for other emulators — [#5](https://github.com/l2nx/nx-l2/issues/5).
- Moving a chronicle into its own repository — [#6](https://github.com/l2nx/nx-l2/issues/6).
- Datapack text review — [#9](https://github.com/l2nx/nx-l2/issues/9).
- Repository mirror outside GitHub — [#10](https://github.com/l2nx/nx-l2/issues/10).
- CLA or DCO enforcement before external contributions — [#7](https://github.com/l2nx/nx-l2/issues/7).
- Separate release of the High Five scripts — [#11](https://github.com/l2nx/nx-l2/issues/11).
- PostgreSQL in the adapter's change capture — [#13](https://github.com/l2nx/nx-l2/issues/13).
