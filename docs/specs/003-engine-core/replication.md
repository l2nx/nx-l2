# 003 — Engine core: replication

How simulation results reach clients. Part of [spec 003](spec.md).

A Lineage 2 client does not take world snapshots. It learns objects through spawn packets (`CharInfo`, `NpcInfo`,
`SpawnItem`, ...), forgets them through `DeleteObject`, and between the two receives events: movement
(`MoveToLocation`), attacks, casts, status updates. Replication is therefore an exact mirror of what each client
knows, plus the routing of events to the clients that know the object.

Mobius has no known lists: visibility is computed per query by scanning neighbouring 2048×2048 regions, and
`broadcastPacket` writes to sockets immediately from whatever thread runs. Each HP change is its own packet.

## 1. Client view

- **Each session keeps the set of handles currently spawned on its client.** It is the source of truth for spawn and
  despawn; a client can neither keep a phantom object nor miss one.
- The view changes only in the replication phase, from the visibility diff below.

## 2. Visibility grid

- **A uniform grid per partition.** Cell size and visibility radii are configuration properties; a cell is of the
  order of the visibility radius.
- **Only entities that crossed a cell boundary** (and their neighbours) get their visibility recomputed, on the
  replication cadence ([execution.md §5](execution.md#5-periodic-systems)). Objects that stand still cost nothing.

## 3. Outgoing events

- **Simulation never writes to sockets.** Systems append view events to the entity's outbox: started moving,
  attacked, cast, appearance changed, status changed.
- **The replication phase,** once per pass:
  1. computes view diffs and emits spawn and despawn events, spawns before any event about the object;
  2. routes each entity's events to the sessions whose view contains it;
  3. coalesces state: several HP changes in a pass become one status change; the player's own state and its
     appearance to others go once.
- **Replication emits view events, not packets.** Each session's `ClientPort` hands them to the session's
  protocol adapter, which maps them to packets and serializes them
  ([spec 008 §8](../008-network.md#8-ports-and-protocol-adapters)). The classes below are properties of the
  events; packet names in this document are those of the High Five protocol.

## 4. Event classes

| Class              | Examples                                                                  | Rule                        |
| ------------------ | ------------------------------------------------------------------------- | --------------------------- |
| critical, ordered  | spawn, despawn, own state, damage to self, movement echo, system messages | always sent, in order       |
| coalescable state  | HP / MP / CP of others, stats, appearance                                 | latest value per pass       |
| droppable cosmetic | social actions and visual effects of distant objects                      | may be dropped under budget |

## 5. Priority and budgets

- **Byte budget per connection per pass,** configurable, default 32 KB. Bytes exist only after the adapter maps an
  event, so the phase orders a session's events by class and priority, the adapter maps and serializes them in that
  order, and the budget cuts after serialization: critical events are never cut; coalescable state of low-priority
  objects is sent less often; cosmetic events are dropped first.
- **Priority:** own target, own party, whoever attacks the player, then by distance. This is what keeps a siege with
  hundreds of players inside the network budget.

## 6. Network hand-off

- **Events and legacy packets emitted earlier in the pass are buffered per session** and released by the
  replication phase in emission order, after the spawns of the pass; the phase then schedules each connection's
  outbound drain once, so a pass ends with one flush per connection. Serialization once per audience, encryption on the
  IO thread and buffer ownership are [spec 008 §6](../008-network.md#6-outbound-path).

## 7. Movement contract

Taken from the measured client behaviour recorded in the `nx-physics` repository (its `docs/specs`, specs 001–008:
click and keyboard movement, follow, validation cadence, start ramp, divergence):

- every accepted movement request is answered (silence lets the client simulate on its own);
- repeated echoes are deduplicated (about 200 ms), or the client shakes;
- observers see others only through `MoveToLocation`;
- a running client ignores `ValidateLocation`; correction goes through the move target.

## 8. Placement

Replication is a phase inside the process. Moving it with the client wire into a separate process is the edge
gateway ([#4](https://github.com/l2nx/nx-l2/issues/4)), taken only if profiles of sieges show the phase does not
fit the partition's budget.
