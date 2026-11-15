# 003 — Engine core

The target architecture of the game code: how the simulation runs, how game objects are modelled and wired, how
state reaches clients and the database, and the order in which the imported Mobius code gets there. Decides
[#18](https://github.com/l2nx/nx-l2/issues/18).

| File                                 | Covers                                                                                    |
| ------------------------------------ | ----------------------------------------------------------------------------------------- |
| `spec.md` (this file)                | principles, partitions, decisions at a glance, rejected alternatives, migration, deferred |
| [execution.md](execution.md)         | partition loop, inbox, session queues and packet order, timers, cadences, clock, threads  |
| [entities.md](entities.md)           | entity model, handles, static data, events, side effects, global services, scripts        |
| [effects.md](effects.md)             | buffs, debuffs and other effects on deadlines                                             |
| [replication.md](replication.md)     | client view, visibility grid, event classes, per-connection budgets, network hand-off     |
| [persistence.md](persistence.md)     | identity (UUIDv7, wire handles), value transactions, ledger, write-behind, outbox, schema |
| [wiring-layout.md](wiring-layout.md) | Spring wiring, lifecycle, module and package layout                                       |
| [roadmap.md](roadmap.md)             | status of the migration steps: done, in progress, left                                    |

The research behind these decisions (tick models, mature MMO server cores, ECS, spatial scaling, persistence) is
published on l2nx.dev: `docs/public/{en,ru}/engine-research.md`.

## 1. Principles

- **The client is the only fixed contract.** The protocol, packet semantics and the measured behaviour of the
  client (movement echo, validation cadence, what it ignores) are given. The server behaviour of Mobius is not a
  parity target: every mechanism is chosen on its merits.
- **One writer per piece of state.** Every mutable game state has exactly one owning thread at a time. No locks,
  no concurrent collections in game state.
- **Time and randomness are inputs.** Game code reads a partition's `GameClock` and RNG, never the system clock or
  a global random. This makes a partition deterministic and testable.
- **Industry practice over invention.** The shape below is what authoritative online games converge on; custom
  code is written only where Lineage 2 needs it (spec 001, principle in `CLAUDE.md`).
- **Every migration step keeps a playable server** ([spec 001 §11](../001-project-design.md#11-stages)).

## 2. Architecture at a glance

```mermaid
flowchart LR
  subgraph IO[Netty IO threads]
    C1[protocol adapter] --> SQ[session queues]
  end
  SQ --> W
  SQ --> I
  subgraph Loops[loop threads]
    W[world partition]
    I[reflection / olympiad partitions]
    G[global services: clan, olympiad, siege, auction]
  end
  W <-->|messages| G
  I <-->|messages| G
  W <-->|entity transfer| I
  W --> R[replication phase] --> IO
  W --> P[persistence executor] --> DB[(PostgreSQL)]
  G --> P
  P -->|results| W
```

## 3. Partitions

- **A partition is a simulation space behind a hard game seam:** the open world, each reflection, each olympiad
  match. Entities in different partitions never interact directly, so no cross-partition locking, ghosting or
  hand-off protocol is needed beyond moving an entity.
- **The contiguous open world is one partition.** It is not split into regions: splitting a seamless world across
  writers is the approach that failed in practice, and Lineage 2 concentrates load on exactly the borders a split
  would create (sieges). The fallback, if one thread proves insufficient, is
  [#21](https://github.com/l2nx/nx-l2/issues/21).
- **Partitions run in one process** on a pool of loop threads. A partition is pinned to one loop thread for its lifetime
  ([execution.md §7](execution.md#7-threads));
  there is no global barrier, so a heavy partition does not delay the others.
- **Global services** own state that spans partitions (clans and alliances, parties, olympiad, castle ownership and
  siege schedule, auction, scripted world events). Each is a single writer with its own inbox:
  [entities.md §5](entities.md#5-global-services). Mail is not one: the database serializes it
  ([spec 010 §1](../010-mail/spec.md#1-decisions)).
- **Partitions exchange messages only.** An entity moving into a reflection is detached from one partition and
  attached to the other through their inboxes; it keeps its wire handle ([persistence.md §2](persistence.md#2-wire-handles)).

## 4. Decisions at a glance

| Concern      | Decision                                                                                                                    | Where                                                                                                |
| ------------ | --------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Execution    | event loop per partition: inbox + deadline timers + periodic systems on their own cadences; no fixed global tick            | [execution.md](execution.md)                                                                         |
| Packet order | strict FIFO per client session, fair round-robin across sessions                                                            | [execution.md §3](execution.md#3-inbox-and-session-queues)                                           |
| Time         | injected `GameClock` per partition, game time vs wall-clock classified per mechanic; exact deadlines with carried remainder | [execution.md §6](execution.md#6-clock-and-randomness)                                               |
| Entity model | objects as facades for scripts, composed of components; data-oriented storage only for profiled hot paths; no ECS rewrite   | [entities.md](entities.md)                                                                           |
| References   | entities refer to each other by handle through the partition registry, never by Java reference                              | [entities.md §2](entities.md#2-handles-and-registries)                                               |
| Events       | synchronous typed dispatch inside a partition, deferred side-effect queue, no async in-process events                       | [entities.md §4](entities.md#4-events-and-side-effects)                                              |
| Effects      | absolute deadlines on the partition timer queue, stats recalculated once per pass                                           | [effects.md](effects.md)                                                                             |
| Replication  | separate phase after simulation: exact client view, coalescing, event classes, byte budgets                                 | [replication.md](replication.md)                                                                     |
| Protocols    | the game takes commands and emits view events through ports; a client protocol is an adapter that maps packets to them      | [spec 008 §8](../008-network.md#8-ports-and-protocol-adapters)                                       |
| Identity     | UUIDv7 for everything persistent; `int32` wire handles allocated on load, reused after quarantine                           | [persistence.md](persistence.md)                                                                     |
| Values       | one PostgreSQL transaction per value transfer, balance + append-only ledger, idempotent `idempotency_key`                   | [persistence.md §3](persistence.md#3-value-transactions)                                             |
| Other state  | write-behind with dirty tracking, about 30 s loss window, synchronous save on logout                                        | [persistence.md §4](persistence.md#4-write-behind)                                                   |
| Wiring       | Spring beans for services, registries, systems; plain objects on the hot path; no Spring threading in partitions            | [wiring-layout.md](wiring-layout.md)                                                                 |
| Layout       | `engine/core` for runtime mechanisms; chronicle packages by feature slice, boundaries checked by Spring Modulith            | [wiring-layout.md §4](wiring-layout.md#4-layout)                                                     |
| Overload     | metrics and bot load tests; a lagging pass is never caught up in bursts; time dilation deferred                             | [execution.md §8](execution.md#8-budget-and-metrics), [#20](https://github.com/l2nx/nx-l2/issues/20) |

## 5. Rejected alternatives

- **One tick per region of the open world.** More parallelism on paper; in practice every border interaction
  becomes a distributed protocol, and border bugs turned into item duplication and loss in the projects that
  shipped it. Kept only as the measured fallback [#21](https://github.com/l2nx/nx-l2/issues/21).
- **A fixed global tick (100 ms or 50 ms) for everything.** A tick adds up to one period of latency to every input
  and quantizes combat timing. The event loop removes the input latency; heavy per-entity work still runs on fixed
  cadences ([execution.md §5](execution.md#5-periodic-systems)).
- **Timers on a shared thread pool (the Mobius model).** Hundreds of tasks from many threads touch the same objects;
  ordering is nondeterministic and correctness depends on ad-hoc locks.
- **A full entity-component-system rewrite.** No public precedent of a classic MMO server core on a pure ECS, the
  scripts (about 310k lines) are written against object facades, and the measured gain needs data-oriented storage
  only on a few hot paths. ECS ideas are kept: components as data owned by systems, a single place for side effects.
- **Netty's event loop or Agrona's `AgentRunner` as the partition loop.** The partition loop follows Netty's
  `SingleThreadEventExecutor` in shape (pinned thread, MPSC inbox, `inEventLoop` test, scheduling from any thread)
  and uses JCTools parts, but neither runner fits as is. A partition keeps two time bases (game and wall
  clock) with deadlines on each; Netty 4.2 lets a loop replace its `Ticker`, but one per loop. A pass has phases
  (input, deadlines, systems, deferred side effects, replication, flush) with a `pass-min` batching gate,
  round-robin input sources with a per-pass quota, and a deterministic order a replay can rely on ([execution.md §9](execution.md#9-replay)); Netty runs tasks and its
  scheduled tasks from a priority queue with no such structure. Agrona's `AgentRunner` calls `doWork` under an idle
  strategy of fixed backoff, which cannot sleep exactly until the next deadline and wake on an offer, so it costs
  either latency or a spinning core per loop.
- **A hashed timer wheel (Agrona `DeadlineTimerWheel`) for partition timers.** It ran until the slop audit of
  2026-10-22: a wheel does not order timers within a tick nor know its earliest deadline, so it needed turn buckets,
  an occupancy bitmap and a sort per tick, about 560 lines. An interleaved JMH comparison found the binary heap no
  slower for a pass-sized batch and slower only for single timers at a million pending ones
  ([execution.md §4](execution.md#4-timers)).
- **Actor per entity (Orleans, Akka).** Fits meta services, not combat: every interaction between two entities
  becomes a message exchange with its own races. Global services use the single-writer loop without a framework.
- **An asynchronous event bus inside the simulation.** Unpredictable ordering and allocation on the hot path.
- **`BIGINT` sequences for persistent ids.** Need a database round trip or block allocation on the simulation
  thread and collide when worlds merge or characters transfer. UUIDv7 is generated in process and unique
  globally.

## 6. Migration

The steps form a track that runs alongside the stages of [spec 001 §11](../001-project-design.md#11-stages); the
dependency column ties them together. Each step ships on its own, ends with a playable server and is accepted by a
bot load test that includes a siege scenario (E0). Until E0 exists, a step is accepted by its metrics (E1) and by
playing with a real client. Scripts keep calling facades at every step; in E10 their direct packets become domain
calls on the facades.

| #   | Step                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Depends on                                                                                    |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------- |
| E0  | Load harness `tools/load` ([spec 009](../009-load-harness.md), [#68](https://github.com/l2nx/nx-l2/issues/68)): load bots playing the client protocols from a separate process, hundreds per process, load scenarios for login, movement, PvE and a crowd PvP fight; reports client-side latency and reads the engine meters; its `load-crowd-pvp` run is the acceptance gate of E6                                                                                                                                                                                                                                                                                                                                      | stage 4 (Netty client in `libs/network`), first `protocols/game/p267` codecs                  |
| E1  | Metrics: first a baseline of the legacy execution (task and packet durations per type, pool queue depths), then pass time per partition and phase as partitions appear ([execution.md §8](execution.md#8-budget-and-metrics))                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | spec 002 metrics                                                                              |
| E2  | Identity: UUIDv7 keys in the PostgreSQL baseline, wire-handle allocator ([persistence.md §1–2](persistence.md)); delivered inside stage 3                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | stage 3 (written into its baseline)                                                           |
| E3  | Value transactions, ledger, `idempotency_key`, outbox                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | E2                                                                                            |
| E4  | Database access off game threads; write-behind with a 30 s window                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | E2                                                                                            |
| E5  | `GameClock` and RNG instead of the system clock, `Calendar` and `ThreadLocalRandom` ([execution.md §6](execution.md#6-clock-and-randomness)); game time becomes monotonic, game-time state is stored without game-time instants ([persistence.md §4](persistence.md#4-write-behind))                                                                                                                                                                                                                                                                                                                                                                                                                                     | —                                                                                             |
| E6  | World partition as single writer, in two parts. The cutover: the world partition drains the session queues in the world, every `ThreadPool` call site moves to the loop API and `ThreadPool` is removed, the other threads that touched the world go through its inbox, world loading runs on the loop, the clock bridge returns the pass time and the partition RNG ([execution.md §7.1](execution.md#71-work-routed-into-the-world-partition-e6)); the legacy JDBC runs on the loop until E4 ([#69](https://github.com/l2nx/nx-l2/issues/69)). Then locks and concurrent collections removed class by class under an always-on ownership check ([execution.md §7.2](execution.md#72-locks-removed-from-game-state-e6)) | stage 3, stage 4, E1, E5; E0 for acceptance; E4 if the siege gate fails with JDBC on the loop |
| E7  | Subsystems moved from legacy loop timers to subsystem deadlines and periodic systems one at a time: effects, combat, casting, AI, regeneration; movement together with `nx-physics`. Started: AI think and movement as periodic systems, an attackable's drops and rewards as side effects of its death                                                                                                                                                                                                                                                                                                                                                                                                                  | E6, [#45](https://github.com/l2nx/nx-l2/issues/45)                                            |
| E8  | Replication phase; the replicated world state (spawn, despawn, movement, status, appearance, cosmetic events) moves to view events ([spec 008 §8](../008-network.md#8-ports-and-protocol-adapters))                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | E6                                                                                            |
| E9  | Global services; reflections and olympiad matches as their own partitions                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | E6                                                                                            |
| E10 | Package rework by feature slice, alongside E6–E9: a slice takes over its incoming packets as commands, its other outgoing packets as view events, and the scripts that send them move to domain calls on the facades; moves and content changes in separate commits                                                                                                                                                                                                                                                                                                                                                                                                                                                      | stage 4                                                                                       |
| E11 | Data-oriented hot paths where the profile shows them                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | E7, E8                                                                                        |

How E6 works without rewriting subsystems: every `ThreadPool` call site of game code and scripts is rewritten
mechanically to the world loop's `schedule`, `scheduleAtFixedRate` and `execute`, with the same delays and periods
([execution.md §4](execution.md#4-timers)); client packets go through the session queues, drained by the partition
instead of the legacy packet pool ([execution.md §3](execution.md#3-inbox-and-session-queues)). Work that never
touches game state runs as background tasks on Spring's task scheduler and hands its results back through the inbox.
From then on the world is single-writer even though the code still looks like Mobius; E7 then turns the timers into
subsystem deadlines and periodic systems.

Each step ends with a rule in the ArchUnit tests of the `boot` modules, so a migrated area cannot regress: the
forbidden time and randomness APIs (E5), no executors or threads in game code and no lock in a migrated class
(E6), no JDBC on loop threads (E4), no server packets in a migrated slice (E8, E10, [spec 008 §8](../008-network.md#8-ports-and-protocol-adapters)).
The slice layout and Spring Modulith `verify()` are checked from stage 4, when the first slice (`login`) appears,
over the migrated slices only ([wiring-layout.md §4](wiring-layout.md#verification)).

E6 is the step with the main risk: legacy code running on one thread may not fit the budget. E1 measured the legacy
load. The cutover landed on main before the E6 gate of the load harness ([spec 009 §8](../009-load-harness.md#8-gate)),
on the owner's decision, after two manual runs kept the world pass p99 within 1 ms. The gate is still its
acceptance: the world pass p99 must stay within budget under `load-crowd-pvp`, and an overrun is fixed by profiling,
not by returning to shared-pool threading.

## 7. Deferred

- Time dilation for overloaded partitions: [#20](https://github.com/l2nx/nx-l2/issues/20).
- Parallel open world if one partition exceeds its budget: [#21](https://github.com/l2nx/nx-l2/issues/21).
- Replication and client wire in a separate process (edge gateway): [#4](https://github.com/l2nx/nx-l2/issues/4).
- Stable script API for other emulators, which `ScriptContext` prepares: [#5](https://github.com/l2nx/nx-l2/issues/5).
