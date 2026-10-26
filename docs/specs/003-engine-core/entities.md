# 003 — Engine core: entities, events, global services

How game objects are modelled, how they refer to each other, how static data is held, how events and side effects
flow inside a partition, and how state shared by several partitions is owned. Part of [spec 003](spec.md).

## 1. Entity model

- **Objects stay, composed of components.** `Player`, `Npc`, `Item` and the other entity classes remain the
  facades scripts call. Behind a facade the state is split into components owned by systems: movement, stats,
  status (HP, MP, CP), effects, inventory, AI, and so on. The 15k-line `Player` shrinks into its components over the
  migration; its public surface for scripts stays.
- **Components are data, systems hold the logic.** A component is mutated only by its system, on the partition
  thread. Cross-component logic lives in systems, not in the facade.
- **Data-oriented storage only where measured.** A hot path the profiler points at (positions for visibility,
  timers, combat stats in sieges) may move into primitive arrays or structure-of-arrays storage behind its
  component's API (fastutil collections). Nothing is moved on speculation.
- **No entity-component-system framework.** See [spec.md §5](spec.md#5-rejected-alternatives).

## 2. Handles and registries

- **Every live entity has a wire handle** (`int`, [persistence.md §2](persistence.md#2-wire-handles)). The partition
  keeps a registry `handle → entity` (fastutil `Int2ObjectOpenHashMap`).
- **Entities refer to each other by handle,** not by Java reference: target, attacker, effector, summon owner,
  party members. A lookup that misses means the entity left the partition or despawned; the caller treats it as
  gone. This removes dangling references after despawn and accidental shared memory across partitions.
- **Persistent identity** (UUIDv7) is used only for persistence and for the outside world (adapter, web), never on
  the hot path.

## 3. Static data

- **Templates and game data are immutable registries:** skills, items, NPCs, zones, multisells and the like, each a
  Spring bean holding its current snapshot in an `AtomicReference`.
- **Reload builds a new snapshot off-thread and swaps it.** Partitions pick up the new reference at the start of a
  pass, so one pass never sees two versions.
- **Instances keep a template id,** not a reference to a template object; they resolve it through the current
  snapshot. Reloading a template therefore applies to live instances without walking them.

## 4. Events and side effects

- **Synchronous typed dispatch inside a partition.** The Mobius event system (89 event types, script listeners)
  stays as the mechanism and runs on the partition thread. An event object is built only if its type has a
  listener (the existing `hasListener` guards), so idle event types cost nothing.
- **No asynchronous events in process.** `notifyEventAsync` and `notifyEventAsyncDelayed` go away (E7; from the E6
  cutover until then they run as the loop's `execute` and `schedule`): a delayed reaction is a timer deadline, a
  reaction in another partition or service is a message.
- **Side effects are deferred to one place.** Death, drop, rewards, experience, karma, packets to third parties are
  queued during a pass and executed in the side-effect phase ([execution.md §2](execution.md#2-pass-phases)) in a
  fixed order. Systems stay free of each other's consequences, and the order is deterministic. Work deferred by
  deferred work runs after its generation in the same phase, FIFO, up to `trigger-depth` generations.
  - The first consumer (E7) is an attackable's death: the death itself stays synchronous, and in the side-effect phase
    of the same pass its drop, then its event drop, then its experience, SP and vitality run, then the kill
    notification (`OnAttackableKill`). They read a snapshot taken at the death (damage per attacker, over-hit share,
    maximum HP, champion flag, vitality rate), because the rest of the death, or a script that deletes the corpse in
    the same stack, clears the aggro list and the over-hit and stops the effects that raise the maximum HP, and a
    corpse hit again in the same pass would add damage. Two things are evaluated when the phase runs, not at the
    death: the party range of each attacker and whether the attacker is in the corpse's surrounding region (the
    region itself comes from the snapshot). The listeners' `onKillDelay` (2.5 s) counts from the side-effect phase,
    so a listener sees the drop and the rewards done.
- **Events to the outside** (adapter, Kafka) for value operations go through the transactional outbox
  ([persistence.md §5](persistence.md#5-outbox)); other events are published after the pass.

## 5. Global services

State that spans partitions has its own single writer:

| Service  | Owns                                                    |
| -------- | ------------------------------------------------------- |
| clan     | clans, alliances, members, clan skills, clan warehouse  |
| party    | all parties: membership, leader, loot rules             |
| olympiad | registration, points, match making, periods             |
| siege    | castle and fortress ownership, siege schedule and state |
| mail     | mail and attachments                                    |
| auction  | auctions and bids                                       |
| events   | scripted world events with global state                 |

- **Each service is a loop with an inbox**, the same mechanism as a partition, without space.
- **Partitions send commands, services answer with results.** "Player X joins clan Y" goes to the clan service; the
  result returns to the player's partition inbox, which updates the player and the client.
- **Reads go through immutable snapshots.** A service publishes a new snapshot after a change; partitions read it
  without locks (the clan name over a character's head, castle owners).
- **Global script listeners** (`Containers.Global` today) run in the loop of their home service, not on the thread of
  whichever partition raised the event; the event is delivered there as a message.

## 6. Entity transfer

Moving an entity between partitions (entering a reflection, an olympiad match, leaving them):

1. the source partition detaches the entity at a pass boundary: removes it from its registry, visibility and timer
   queue (timer entries lapse by generation), and serializes nothing: the object moves as is;
2. remaining time of its deadlines is converted to durations ([effects.md §8](effects.md#8-partition-transfer));
3. a transfer message carries the object to the target inbox; the target attaches it, re-registers its deadlines
   against its own clock and adds it to visibility;
4. the session queue follows ([execution.md §3](execution.md#3-inbox-and-session-queues)).

Between steps 1 and 3 no thread owns the object except through the message, so there is no shared access.

## 7. Scripts

- **Scripts receive a `ScriptContext`:** a facade over the beans they need (data registries, services, spawn, timers,
  messaging). They are not beans, do not see Spring, and do not call `getInstance()`
  ([#5](https://github.com/l2nx/nx-l2/issues/5) requires a script API that does not need a Spring context).
- **Script callbacks run on the partition thread** of the entity concerned, or on their home service for global
  listeners. `startQuestTimer` becomes a partition deadline.
- **Script state is partition- or service-confined.** Static mutable fields shared across partitions are not
  allowed in migrated scripts.
