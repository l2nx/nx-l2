# 009 — Load harness

A load harness that plays the client protocols from a separate process: load bots log in through the login server,
enter the world and play load scenarios, hundreds per process, while the harness records client-side action latency
and reads the engine meters. It is step E0 of [spec 003](003-engine-core/spec.md#6-migration) and the acceptance gate of
E6 (world partition as single writer). Issue: [#68](https://github.com/l2nx/nx-l2/issues/68).

## 1. Principles

- **Own harness on our own client stack.** No load tool speaks a stateful, encrypted, server-push binary protocol:
  Gatling has no TCP support and a custom protocol goes through its Scala internals; Locust and k6 would need the
  codecs and ciphers again in Python or JavaScript; JMeter and Tsung model request and response. Studios with such
  protocols write their own bot harness (CCP thin clients, Riot's mock clients, MY.GAMES). The harness reuses the
  Netty client and ciphers of `libs/network` and the packet codecs of `protocols/*` (§3).
- **Protocol-agnostic core.** Load scenarios speak in load bot intents and observations, never in packets; a client
  adapter per protocol maps them, the client-side mirror of the server's ports and adapters
  ([spec 008 §8](008-network.md#8-ports-and-protocol-adapters)). A new protocol or chronicle adds a client adapter
  and, when its content differs, scenarios; the core, the report and the gate stay.
- **Each packet layout exists once.** Server and load bot share the wire codecs of `protocols/<id>`; no layout is
  written twice and cross-checked.
- **Standard methodology:** latency from the intended send time (coordinated omission, HdrHistogram), a fixed
  population acting at a fixed rate, warm-up excluded, server meters read per window from Prometheus, repeated runs,
  a run invalid when the generator itself is saturated.
- **Load bots do not replace players.** They give a repeatable regression gate; real dynamics come from recorded
  sessions (§10) and play tests.

## 2. Layout

| Module                     | Holds                                                                                                                         |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `protocols/c621`           | login protocol: wire records and codecs (moved out of `login/server` `protocol/c621`)                                         |
| `protocols/highfive`       | game protocol 267–273: wire records and codecs                                                                                |
| `tools/load/core`          | load bot model, the `ClientProtocol` port, action scheduler, injection profiles, latency recording, Prometheus window reading |
| `tools/load/protocol-<id>` | client adapter of one protocol: wire records to intents and observations; `protocol-highfive` pairs `c621` with `highfive`    |
| `tools/load/app`           | Spring Boot command-line application: load scenarios, seeding, report, gate                                                   |

`tools/load/core` depends on neither Spring nor a chronicle nor `protocols/*`. Server, login server, load bots and
PostgreSQL run as separate processes.

## 3. Wire codecs

- **A wire record per packet** holds the layout only: fields in wire order, strings with their bound, no game types.
- **A codec per packet** is pure and works in both directions: for a client packet `decode` (server) and `encode`
  (load bot), for a server packet `write` (server) and `read` (load bot). Codecs depend on Netty buffers only.
- **The server adapter is thin:** a decoder is `codec.decode` plus mapping the record to a command; a mapper is a view
  event mapped to a record plus `codec.write`. A legacy packet still used by legacy code calls the codec.
- **Golden tests per codec** (bytes ↔ record) serve both sides.
- **Who writes them:** `protocols/c621` comes with its move out of `login/server` once the game transport lands; the
  first `highfive` codecs come with the first migrated slices (character, movement, chat, message). Combat packets
  stay legacy routes there, so E0 extracts their
  layouts into `protocols/highfive` codecs and points the legacy packets at them, without migrating a slice (§9, P3).

## 4. Load bot

- **Threads:** a Netty `EventLoopGroup` sized to the cores given to the harness. A load bot lives on the event loop of
  its connection and all its logic runs there, so it holds no locks. Timers are the event loop's `schedule`.
- **Lifecycle:** `LOGIN_CONNECT → LOGIN_AUTH → SERVER_LIST → GAME_CONNECT → CHARACTER_SELECT → ENTERING → IN_GAME →
CLOSING`, the same for every protocol.
- **Intents** (sent): `Login`, `SelectServer`, `SelectCharacter`, `EnterWorld`, `MoveTo`, `Attack`, `UseSkill`,
  `Say`, `Restart`, `Logout`.
- **Observations** (kept): its own object id, position, HP and MP, alive or dead; the visible objects as
  `object id → kind, position, HP, team`. Only what scenarios need.
- **Acknowledgement:** the client adapter names the observation that confirms each intent (`MoveTo` the echo of its
  own movement, `Attack` the attack packet, `UseSkill` the start of the cast, `Say` the chat echo). An intent without
  it within its timeout is recorded as a timeout.
- **A capability set per adapter:** an intent a protocol cannot express is reported by the adapter, and a scenario
  skips the step or fails with that reason.
- **Determinism:** one seed per run, each load bot draws from `SplittableRandom(seed, botIndex)`. Load bot inputs are
  reproducible; the order and timing of server answers are not.
- **Errors:** a packet that does not decode stops the load bot and logs the frame in hex; a dropped connection is not
  reconnected. A run where more than 1% of load bots stopped on an error is invalid (§7).

## 5. Load scenarios

Load scenarios are Java code on a small DSL: a scenario, an injection profile and assertions, the model of Gatling.
Ids carry the `load-` prefix, classes are `<Name>LoadScenario`.

- **Injection profile:** ramp (N load bots over T), warm-up (not reported), steady (the measurement window),
  ramp-down.
- **Rate:** a load bot acts on a schedule (interval plus jitter from its random generator) and does not wait for the
  answer to its previous action: a fixed population, an open arrival rate inside it.

| Id                 | What it does                                                                                                                                                                                                                                                |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `load-login-storm` | N logins over T seconds through login and game; time of every lifecycle step up to `IN_GAME`                                                                                                                                                                |
| `load-roam`        | walking routes around points, chat at a set rate                                                                                                                                                                                                            |
| `load-pve`         | fighting monsters in a hunting spot, target = nearest alive                                                                                                                                                                                                 |
| `load-crowd-pvp`   | the E6 gate: N characters placed in a circle in an arena PvP zone (no karma), two teams by index parity; each attacks the nearest enemy and casts a skill every K seconds; a dead load bot restarts at its restart point and walks back. N = 500 by default |

## 6. Accounts and characters

The harness seeds a database `l2nx_load` in the `l2nx-db` container: accounts, characters with level, items, skills
and positions per scenario. A seeded snapshot is kept as a template database, and every run starts from
`CREATE DATABASE … TEMPLATE`, so runs start from the same state and never touch other databases. Logins still go
through the protocol.

## 7. Measurement and report

**Server meters:** the harness scrapes `/actuator/prometheus` every second, parses it with the Prometheus Java
client's text-format parser and computes values over the steady window from the deltas between its first and last
scrape.

| Meter                                                            | Taken as                                    |
| ---------------------------------------------------------------- | ------------------------------------------- |
| `l2nx.engine.pass` (percentile histogram)                        | p50, p99, max per `loop` from bucket deltas |
| `l2nx.engine.pass.phase`                                         | mean per phase from sum and count deltas    |
| `l2nx.engine.pass.over.budget`, `failures`, `timers.overdue`     | deltas                                      |
| `l2nx.engine.inbox.depth`                                        | max and p99 of the per-second samples       |
| `l2nx.session.queue.depth` (percentile histogram, added by E6)   | p99 from bucket deltas, max                 |
| `l2nx.legacy.task`, `l2nx.legacy.task.lag`, `l2nx.legacy.packet` | p99 per type, for the baseline before E6    |
| `jvm.gc.pause`, `process.cpu.usage`, `jvm.threads.live`          | p99, mean, max                              |

**Generator self-check:** the harness measures its own event loop lag (scheduled against actual action time). A p99
lag above 10 ms marks the run invalid: the generator, not the server, was saturated.

**Profiling** (`--profile`): JFR on the server for the steady window through `jcmd <pid> JFR.start`, the recording
kept with the run; async-profiler in wall-clock mode when installed.

**Report** in `build/load/<run>/`: `summary.json` (scenario, seed, N, windows, server commit, CPU affinity, client
p50, p99, p99.9 and max per intent, timeout and error shares, the server values above), an `.hgrm` per intent,
`server-metrics.prom` with the raw window scrapes, and a table on the console.

**Start:** `./gradlew :tools:load:app:bootRun --args='--scenario=load-crowd-pvp --bots=500 --seed=42'` or the jar;
settings are `@ConfigurationProperties`. A compose profile `load` adds Prometheus and Grafana with a committed
dashboard for watching a run live; the report never depends on it.

## 8. E6 gate

The `gate` command compares the reports of a scenario with its baseline:

| #   | Criterion                                      | Threshold         |
| --- | ---------------------------------------------- | ----------------- |
| 1   | `l2nx.engine.pass` p99 over steady, every loop | ≤ `pass-budget`   |
| 2   | Share of passes over budget                    | ≤ 0.1%            |
| 3   | Client action latency p99, every intent        | ≤ baseline × 1.10 |
| 4   | Timeouts and errors                            | ≤ 0.5%            |

- A scenario runs three times and the gate takes the median of the p99 values. An invalid run does not count; with
  fewer than three valid runs the gate fails with that reason.
- Thresholds live in the gate settings. The baseline numbers and the method behind them are kept in
  `docs/runbooks/load-baseline.md`.
- **Where it runs:** one machine, separate processes with CPU affinity set by the start script (server, load bots,
  PostgreSQL on disjoint cores). The gate is relative to a baseline taken on the same machine; criteria 1 and 2 are
  absolute because the engine measures them itself.
- E6 lands on main after `load-crowd-pvp` passes the gate.

## 9. Tests and trust

- **Codecs:** golden tests in `protocols/*` (§3).
- **Client adapter:** intent → expected frame bytes; golden server frames → expected observations; every intent has
  an acknowledgement.
- **Core** on an in-memory `ClientProtocol` and Netty `EmbeddedEventLoop`: latency taken from the intended time,
  including a stalled fake server (a 2 s stall must show in the latency); injection profile phases; the invalid mark
  on generator lag.
- **Window reading:** quantiles from bucket deltas against HdrHistogram on known data.
- **Gate:** report fixtures for pass, fail, invalid and too few runs.
- **One integration test:** Testcontainers PostgreSQL, login and game servers in the test context, 20 load bots in
  `load-roam` for 30 s; all reach `IN_GAME`, no errors, window scrapes taken.

**Before the gate is trusted** (once, results in `load-baseline.md`):

1. A real client stands in the `load-crowd-pvp` spot: load bots are visible, walk, hit and cast.
2. Sensitivity: the same run with the server limited to two cores must raise client p99 and fail the gate.
3. Repeatability: three runs with one seed spread their p99 by at most 10%; a wider spread widens the thresholds or
   the number of runs, recorded with the reason.

## 10. Stages

| #   | Delivers                                                                                                                        | Needs                   |
| --- | ------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| P0  | `tools/load/core`, window reading, gate, `app` on an in-memory protocol                                                         | —                       |
| P1  | login part of `protocol-highfive`; `load-login-storm` up to `SERVER_LIST`                                                       | `protocols/c621`        |
| P2  | game part: entering the world, `load-roam`, the integration test                                                                | first `highfive` codecs |
| P3  | combat codecs (§3), `load-pve`, `load-crowd-pvp`, the trust checks, the legacy baseline (also E1's baseline repeated with bots) | P2                      |
| P4  | dev portal page (§11)                                                                                                           | P3 numbers              |

## 11. Dev portal page

The load harness gets its own page **Load testing** (`docs/public/{en,ru}/load-testing.md`), plain and technical,
without a brand name: what the harness does and how to run it, the load scenarios, the method (coordinated omission,
open rate, windows, repeats, invalid runs), the gate criteria and the measured baseline and E6 numbers with the
machine and settings they came from; the Zero-Tick Engine page links to it for its numbers. The page lands with P4
and states only measured numbers.

## 12. Later

- [#71](https://github.com/l2nx/nx-l2/issues/71) Load from recorded sessions: replaying E6 input recordings
  ([execution.md §9](003-engine-core/execution.md#9-replay)) with object ids remapped on the fly.
- [#72](https://github.com/l2nx/nx-l2/issues/72) A real castle siege as a load scenario (registration, siege time,
  residence capture).
