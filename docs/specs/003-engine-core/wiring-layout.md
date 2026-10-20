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

| Mechanism                            | In partition and service code | Elsewhere                                                       |
| ------------------------------------ | ----------------------------- | --------------------------------------------------------------- |
| `@Scheduled`, `TaskScheduler`        | no: use partition deadlines   | maintenance jobs; anything touching game state goes to an inbox |
| `@Async`                             | no                            | no need                                                         |
| `@EventListener`, application events | no: use the game event system | lifecycle only                                                  |
| `@Transactional`                     | no                            | persistence services on the persistence executor                |

## 3. Lifecycle and configuration

- **The partition runtime is a `SmartLifecycle`** with its own phase: it starts after the data registries are
  loaded and before the game port opens; on stop it stops accepting input, drains the inboxes, saves players
  ([persistence.md §4](persistence.md#4-write-behind)) and stops the loops. This is the "engine stop" step of the
  spec 002 stop order.
- **Engine settings are `@ConfigurationProperties`:** pass budget and minimum, cadences, packets per pass, byte
  budgets, handle quarantine, write-behind interval, loop thread count. They move together with the game settings
  ([#17](https://github.com/l2nx/nx-l2/issues/17)).

## 4. Layout

### Engine

`engine/core` (`app.l2nx.engine.core`) is created by its first package, not at stage 9. Each package lands with its
first consumer, never ahead of it: `clock` with E5, `identity` with stage 3, `session` with stage 4, `loop` and
`partition` with E6, `replication` with E8, `persistence` with E3 and E4. It holds the chronicle-agnostic
mechanisms:

| Package       | Contents                                                                       |
| ------------- | ------------------------------------------------------------------------------ |
| `loop`        | partition and service loops, inbox, pass phases, timer wheel                   |
| `clock`       | `GameClock`, RNG                                                               |
| `partition`   | partitions, entity transfer, global service base                               |
| `identity`    | UUIDv7 generation, wire-handle allocator                                       |
| `session`     | session queues and their drain, session states, command dispatch, `ClientPort` |
| `replication` | client view, visibility grid, event classes, budgets                           |
| `persistence` | write-behind, value transaction runner, outbox                                 |

Game mechanics shared by chronicles still join `engine/` only by proven overlap when Essence arrives
([spec 001 §4](../001-project-design.md#4-module-layout)).

### Chronicle

The chronicle server (`app.l2nx.highfive`) is cut by feature, not by technical layer:

```
app.l2nx.highfive
  entity      facades (Player, Npc, Item, ...) and templates
  network     pipeline, protocol registry, protocol/<id>/ adapters (packets); no game logic
  combat  skill  effect  movement  ai  item  trade  mail  auction
  clan  party  siege  olympiad  quest  chat  instance  ...
```

- **A slice owns** its components and systems, its static data loader and registry, its commands and their
  handlers, the view events it emits to clients, its tables and Liquibase changesets, its
  `@ConfigurationProperties`. Packets belong to the protocol adapters, not to slices
  ([spec 008 §8](../008-network.md#8-ports-and-protocol-adapters)).
- **Ports and adapters only at the outer edges:** client protocols, repositories for the database, the adapter and
  Kafka. Game logic is not split into `api` / `domain` / `infra`.
- **Catch-all packages dissolve** into slices as code is reworked: `managers`, `util`, `holders`, `taskmanagers`,
  `data/xml`.
- **Slice boundaries are verified by Spring Modulith** (`ApplicationModules.verify()` in a test): no cycles, and a
  slice uses another only through that slice's exposed API.

### Moves

The rework is thousands of moves. They are done slice by slice with `git mv`; a move and a content change are
always separate commits, so file history survives. Scripts follow the facades and are moved by the same rules.
