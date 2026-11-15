# 008 — Network

The transport for every connection (client↔game, client↔login, login↔game) and the multi-protocol design: how a
client protocol becomes an adapter in front of a game that knows no packets. Stage 4 of
[spec 001 §11](001-project-design.md#11-stages); the engine side (session queues, partitions, replication) is
[spec 003](003-engine-core/spec.md).

## 1. Principles

- **Netty for the client protocols, gRPC between our own servers.** The game client's protocol is the one fixed
  contract and runs on Netty with our framing and ciphers; the login↔game link is ours on both ends, so it uses the
  industry standard for service-to-service calls, gRPC over HTTP/2 through Spring Boot's gRPC support (§10). The
  Mobius NIO2 stack and the blocking GS-link sockets are removed when stage 4 ends.
- **The game knows no protocol.** Game code receives commands and emits view events through ports; a client
  protocol is an adapter that maps packets to commands and view events to packets (§8). Adding a protocol adds
  mappers, not game code.
- **IO threads never touch game state.** They frame, decrypt, decode into commands and hand them to the session
  queue; they encrypt and write what the game serialized.
- **Netty defaults and idioms first:** pooled buffers, `EmbeddedChannel` tests, leak detection, the 4.2 IO API.
  Custom code only for Lineage 2 framing, ciphers and packets.

## 2. Layout

| Module / package                                                      | Holds                                                                                                                                |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| `libs/network` (`app.l2nx.network`)                                   | bootstraps, transport selection, framing, cipher contract and handler, frame encoder, limits, metrics, the cipher primitives         |
| `libs/logging` (`app.l2nx.logging`)                                   | the rate-limited log every server, the network library and the load bots share                                                       |
| `protocols/{login,game}/<id>` (`app.l2nx.protocol.{login,game}.<id>`) | one wire protocol: opcodes, wire records with their codecs, the protocol's keys and frame cipher (§8 Wire codecs)                    |
| `engine/core` `session`                                               | session queue, session states, command dispatch, `ClientPort` ([wiring-layout §4](003-engine-core/wiring-layout.md#4-layout))        |
| `login/server` `protocol/<variant>/`                                  | the login server's side of one login protocol: handshake, decoder, a handler per client packet, accepted states                      |
| `libs/gslink` (`app.l2nx.gslink`)                                     | the GS-link contract: `.proto` (`l2nx.gslink.v1`), generated stubs, the shared-secret interceptors, `l2nx.gslink` settings           |
| `login/server` `gslink`                                               | login side of the GS-link: the gRPC service and one stream adapter per game server (`GameServerLink`)                                |
| `login/server` `gameserver`, `account`                                | the registration policy of game servers (`GameServerRegistry`) and the account rules of the login and the GS-link (`AccountService`) |
| `chronicles/<c>/server` `login`                                       | the game server's side of the login server as a slice: online accounts, server listing, `LoginPort`, the login commands              |
| `chronicles/<c>/server` `network`                                     | game pipeline, version handshake, protocol registry, `gslink/` (the gRPC client of the GS-link, adapter of `LoginPort`)              |
| `chronicles/<c>/server` `network/protocol/<id>/`                      | one client protocol adapter: wire records to commands, view events to wire records                                                   |

`libs/network` knows no game rules and no protocol; it knows bytes, frames and the Lineage 2 cipher primitives
(LE Blowfish, rolling XOR, XOR checksum). The keys, opcodes and layouts of a protocol live in its `protocols/` module, which depends on `libs/network` and Netty buffers only, so the load harness uses it as the server does
([spec 009](009-load-harness.md)).

## 3. Configuration

New settings are Spring Boot properties under `l2nx.network`, shared by both servers through
`@ConfigurationProperties` in `libs/network`; they replace `l2nx.login.network.*`. The game client port stays where
the server reads it today (login: `l2nx.login.*`; game: `.ini` until [#17](https://github.com/l2nx/nx-l2/issues/17)).
The GS-link is configured by Spring Boot's gRPC properties (`spring.grpc.server.*` on the login server,
`spring.grpc.client.channel.login.*` on the game server: address, TLS through an SSL bundle, keepalive) plus
`l2nx.gslink` (§10).

| Property                               | Default        | Meaning                                                                                                                   |
| -------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `transport`                            | `auto`         | `auto`, `epoll`, `nio`, `io-uring` (§4)                                                                                   |
| `worker-threads`                       | Netty default  | IO threads per process                                                                                                    |
| `max-frame`                            | 16 KB          | largest inbound frame; larger closes the connection                                                                       |
| `read-timeout`                         | 120 s          | a connection silent before `AUTHED` (login: before auth, §9) is closed                                                    |
| `ping-interval`                        | 30 s           | from `AUTHED`, read idle this long sends `NetPing`                                                                        |
| `ping-misses`                          | 3              | unanswered pings in a row that close an authenticated connection                                                          |
| `handshake-timeout`                    | 10 s           | time from accept to the protocol version (game) or `AuthGameGuard` (login)                                                |
| `auth-timeout`                         | 60 s           | time from accept to a completed login or game authentication                                                              |
| `inbound-queue-limit`                  | 256            | session queue capacity (§5)                                                                                               |
| `packets-per-drain`                    | 16             | items one session drain run executes before the session is `AUTHED` (§5)                                                  |
| `max-concurrent-drains`                | 32             | session drains of sessions not yet `AUTHED` running at once (§5)                                                          |
| `packets-per-second`                   | 200            | sustained inbound packets per session, at most 10^9 (Bucket4j's fastest refill); burst = `inbound-queue-limit`            |
| `decode-error-limit.count` / `.window` | 10 / 60 s      | decode errors after authentication, and failed commands or packets, tolerated per session before the connection is closed |
| `write-buffer-low` / `-high`           | 32 KB / 128 KB | Netty write watermarks                                                                                                    |
| `outbound-limit`                       | 1 MB           | pending outbound bytes per connection (queue plus channel buffer)                                                         |
| `slow-client-timeout`                  | 10 s           | longest time a connection may stay unwritable                                                                             |
| `connections-per-ip`                   | 10             | concurrent connections from one address                                                                                   |
| `accept-per-ip-per-second`             | 5              | new connections from one address                                                                                          |
| `max-connections`                      | 10000          | concurrent connections per server                                                                                         |
| `shutdown-quiet-period` / `-timeout`   | 2 s / 15 s     | `shutdownGracefully` of the event loop groups                                                                             |

## 4. Event loops and transports

- **Netty 4.2 IO API:** `MultiThreadIoEventLoopGroup` with an `IoHandlerFactory`; the version comes from the Spring
  Boot BOM, the native transports (epoll for x86_64 and aarch64, io_uring) from the version catalog.
- **Transport:** `auto` uses epoll when `Epoll.isAvailable()` and NIO otherwise, logging why it fell back. io_uring
  is opt-in because the default Docker seccomp profile blocks its system calls.
- **Groups:** one boss thread and one worker group per process, shared by every bootstrap in it.
- **Allocator:** Netty's default (adaptive, pooled). Buffers allocated on non-Netty threads (serialization, §6) use
  the same allocator; their thread caches are bounded by the allocator's defaults.

## 5. Inbound pipeline

Game connection, in order:

1. **Accept filter:** bans and `connections-per-ip`, `accept-per-ip-per-second`, `max-connections`, checked in
   `initChannel` before any byte is read.
2. **Frame decoder:** `LengthFieldBasedFrameDecoder`, little-endian, 2-byte length that counts itself
   (`lengthAdjustment = -2`, `initialBytesToStrip = 2`), `max-frame`.
3. **Cipher decoder:** decrypts the frame in place; the frame is a retained slice of the decoder's cumulation and is
   writable.
4. **Frame encoder, idle, outbound queue, deadlines:** the frame encoder frames and encrypts what leaves; the
   outbound queue sits above it, so every queued packet is framed and encrypted. Steps 2 to 4 are one shared
   assembly, `L2Pipeline` in the `libs/network` bootstrap, which the login and game servers both call
   (`install`: codec, then the guards); `installCodec` (framing and cipher only) is also used by the load harness
   client. The guards come after the cipher decoder, so idle detection sees only frames that decrypted:
   - **Idle:** `IdleStateHandler`: until `AUTHED` read idle for `read-timeout` closes the connection; from
     `AUTHED` read idle for `ping-interval` sends `NetPing`, and `ping-misses` unanswered pings close it. The client
     sends nothing on the character selection screen, so the ping starts there, not in game.
   - **Deadlines:** `handshake-timeout` and `auth-timeout` are timers on the event loop, cancelled when the session
     reaches the state.
5. **Packet decoder:** one decoder per channel that delegates to the channel's current adapter (§8). It reads the
   opcode (one byte, and for extended packets of High Five `0xD0` plus a 2-byte sub-opcode) and looks it up in the
   adapter's immutable table, one route per opcode. The decoder wraps the decoded command or legacy packet in the
   session's queue item itself, and the session handler offers that item as is: one allocation per packet. What an
   item needs from the game (a selected character, a character in the world) is checked when it runs (§8 Session
   states), never here. It only parses: a decoder caps a string unless the handler answers any length (a login is capped; a character name, a chat text and a whisper target are bounded by the frame alone, so the handler can answer them), values it keeps are copied, and the
   frame is released before the command is enqueued; commands never hold a `ByteBuf`.
   - An unknown opcode is counted and dropped, logged rate-limited, never with a stack trace.
   - A known opcode without a handler is counted as `ignored` and dropped.
   - The client's `NetPing` answer is consumed on the IO thread (it only proves the connection alive); it never
     reaches the session.
   - Before authentication (`CONNECTED`) only the routes marked pre-auth are read: the login packet. Any other
     packet is counted as `unauthenticated` and dropped undecoded, before the rate limit and the queue, so an
     unauthenticated client never reaches game code. The client sends nothing else before the server has answered
     its authentication, and the server moves the session on before it answers. This is the only filter the
     transport applies by route.
   - A decode error closes the connection before authentication; after it such errors are counted and more than
     `decode-error-limit` closes it. A decoder returning neither a command nor a legacy packet
     is a server bug: it reaches the tail handler, which logs its stack and closes with `error`.
6. **Session handler:** rate-limits the session (`packets-per-second`, a Bucket4j local bucket) and offers the
   command to the session queue, a bounded SPSC queue of `inbound-queue-limit`
   ([execution.md §3](003-engine-core/execution.md#3-inbox-and-session-queues)). A full queue or an empty bucket is
   a flood and closes the connection.

**Who drains the session queue.** One drain per session runs at a time, so a session's commands execute strictly in
order. Before the world partition exists (E6), the drain runs on the bounded virtual-thread executor of `libs/network`
(the one the login uses, §9), named `l2nx-game-packets` and bounded by `max-concurrent-drains`: a session with input is
scheduled once, executes up to `packets-per-drain` items and reschedules itself if more are queued. From E6 the session drain
keeps only the key exchange and the authentication (`CONNECTED`); once the session is `AUTHED` it is attached to the
world partition, which drains the queue in its inbox phase with the engine's `packets-per-pass`, character selection
included, since selecting, creating and entering load and create world objects
([execution.md §3](003-engine-core/execution.md#3-inbox-and-session-queues)).

## 6. Outbound path

- **The adapter serializes on the thread that emits the view event or legacy packet** (the owner of the game state
  it reads), into a pooled buffer with 2 bytes of headroom for the length and, for login, tailroom for padding and
  checksum. Nothing reads game state on an IO thread.
- **One frame encoder** finishes the frame on the IO thread: encrypts in place and fills in the length when the
  buffer is not shared (reference count 1); copies first and then does the same when it is shared. The game cipher
  never covers the length bytes.
- **Serialized per session.** Each session's port serializes a view event for its own client; sharing one frame
  across an audience measured no gain for chat (`ChatBroadcastBenchmark`: one shout line to N p267 connections from
  `Audience.emit` through the production encoder, JDK 25 in `eclipse-temurin:25-jdk`, Ryzen 5 5600X, two runs of
  2 forks, 5×1 s warmup, 10×1 s measurement; shared vs per-session, µs/op per run: 200 viewers × 105 characters
  402/397 vs 361/360, 50 × 16 47.2/44.5 vs 43.7/42.4, 1 × 16 1.20/1.17 vs 1.08/1.09; at 200 viewers shared allocated
  about 49 KB/op, per-session 15–36 KB/op), and E8 replication decides again with its own benchmark.
- **A legacy broadcast serializes once:** a packet sent to an audience serializes for its first recipient, every
  recipient queues a share of that frame (`Frames.share`), and the broadcaster releases it after the fan-out. A packet
  whose bytes depend on the recipient (`NpcInfo`, `NpcSay`, `ExShowScreenMessage` and `SystemMessage` with multilang
  on) declares itself receiver-dependent and serializes per recipient ([#205](https://github.com/l2nx/nx-l2/issues/205)).
- **Buffer ownership:** the emitter holds one reference and releases it after the fan-out; each queued duplicate is
  released by the encoder after writing, or by the outbound drain or the close path when the channel closes, the write fails or
  the event loop rejects the task during shutdown.
- **A frame never outgrows its length field:** a frame longer than 65535 bytes after padding and checksum fails its
  write and closes the connection with `error`; the length is never truncated.
- **One ordered outbound queue per connection** (MPSC), drained on its event loop:
  - an atomic consumer role guards one outbound drain task; the drain hands the role back and re-checks the queue,
    so no wake-up is lost; a drain writes at most 64 frames (`DRAIN_BATCH`) with `voidPromise()`, flushes once and
    reschedules if frames remain, so one connection cannot hold its event loop from the others;
  - the outbound drain is the only writer of the channel: the key packet, legacy packets and view events all go through it;
    a direct `channel.write` from another thread is forbidden, since the rolling XOR depends on write order;
  - the send path holds its references in final fields: `GameConnection` keeps the `OutboundQueue` that
    `L2Pipeline.install` returns and the server's `NetworkMetrics`, `FrameEncoder` gets `NetworkMetrics` in its
    constructor; no channel attribute is looked up per packet ([#194](https://github.com/l2nx/nx-l2/issues/194));
  - from E8, view events and legacy packets emitted in any phase of a pass are buffered per session in emission
    order and released by the replication phase after its spawns
    ([replication.md §6](003-engine-core/replication.md#6-network-hand-off)).
- **Network hand-off:** the world loop opens a hand-off before the phases of a pass and closes it in the flush phase
  ([#194](https://github.com/l2nx/nx-l2/issues/194)):
  - inside an open hand-off a send that takes the consumer role queues the drain with `EventExecutor.lazyExecute`
    (no wake-up), marks the role `DEFERRED` and records the event loop; the flush phase wakes each recorded event
    loop once, so a pass costs one wake-up per event loop, not one per connection;
  - the flush runs in a `finally` around the phases and catches a `Throwable` per event loop, so a failed phase or
    an `Error` never strands frames;
  - a send from any other thread (an event loop, a background pool) to a `DEFERRED` connection takes the role to
    `SUBMITTED` and executes the drain at once; the deferred task then finds the role taken and does nothing;
  - outside an open hand-off (startup before the first pass, shutdown hooks, the stop drains) and on any other
    thread a send executes the drain at once;
  - the hand-off moves only the wake-up: a connection's frame order, the `outbound-limit` check in the enqueue and
    the close path stay as above; an event loop that rejects the task or the wake-up takes the same path as a
    rejected drain, the connection releases its queued frames;
  - the type lives in `libs/network`, the loop owns it through `engine/core` (`session/outbound`); chronicles and
    the world partition do not take part.
- **Failures:** a tail handler counts, logs rate-limited and closes on exceptions, which with `voidPromise()` are
  the only failure signal. A send that must precede a close (server close, kick, leave world) uses a real promise
  that closes the channel when the write completes.

## 7. Backpressure and protection

- **Slow readers:** watermarks signal; `outbound-limit` caps memory. Pending bytes over `outbound-limit` close the
  connection at once; a connection unwritable for longer than `slow-client-timeout` is closed too. Dropping
  coalescable and cosmetic events of a busy connection is the replication budget
  ([replication.md §5](003-engine-core/replication.md#5-priority-and-budgets)).
- **Floods:** the session rate limit and the bounded queue (§5); from E6 the partition's `packets-per-pass` keeps a
  fast client from delaying others.
- **Connections:** the accept filter (§5) and the handshake and authentication timeouts.

## 8. Ports and protocol adapters

The game exposes ports; a protocol adapter implements them for one client protocol.

```mermaid
flowchart LR
  subgraph Adapter[protocol adapter]
    D[decoders: packet to command]
    M[mappers: view event to packet]
  end
  NET[Netty pipeline] --> D --> Q[session queue] --> H[command handlers in slices]
  H --> S[game state] --> E[view events] --> CP[ClientPort] --> M --> NET
```

### Commands and view events

- **Commands are intents of a player,** records in the `command/` package of their feature slice:
  `movement.command.MoveTo`, `item.command.UseItem`, `trade.command.ConfirmTrade`, `html.command.FollowLink`. They
  are not one to one with packets: several packets may map to one command, one packet to several or to none. A link
  in an HTML dialog, a bypass and a chat command are commands of the slice that owns them. What the login server
  sends is a command too (§10).
- **View events are facts a client must learn,** records in the `view/` package of their slices: `Spawned`,
  `Despawned`, `CreatureMoved`, `StatusChanged`, `MessageShown`, `HtmlDialogOpened`. An event carries handles and
  domain values; a mapper reads what else it needs from the slice's read-only views, on the thread that emits the event.
- **No wire values in ports:** commands and events carry domain enums and value types. Object references are wire
  handles ([persistence.md §2](003-engine-core/persistence.md#2-wire-handles)), which are protocol-independent.
  Messages are domain message keys with typed parameters (player, NPC, item, skill, number, text); each adapter
  maps a key to its client's string id and decides what to show when its client has no such message.
- **Each command has exactly one handler, and the handler's type declares where it runs.** A Spring bean holds the
  registry `command type → handler`, built from the handler beans; it refuses a second handler for a type.
  - **Session** (`SessionCommandHandler<C>`, `handle(Session, C)`): runs in the drain of the session's queue, in
    order with that client's packets, wherever the queue is drained (§5): on the session drain before E6; from E6
    on the session drain until `AUTHED` and on the world partition after it. The handler does not change when the
    drain moves. A client's own commands land in its session. A command from another edge that targets a
    session carries its address (`SessionAddressed`): the account (`SessionAddress.Account`) for a push such as a
    kick, or the correlation id of the request it answers (`SessionAddress.Request`, a UUIDv7 the game generated
    when it sent the request, since an answer may concern a client that has no account yet). The dispatcher finds
    the session and offers the command to its queue on the connection's event loop, so the queue keeps its single
    producer and internal input skips the rate limit. With no session for the address the command is counted as
    dropped (`no-session`) and logged at debug.
    Then the handler's `handleWithoutSession` runs on the sender's lane, so a push such as a kick still frees the
    login-side state of an account that has no live session.
    A client command that needs a game context (a selected character, a character in the world) gets it the way
    Spring MVC gets a handler argument from an argument resolver: its handler is a `ContextCommandHandler` that
    names a `ContextResolver` and takes the resolved context as the first argument, and the dispatcher resolves it from
    the session just before the call (High Five: `GameContexts`, over `session/GameContext`). A missing context rejects the command as
    for a legacy packet (§8 Session states); finer conditions stay the handler's own checks.
  - **Global service** (`ServiceCommandHandler<C>`, `handle(C)`): runs on the serial lane of the service that owns
    the state (the GS-link stream for the `login` slice, §10; a service inbox from E9). No session is involved.
  - **Partition:** a world command not bound to a session, introduced with the world partition (E6).

  Routing by an address in the command is the pattern of Axon's target aggregate identifier and Pekko's sharded
  entity id; the registry stays ours because Spring has no command bus and its application events are neither
  typed per handler nor ordered per session.

- **Requests and dialogs** (trade, party invite, confirmation dialogs) are game state with a domain request id.
  The view event carries the id, the adapter maps it to its packet, the answering command carries it back, and the
  timeout is a deadline of the owner.
- **Game state is built by game code, never as a side effect of sending.** The actions an HTML dialog allows are
  recorded by the slice before it emits `HtmlDialogOpened`; the mapper only serializes. Mappers have no side
  effects, with one exception: a mapper may record the session-local wire ids it issues (the `p267` mail numbers,
  [spec 010 D13](010-mail/spec.md#1-decisions)) in a table of the connection, which the decoders read back. Such a
  table holds only ids the client was shown, never game state, and ends with the connection.

### Session states

`CONNECTED → AUTHED → CLOSING → DISCONNECTED`: the lifecycle of the connection, in `engine/core`. The transport
uses it for one rule only: before authentication it reads nothing but the login (§5).

Whether a character is selected or in the world is game state, not a session state, and the transport never
filters by it. An item that needs it says so: it is a `ContextBound` and answers, just before it runs, whether its
context is present. The check runs in the session item's execution path, shared by the pre-E6 session drain and the
partition inbox, so it sees every change made by the items queued before it (a packet read right behind
`CharacterSelect` sees the selected character). The character's rows are read off the loop, so the session takes
no input from `CharacterSelect` until the player is built or the load fails; the items behind it wait in the queue
and run in order afterwards, which keeps that guarantee, and a repeated `CharacterSelect` runs after the first one
with its usual checks ([execution.md §3](003-engine-core/execution.md#3-inbox-and-session-queues),
[#204](https://github.com/l2nx/nx-l2/issues/204)). The character list is read off the loop too: after
authentication, and for the lobby, restart, delete, restore and create paths, the session is held until the list is
sent; a list that cannot be read closes the connection with `ServerClosed`.

A held session (`Session.hold()` until `release()`, drain thread only) hands out no input, and a close still runs.
While held, its queue takes input with no `queueLimit`, bounded by the rate bucket only, since a client that keeps
playing while its outcome is read is no flood. After `release()` the queue accepts `queueLimit` more beyond what
accumulated, and the normal limit applies again once the backlog is below it. An item without its context is rejected, not run: counted as
`l2nx.session.rejected` (tags `type`, `context`) and logged rate-limited at debug; an item that races the session's
close is discarded uncounted like the rest of the queue. Commands the server addresses to a session itself carry no
context and always run.

Legacy packets declare their context in the High Five opcode enums (`session/GameContext`), read from the client's game
state: `LOGIN` (the login, read before authentication and run only until an account is authenticated), `ACCOUNT` (an authenticated account), `LOBBY` (the selection
screen: no character selected), `CHARACTER` (a selected character, while the world loads or in it) and `WORLD`
(after `EnterWorld`), the default. The list of packets that run before the world follows what the real client
sends, not Mobius: the High Five client asks for skill reuse times, the key mapping, the manor list and the fortress list while the
world loads. A condition finer than the context (a repeated `EnterWorld`, a character that cannot log out now) is the
handler's own check. A ported command resolves the context its handler needs itself (§8 Commands and view events)
and is rejected the same way when it is missing; the legacy list shrinks as packets are ported.

### Selection

- **The version handshake is protocol-independent.** The first packet of a game connection is the protocol
  version, read by the chronicle's handshake decoder on the IO thread before any adapter exists.
- **`GameProtocolRegistry`** maps the version to an adapter; the handshake sets the channel's adapter field, which
  the single packet decoder (§5) delegates to, and then removes itself from the pipeline. The packet decoder stays in
  place, so frames already buffered after the version reach it in order and are decoded by the new adapter. After a
  refused version the handshake stays and discards the frames that follow while the connection closes.
- **Key exchange:** the adapter's key packet is the first outbound packet and goes out in clear; the encoder
  enables the rolling XOR in both directions right after encoding it. An unknown version is refused through the
  handshake's own refusal packet and the connection closes after the write; a ping version (`-2`) closes without a
  log line.

### Adapters

- **An adapter** (`network/protocol/<id>/`) is thin: an inbound decoder is the record's `decode` plus mapping the
  record to a command, an outbound mapper maps a view event to a record and writes it. Layouts are never written in
  the adapter. What its client cannot express is decided in its mapper.
- **Completeness is tested:** every command a client can send has a handler, and every view event has a mapper or
  an explicit drop, in each adapter.
- **The first adapter is `p267`,** the High Five client, accepting protocol versions 267, 268, 271 and 273.
- **Further protocols** take their packet layouts from the Mobius builds of other chronicles and from a private
  reference server consulted for packet layouts and behaviour only, under the originality policy of
  [spec 001 §2](001-project-design.md#originality-policy).

### Wire codecs

Each packet layout exists once, in `protocols/`, and serves both sides: the servers and the load bots of
[spec 009](009-load-harness.md). Packet libraries shared by servers and bots are built the same way elsewhere
(MCProtocolLib, Velocity); a schema with generated codecs (Kaitai Struct, a DSL) does not fit Lineage 2's
conditional fields and nested loops and has no Java writer.

- **The record is its own codec.** A wire record holds the layout only: fields in wire order, strings with the
  cap their decoder applies (none when the handler answers any length), wire enums (reason codes) as values of the protocol, no game types. A client packet has
  `static decode(ByteBuf)` (server) and `encode(ByteBuf)` (load bot); a server packet has `write(ByteBuf)`
  (server) and `static read(ByteBuf)` (load bot). Codecs are pure: no state, no clock, no I/O; decoding follows
  §5 (capped strings, values copied, a short frame is a `PacketDecodeException`).
- **Opcodes** are constants of the protocol module; which packet a side accepts in which state is that side's
  decision, not the codec's.
- **Keys and framing of a protocol** (static keys, the `Init` XOR pass, the credentials block and the scrambled RSA
  modulus of the login protocol, the XOR keys of a game protocol) live in its module, over the primitives of
  `libs/network`. Each has both directions where both sides need them.
- **Golden tests** per codec (bytes to record and back) live in the protocol module and serve both sides.
- **Names follow the client protocol, not the chronicle.** A chronicle is content; a client protocol is the wire of
  one client family, and clients of other families (Essence, Grand Crusade) are to play the same chronicle through
  their own adapters. A login protocol is `protocols/login/<revision>`, named by the revision the login server sends
  in `Init` (`c621`); a game protocol is `protocols/game/p<version>`, named by the first protocol version its client
  sends (`p267` for 267 to 273; later `p140`, `p557`). Its server adapter carries the same id
  (`network/protocol/p267`).
- **Modules:** `protocols/login/c621` and `protocols/game/p267`, which holds the keys and the frame cipher of the
  High Five client in both directions and, from the first migrated slices on, its codecs.

### Legacy routes during the migration

Packets not yet moved to ports pass through the `p267` adapter as they are:

- **Inbound:** a legacy packet runs its own `run()` from the same session queue, in order with commands.
  Coalescing (execution.md §3) is a property of command types and never applies to a legacy packet.
- **Outbound:** a legacy packet goes through `ClientPort.legacy(packet)` into the same ordered outbound queue (§6)
  and serializes itself. From E8, a legacy packet that names an object is held until the replication phase has
  spawned that object for the session, and dropped if the session does not see it.
- **Send-time side effects of legacy packets** (a packet's `runImpl` on the receiving player: chat snooping, the
  quest item list after the item list) move into handlers or systems when their slice moves, at the latest.
- **Scripts** send packets directly (`NpcSay`, `SystemMessage`, `NpcHtmlMessage` and others in about 460 files);
  they move to domain calls on the facades (`say`, `showHtml`, `message`) with their slice in E10, and
  `sendPacket` leaves the facades at the end of E10.
- **The gate:** legacy routes are inbound legacy opcodes in the `p267` table (a pinned count) and references to
  server packet classes outside `network/protocol/p267/`, scripts included (a `FreezingArchRule` whose store
  lists them, §13). Both may only fall; a migrated slice may not reference server packets. A test fails if the production
  configuration registers a second adapter while the count is above zero, and at start the server refuses a second
  adapter while any adapter has legacy inbound entries; tests may register one through a test profile.
- **Order of migration:** inbound packets and the outbound packets of a slice (inventory, warehouse, skills,
  dialogs, clan windows) move with the slice in E10; the replicated world state (spawn, despawn, movement, status,
  appearance, cosmetic events) moves with the replication phase in E8. Each packet moves once.

## 9. Login

- **One port per protocol variant** ([spec 001 §8](001-project-design.md#8-login-and-multi-protocol)): the login
  server speaks first, so the port names the variant. `l2nx.login.client.ports` lists `{port, variant}` and replaces
  `l2nx.login.client.port`; each port has its
  own bootstrap and initializer on the shared groups. One variant exists today.
- **Handshake handler:** sends `Init` with the RSA public key and the connection's own Blowfish key, drawn from
  `SecureRandom` when the connection opens, then removes itself; timeouts per §5.
- **Cipher:**
  - outbound: the encoder pads the payload to 8 bytes, appends the XOR checksum, encrypts with Blowfish and fills
    in the length after padding; `Init` instead gets the XOR pass with a random key and the static Blowfish key;
  - inbound: Blowfish decrypt, then checksum verification; a bad checksum closes the connection with `error` and a
    rate-limited warning;
  - session key bytes are 1..255: the client reads the key from `Init` up to its first zero byte, so a zero inside
    the key would leave the two sides with different keys;
  - every cipher instance belongs to one connection and runs only on its event loop, so it is not thread-safe by
    design and takes no locks; the static-key cipher of `Init` is created for that one frame.
- **Blocking and heavy work** (RSA decrypt of the credentials, account lookup, password check) runs on a bounded
  executor of virtual threads, ordered per channel; results return through the channel's event loop. Never on an
  IO thread. A channel holds an executor slot for at most a batch of its tasks and then queues again behind the
  others (a fair semaphore), so a busy channel cannot starve logins; tasks the executor can no longer run after
  close go to the channel's rejection handler instead of running against a closing context.
- **Passwords** are Argon2id hashes through Spring Security's `DelegatingPasswordEncoder` with Argon2id as its only
  encoding (`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8`: 16 MiB, 2 iterations, one lane), stored with
  their `{argon2}` id; a hash with any other id fails the check like a wrong password: it is logged with a warning,
  spends one hash and counts as a failed login. `AccountService` checks the password on the executor above. A login
  of an unknown account spends one hash too, so the answer time does not tell which logins exist; the hash it is
  matched against is encoded once when the service starts. `l2nx.login.ban.failed-logins` failed logins from one
  address ban it for `ban.duration`; the count lapses once `ban.failed-logins-window` passes without a failure (a
  Caffeine cache expiring after write) and resets on a successful login. A stored hash with
  weaker parameters than the encoder's is re-encoded after a successful login. A password change from a game server
  (§10) refuses a new password the client cannot send back intact (more than 16 UTF-8 bytes, a zero character, an
  unpaired surrogate, whitespace at its edges, which the credentials block trims), compares the typed old password
  with the stored hash in Java and replaces the hash only if it is still the one it read.
  - Cost, measured with JMH on a 6-core desktop CPU with 12 hardware threads: about 18 ms and 16.6 MiB allocated per
    hash on one thread, about 165 hashes/s on 12 threads.
  - Memory: a hash holds about 16.6 MiB of heap while it runs and never unmounts its virtual thread, so unbounded a
    burst of logins would run one per carrier thread and need heap in proportion to the core count. The encoder bean
    wraps the Argon2id encoder in a fair semaphore of `l2nx.login.password.max-concurrent-hashes` permits (default 4),
    taken by `encode` and `matches`, the anti-enumeration hash included; `upgradeEncoding` only parses the stored hash
    and takes none. Callers wait on their virtual thread: the login lane or the GS-link stream lane. Worst case
    4 × 16.6 MiB ≈ 66 MiB of the distribution's `-Xmx256m`; the key and the heap are raised together. The wait is the
    timer `l2nx.login.password.wait`, the waiting hashes the gauge `l2nx.login.password.queued` (§11).
- **Codecs and handlers:** the records, the frame cipher and the credentials block are `protocols/login/c621`
  (§8 Wire codecs); `login/server` holds a handler per client record, which does what the packet means (RSA
  decrypt and account check for `RequestAuthLogin`), and sends server records. The login server has no ports or
  commands: it is one small process without slices, so the Netty idiom of a handler per message type suffices. The
  handlers and the server list mapper are beans, and `LoginRoutes`, the route table built from the handler beans, is
  handed to each channel initializer; nothing in the login server is reached through a static instance.
- **Login states:** the packet decoder decodes the record whatever the state and hands it with its handler to the
  channel's lane; the connection state is checked there, just before the handler runs, and is dropped as
  `l2nx.network.dropped{reason=wrong-state}` when the state does not accept it. A packet read right behind a
  state-changing one (`AuthGameGuard`, then `RequestAuthLogin` in the same read) is judged by the state the first
  one leaves. A packet that reaches the lane after the connection started closing (a pipelined second
  `RequestAuthLogin` behind a failed one) is discarded uncounted. Unknown and ignored opcodes are dropped on the IO
  thread in any state.
- **Idle:** `read-timeout` applies until the account is authenticated. The client sends nothing on the licence and
  server list screens, so authentication re-arms the read-idle close with the login session lifetime (5 minutes):
  every login connection stays bounded on its own channel, including sessions the login server no longer tracks.
  That close sends `LoginFail` `REASON_ACCESS_FAILED` first and counts as `idle`, because the High Five client
  ignores a bare close on the server list screen and Confirm then does nothing. On top of that the purge closes an authenticated session that has not moved on to a game server within 5 minutes,
  checking every 5 minutes.
- **Server list:** the High Five client asks for the list once, so it is sent after every online game server
  reported the characters of the account or after 500 ms, whichever comes first; the wait is a timer on the channel's
  event loop, cancelled on close, and the list is built on the channel's executor lane.

## 10. GS-link

The login↔game link is gRPC: Spring Boot's gRPC server on the login server, a named client channel on the game
server, the contract in `libs/gslink`. The Mobius packet set, its RSA and Blowfish key exchange and its revision
number are not kept.

```proto
service GsLink {
  rpc Connect(stream GameServerMessage) returns (stream LoginServerMessage);
}
```

- **One bidirectional stream per game server.** A registration lives as long as its stream; messages keep their
  order in both directions (a logout is never overtaken by the status that takes the server down), and the login
  server pushes its requests (kick, character count) on the same stream. Each direction is a `oneof` envelope; the
  message names are the commands and port calls of the `login` slice. The proto package carries the version
  (`l2nx.gslink.v1`); a breaking change is a new package, not a revision field.
- **Registration:** the first message is `Register` with the hex id and the chronicle; a stream that sends anything
  else first, or nothing within `handshake-timeout`, is cancelled. A registration with the hex id of a live stream is
  refused (`ALREADY_EXISTS`), so two processes started with the same hex id cannot evict each other in a loop; a dead
  stream is closed by keepalive or by its end, and the game server's next attempt then registers. Which server id a
  registration gets is `GameServerRegistry`'s decision: a known hex id gets its server back; an unknown one takes the
  desired id when it is free, or the lowest free id when the game server accepts another one (also when another
  registration takes the desired id at the same moment), and only while
  `l2nx.login.game-server.accept-new` is on (otherwise `PERMISSION_DENIED`; no free id is `RESOURCE_EXHAUSTED`); a new
  server is stored at once. `GameServerLink` keeps only the stream's state machine and the mapping between proto
  messages and the login server's calls.
- **Authentication:** the game server sends `l2nx.gslink.secret` (required on both servers, no default, at least
  32 characters) as a bearer token in the `authorization` metadata through Spring gRPC's
  `BearerTokenAuthenticationInterceptor`; a server interceptor compares it in constant time and rejects the call with
  `UNAUTHENTICATED`. It logs each refused call with a warning and each accepted call with the peer's address at
  info, one line per game server connect since a call is a long-lived stream: the service never sees the transport,
  so this line ties its later log lines (a refused registration) to an address. The secret travels in clear only on
  loopback: the login server refuses to start when the gRPC server listens on another address without TLS, and the
  game server refuses a non-loopback target without TLS. TLS comes from a Spring Boot SSL bundle; the game server verifies the login server's certificate, host name
  included. The server binds to `127.0.0.1` by default.
- **Liveness:** gRPC keepalive pings (`keepAliveTime` 15 s and its timeout on the game server's channel, a matching
  `permitKeepAliveTime` on the login server) detect a dead link. The channel reconnects by gRPC's own connection
  backoff; when the stream fails, the game server opens a new one after a delay from Spring's `ExponentialBackOff`
  (`l2nx.gslink.reconnect-delay.initial` 1 s doubling to `.max` 30 s) and registers again.
- **Threading:** gRPC delivers the messages of one stream one at a time on its executor, never on a Netty IO
  thread, so each link is a serial lane without an executor of ours; sends to a stream go through one serializing
  writer, since a `StreamObserver` is not thread-safe.
- **Messages** carry the account by UUID ([persistence.md §1](003-engine-core/persistence.md#1-persistent-identity),
  [spec 006](006-database.md)). `Register` is bounded: a hex id of 1 to 64 bytes (stored as raw bytes, `bytea`) and
  at most 32 host and subnet pairs; anything else is refused with `INVALID_ARGUMENT`.
- **Chronicle:** the game server reports its chronicle when it registers, and the login server stores it with the
  server. A client sees and enters only servers of the chronicles its protocol variant serves; a server that never
  registered has no chronicle and is listed to nobody.
- **A player's entry:** `Authenticate` carries the login and the session key the client shows the game server. Both
  servers lower-case the login with `Locale.ROOT`, so the login server finds the client by plain equality whatever
  either machine's default locale is (a Turkish one would turn `I` into a dotless `ı`). The
  login server accepts it only on the stream of the game server the client got `PlayOk` for, when the PlayOk pair
  matches, and the LoginOk pair too when `l2nx.login.show-licence` is on (only then did the client receive it).
  Accepting removes the client from the authenticated clients, so a key is redeemed once and a replay finds nothing.
  Another game server presenting the key is refused with a rate-limited warning, and the key stays valid for the
  selected server.
- **Listing:** `PublishListing` sets only the attributes it carries. The login server maps the proto `ListingStatus`
  to its own enum and refuses the whole listing when the status is a value it does not know (a rate-limited warning;
  the stream stays open). The game server sends the proto value of its own status's code and throws on a code the
  proto lacks, a programming error rather than input.
- **Account messages** apply the rules of `AccountService`; every refusal is logged with the game server's id, none
  is ignored silently:
  - `ChangeAccessLevel` and `RequestTempBan` apply to any account, online or not, and are logged: the game server is
    trusted through the secret and resolves the account from its own characters. A temporary ban with a length that is not positive
    is refused with a warning. Otherwise the account ban and the address ban it carries apply independently, each
    logging its own outcome: an unknown account or a failed store skips only the account ban, and an address that is
    not an IP literal skips only the address ban. The address ban is kept in memory for the same length, and an
    address already banned keeps the later expiry;
  - `ReportRoute` and `ChangePassword` act for the player itself, so they apply only to an account on the game server
    that sent them; another one is refused with a warning (rate-limited for the route), and a refused password change
    answers with the failure text; a new password the client cannot send (§9) is refused with a warning and the
    invalid-data text.
- **A lost link fails what waits for it:** the game side marks every request that needs an answer (a player's
  authentication, a password change) as pending, and when the stream ends while the server runs, each one is failed
  at once (the client gets the login failure, the player the error text) instead of waiting forever. A request sent
  while no stream is open fails the same way.
- **The game side is a port and an adapter:** game code talks to the login server only through the `login` slice
  (`LoginService`), which sends through the `LoginPort` interface; `network/gslink/LoginServerLink` implements it and
  turns every inbound message into a command of the slice, dispatched through the registry (§8,
  [wiring-layout §4](003-engine-core/wiring-layout.md#ports)):
  - session commands: `AuthenticationResult` and `PasswordChangeResult`, addressed by the request id the game sent
    with the request and the login server echoes; `KickAccount`, addressed by account (the kick runs in order with
    the player's packets, never on the gRPC thread; for an account with no live session the stream's lane frees the
    account, and first saves and removes the player of a client that went offline (trade or play) whose connection
    has not closed yet). A session frees its account when its connection closes, an offline trader's or player's
    included, so the login server lets the owner log in again; the character stays in the world until the owner
    selects it, which saves and removes it first. A session frees only what it still holds: after a kick freed its
    account the login server hears no second AccountLeft, and a newer session of the account keeps it. Game and login servers of one
    release only work together, so a message whose request or account id is not a UUID is dropped and logged (a warn
    per message type at most every ten seconds), and the stream stays open;
  - service commands on the stream's lane: `RegistrationResult`, `ReportCharacters`, `LinkLost` (which fails each
    pending request by addressing a rejection to its request id).

## 11. Metrics

Micrometer, per server:

| Meter                            | Type    | Tags                                                                                                                                                                            |
| -------------------------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `l2nx.network.connections`       | gauge   |                                                                                                                                                                                 |
| `l2nx.network.bytes`             | counter | `direction` (`in`, `out`)                                                                                                                                                       |
| `l2nx.network.commands`          | counter | `type`                                                                                                                                                                          |
| `l2nx.network.legacy.packets`    | counter | `type`, `direction`                                                                                                                                                             |
| `l2nx.network.decode.errors`     | counter | `state`                                                                                                                                                                         |
| `l2nx.network.dropped`           | counter | `reason` (`banned`, `connections-per-ip`, `accept-rate`, `max-connections`, `unknown-opcode`, `unauthenticated`, `wrong-state`, `ignored`, `closed`, `unhandled`, `no-session`) |
| `l2nx.network.closed`            | counter | `reason` (`idle`, `timeout`, `flood`, `slow`, `outbound-limit`, `error`)                                                                                                        |
| `l2nx.network.unwritable`        | counter |                                                                                                                                                                                 |
| `l2nx.network.outbound.handoffs` | counter | `group` (`io`), `name` (event-loop thread)                                                                                                                                      |

`l2nx.network.outbound.handoffs` counts every outbound drain task handed to an event loop, deferred by the network
hand-off (§6) or submitted at once, woken or not (Netty decides); its tags match `netty.eventexecutor.tasks.pending` ([#194](https://github.com/l2nx/nx-l2/issues/194)).

A `type` tag is the fully qualified class name, so anonymous classes never yield a blank tag and two protocol
adapters never share a series. Event-loop and allocator meters come from Micrometer's Netty binders (`netty.eventexecutor.tasks.pending`,
`netty.allocator.*`).

The blocking executors of the login (§9) and of the session drain (§5) publish the standard `executor.*` meters with tag
`name=l2nx-login-tasks` and `name=l2nx-game-packets`: the timers `executor.execution` and `executor.idle` (run time and
wait for a slot) and the gauges `executor.active`, `executor.queued` and `executor.pool.max`. The gauges read the
semaphore, since Micrometer cannot read a `ThreadPerTaskExecutor` without `--add-opens`; closing the executor removes
all five.

The login's password encoder (§9) publishes `l2nx.login.password.wait`, a timer of the wait for a hash slot, and
`l2nx.login.password.queued`, a gauge of the hashes waiting for one.

The GS-link has the standard gRPC observations of Spring Boot (`grpc.server`, `grpc.client`) and the gRPC health
service.

Run time of commands is `l2nx.session.command`, of legacy packets `l2nx.legacy.packet` (tags `type`, `outcome`,
`exception`), timed by the session drain (`SessionMetrics`, [execution.md §8](003-engine-core/execution.md#8-budget-and-metrics)).
The input time of a session input, from its enqueue on the IO thread to the end of its run (queue wait plus run, on
the session pool before `AUTHED` and on the world loop after it), is `l2nx.session.input` with the same tags; an input
that never runs (discarded by the close, rejected, replaced by a newer coalescable input) is not recorded. The
profile `load` publishes its percentile histogram ([spec 009 §7](009-load-harness.md#7-measurement-and-report)).
Items rejected for a missing game context are `l2nx.session.rejected` (tags `type`, `context`). The depth of a session
queue after each enqueue is `l2nx.session.queue.depth`, a distribution summary without tags (one series per connection
would not scale) with a percentile histogram bounded by the queue limit, so p99 and max come from Prometheus. `unauthenticated` is
a game connection's packet read before authentication; `wrong-state` is the login protocol's packet out of its
handshake step (§9).

## 12. Shutdown

The network steps of the stop order of [spec 002](002-spring-boot-runtime.md#stop):

1. Close the server channels and stop reading on client channels (`autoRead = false`).
2. After the engine has saved the players, send the server close packet to every client connection in every
   session state (character selection included, not only connections with a player) with a close-on-write promise
   and wait for the writes up to `shutdown-timeout`. The login server does the same with a login failure packet
   whose reason the client shows, so a client on the login or server list screen learns the server went down
   instead of seeing nothing.
3. The game server waits until every client session has closed and logged out (`SessionFactory.awaitClosed`, bounded
   by `shutdown-timeout`; an offline trader's session closed with its connection and freed its account then), logs
   out the accounts of the sessions that did not close in time, and only then sends its last status
   (down) and completes the GS-link stream, then shuts the channel down, bounded by `shutdown-timeout`; a failed step
   does not skip the next. The login server stops its gRPC server gracefully.
4. `shutdownGracefully` with `shutdown-quiet-period` and `shutdown-timeout`.

## 13. Tests

- **Pipeline tests on `EmbeddedChannel`:** fragmented and merged frames, oversize frames, bad checksum, the login
  and game handshakes, a frame buffered behind the protocol version, unknown opcodes, decode errors, queue
  overflow, rate limit, slow client and outbound limit, close with queued buffers.
- **GS-link tests on gRPC's in-process transport:** registration, a wrong or missing secret, the registration
  timeout, refusal of a live hex id, re-registration after a dropped stream, ordering on shutdown, a key accepted
  only on the stream of the server its PlayOk named; one test over TLS with a test certificate.
- **Leak detection** at `paranoid` in every test JVM, checked once after the JVM's last test: the check forces a
  full GC, which stops every test running beside it, so a check per test class stalls parallel tests for seconds.
- **Golden tests** of the ciphers, of each adapter's decoders (bytes in, command out) and mappers (event in, bytes
  out).
- **Adapter completeness** (§8) and the legacy-route gate: references to server packet classes outside the
  adapter are an ArchUnit `FreezingArchRule` with its violation store in the repository (it only shrinks, like the
  slice layout store of [wiring-layout §4](003-engine-core/wiring-layout.md#verification)); inbound legacy opcodes
  of the `p267` table are a pinned count that may only fall.
- **JMH:** encode, broadcast fan-out, decode.
- **End to end:** a real client logs in and plays at the end of the stage.

## 14. Stage 4 scope

Stage 4 is done when:

- both client connections run on Netty, the GS-link runs on gRPC, and the Mobius transport is gone;
- the port framework exists (commands, handler registry, `ClientPort`, session states, `GameProtocolRegistry`)
  with the `p267` adapter and the legacy-route gate;
- the first slice runs through ports: character selection (`AuthLogin`, `CharacterSelect`, `CharacterCreate`,
  `CharacterDelete`, `CharacterRestore`, `RequestRestart`), entering the world (`EnterWorld`), movement
  (`MoveToLocation`, `ValidatePosition`, `CannotMoveAnymore`) and chat (`Say2`), inbound and outbound. Until the
  replication phase exists (E8), their view events reach observers through the legacy visibility of `World`;
- every other packet is a legacy route;
- a client logs in and plays.

## 15. Deferred

- Client protocol 140 over High Five content: [#27](https://github.com/l2nx/nx-l2/issues/27).
- Client protocol 557 over High Five content: [#28](https://github.com/l2nx/nx-l2/issues/28).
- Edge gateway: [#4](https://github.com/l2nx/nx-l2/issues/4).
- Database work and password hashing off the GS-link stream's lane:
  [#93](https://github.com/l2nx/nx-l2/issues/93).
