# 003 — Engine core: execution

How a partition runs: its loop, what wakes it, the order of work inside one pass, how client packets reach it in
order, how time and randomness enter, and which threads exist. Part of [spec 003](spec.md).

## 1. Partition loop

A partition is an event loop, not a fixed-rate tick.

- **It sleeps until the earliest of:** a message in its inbox, the next timer deadline, the next due periodic
  system.
- **A pass has a minimum granularity** (`pass-min`, default 5 ms, configurable). Work that arrives inside that
  window is processed in the same pass; this batches bursts without adding a fixed tick of latency.
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

Phases 1–4 may enqueue further work for the same pass (a triggered skill, a death caused by a damage tick); the
loop drains it before phase 5, with a depth limit per pass (`trigger-depth`, default 8) so a trigger chain cannot
loop forever.

## 3. Inbox and session queues

- **One FIFO per client session.** Netty binds a channel to one IO thread, so a session has exactly one producer:
  decoded packets go into the session's single-producer queue (JCTools `SpscUnboundedArrayQueue`). The partition
  inbox (JCTools `MpscUnboundedArrayQueue`) receives only "this session has input".
- **Packets of one client are processed strictly in arrival order.** Mobius ran them on a shared executor where two
  packets of one client could execute concurrently or reorder (trade confirm against an item move, target change
  against attack); that class of race and duplication disappears.
- **Fairness and flood control:** the partition walks ready sessions round-robin and takes at most
  `packets-per-pass` (configurable, default 16) from each; the rest waits for the next pass. A flooding client delays only
  itself, and order is kept.
- **Packets outside the world** (login key exchange, ping, character selection) are handled by the session state
  machine outside the partition, in order. A packet that arrives in the wrong state is dropped.
- **Entity transfer between partitions:** while a session is transferring, its queue keeps filling; after the
  target partition attaches the entity, the queue is drained there in the same order. Nothing is lost or reordered.
- **Coalescing is opt-in.** Only packets explicitly marked as intent, where only the latest matters (movement
  requests), may be collapsed; `nx-physics` already keeps the last intent. Every other packet executes.
- **Order across clients** is the order of processing within a pass. It is deterministic and recorded (§9): when
  two players pick up the same item, the one processed first wins.

## 4. Timers

- **One timer wheel per partition:** Agrona `DeadlineTimerWheel`, polled by the loop; no timer threads.
- **Deadlines are absolute** in the partition's game time or wall-clock time (§6). Due timers run in
  `(deadline, sequence)` order; the sequence keeps registration order for equal deadlines.
- **Periodic actions carry the remainder.** The next occurrence is `previous deadline + period`, not `now + period`.
  An attack every 333 ms stays 333 ms on average instead of drifting to the next pass boundary.
- **Cancellation is lazy.** A timer entry carries the target handle and a generation; when it fires, it is ignored
  if the target is gone or its generation changed. Mass cancellation (cleanse, death, logout) costs nothing on the
  wheel.
- **Overdue timers** (a pass that ran late) execute in deadline order in the next pass, up to a per-pass cap (`overdue-per-pass`, default 4096); the
  remainder continues in the following pass.
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

  Today both run at the same rate; the split makes time dilation ([#20](https://github.com/l2nx/nx-l2/issues/20))
  a clock policy rather than a redesign.

- **RNG per partition**, a seeded `java.util.random.RandomGenerator` (`L64X128MixRandom`). The seed is recorded
  (§9).
- **Forbidden in game code:** `System.currentTimeMillis()`, `System.nanoTime()`, `Instant.now()`,
  `ThreadLocalRandom`, `Math.random()`, the Mobius `Rnd`. Step E5 replaces them mechanically.

## 7. Threads

| Thread               | Count                         | Touches                                                           |
| -------------------- | ----------------------------- | ----------------------------------------------------------------- |
| Netty IO             | Netty default                 | sockets, codec, cipher, session queues; never game state          |
| Loop threads         | configurable, default = cores | partitions and global services; one owner per partition at a time |
| Persistence executor | small pool, ordered per key   | database only; results return through inboxes                     |
| Spring / Tomcat      | framework                     | management endpoints; reach game state only through inboxes       |

A thread that is not the current owner of a partition never reads or writes its mutable state. Debug builds
assert ownership on entry to partition code.

## 8. Budget and metrics

- **Micrometer meters per partition:** pass duration (timer with p95, p99), time per phase and per periodic system,
  inbox depth, session queue depth, overdue timers, packets per pass.
- **A pass over budget** (`pass-budget`, configurable, default 50 ms) is counted and logged with the phase breakdown.
- **Bot load tests** with a siege scenario on the full local stack are the acceptance criterion for every migration
  step ([spec.md §6](spec.md#6-migration)).

## 9. Replay

A partition records its inputs: inbox messages with their pass number and order, the RNG seed and the clock
readings per pass. Replaying the record against the same build reproduces the partition's behaviour, which turns
an incident into a test. The record is written with bounded retention.

## 10. Movement engine (`nx-physics`)

The movement engine already follows this model: time is passed in, network threads only submit the last intent,
state changes only in its step. To run inside the partition loop it needs:

- thresholds counted in ticks (stall detection, rescue after N rejects, N stalled steps) expressed in time;
- the step length as a parameter (the player movement cadence, §5) instead of a fixed 100 ms;
- its step called from the player movement system; its listeners (`onMoved`, `onEcho`, `onCorrect`, `onStop`)
  feed the replication phase.
