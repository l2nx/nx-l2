# 003 — Engine core: effects

Buffs, debuffs, toggles, dances, triggered and passive effects on the partition loop. Part of [spec 003](spec.md).

In Mobius, a shared task scans every active buff once a second and counts its seconds, so a buff lasts between N and
N+1 seconds and ends on a pool thread; every periodic effect has its own fixed-rate task; the effect list is six
concurrent queues mutated from packets, AI, timers and combat on different threads. All of that is replaced.

## 1. Ownership

- **The effect list is a component of its creature,** mutated only on the owning partition thread. Plain arrays or
  lists replace the concurrent queues; there are no locks.
- **An effect refers to its effector by handle.** If the effector or the target is gone, the effect does nothing
  that needs them (no reflected damage to a despawned caster).

## 2. Time

- **Absolute deadlines in game time:** an active effect has `endsAt`; a periodic effect has `nextTickAt` and its
  period (`ticks × effect tick period` from the skill data).
- **Durations are exact.** A 20-minute buff lasts 20 minutes, not "20 minutes plus up to a second".
- **No per-effect tasks and no global scan.** Both deadlines are entries on the partition's game timer queue
  ([execution.md §4](execution.md#4-timers)).

## 3. Timer entries

- An effect owns one entry at a time, `(creature handle, effect id, generation)`, at its next tick or its end,
  whichever comes first.
- **Cancellation is lazy:** removing an effect bumps its generation; a stale entry is dropped when it comes up. A
  cleanse that removes twenty effects costs twenty generation bumps.
- **Insertion is O(log n)** on the queue's binary heap: an area buff on hundreds of players in a siege inserts
  hundreds of entries at a logarithmic cost each.

## 4. Last tick and end

A tick and the end never compete for one deadline: the effect's single entry fires once, applies every tick due by
then, and ends the effect when `endsAt` has come. The last damage tick of a poison is therefore applied before the
poison ends. Across effects, `(deadline, sequence)` order applies.

## 5. Periodic ticks

- **The next tick is `nextTickAt + period`,** so ticks do not drift with pass timing.
- **Owed ticks are effect logic, not timer logic.** The effect arms a one-shot deadline at `nextTickAt`. When it fires
  at `now`, the effect computes `owed = min(cap, (now - nextTickAt) / period + 1)`, applies that many ticks in order,
  moves `nextTickAt` by `owed x period`, ends when `endsAt` is reached and otherwise arms the next deadline at
  `min(nextTickAt, endsAt)`. Damage and healing owed by a late pass are applied, not lost; the cap bounds one firing.
  The timer core has no notion of ticks: fixed-rate timers skip missed runs ([execution.md §4](execution.md#4-timers)).
  Code lands with the E7 effects.
- **Toggles and dances** consume MP per tick through the same mechanism; a tick that cannot pay ends the effect.

## 6. Stats once per pass

- Adding or removing an effect marks the creature's stats dirty. Recalculation (the stat calculator), `UserInfo` /
  `CharInfo` and `AbnormalStatusUpdate` happen once per pass in the side-effect phase.
- A buffer applying twenty buffs costs one recalculation and one update instead of twenty of each, which matters
  most in sieges.

## 7. Reactive effects

- **On-hit, on-skill, reflect, vampiric, triggered skills by chance** are called synchronously from the combat or
  casting system while it handles the hit or cast, in a fixed order. They are deterministic for a given RNG state.
- **A skill a trigger casts** is queued as work of the same pass ([execution.md §2](execution.md#2-pass-phases)),
  not executed recursively; the `trigger-depth` limit ([execution.md §2](execution.md#2-pass-phases)) stops
  trigger chains.
- **Auras, cubics and channelized skills** run on the same deadlines; their targets are found through the partition
  registry and visibility at the moment of the tick.

## 8. Partition transfer

When a creature moves to another partition ([entities.md §6](entities.md#6-entity-transfer)), the effect list moves
with it. Each `endsAt` and `nextTickAt` is converted to the remaining duration in the source clock and re-anchored to
the target clock, and the entries are registered on the target partition's
queue.

## 9. Persistence

On save, an effect is stored with its remaining duration (`endsAt − now` in game time) and its tick phase; on load,
deadlines are rebuilt from them, so an effect does not run down while the player is offline. Effects that do not
survive logout are not stored. Effects that must keep running in real time while their owner is offline:
[#31](https://github.com/l2nx/nx-l2/issues/31).

## 10. Global effects

Effects that come from global state (clan skills, siege and territory effects, world events) are owned by the
global service. It publishes the state as a snapshot; partitions apply or remove the effects on their own entities
when they receive the change.

## 11. Effect handlers

The effect handler API stays: `onStart`, `onActionTime` (one tick), `onExit`, called on the partition thread. The
handlers in `scripts` change only where they schedule their own tasks on the loop (`schedule`, from the E6 cutover); those become deadlines.
