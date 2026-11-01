# 003 — Engine core: execution

How a partition runs: its loop, what wakes it, the order of work inside one pass, how client packets reach it in
order, how time and randomness enter, and which threads exist. Part of [spec 003](spec.md).

## 1. Partition loop

A partition is an event loop, not a fixed-rate tick.

- **It sleeps until the earliest of:** a message in its inbox, the next timer deadline, the next due periodic
  system.
- **Input has a minimum granularity** (`pass-min`, default 5 ms, configurable). Messages, session input and
  deferred work queued within `pass-min` of the previous pass wait for its end and are processed in one pass; this
  batches bursts, so a client flood never turns into hundreds of passes per second, without adding a fixed tick of
  latency. A due timer or periodic system does not wait for `pass-min`: its pass runs when due, but at most one per
  millisecond, and processes the queued input too. The next pass is the earlier of the next deadline, no sooner than
  1 ms after the previous pass started, and, when input is queued, the end of `pass-min`. Deadlines are whole
  milliseconds, so the spacing makes no deadline fire later; it bounds the pass rate at 1000 per second, so a
  timer re-armed at the pass time or a backlog of overdue timers cannot run passes back to back.
- **Input latency is close to zero.** A packet is processed in the pass after it is decoded, not at the next tick
  boundary. A fixed tick of 100 ms or 50 ms would add up to that much to every action.
- **No catch-up.** A pass that overruns is followed by the next pass as soon as possible; missed periodic runs are
  not replayed in a burst (that is how loops spiral under load), neither for a periodic system nor for a fixed-rate
  timer. Deadlines that fell due run in order (§4).

## 2. Pass phases

One pass runs, in this order:

1. **Inbox:** session input (§3), messages from other partitions and global services, database results.
2. **Deadlines:** due timers in `(deadline, sequence)` order (§4).
3. **Periodic systems** that are due (§5).
4. **Side effects:** the deferred queue built during phases 1–3 ([entities.md §4](entities.md#4-events-and-side-effects)),
   then stat recalculation of creatures marked dirty ([effects.md §6](effects.md#6-stats-once-per-pass)).
5. **Replication:** visibility diffs, outgoing events, coalesced state ([replication.md](replication.md)).
6. **Flush:** per-connection buffers handed to Netty, one flush per connection.

TODO: the inbox runs before the deadlines, so input is evaluated against timers that fell due up to one pass
earlier; ordering input against due timers by arrival time removes this window ([#45](https://github.com/l2nx/nx-l2/issues/45)),
after the E6 cutover and before E7 moves effects and control onto deadlines, where the window shows in play.

Phases 1–4 may enqueue further work for the same pass (a triggered skill, a death caused by a damage tick); the
loop drains it before phase 5, with a depth limit per pass (`trigger-depth`, default 8 generations) so a trigger
chain cannot loop forever. The stat recalculation of phase 4 runs after the drain, and again only after a drain of
work it enqueued itself, so it sees what that work changed. Work still queued once the limit is reached is counted
and logged as an overflow and runs first in the next pass; a chain that ends exactly at the limit is no overflow.

## 3. Inbox and session queues

- **One FIFO per client session.** Netty binds a channel to one IO thread, so a session has exactly one producer:
  decoded commands (and legacy packets during the migration) go into the session's bounded single-producer queue
  (JCTools `SpscAtomicArrayQueue`, the field-updater variant like the partition inbox, capacity
  `inbound-queue-limit`; overflow closes the connection, [spec 008 §5](../008-network.md#5-inbound-pipeline)). The
  partition inbox (JCTools
  `MpscUnboundedAtomicArrayQueue`: the `MpscUnboundedArrayQueue` algorithm on field updaters, since the
  `sun.misc.Unsafe` variant warns on JDK 24 and later) receives only "this session has input".
- **A ready session is signalled once.** The producer enqueues, issues a full fence (`VarHandle.fullFence()`) and
  sets the session's signal flag, posting "has input" only when the flag was clear; the partition clears the flag
  once the queue is empty, issues a full fence and checks the queue again. A JCTools offer is only a release store,
  so without the two fences an item offered while the flag is cleared could stay in the queue unsignalled; with
  them either the producer sees the cleared flag or the partition sees the item. A session whose queue throws is
  reported and leaves the ready set until its producer signals again; the pass goes on.
- **Who drains it.** An authenticated session is an `InputSource` of the world partition: its producer signals the
  world loop, and the partition drains it in the inbox phase. Before authentication (key exchange, login) the session
  keeps its own drain on the `l2nx-game-packets` executor, one drain per session at a time. Until the E6 cutover every
  session is drained that way, so the order below already holds from stage 4.
- **A session closing while the loop stops** reaches the partition as an inbox message, not as session input: the
  drain passes of a stopping loop take no input but run messages, so the close still runs logout and save. Once the
  loop has terminated, the close runs on the closing thread: no single writer is left to race it, and the network
  stop phase waits for every session to close.
- **Packets of one client are processed strictly in arrival order.** Mobius ran them on a shared executor where two
  packets of one client could execute concurrently or reorder (trade confirm against an item move, target change
  against attack); that class of race and duplication disappears.
- **Fairness and flood control:** the partition walks ready sessions round-robin and takes at most
  `packets-per-pass` (configurable, default 16) from each; the rest waits for the next pass. A flooding client delays only
  itself, and order is kept.
- **One failure costs one item.** A session queue only hands out its next item; the partition takes the item off the
  queue and then runs it, so a handler that throws is reported with that item and the rest of the batch
  runs in the same pass. A taken item is gone whether it ran or failed, so it is never retried and a failing packet
  cannot keep the partition busy. Disconnecting a client whose packets keep failing is the session's decision, not
  the loop's.
- **Packets before authentication** (key exchange, login) are handled by the session outside the partition, in order.
  From authentication on, character selection included, the session is an input source of the world partition
  (§7.1). What a packet needs from the game (a selected character, a character in the world) is checked when it
  executes, not when it is read, and the packet is rejected when it is missing, so a packet queued right behind the
  one that selects a character sees that character ([spec 008 §8](../008-network.md#session-states)); on the world
  loop that check reads the player on its owner thread.
- **Entity transfer between partitions:** while a session is transferring, its queue keeps filling; after the
  target partition attaches the entity, the queue is drained there in the same order. Nothing is lost or reordered.
- **Coalescing is opt-in.** Only command types explicitly marked as intent, where only the latest matters (movement
  requests), may be collapsed; `nx-physics` already keeps the last intent. Every other command executes, and a
  legacy packet is never collapsed.
- **Order across clients** is the order of processing within a pass. It is deterministic, which is what a recording would capture (§9): when
  two players pick up the same item, the one processed first wins.

## 4. Timers

- **Two timer queues per partition,** one for game-time and one for wall-clock deadlines (§6): a JDK
  `PriorityQueue` of entries ordered by `(deadline, sequence)`, polled by the loop; no timer threads. The sequence is a
  per-queue counter, so equal deadlines keep registration order. The deadlines phase runs the game queue, then the
  wall queue. A timer armed while the deadlines phase fires never fires in the same pass: its deadline becomes
  `max(requested, now + 1)`, with `now` the time of the running queue, so it sorts after every entry already due, a
  handler re-arming at its own deadline cannot loop inside one pass, and a handler re-arming into the past cannot
  starve the timers behind it under the overdue cap. The queue keeps no clock of its own: a wall clock set back only
  lowers `now`, and no deadline fires early.
- **The earliest deadline is exact:** the head of the heap. An idle partition with timers minutes ahead runs no pass
  before the first of them.
- **Why a heap, not a timer wheel.** The partition ran Agrona's `DeadlineTimerWheel` before (1 ms tick, one turn of
  1024 ticks, buckets per later turn, an occupancy bitmap to find the earliest deadline, a sort of each due tick). A
  wheel does not order timers within a tick and cannot tell its earliest deadline, which the loop needs to sleep
  until it; the heap gives both by construction in about 200 lines instead of 560. Measured with JMH
  (`TimerComparisonBenchmark`, a batch of 1 000 timers scheduled and fired in one 5 ms pass while 1 000 / 100 000 /
  1 000 000 respawn-like timers are pending; JDK 25 in Docker): the wheel build and the queue build ran
  interleaved in one session, two rounds each. In the quiet first round the queue cost 85 / 134 / 169 ns per timer
  in a batch against 581 (±1050) / 387 / 361 ns of the wheel; a timer scheduled and fired one at a time cost
  73 / 262 / 3 143 ns against 78 / 224 / 1 143 ns, so the heap is slower per single timer at a million pending
  ones (the O(log n) sift). The second round ran under CPU load from other processes and is not usable for
  figures. A differential test (random chains of 200 actions, including handlers that arm timers while timers
  fire, against a list-based reference model) checks the order on every build.
- **Deadlines are absolute** in the partition's game time or wall-clock time (§6).
- **Fixed-rate timers keep their phase and do not catch up.** After a run the next deadline is the first point of
  the period grid after the pass time: `deadline + period × ((now − deadline) / period + 1)`. An attack every 333 ms
  stays 333 ms on average instead of drifting to the pass boundary, and a pass late by several periods runs the timer
  once, not once per missed period. Game logic that cares for the number of missed runs derives it from time, as an
  effect does for its owed ticks ([effects.md §5](effects.md#5-periodic-ticks)).
- **Cancellation is lazy.** A cancelled entry is flagged and skipped when it reaches the head; when cancelled entries
  make up half the heap, the heap is rebuilt without them, so a timer re-armed on every event costs O(1) amortized
  and the heap stays bounded. An entry also carries the target handle and a generation; when it comes up, it is
  dropped if the target is gone or its generation changed, and that check comes before the overdue cap, so stale
  entries never use it up. A mass generation bump that must leave nothing behind in the queue's size and next
  deadline (the shutdown countdown) calls `cancelStale`, which cancels the stale entries of one handler at once; it
  is O(n) and meant for rare bumps.
- **Overdue timers** (deadline older than `pass-min` before the pass) execute in order, up to a cap of overdue timers
  fired per pass and queue (`overdue-per-pass`, default 4096); the rest keeps its place for the following pass, which
  is due 1 ms later rather than after `pass-min`. A timer scheduled outside the deadlines phase at a deadline in the
  past keeps it, sorts by it and counts as overdue when it is older than `pass-min` before the pass.
- **Legacy code schedules on the partition** from the E6 cutover; the Mobius `ThreadPool` and its pools are gone,
  and every call site is rewritten to the loop. The loop does not implement `ScheduledExecutorService` as a whole
  (shutdown, `invokeAll` and `submit(Callable)` have no meaning for a partition, and Spring's `TaskScheduler` works
  in `Instant`s of the wall clock, not in game time), but the handle it returns is the JDK type, as in Netty's
  `AbstractScheduledEventExecutor`:
  - `schedule(task, delay)` and `scheduleAtFixedRate(task, initialDelay, period)` arm a timer on the game queue at
    the current time plus the delay; a fixed-rate timer keeps its phase and skips missed runs like every periodic timer. The
    Mobius priority variant maps to `scheduleAtFixedRate`: one thread has no priorities.
  - They return a `java.util.concurrent.ScheduledFuture<Void>`, so legacy fields and `cancel`/`isDone`/`getDelay`
    calls stay as they are. `cancel(mayInterruptIfRunning)` ignores the interrupt flag, there is no other thread to
    interrupt; `getDelay` counts from the current time; `get()` of an unfinished task on the loop thread throws
    `IllegalStateException`, since it would wait for itself (Netty's `checkDeadLock` throws
    `BlockingOperationException` for the same reason); `get()` of a finished one returns at once.
  - The handle's state changes by compare-and-set, as Netty's scheduled task does: a timer about to run makes
    itself uncancellable first, so `cancel` returns true exactly when the task will not run again, from any thread.
    A periodic task cancelled while it runs finishes that run and is not run again, as in the JDK.
  - A fixed-rate task that throws is reported and keeps its schedule, unlike the JDK and Netty, which stop it: one
    failure costs one run, as one failing packet costs one item (§3). A regeneration or AI task stopping for good on
    one bad target would be a silent outage, and the failure is already counted by the partition's meters (§8).
  - `cancel` takes the timer out of the queue's count at once and out of the heap by the purge above, so a timer
    re-armed on every event does not pile up cancelled entries until their deadlines (the Mobius pools removed
    cancelled tasks too). The generation check above stays for the entity timers that are cancelled en masse.
  - `execute(task)` runs the task in the next pass, through the inbox, from any thread; it is not deferred work of
    the current pass, so long chains of legacy asynchronous work (`notifyEventAsync`) do not run into
    `trigger-depth`.
  - Called from another thread, `schedule` and the removal after `cancel` are offered to the inbox and applied by
    the loop, as Netty does; the handle is returned at once. The delay counts from the pass that applies the
    message: game time cannot be read off the loop (§6), and the difference is one pass.
  - Tasks still pending when the loop stops end cancelled, so `get()` throws `CancellationException` instead of
    waiting for good, as Netty cancels its scheduled tasks on termination; `schedule` on a stopped loop throws
    `RejectedExecutionException`.

  E7 then turns these timers into subsystem deadlines (effect deadlines, [effects.md §5](effects.md#5-periodic-ticks))
  and periodic systems (§5) one subsystem at a time. `java.util.Timer`, `TimerTask` and every other executor or thread are forbidden in game code by ArchUnit
  rules (`ScheduledWorkRules`): a timer thread dies silently on the first exception, and any other thread is a
  second writer. The rules name their two exceptions: the static data loader's parse pool and the bridge to
  background tasks (§7.1). Calls that block the thread (`Thread.sleep`, `Object.wait`, `Thread.join`,
  `LockSupport.park*`, `CountDownLatch.await`, `Semaphore.acquire*`, `Future.get`, `CompletableFuture.join`) are
  forbidden in game code by the same rules, since one of them freezes every pass of the world: a legacy "do, wait,
  do" sequence becomes a chain of loop timers, each step scheduling the next and keeping the handle where a later
  call cancels it. Waiting is left to the partition bridges and the GS-link shutdown, which run on Spring lifecycle
  threads. Rewriting the call sites instead of keeping `ThreadPool` as a delegating facade (the seam spec 004
  once kept for this step) is deliberate: one static entry point to the world loop instead of two, and no legacy
  type left between game code and the engine.

## 5. Periodic systems

Systems that do work per entity regardless of input run on their own cadence, expressed in time, never in ticks:

| System          | Name              | Default cadence | Property                       | Note                                                                            |
| --------------- | ----------------- | --------------- | ------------------------------ | ------------------------------------------------------------------------------- |
| player movement | `player-movement` | 50 ms           | `l2nx.movement.player-cadence` | position is a function of time; checks for range, collision, arrival            |
| NPC movement    | `npc-movement`    | 100 ms          | `l2nx.movement.npc-cadence`    | every other moving creature; arrival is re-checked at this cadence              |
| AI think        | `ai-think`        | 1 s             | `l2nx.ai.think-cadence`        | the think step of every attackable with an active AI, the Mobius think interval |
| visibility      | -                 | 100 ms          | -                              | from E8: recomputed for entities that crossed a cell, see replication.md        |

Every cadence is a configuration property (whole milliseconds); the values above are starting points to be tuned
with bot load tests. The same holds for every other default in this spec. No game logic counts ticks or passes.

A system is registered on its loop with a name and a cadence (`Loop.addSystem`); the name tags its meters
(`l2nx.engine.system{system}`, `l2nx.engine.system.skipped{system}`) and its failures, and its first run is one
cadence after the pass time. Each slice that owns a system installs it through a `WorldSystem` bean
(`partition.WorldSystem`); `WorldLifecycle` collects them and calls them in the startup pass, before the world loads,
so the slice stays free of the lifecycle. Today `ai-think` is installed by the `ai` slice
(`ai/system/AttackableThinkSystem`) and `player-movement` and `npc-movement` by the `movement` slice
(`movement/system/MovementSystems`); legacy callers that are not beans register through the root bridge
`movement.Movement`. Each system walks a copy of its entity set in insertion order, so a step that adds or removes
entities (a death, an arrival) is safe and the order of a pass is deterministic; a failing entity costs its own step,
logged, not the run of the others. A system outlives a world loop, so installing it clears the entities of the
previous one. Missed runs are skipped and counted in `l2nx.engine.system.skipped`, never replayed.

Not periodic systems: routing outgoing events and coalesced state runs in every pass (replication phase), and
per-creature work with its own rhythm (regeneration, effect ticks, attack intervals) is a deadline per creature,
not a scan.

## 6. Clock and randomness

- **`GameClock` per partition** gives game time and wall-clock time. Every mechanic is classified:
  - **game time:** cooldowns, casts, attack intervals, effects, regeneration, movement;
  - **wall-clock:** siege start and registration, olympiad periods, auctions, daily and weekly resets, scheduled
    restart times: anything a player checks against a real clock.

  Game time is monotonic (`System.nanoTime()` from process start) and is never persisted as an instant; how
  game-time state is stored is [persistence.md §4](persistence.md#4-write-behind). It starts at a fixed origin of
  2^32 ms, not 0, so the many legacy fields that hold 0 for "never" still read as long ago. Wall-clock time
  is epoch time with a zone (`java.time.Clock`). Today both run at the same rate; the split makes time dilation
  ([#20](https://github.com/l2nx/nx-l2/issues/20)) a clock policy rather than a redesign.

- **Legacy code not yet migrated** reaches the clock and RNG through a static bridge bound by a bean. Until the
  E6 cutover the bridge returns one process-wide `GameClock`, and the RNG delegates to `ThreadLocalRandom`, since
  legacy code still runs on many threads. From E6 the bridge answers by the calling thread:
  - **on a loop thread,** the time of the current pass (game and wall-clock, read once at pass start) and the seeded
    RNG of the partition or global service whose loop owns the thread. Every action of a pass sees one "now", and
    a recording of the pass needs only its two clock
    readings (§9). The startup pass (§7.1) is the exception: it loads the world for minutes, so it reads the clock live and a timer counts from the moment it is armed, not from
    the start of the load. Timers that fall due during the load are overdue when it ends and run in the following
    passes in their order, `overdue-per-pass` at a time;
  - **on any other thread,** wall-clock time only; game time and the RNG fail with `IllegalStateException`, since
    a caller off the loop that needs them is a second writer. Wall-clock time is not deterministic anyway, and a
    background task (§7) may stamp what it saves with it.

  The bridge is removed when its last caller receives the clock and RNG from its system (E7, E10).

- **RNG per partition**, a seeded `java.util.random.RandomGenerator` (`L64X128MixRandom`). The seed is logged at loop
  start and is part of what a recording needs (§9).
- **Forbidden in game code:** `System.currentTimeMillis()`, `System.nanoTime()`, `Instant.now()`,
  `LocalDateTime.now()`, `ZonedDateTime.now()`, `Calendar.getInstance()`, `new Date()`, `ThreadLocalRandom`,
  `Math.random()`, `new Random()`, `Collections.shuffle` without a generator. Step E5 replaces them and an ArchUnit
  test enforces the list in the game packages (`app.l2nx.highfive..`); only the chronicle's `clock` package is
  exempt there. Network, logging and metrics code lives outside the game packages, so the rule does not reach it.

## 7. Threads

| Thread               | Count                                                                                         | Touches                                                                   |
| -------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Netty IO             | Netty default                                                                                 | sockets, framing, cipher, decoders, session queues; never game state      |
| Loop threads         | one per partition and global service                                                          | the loop pinned to it                                                     |
| Persistence executor | small pool, ordered per key                                                                   | database only; results return through inboxes                             |
| Background tasks     | Spring Boot's auto-configured `TaskScheduler` and `applicationTaskExecutor`, platform threads | work with no game state (§7.1); results return through inboxes            |
| Loop watchdog        | 1                                                                                             | reads only the start time of the running pass and the loop thread's stack |
| Spring / Tomcat      | framework                                                                                     | management endpoints; reach game state only through inboxes               |

- **A loop is pinned to one loop thread** for its lifetime, as a Netty channel is to its event loop, and the thread
  runs that loop only: it sleeps (`LockSupport.parkNanos`) until the loop's next inbox signal, deadline or periodic
  system. An offer to a sleeping loop's inbox wakes it; a loop that is awake is not woken again.
- **Every partition and global service has a loop thread of its own,** so a heavy partition delays only itself.
  When E9 brings more partitions than cores, sharing threads comes back as a change of its own, with measurements.

A loop thread that dies (an `Error`, a defect in the loop) stops its partition, draining it and running its
shutdown hooks, and the server exits with code 1 so that its supervisor restarts it
([spec 002](../002-spring-boot-runtime.md)).

A thread that is not the owner of a partition never reads or writes its mutable state. Two checks enforce it:

- **Always on, in game state without locks.** Every public method of a migrated class (§7.2), constructors and
  `toString`/`equals`/`hashCode` aside, first checks that the calling thread owns the loop of the object: the world
  partition in E6, the service loop for a party or clan once E9 moves them out. Reads are checked as well as writes:
  a read of a plain collection while the owner writes it is a data race too. A violation throws at the caller,
  so the write does not happen, counts `l2nx.engine.ownership.violations` and logs the stack, at most once per call
  site and minute so a load run cannot flood the log. Without locks a missed second writer would corrupt state
  silently (duplicated or lost items); a failed task with a loud log is the lesser harm. This is the thread
  confinement check of the UI toolkits (Android `View.checkThread`, JavaFX `checkFxUserThread`); Netty's
  `inEventLoop()` is the same test, used there to hop threads instead of failing. The check is one reference
  comparison against `Thread.currentThread()`, a JIT intrinsic; its cost is measured by JMH. An ArchUnit rule
  checks that every public method of a migrated class calls it before any other call or field access. An abstract
  method has no body to check: its implementations carry the check once their own classes migrate, and until then
  an override in a subclass that is not migrated runs unchecked. A migrated class has no Lombok accessors, since a
  generated method cannot carry the check; they are written out.
- **Debug only, on the loop's own hot path.** The loop's owner-only methods (timers, systems, deferred work) assert
  the owner with Java `assert`, enabled (`-ea`) in tests and local runs.

Reads from other threads (meters, management endpoints) do not reach game state: they read values the partition
publishes once per pass (a flush hook writing a `volatile` field that a Micrometer gauge holds by a strong
reference), or they ask through the inbox. The online player gauge is the first of them.

**A pass that does not end** (an endless loop in a legacy script) would freeze the world without a word, since
`pass-budget` is checked when a pass ends. A watchdog thread reads the start time of the running pass and, past a
threshold (`l2nx.engine.blocked-pass-warning`, default 2 s), logs the loop thread's stack once per threshold, as
Vert.x's `BlockedThreadChecker` does for its event loops. Past a second threshold (`l2nx.engine.blocked-pass-exit`,
off by default) it exits the process through `ServerExit` so the supervisor restarts it; the world cannot save from
a frozen loop, so this trades the changes since the last save for availability, which is why it is off unless an
operator turns it on. The startup pass is not watched (it loads the world for minutes); during stop the watchdog
keeps logging a blocked drain pass or shutdown hook, such as a save stuck on the database, but no longer exits, since the process is
already stopping. Nothing in the JDK or Micrometer watches a running iteration of a loop, so this stays our own:
one thread and a few lines. It replaces the Mobius `DeadlockWatcher` and its `RestartOnDeadlock` setting, which have
nothing to find once the world holds no locks; the countdown restart cannot serve, since its timers run on the loop
that is stuck.

### 7.1 Work routed into the world partition (E6)

From the E6 cutover the world partition is the only thread that touches world state; every entry into it is
listed here, and anything else is a defect found by the ownership check.

- **Client input** of sessions in the world (§3). A session enters the world when the game server has
  authenticated it: character list, creation, deletion and selection load and create world objects, so they run on
  the world loop too. Only the key exchange and the authentication stay in the session's own drain.
- **Legacy scheduled and immediate work:** every former `ThreadPool` call site of game code and scripts uses the
  loop API of §4, including the task managers, which moved unchanged in E6; the AI think and movement ones are periodic systems since
  E7 (§5).
- **The day and night cycle:** a timer on the wall queue at the next change of the game day, computed as today from
  the local time of day (with daylight saving), instead of a thread polling every 100 ms. Game time restarts with
  the process, so it cannot carry a cycle that must survive a restart.
- **The shutdown and restart countdown:** game-queue timers that announce the remaining time, instead of a thread
  that sleeps between announcements. They sit on the game queue because the steps are delays from the start, which
  must not follow a step of the wall clock.
- **The GS-link:** messages from the login server (authentication results, kicks) arrive on gRPC threads and are
  offered to the inbox.
- **Background task results** and **management endpoints** that act on the world: offered to the inbox.
- **World loading.** Static data and world loading run as the first message of the world loop, before the game
  port opens (the start order in [wiring-layout.md §3](wiring-layout.md#3-lifecycle-and-configuration)), because
  legacy constructors arm timers and use the partition RNG. That pass is a startup pass: it reads the clock live
  (§6), its length is logged, and it is neither timed by the pass meters nor counted against `pass-budget`: a
  one-off pass of minutes would pin the pass maximum and histogram after every start, and Boot reports the start time
  itself (`application.started.time`). Parsing static data files in parallel
  is CPU work, not world work: the loader may fan it out on a bounded platform pool that lives only while it loads
  (`Executors.newFixedThreadPool` in try-with-resources); a parser builds immutable data and never arms a timer or
  draws a random number.

**Background tasks** are the explicit exception: work that never reads or writes game state and never reads game
time or the partition RNG runs on Spring Boot's
auto-configured `ThreadPoolTaskScheduler` for delayed and periodic work and `applicationTaskExecutor` (a
`ThreadPoolTaskExecutor`) for immediate work, sized by `spring.task.scheduling.pool.size` and
`spring.task.execution.pool.*`, reached by legacy code through a static bridge bound by a bean, as the clock is. A
background task hands any result back through the inbox, so a job that needs both is split: the custom mail poll
reads the database in the background and delivers in a world pass, the precautionary restart reads CPU and memory in
the background and decides on the loop, the captcha image set is drawn in the background with its own generator and
swapped in on the loop. Saves are not background tasks until E4: each reads live state the loop mutates, so they
stay on the loop (TODO below). The auto-configuration applies while the context has no
`TaskScheduler` or `ScheduledExecutorService` bean of its own and no other `Executor` bean (the persistence executor
of E4 is therefore not exposed as an `Executor` bean). Platform threads, not `spring.threads.virtual.enabled`: with
virtual threads Boot builds a `SimpleAsyncTaskScheduler`, whose fixed-rate runs start on a new thread each time and
may overlap, while a periodic task on `ThreadPoolTaskScheduler` never overlaps itself; and the property would also
switch Tomcat and every other executor of the process, a decision of its own.

TODO: drop once E4 moves JDBC off the loop ([#69](https://github.com/l2nx/nx-l2/issues/69)). Until write-behind
(E4), the legacy code's synchronous JDBC runs on the world loop with the rest of its work: saves (autosave, items,
quests, mail, clan ranks) and loads (character selection). It is a temporary state with a gate, not a design: a
meter (`l2nx.engine.jdbc.on.loop{loop}`, a datasource-proxy method listener, so pool waits and commits count with the
statements) times every JDBC call on a loop thread and a warning names the caller, the E0 siege gate measures its
cost, and if the world pass does not fit the budget with it, E4 comes before the cutover. E4 replaces each place
with a snapshot taken on the loop and a write on the persistence executor, or a load on the executor whose result
returns through the inbox ([persistence.md §4](persistence.md#4-write-behind)); then the meter reads 0, its code is
removed and the ArchUnit rule against JDBC on loop threads is switched on.

Packet parsing (`read()`) runs on the Netty IO thread before the packet is queued; it decodes bytes only and never
reads world state, which the cutover verifies for every legacy packet.

### 7.2 Locks removed from game state (E6)

With one writer the locks and concurrent collections of game state only cost time. They are removed class by
class, each step a change of its own and in this order: `World` and `WorldRegion` (object registry, visibility),
`Creature`, then its non-player line `Npc`, `Attackable`, `Playable` and `Summon` (`Playable` as the parent `Summon`
and `Player` share); `WorldObject` with every subclass below the migrated ones (the `instance` package, `Tower`,
`Vehicle`, the subclasses in scripts), since an override in a class that is not migrated runs unchecked, and with its
event listener base `ListenersContainer`, which templates, zones, residences, the olympiad and the global event
containers share, so their listener API is loop-only too; `EffectList` with its entries `BuffInfo`, `TimeStamp` and
`InvulSkillHolder`, state of the creature they sit on; `Item` with its `DropProtection` and the item containers
(`ItemContainer`, `Inventory`, `PlayerInventory`, `PetInventory`, `Warehouse`, `PlayerWarehouse`, `ClanWarehouse`,
`PlayerFreight`, `PlayerRefund`); `Player` with `PlayerWorldEntry`, the holders of a player's own state (shortcuts,
macros, contacts, block list, radar, duel, Nevit, auto-use and auto-play settings, subclasses, teleport bookmarks),
the `CreatureStat` and `CreatureStatus` hierarchies and the variables family (`AbstractVariables`, `PlayerVariables`,
`AccountVariables`, `ItemVariables`, `NpcVariables`, `GlobalVariablesManager`), which extended `StatSet` and now holds
one, since the public methods of a shared base cannot carry the check; `Party`, `CommandChannel`, `Clan` and `ClanMember`. Every
world entity is then a single writer. A class is
migrated when its methods carry the ownership check, runs show no violation, and its `synchronized` methods and
blocks, `Lock` fields, concurrent collections and atomics are gone (plain fastutil or JDK collections in their
place). Done: `World`, `WorldRegion`, `Creature`, `Npc`, `Attackable`, `Playable`, `Summon`, `WorldObject` with
`ListenersContainer`, `Vehicle`, `Tower`, the `instance` package, `EffectList` with `BuffInfo`, `TimeStamp` and
`InvulSkillHolder`, `Item` with `DropProtection` and the item containers, and `Player` with `PlayerWorldEntry`, its
holders, the stat and status hierarchies and the variables family. Their runs were the tests, client runs on the live
world and a JMH A/B of the hot paths (`WorldBenchmark` for visibility, `CreatureBenchmark` for zones, stat functions,
skills and reuse, `AttackableBenchmark` for the aggro list, `WorldObjectBenchmark` for positions and distances, `EffectListBenchmark` for adding,
stacking and stopping effects and their lookups, `ItemContainerBenchmark` for adding and removing items, lookups by
item and object id, the item snapshot and item counts, `VariablesBenchmark` for reading, writing and probing
variables); the
load-run check waits for the load harness to enter the world
([#68](https://github.com/l2nx/nx-l2/issues/68)), since a login storm that stops at `GAME_CONNECT` never touches it.
A caller that used to change a collection a getter handed out now calls a mutator or a lookup of the class (for
`Creature`: the attacker list, known relations, fake player drops, `getKnownSkill` instead of a scan; for
`EffectList`: `hasDispellableDebuff`, `isBlockedAbnormalType`, `collectStealable` and `collectDebuffs` instead of a
copied getter; for the item containers: `collectItems` with a filter for the item and warehouse list packets, the
paperdoll lookups for the expertise penalty, the pet weapon and the talisman mana, `getInventoryItemCount` for quest
item counts, `Item.hasElementals` and `elementalCount` instead of a copied attribute array; for `Player`: subclass,
friend, premium item, sell buff, manufacture and recipe lookups and counts, the auto-shot walks
`chargeAutoShots`/`disableAutoShots`, `forEachStatusListener` and the trait mutators of `CreatureStat`), so the
snapshot rule below costs no copy on those paths. The `StampedLock` around `Creature.doAttack` was also a re-entry
guard (`tryWriteLock` fails on the thread that holds it), so an attack ordered from inside an attack is still
dropped, by a plain flag. The stat getters check once each: a `public final` getter checks and calls protected unchecked internals that
the subclasses override, and the calculation is reached through an unchecked route the creature hands its stat
(`CreatureStat.CreatureAccess`), so the stat calculation measures faster than before the checks (19 ns to 15 ns);
the getters of `Creature` that delegate to its stat and a player's level lookup still pay a second check
([#156](https://github.com/l2nx/nx-l2/issues/156)). Every variables method checks too, about 2 ns per read
(tracked in [#146](https://github.com/l2nx/nx-l2/issues/146)). With `WorldObject`
checked, its accessors carry the check too, so a region switch over 500 objects per region, about 6 000 checks, runs
about twice as long (23 µs to 54 µs; a build with the check emptied measures as before); the same issue tracks it.

**Iteration without concurrent collections.** Legacy code removes and moves objects while it iterates them, which the
weakly consistent iterators of `ConcurrentHashMap` tolerated and a plain collection does not (a JDK iterator throws
`ConcurrentModificationException`, a fastutil one silently skips or repeats elements). So a migrated class hands out
only read-only snapshots of its collections, and walks a live collection itself only when the loop body is a pure
filter that calls nothing able to move an object; any walk that calls out (packets, AI, a consumer or a caller's
predicate) runs over a copy. These copies go into reusable per-depth scratch buffers (`ScratchBuffers`: one list per
nesting level, since a callback may query the world again, and an `int` list for primitive id sets, which a boxed
copy would allocate on every walk), so they allocate nothing in a steady state; no standard
library offers single-threaded scratch buffers, and Netty's `Recycler` pools objects per thread. Region sets are
fastutil `ObjectArraySet`, a dense array whose copy is one `arraycopy`; adding and removing scan it, which happens
only when an object enters or leaves a region. The visibility paths measured 7–48% faster than with
`ConcurrentHashMap`. The target design is to defer membership changes to a sync point of the pass instead of copying
([#122](https://github.com/l2nx/nx-l2/issues/122), E7): it changes the immediate-removal semantics legacy code
relies on.

**Iteration order is deterministic.** A replacement collection keeps an order that does not depend on the JVM run,
since ties in hate, the drop owner and seeded random picks over a list follow it, and replay (§9) needs the same
choice from the same inputs. Keys of game objects compare by `equals` (the object id, as the concurrent maps did),
never by identity: identity hash order changes on every run. Where order decides an outcome, the map is a linked one
(`Attackable`'s aggro list is an `Object2ObjectLinkedOpenHashMap` in insertion order). Effect lists keep insertion order, which the client shows and stacking relies on (a buff that
replaces a stacked one goes to the end). A pass that stops effects walks a copy, and an effect's exit may add a new
buff behind it, which the concurrent queues' iterators reached at their tail: such a pass repeats while exits add buffs. An item container keeps its items by object id in the order they were added, the order of the
client's item list; a refund that overflows destroys its oldest item. `Item` saved under a lock of its own, which the
loop-only saves of §7.1 made redundant. Within one listener container the
listeners of a type are notified highest priority first and, at equal priority, in registration order; a creature's
event then goes to its own, its template's and its kind's global container in that order. The `PriorityBlockingQueue`
these lists replaced iterated in heap order, so priority held only for its head.

A migrated class carries the annotation `@SingleWriter`, the one list both build checks read, so a lock cannot
return: an ArchUnit rule over annotated classes (fields of any `java.util.concurrent` type, futures and
synchronizers included, enums such as `TimeUnit` aside; `volatile` fields; creating or obtaining a concurrent
object; `Collections.synchronized*` wrappers; lock calls; public non-final fields, whose writes cannot carry a
check; and the ownership check of §7 as the first call; a `ScheduledFuture` field holding a timer handle of §4 is
allowed) and PMD 7 (7.29.0, on the main sources of `:chronicles:high-five:server`) with
`AvoidSynchronizedStatement` and `AvoidSynchronizedAtMethodLevel`, limited to annotated types by the standard
`violationSuppressXPath` property on both rule references (suppress unless an enclosing class, interface, enum or
record has the annotation; `hasAnnotation` needs the auxiliary classpath, which the PMD Gradle plugin passes). ArchUnit sees a `synchronized` modifier but not a
`synchronized` block, which compiles to monitor instructions it does not import; PMD reads the source and covers
both, so `synchronized` has one tool. The rest of the legacy locks (managers, scripts) go as E7 and E10 rework their
code.

## 8. Budget and metrics

- **Micrometer meters per partition:** pass duration (timer with p95, p99), time per phase and per periodic system,
  inbox depth, session queue depth, overdue timers, packets per pass.
- **A pass over budget** (`pass-budget`, configurable, default 50 ms) is counted and logged with the phase breakdown.
- **A running pass:** `l2nx.engine.pass.running{loop}`, a gauge of the seconds since the running pass started, 0
  between passes, since the pass timer records a blocked pass only once it ends.
- **Ownership violations** (§7): `l2nx.engine.ownership.violations`, a counter per partition; any value above zero
  is a defect.
- **Offers to a stopping loop:** an offer counts itself in flight, checks that the inbox has not terminated and
  enqueues; termination waits for the offers in flight. `true` therefore means the message will be processed, by a
  pass or by the stop drain; `false` means the inbox had terminated and the message was not taken. Messages left
  after the last drain (the drain gave up after its pass limit) are logged at WARN with their count; there is no
  meter, since the loop's meters are removed right after.
- **Baseline of the legacy execution (E1),** before partitions exist. The three task meters go with `ThreadPool` at
  the E6 cutover: from then on the same work is timed by the partition meters above (pass, phase, timers, failures),
  and the runbook compares the two. `l2nx.legacy.packet` stays while legacy packets exist:
  - `l2nx.legacy.task`: run time of every `ThreadPool` task, tagged with `pool` and the tag keys of Spring's
    `tasks.scheduled.execution`: `code.namespace` (the class that declares the task; a lambda, an anonymous or a
    local class counts as its declaring class, a JDK dynamic proxy is `proxy`, so labels stay stable across runs and builds), `outcome`
    (`SUCCESS`, `ERROR`), `exception` (simple class name, the binary name for an anonymous class, `none`); count, sum and max, no histogram;
  - `l2nx.legacy.task.all`: one histogram (p95, p99 via `histogram_quantile`) across all tasks, so the series stay
    bounded; a separate name, since a sentinel tag on `l2nx.legacy.task` would double every sum;
  - `l2nx.legacy.task.lag`: start delay of scheduled tasks against their planned time (fixed-rate: the previous
    plan plus the period), histogram per pool, read from the executor's own trigger time in
    `ScheduledThreadPoolExecutor.beforeExecute`. Neither Micrometer nor Spring has such a meter; `executor.idle`
    includes the planned delay and needs the `ExecutorServiceMetrics.monitor` wrapper that spec 002 rules out;
  - `l2nx.legacy.packet`: run time of every client packet, tagged with `type`, the packet's fully qualified class
    name as in [spec 008 §11](../008-network.md#11-metrics), and `outcome` and `exception` as `l2nx.legacy.task`;
    timed by the packet executor of `libs/network`, which knows only the class. From stage 4 measured by the
    session drain under the same name, next to `l2nx.session.command` for commands (tagged with the command type).
  - Histograms are enabled on the meters with a 1 µs minimum expected value (legacy tasks and lags take
    microseconds; the default 1 ms floor would put them all into the first bucket) and the default 30 s maximum;
    overridable by `management.metrics.distribution.*`. Queries and the recorded baselines:
    [runbook](../../runbooks/legacy-execution-baseline.md).
- **Bot load tests** with a siege scenario on the full local stack are the acceptance criterion for every migration
  step ([spec.md §6](spec.md#6-migration)). They run from a separate process (E0,
  [spec 009](../009-load-harness.md)); the E6 cutover lands on `main` only after its gate scenario passes
  ([spec 009 §8](../009-load-harness.md#8-e6-gate)), with the world pass p99 within `pass-budget`. A pass over budget is analysed with JDK Flight Recorder and fixed in the hot
  code; returning work to shared pools is not a fix.

## 9. Replay

Deferred design; nothing is recorded yet. A partition would record its inputs: inbox messages and the items taken
from session queues with their pass number and order, the RNG seed and the clock readings per pass. An item is
recorded when it is taken, before it runs, so one that fails stays in the record in its place. A recorder failure is
reported and the work still runs; the replay of that pass is incomplete. Replaying the record against the same build
reproduces the partition's behaviour, which turns an incident into a test. The record is written with bounded
retention. The passes of the shutdown drain run no deadlines and no periodic systems, so the record marks them as
drain passes.

TODO: recording and replay are designed and built together as one change
([#44](https://github.com/l2nx/nx-l2/issues/44)): replaying a production incident needs inputs that can be written out
(raw packet bytes or command records instead of objects and lambdas) and the partition state at the start of the
record (a checkpoint). A recorder interface the loop called with nothing behind it was removed rather than kept as a
seam. What replay depends on is in place from E6: the partition's seeded RNG, one recorded time per pass (§6) and the
deterministic order of a pass.

## 10. Movement engine (`nx-physics`)

The movement engine already follows this model: time is passed in, network threads only submit the last intent,
state changes only in its step. To run inside the partition loop it needs:

- thresholds counted in ticks (stall detection, rescue after N rejects, N stalled steps) expressed in time;
- the step length as a parameter (the player movement cadence, §5) instead of a fixed 100 ms;
- its step called from the player movement system; its listeners (`onMoved`, `onEcho`, `onCorrect`, `onStop`)
  feed the replication phase.
