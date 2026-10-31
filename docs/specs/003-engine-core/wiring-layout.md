# 003 — Engine core: wiring and layout

How the game code is wired with Spring and where it lives. Part of [spec 003](spec.md). Host runtime, lifecycle and
shutdown order: [spec 002](../002-spring-boot-runtime.md).

## 1. Beans and plain objects

- **Spring beans** (singletons, constructor injection through Lombok `@RequiredArgsConstructor`):
  - static data registries ([entities.md §3](entities.md#3-static-data));
  - systems (combat, casting, effects, movement, AI, regeneration);
  - global services and partition runtime, the wire-handle allocator, persistence services;
  - handler registries (commands, protocol adapters, admin commands, effect handlers);
  - the `ScriptContext` implementation.
- **Plain objects, never beans:** entities (`Player`, `Npc`, `Item`, ...), their components, partition state,
  events, commands and messages. The engine creates them; they get collaborators from the systems that handle them,
  not through injection. No prototype-scoped beans on the hot path.
- **`getInstance()` singletons become beans** as their subsystem is migrated; callers receive the bean through their
  constructor. Scripts reach beans only through `ScriptContext`.

## 2. Spring inside partitions

Partition and service loops own their threads ([execution.md §7](execution.md#7-threads)). Spring mechanisms that run
code on other threads are not used in partition code:

| Mechanism                            | In partition and service code | Elsewhere                                                                                                                                                           |
| ------------------------------------ | ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `@Scheduled`, `TaskScheduler`        | no: use partition deadlines   | maintenance jobs and background tasks ([execution.md §7.1](execution.md#71-work-routed-into-the-world-partition-e6)); anything touching game state goes to an inbox |
| `@Async`                             | no                            | no need                                                                                                                                                             |
| `@EventListener`, application events | no: use the game event system | lifecycle only                                                                                                                                                      |
| `@Transactional`                     | no                            | persistence services on the persistence executor                                                                                                                    |

## 3. Lifecycle and configuration

- **Start and stop are `SmartLifecycle` phases** after Tomcat's, in this order on start and the reverse on stop.
  The management endpoints answer while the world loads, with readiness `OUT_OF_SERVICE`; liveness stays `BROKEN`
  until the context has started (Boot publishes `CORRECT` only then), so a container healthcheck on liveness needs
  a start period above the load time.
  1. **Network** (`GameServer`, below the partition runtime): starts nothing itself, the Netty IO threads are a
     bean of their own. On stop, last: the farewell to the clients and the close of their connections, a wait for
     their sessions to close, then the accounts still online are reported as left and the GS-link shuts down.
  2. **Partition runtime** (`PartitionRuntime.PHASE`, `DEFAULT_PHASE - 512`): the loop threads start, the world
     loop is idle. On stop the loops drain and stop, bounded by `spring.lifecycle.timeout-per-shutdown-phase`.
  3. **World** (above the partition runtime): offers static data and world loading to the world loop as its first
     message and waits for it ([execution.md §7.1](execution.md#71-work-routed-into-the-world-partition-e6)). A
     failure makes `start()` throw: Boot closes the context and the process exits with code 1 before any port
     opens ([spec 002 §3](../002-spring-boot-runtime.md#start)). On stop it offers the shutdown work to the world
     loop and waits for it to the end, as the synchronous save of spec 002 does: players logged out and saved
     ([persistence.md §4](persistence.md#4-write-behind)) and their accounts reported to the login server, the
     global saves (Seven Signs, olympiad, heroes, clans, manor, scripts).
  4. **Game port** (`DEFAULT_PHASE`): binds the port after the world is loaded, so no input reaches a world without
     its data, then starts the GS-link, so the login server lists the server only once it can take players. Boot sets readiness to `ACCEPTING_TRAFFIC` on
     `ApplicationReadyEvent`; no own availability event. On stop, first: the server channel closes and client reads
     stop, so nobody logs in or acts during the save, while the IO threads and the GS-link stay up for it.

- **Engine settings are `@ConfigurationProperties`:** pass budget and minimum, cadences, packets per pass, byte
  budgets, handle quarantine, write-behind interval, loop thread count. They move together with the game settings
  ([#17](https://github.com/l2nx/nx-l2/issues/17)).

## 4. Layout

### Engine

`engine/core` (`app.l2nx.engine.core`) is created by its first package, not at stage 9. A package lands on `main` as
soon as it is complete and tested (unit tests, its ArchUnit rule if any, no stubs or half-built API), ahead of its
first consumer when needed: the earlier the new architecture is in the tree, the earlier legacy code is refactored
onto it and the longer it is exercised. The order follows the stages: `clock` (E5), `identity` (stage 3),
`loop` and `partition` (E6 part 1, before stage 4: `session` implements `InputSource` of `loop.inbox`), `session`
(stage 4), `replication` (E8), `persistence` (E3, E4). It holds the chronicle-agnostic mechanisms:

| Package       | Contents                                                                                                    |
| ------------- | ----------------------------------------------------------------------------------------------------------- |
| `loop`        | partition and service loops, pass phases, loop threads; sub-packages `inbox`, `timer`, `periodic`        |
| `clock`       | `GameClock`, RNG                                                                                            |
| `partition`   | partitions, entity transfer, global service base                                                            |
| `identity`    | UUIDv7 generation, wire-handle allocator                                                                    |
| `session`     | session queues and their drain, session states, command dispatch, `ClientPort`                              |
| `replication` | client view, visibility grid, event classes, budgets                                                        |
| `persistence` | write-behind, value transaction runner, outbox                                                              |

Only `session` depends on `libs/network` (its transport, settings and meters); an ArchUnit rule in `engine/core` keeps
the other packages transport-free.

Game mechanics shared by chronicles still join `engine/` only by proven overlap when Essence arrives
([spec 001 §4](../001-project-design.md#4-module-layout)).

### Chronicle

The chronicle server (`app.l2nx.highfive`) is cut by feature, not by technical layer:

```
app.l2nx.highfive
  entity      facades (Player, Npc, Item, ...) and templates
  network     pipeline, protocol registry, protocol/<id>/ adapters (packets), gslink/; no game logic
  session     shared session kernel: the command handler base, legacy notices
  login       the game server's side of the login server: online accounts, server listing, LoginPort
  movement    the periodic systems `player-movement` and `npc-movement`, the root bridge `Movement` for legacy callers
  partition   the world partition: world load and shutdown hook, `WorldSystem` (how a slice installs its periodic
              systems into the world loop), the static bridges of legacy code to the world loop and to background tasks
  combat  skill  effect  ai  item  trade  mail  auction
  clan  party  siege  olympiad  quest  chat  reflection  ...
```

- **A slice owns** its components and systems, its static data loader and registry, its commands and their
  handlers, the view events it emits to clients, its repositories, tables and Liquibase changesets, its
  `@ConfigurationProperties`. Packets belong to the protocol adapters, not to slices
  ([spec 008 §8](../008-network.md#8-ports-and-protocol-adapters)).
- **Catch-all packages dissolve** into slices as code is reworked: `managers`, `util`, `holders`, `taskmanagers`,
  `data/xml`, and the stage 3 `repository` groups ([#49](https://github.com/l2nx/nx-l2/issues/49)).

#### Inside a slice

Each slice is a Spring Modulith application module. Its root package is its API; subpackages are internal unless
declared a named interface. The subpackage names are fixed, and a subpackage exists only when it has content:

```
app.l2nx.highfive.<slice>
  (root)       API: facade or service, outbound <Edge>Port, value types and enums carried by commands or view events
  command/     command records (public) and their handlers (package-private)      named interface
  view/        view events and read-only view interfaces                          named interface
  event/       domain events (entities.md §4)                                     named interface
  system/      systems                                                            internal
  model/       components, internal value types and enums                         internal
  template/    static data: templates, loader, registry                           internal
  repository/  @Repository classes and their row records                          internal
```

- **`view/` is what an adapter may read:** the view events and the read-only views a mapper reads to serialize them.
  Components in `model/` implement those view interfaces; they are mutable and owned by their system, so no adapter
  reaches them.
- **View events and domain events are kept apart:** a view event (`ViewEvent`) is a fact for clients, a domain event
  (`DomainEvent`) is dispatched inside the partition to systems and scripts.
- **A command and its one handler sit together** in `command/`; the handler class is package-private, so the named
  interface exposes the records only.
- **Value types that cross a port** (a chat type, a move input, a message key, a failure reason) are API and live in
  the root, not in `model/`.
- **Repositories are concrete classes,** not an interface and an adapter: there is one database and one
  implementation, and the repository is already the adapter of that edge. They are tested against PostgreSQL.
- **Another slice is used only through its root API.**

#### Ports

Ports exist only at the outer edges that are a network or another process. Game logic is not split into `api` /
`domain` / `infra`.

| Edge       | Into the game       | Out of the game                              | Adapter                                      |
| ---------- | ------------------- | -------------------------------------------- | -------------------------------------------- |
| client     | command and handler | view event to `ClientPort` (`engine/core`)   | `network/protocol/<id>/` (`P267Adapter`) |
| login link | command and handler | `LoginPort` (slice `login`)                  | `network/gslink/` (`LoginServerLink`)        |
| database   | —                   | `@Repository` class in `<slice>/repository/` | the repository itself                        |
| Kafka      | command and handler | a port in the owning slice                   | the L2NX adapter module (stage 5)            |

- **Everything that enters the game is a command,** whatever its source; there are no inbound port interfaces. A
  command is named as an intent (`KickAccount`, `SendChat`); the answer to a request the game made is a noun
  (`AuthenticationResult`). Its handler is `<Command>Handler`.
- **An outbound port is an interface named `<Edge>Port`.** `*Port` names only interfaces; the class implementing
  one is an adapter and may carry the `Adapter` suffix (`GameClientAdapter`). The suffixes `Impl` and `Gateway` are
  not used.
- **Adapters live under `network/`** (or in an integration module). Slices never depend on `network`; `network`
  sees a slice only through its root, `command/` and `view/`.

#### Verification

`SliceLayoutRules` in the `boot` tests checks the layout on every build:

- **Spring Modulith** `ApplicationModules.verify()` over the migrated slices: no cycles, no access to another slice's
  internals. Legacy packages are excluded, and the list of verified slices only grows as slices migrate.
- **ArchUnit:** the fixed subpackage names; by marker type, a `Command` is a public record in `command/`, a
  `CommandHandler` is package-private there with a non-public constructor and is named `<Command>Handler` after the
  command it handles, and `command/` holds nothing else; `ViewEvent` types only in `view/`, `DomainEvent` types only
  in `event/`; `*Port` only as interfaces in a slice root or `engine/core`; `@Repository` only in
  `<slice>/repository/`; no `*Impl` or `*Gateway` types; no slice depends on `network`.
- **Legacy classes a slice still needs are named, not tolerated by pattern:** a slice migrated before the port
  framework covers its whole path may use a few legacy `network` classes (the game client, its disconnection, the
  server packets it still sends). They are listed in one constant of the rules (`LEGACY_NETWORK_FACADES`), every
  entry leaves the list when the slice moves to commands and view events, and the list never grows.
- **Legacy violations are frozen,** not allowed: a rule the imported code breaks is a `FreezingArchRule` with its
  violation store in the repository. A new violation fails the build; fixed ones leave the store, so it only
  shrinks.

### Moves

The rework is thousands of moves. They are done slice by slice with `git mv`; a move and a content change are
always separate commits, so file history survives. Scripts follow the facades and are moved by the same rules.
