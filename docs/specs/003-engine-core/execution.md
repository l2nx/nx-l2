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
  1 ms after the previous pass started, and, when input is queued, the end of `pass-min`. One millisecond is the
  timer wheels' resolution, so the spacing makes no deadline fire later; it bounds the pass rate at 1000 per second, so a
  timer re-armed at the pass time or a backlog of overdue timers cannot run passes back to back.
- **Input latency is close to zero.** A packet is processed in the pass after it is decoded, not at the next tick
  boundary. A fixed tick of 100 ms or 50 ms would add up to that much to every action.
- **No catch-up.** A pass that overruns is followed by the next pass as soon as possible; missed periodic runs are
  not replayed in a burst (that is how loops spiral under load). Deadlines that fell due run in order (§4).

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
earlier; ordering input against due timers by arrival time removes this window ([#45](https://github.com/l2nx/nx-l2/issues/45)).

Phases 1–4 may enqueue further work for the same pass (a triggered skill, a death caused by a damage tick); the
loop drains it before phase 5, with a depth limit per pass (`trigger-depth`, default 8 generations) so a trigger
chain cannot loop forever. The stat recalculation of phase 4 runs after the drain, and again only after a drain of
work it enqueued itself, so it sees what that work changed. Work still queued once the limit is reached is counted
and logged as an overflow and runs first in the next pass; a chain that ends exactly at the limit is no overflow.

## 3. Inbox and session queues

- **One FIFO per client session.** Netty binds a channel to one IO thread, so a session has exactly one producer:
  decoded commands (and legacy packets during the migration) go into the session's bounded single-producer queue
  (JCTools `SpscArrayQueue`, capacity `inbound-queue-limit`; overflow closes the connection,
  [spec 008 §5](../008-network.md#5-inbound-pipeline)). The partition inbox (JCTools
  `MpscUnboundedAtomicArrayQueue`: the `MpscUnboundedArrayQueue` algorithm on field updaters, since the
  `sun.misc.Unsafe` variant warns on JDK 24 and later) receives only "this session has input".
- **A ready session is signalled once.** The producer enqueues, issues a full fence (`VarHandle.fullFence()`) and
  sets the session's signal flag, posting "has input" only when the flag was clear; the partition clears the flag
  once the queue is empty, issues a full fence and checks the queue again. A JCTools offer is only a release store,
  so without the two fences an item offered while the flag is cleared could stay in the queue unsignalled; with
  them either the producer sees the cleared flag or the partition sees the item. A session whose queue throws is
  reported and leaves the ready set until its producer signals again; the pass goes on.
- **Before the world partition exists (E6)** the queue is drained by the legacy packet pool, one drain per session
  at a time, so the order below already holds from stage 4.
- **Packets of one client are processed strictly in arrival order.** Mobius ran them on a shared executor where two
  packets of one client could execute concurrently or reorder (trade confirm against an item move, target change
  against attack); that class of race and duplication disappears.
- **Fairness and flood control:** the partition walks ready sessions round-robin and takes at most
  `packets-per-pass` (configurable, default 16) from each; the rest waits for the next pass. A flooding client delays only
  itself, and order is kept.
- **One failure costs one item.** A session queue only hands out its next item; the partition takes the item off the
  queue, records it and then runs it, so a handler that throws is reported with that item and the rest of the batch
  runs in the same pass. A taken item is gone whether it ran or failed, so it is never retried and a failing packet
  cannot keep the partition busy. Disconnecting a client whose packets keep failing is the session's decision, not
  the loop's.
- **Packets outside the world** (login key exchange, ping, character selection) are handled by the session state
  machine outside the partition, in order. A packet that arrives in the wrong state is dropped.
- **Entity transfer between partitions:** while a session is transferring, its queue keeps filling; after the
  target partition attaches the entity, the queue is drained there in the same order. Nothing is lost or reordered.
- **Coalescing is opt-in.** Only command types explicitly marked as intent, where only the latest matters (movement
  requests), may be collapsed; `nx-physics` already keeps the last intent. Every other command executes, and a
  legacy packet is never collapsed.
- **Order across clients** is the order of processing within a pass. It is deterministic and recorded (§9): when
  two players pick up the same item, the one processed first wins.

## 4. Timers

- **Two timer wheels per partition,** one for game-time and one for wall-clock deadlines (§6): Agrona
  `DeadlineTimerWheel` with a 1 ms tick, polled by the loop; no timer threads. The deadlines phase runs the game
  wheel, then the wall wheel. The wheel does not order timers inside one tick, so the loop collects the due timers
  of a pass and sorts them by `(due time, deadline, sequence)` before running them; the due time is the deadline,
  except for a timer scheduled at or before the last run (overdue timers below); inside one sequence `TICK` runs
  before `END` ([effects.md §4](effects.md#4-order-on-equal-deadlines)). A timer scheduled during a pass fires in a
  later pass. A wall clock set back never fires a deadline early.
- **The wheel holds one turn** (1024 ticks, about a second). A deadline in a later turn waits in that turn's
  bucket, which keeps its earliest due time, and the bucket enters the wheel whole when the cursor enters the turn;
  the occupied turns are ordered by a fastutil `LongHeapPriorityQueue`.
  The earliest due time the loop sleeps until is always a real one: an idle partition with timers minutes ahead
  runs no pass before the first of them.
- **Deadlines are absolute** in the partition's game time or wall-clock time (§6). Due timers run in
  `(due time, deadline, sequence)` order; the sequence keeps registration order for equal deadlines.
- **Periodic actions carry the remainder.** The next occurrence is `previous deadline + period`, not `now + period`.
  An attack every 333 ms stays 333 ms on average instead of drifting to the next pass boundary.
- **Cancellation is lazy.** A timer entry carries the target handle and a generation; when it fires, it is ignored
  if the target is gone or its generation changed. Mass cancellation (cleanse, death, logout) costs nothing on the
  wheel and does not use up the overdue cap.
- **Overdue timers** (deadline older than `pass-min` before the pass) execute in that order, up to a cap of overdue
  timers fired per pass and wheel (`overdue-per-pass`, default 4096); the rest keeps its order for the following pass,
  which is due 1 ms later rather than after `pass-min`. A timer scheduled at or before the time of the last deadline
  run keeps its deadline, so it counts as overdue when that deadline is older than `pass-min` before the pass, but its
  due time is 1 ms after that run, after every entry already due, so a handler re-arming at its own overdue deadline
  cannot starve later timers under the cap; the owed ticks of such a periodic timer keep that due time until they
  catch up with it. A wall clock set back lowers that time to just before the new clock at the start of the next
  pass, and timers scheduled late under the old clock become due at the new one.
- **No `ThreadPool.schedule` in game code** once a subsystem is migrated (E7). During migration the rule is: a task
  that touches world state is executed through the owning partition's inbox (E6).

## 5. Periodic systems

Systems that do work per entity regardless of input run on their own cadence, expressed in time, never in ticks:

| System          | Default cadence | Note                                                                 |
| --------------- | --------------- | -------------------------------------------------------------------- |
| player movement | 50 ms           | position is a function of time; checks for range, collision, arrival |
| NPC movement    | 100 ms          | arrival is a precise deadline, the cadence only re-checks paths      |
| visibility      | 100 ms          | recomputed for entities that crossed a cell, see replication.md      |
| AI think        | per AI type     | taken from the AI's own interval                                     |

Every cadence is a configuration property; the values above are starting points to be tuned with bot load tests.
The same holds for every other default in this spec.
No game logic counts ticks or passes.

Not periodic systems: routing outgoing events and coalesced state runs in every pass (replication phase), and
per-creature work with its own rhythm (regeneration, effect ticks, attack intervals) is a deadline per creature,
not a scan.

## 6. Clock and randomness

- **`GameClock` per partition** gives game time and wall-clock time. Every mechanic is classified:
  - **game time:** cooldowns, casts, attack intervals, effects, regeneration, movement;
  - **wall-clock:** siege start and registration, olympiad periods, auctions, daily and weekly resets, restart
    countdowns: anything a player checks against a real clock.

  Game time is monotonic (`System.nanoTime()` from process start) and is never persisted as an instant; how
  game-time state is stored is [persistence.md §4](persistence.md#4-write-behind). It starts at a fixed origin of
  2^32 ms, not 0, so the many legacy fields that hold 0 for "never" still read as long ago. Wall-clock time
  is epoch time with a zone (`java.time.Clock`). Today both run at the same rate; the split makes time dilation
  ([#20](https://github.com/l2nx/nx-l2/issues/20)) a clock policy rather than a redesign.

- **Legacy code not yet migrated** reaches the clock and RNG through a static bridge bound by a bean. Until the
  world partition exists (E6) the bridge returns one process-wide `GameClock`, and the RNG delegates to `ThreadLocalRandom`, since legacy code still runs on many
  threads. From E6 the bridge returns the clock and seeded RNG of the partition or global service whose loop owns
  the calling thread, and fails on any other thread. The bridge is removed when its last caller receives the clock
  and RNG from its system (E7, E10).

- **RNG per partition**, a seeded `java.util.random.RandomGenerator` (`L64X128MixRandom`). The seed is recorded
  (§9).
- **Forbidden in game code:** `System.currentTimeMillis()`, `System.nanoTime()`, `Instant.now()`,
  `LocalDateTime.now()`, `ZonedDateTime.now()`, `Calendar.getInstance()`, `new Date()`, `ThreadLocalRandom`,
  `Math.random()`, `new Random()`, `Collections.shuffle` without a generator. Step E5 replaces them and an ArchUnit
  test enforces the list in the game packages (`app.l2nx.highfive..`); only the chronicle's `clock` package is
  exempt there. Network, logging and metrics code lives outside the game packages, so the rule does not reach it.

## 7. Threads

| Thread               | Count                         | Touches                                                              |
| -------------------- | ----------------------------- | -------------------------------------------------------------------- |
| Netty IO             | Netty default                 | sockets, framing, cipher, decoders, session queues; never game state |
| Loop threads         | configurable, default = cores | partitions and global services; one owner per partition at a time    |
| Persistence executor | small pool, ordered per key   | database only; results return through inboxes                        |
| Spring / Tomcat      | framework                     | management endpoints; reach game state only through inboxes          |

- **A partition is pinned to one loop thread** for its lifetime, as a Netty channel is to its event loop: a loop
  thread runs the passes of the partitions assigned to it and sleeps (`LockSupport.parkNanos`) until the earliest
  inbox signal, deadline or periodic system among them. An offer to a sleeping loop's inbox wakes it; a loop that
  is awake is not woken again.
- **The world partition has a loop thread of its own;** instances, olympiad matches and global services share the
  others, assigned to the least loaded thread. A heavy partition therefore delays only partitions on its thread.

A loop thread that dies (an `Error`, a defect in the loop) stops its partitions, draining them and running their
shutdown hooks, and the server exits with code 1 so that its supervisor restarts it
([spec 002](../002-spring-boot-runtime.md)).

A thread that is not the owner of a partition never reads or writes its mutable state. Debug builds assert
ownership on entry to partition code.

## 8. Budget and metrics

- **Micrometer meters per partition:** pass duration (timer with p95, p99), time per phase and per periodic system,
  inbox depth, session queue depth, overdue timers, packets per pass.
- **A pass over budget** (`pass-budget`, configurable, default 50 ms) is counted and logged with the phase breakdown.
- **Baseline of the legacy execution (E1),** before partitions exist:
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
  step ([spec.md §6](spec.md#6-migration)).

## 9. Replay

A partition records its inputs: inbox messages and the items taken from session queues with their pass number and
order, the RNG seed and the clock readings per pass. An item is recorded when it is taken, before it runs, so one
that fails stays in the record in its place. A recorder failure is reported and the work still runs; the replay of that
pass is incomplete. Replaying the record against the same build reproduces the partition's behaviour, which turns
an incident into a test. The record is written with bounded retention. The passes of the shutdown drain run no
deadlines and no periodic systems, so the record marks them as drain passes.

## 10. Movement engine (`nx-physics`)

The movement engine already follows this model: time is passed in, network threads only submit the last intent,
state changes only in its step. To run inside the partition loop it needs:

- thresholds counted in ticks (stall detection, rescue after N rejects, N stalled steps) expressed in time;
- the step length as a parameter (the player movement cadence, §5) instead of a fixed 100 ms;
- its step called from the player movement system; its listeners (`onMoved`, `onEcho`, `onCorrect`, `onStop`)
  feed the replication phase.
