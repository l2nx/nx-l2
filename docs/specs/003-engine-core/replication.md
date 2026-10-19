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

- **Simulation never writes to sockets.** Systems append replication events to the entity's outbox: started moving,
  attacked, cast, appearance changed, status changed.
- **The replication phase,** once per pass:
  1. computes view diffs and emits spawn and despawn packets, spawns before any event about the object;
  2. routes each entity's events to the sessions whose view contains it;
  3. coalesces state: several HP changes in a pass become one `StatusUpdate`; `UserInfo` and `CharInfo` go once.

## 4. Packet classes

| Class              | Examples                                                                  | Rule                        |
| ------------------ | ------------------------------------------------------------------------- | --------------------------- |
| critical, ordered  | spawn, despawn, own state, damage to self, movement echo, system messages | always sent, in order       |
| coalescable state  | HP / MP / CP of others, stats, appearance                                 | latest value per pass       |
| droppable cosmetic | social actions and visual effects of distant objects                      | may be dropped under budget |

## 5. Priority and budgets

- **Byte budget per connection per pass,** configurable. Critical packets are never cut; coalescable state of
  low-priority objects is sent less often; cosmetic packets are dropped first.
- **Priority:** own target, own party, whoever attacks the player, then by distance. This is what keeps a siege with
  hundreds of players inside the network budget.

## 6. Network hand-off

- **A packet broadcast to many clients is serialized once;** each connection encrypts its copy with its own session
  key in the Netty encoder on its IO thread.
- **The partition hands Netty ready buffers without flushing** per packet; one flush per connection ends the pass.
- The transport itself (framing, cipher, server bootstrap) is `libs/network`
  ([spec 001 §4](../001-project-design.md#4-module-layout)).

## 7. Movement contract

Taken from the measured client behaviour in `nx-physics`:

- every accepted movement request is answered (silence lets the client simulate on its own);
- repeated echoes are deduplicated (about 200 ms), or the client shakes;
- observers see others only through `MoveToLocation`;
- a running client ignores `ValidateLocation`; correction goes through the move target.

## 8. Placement

Replication is a phase inside the process. Moving it with the client wire into a separate process is the edge
gateway ([#4](https://github.com/l2nx/nx-l2/issues/4)), taken only if profiles of sieges show the phase does not
fit the partition's budget.
