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
  Netty client and cipher primitives of `libs/network` and the packet codecs, keys and framing of `protocols/*`
  (§3).
- **Protocol-agnostic core.** Load scenarios speak in load bot intents and observations, never in packets; a client
  adapter per protocol maps them, the client-side mirror of the server's ports and adapters
  ([spec 008 §8](008-network.md#8-ports-and-protocol-adapters)). A new protocol or chronicle adds a client adapter
  and, when its content differs, scenarios; the core, the report and the gate stay.
- **Each packet layout exists once.** Server and load bot share the wire codecs of `protocols/`; no layout is
  written twice and cross-checked.
- **Standard methodology:** latency from the intended send time (coordinated omission, HdrHistogram), a fixed
  population acting at a fixed rate, warm-up excluded, server meters read per window from Prometheus, repeated runs,
  a run invalid when the generator itself is saturated.
- **Load bots do not replace players.** They give a repeatable regression gate; real dynamics come from recorded
  sessions (§10) and play tests.

## 2. Layout

| Module                     | Holds                                                                                                                                                  |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `protocols/login/c621`     | login protocol revision c621: wire records and codecs, frame cipher, credentials block                                                                 |
| `protocols/game/p267`      | game protocol of the High Five client (267–273): wire records and codecs                                                                               |
| `tools/load/core`          | load bot model, the `ClientProtocol` port, action scheduler, injection profiles, latency recording, Prometheus window reading, report, gate            |
| `tools/load/protocol-<id>` | client adapter of one client: wire records to intents and observations; `protocol-p267` pairs `login/c621` with `game/p267`                            |
| `tools/load/app`           | Spring Boot command-line application: settings, load scenarios, seeding, the commands `run`, `gate`, `seed`, `db-create`, `db-snapshot` and `db-reset` |

`tools/load/core` depends on neither Spring nor a chronicle nor `protocols/*`; the report and the gate are pure code there, so the command-line application only wires them. Server, login server, load bots and
PostgreSQL run as separate processes.

## 3. Wire codecs

The rule is [spec 008 §8 Wire codecs](008-network.md#wire-codecs); what the load harness relies on:

- **The record is its own codec:** a client packet's `encode` and a server packet's `read` are what a load bot
  uses, next to the `decode` and `write` the server uses. A legacy packet still used by legacy code calls them too.
- **Keys and framing come with the protocol:** `protocols/login/c621` gives the load bot the client side of the login
  frame cipher and the credentials block encrypted with the scrambled RSA modulus from `Init`.
- **Golden tests per codec** (bytes ↔ record) serve both sides.
- **Who writes them:** `protocols/login/c621` holds the whole login protocol; the first `game/p267` codecs come with the first migrated slices (character, movement, chat, message). Combat packets
  stay legacy routes there, so E0 extracts their layouts into `protocols/game/p267` codecs and points the legacy
  packets at them, without migrating a slice (§10, P3).

## 4. Load bot

- **Threads:** a Netty `EventLoopGroup` sized to the cores given to the harness, on the transport `libs/network`
  resolves (native epoll on Linux, NIO elsewhere). A load bot lives on the event loop of its connection and all its
  logic runs there, so it holds no locks. Timers are the event loop's `schedule`.
- **Lifecycle:** `LOGIN_CONNECT → LOGIN_AUTH → SERVER_LIST → GAME_CONNECT → CHARACTER_SELECT → ENTERING → IN_GAME →
CLOSING → STOPPED`, the same for every protocol; `STOPPED` without a logout is a dropped connection, unless the load
  bot closed itself on reaching the final state of its load scenario (§5).
- **Lifecycle steps** are timed like intents: each state records the time spent in it, from entering it to entering
  the next. `LOGIN_CONNECT` is entered at the load bot's intended start, so a slow connect is not hidden by a late
  start (coordinated omission); the final state has no row.
- **Intents** (sent): `Login`, `SelectServer`, `SelectCharacter`, `EnterWorld`, `MoveTo`, `Attack`, `UseSkill`,
  `Say`, `ResurrectAtRestartPoint`, `Logout`.
- **Observations** (kept): its own object id, position, HP and MP, alive or dead; the visible objects as
  `object id → kind, position, team, dead`. Only what scenarios need.
- **Acknowledgement:** the client adapter names the observation that confirms each intent (`MoveTo` the echo of its
  own movement, `Attack` the attack packet, `UseSkill` the start of the cast, `Say` the chat echo). An intent without
  it within its timeout is recorded as a timeout.
- **A capability set per adapter:** the intents it expresses and the furthest lifecycle state it reaches. A run whose
  scenario needs more fails before any load bot starts; an intent a protocol cannot express is reported by the
  adapter, and a scenario skips the step or fails with that reason.
- **Identity check:** the adapter refuses, before the run, an account or password its wire format cannot carry.
- **Determinism:** one seed per run, each load bot draws from `SplittableRandom(seed, botIndex)`. Load bot inputs are
  reproducible; the order and timing of server answers are not.
- **Errors:** a packet that does not decode stops the load bot and logs the frame in hex; a dropped connection is not
  reconnected. A run where more than 1% of load bots stopped on an error is invalid (§7).

## 5. Load scenarios

Load scenarios are Java code on a small DSL, the model of Gatling split by owner: a scenario defines the behaviour of
a load bot and the intents it needs, the injection profile comes from the run settings and the assertions from the
gate settings, so one scenario runs at any size and against any baseline. Ids carry the `load-` prefix, classes are
`<Name>LoadScenario`.

- **Injection profile:** ramp (N load bots over T), warm-up (not reported), steady (the measurement window),
  ramp-down.
- **Measured phase:** a load scenario measures either the steady window (actions of load bots already in the game)
  or the ramp (load bots arriving: every lifecycle step and arrival intent, `Login` to `EnterWorld`, of a load bot whose
  intended start lies in the ramp, however late it completes). The server meter window and the generator self-check follow the same
  phase; for the ramp they run until every load bot reached its final state or stopped.
- **Final state:** the lifecycle state at which a load bot is done and closes without an error; `IN_GAME` by default,
  where it plays until the ramp-down. A protocol must express only the lifecycle intents up to the final state.
- **Rate:** a load bot acts on a schedule (interval plus jitter from its random generator) and does not wait for the
  answer to its previous action: a fixed population, an open arrival rate inside it.

| Id                 | What it does                                                                                                                                                                                                                                                |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `load-login-storm` | N logins over T seconds (the ramp, its measured phase) through login and game; time of every lifecycle step up to its final state, `IN_GAME` by default; `GAME_CONNECT` measures the login server alone                                                     |
| `load-roam`        | walking routes around points, chat at a set rate                                                                                                                                                                                                            |
| `load-pve`         | fighting monsters in a hunting spot, target = nearest alive                                                                                                                                                                                                 |
| `load-crowd-pvp`   | the E6 gate: N characters placed in a circle in an arena PvP zone (no karma), two teams by index parity; each attacks the nearest enemy and casts a skill every K seconds; a dead load bot restarts at its restart point and walks back. N = 500 by default |

## 6. Accounts and characters

The servers under load run on their own database `l2nx_load` in the `l2nx-db` container (Spring profile `load` of
each server: that database, connection limits raised for one address, no address ban after failed logins since every load bot shares
that address, accounts not created on login). A seeded
snapshot is kept as the template database `l2nx_load_template`, and every run starts from
`CREATE DATABASE … TEMPLATE`, so runs start from the same state and never touch other databases. The database name differs from the template's and
from the two temporary names `db-snapshot` derives from it (`<template>_new`, `<template>_old`).

| Command       | Does                                                                                                                                | Servers |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------- |
| `db-create`   | an empty `l2nx_load` with the server schemas and their owners                                                                       | down    |
| `seed`        | accounts (a `load-login-storm` up to `SERVER_LIST`), then characters with level, items, skills and positions per scenario (from P2) | up      |
| `db-snapshot` | `l2nx_load` copied to `l2nx_load_template` through a temporary copy, so the template is never lost                                  | down    |
| `db-reset`    | `l2nx_load` again from the template                                                                                                 | down    |

Seeding goes through the protocol like a run: accounts are created by the login server itself on the first login
(profile `load-seed` turns that on), so the harness writes no server table and knows no password hashing. A run
whose load bots lack an account fails on `LoginFail` instead of creating it unmeasured. The template also holds the game
server's registration on the login server, so `l2nx.load.p267.server-id` names the server registered while seeding
(until [#84](https://github.com/l2nx/nx-l2/issues/84), a rebuilt game server registers under a new id).

## 7. Measurement and report

**Server meters:** the harness scrapes `/actuator/prometheus` every second in the Prometheus protobuf exposition
format, which Actuator serves on request, and parses it with classes generated from the Prometheus `metrics.proto`
(client_model), vendored unchanged: the Prometheus Java client has no parser of its text format. Values are computed
over the measured phase from the deltas between its first and last scrape; the text format is fetched verbatim at the
window's start and end for the report.

| Meter                                                            | Taken as                                                             |
| ---------------------------------------------------------------- | -------------------------------------------------------------------- |
| `l2nx.engine.pass` (percentile histogram)                        | p50, p99, max per `loop` from bucket deltas                          |
| `l2nx.engine.pass.phase`                                         | mean per phase from sum and count deltas                             |
| `l2nx.engine.pass.over.budget`, `failures`, `timers.overdue`     | deltas                                                               |
| `l2nx.engine.jdbc.on.loop` (added by E6)                         | delta per `loop`                                                     |
| `l2nx.engine.inbox.depth`                                        | max and p99 of the per-second samples                                |
| `l2nx.session.queue.depth` (percentile histogram, added by E6)   | p99 from bucket deltas, max                                          |
| `l2nx.legacy.task`, `l2nx.legacy.task.lag`, `l2nx.legacy.packet` | mean per type; p99 and max where a histogram is published; before E6 |
| `jvm.gc.pause`, `process.cpu.usage`, `jvm.threads.live`          | p99, mean, max                                                       |

**Generator self-check:** the harness measures its own event loop lag (scheduled against actual action time). A p99
lag above 10 ms marks the run invalid: the generator, not the server, was saturated. The check is on the measured lag,
not on the operating system, so a host whose timers cannot fire on time (Windows, 15.6 ms timer granularity by
default) produces invalid runs by itself.

**Profiling** (`--profile`): JFR on the server for the steady window through `jcmd <pid> JFR.start`, the recording
kept with the run; async-profiler in wall-clock mode when installed.

**Report** in `build/load/<run>/`: `summary.json` (scenario, its measured phase and final state, the number of load bots that reached it, seed, N, windows, server commit, environment, client
p50, p99, p99.9 and max per intent and per lifecycle step, timeout, rejection and stopped shares, the server values above), an `.hgrm` per
intent and per lifecycle step, `server-metrics-start.prom` and `server-metrics-end.prom` with the raw window scrapes, and a table on the
console. A boundary scrape that fails after its retries leaves the server values out; the gate then fails criteria 1
and 2 for missing meters instead of the run losing its client results.

**Start:** `./gradlew :tools:load:app:bootRun --args='run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-crowd-pvp --l2nx.load.bots=500 --l2nx.load.seed=42'` or the jar;
settings are `@ConfigurationProperties`. A database is prepared once with `db-create`, the servers started with the
profiles `load,load-seed`, `seed`, the servers stopped and `db-snapshot`; before every run `db-reset` with the servers
down, then the servers with the profile `load` and `run`. The injection profile leaves the last load bot the ramp-down
and drain time to reach its final state; a run ends as soon as every load bot is done. `seed` writes no report. A compose profile `load` adds Prometheus and Grafana with a committed
dashboard for watching a run live; the report never depends on it.

## 8. E6 gate

The `gate` command compares the reports of a scenario with its baseline:

| #   | Criterion                                                                   | Threshold         |
| --- | --------------------------------------------------------------------------- | ----------------- |
| 1   | `l2nx.engine.pass` p99 over the measured phase, every loop                  | ≤ `pass-budget`   |
| 2   | Share of passes over budget                                                 | ≤ 0.1%            |
| 3   | Client action latency p99, every intent                                     | ≤ baseline × 1.10 |
| 4   | Timed-out intents; intents the server rejected; load bots stopped on errors | ≤ 0.5% each       |

- Criteria 1 and 2 apply to runs whose final state is `IN_GAME`: before it the engine has nothing of the load bots to
  run, so they are reported as not applicable.
- A scenario runs three times, each run its own `run` from `db-reset` with fresh server processes (server state left
  by one run, such as accounts still marked on the login server, must not leak into the next), and the gate takes the median of each criterion's per-run values. An invalid run does
  not count; with fewer than three valid runs the gate fails with that reason.
- Thresholds live in the gate settings. The baseline numbers and the method behind them are kept in
  `docs/runbooks/load-baseline.md`.
- **Where it runs:** one machine, Linux, the way load generators and game servers run in production. The server,
  the load harness and PostgreSQL are services of the compose profile `load`, each pinned to its own cores with
  `cpuset` and limited with `cpus`; the harness uses Netty's native epoll transport, whose timers fire with
  sub-millisecond precision. On a Windows or macOS workstation the same compose runs in Docker's Linux VM (WSL2 on
  Windows); there the cpusets pin VM vCPUs that the host may still move, which a baseline taken in the same setup
  absorbs. A native Windows run (`bootRun`) is for development: its timer granularity shows as generator lag and the
  self-check marks the run invalid. Raising the process timer resolution (`timeBeginPeriod`) is not used: it is
  Windows-only and measures a platform the server does not run on.
- **Same scenario only:** the gate compares reports of one scenario and final state with a baseline of the same;
  otherwise it fails with that reason and compares nothing.
- **Comparable runs only:** the report records the environment (operating system and kernel, CPU model, logical
  cores, the cpuset of each service, JVM version), and the gate fails when a report's environment differs from the
  baseline's. The gate is relative to a baseline taken on the same machine; criteria 1 and 2 are absolute because the
  engine measures them itself.
- E6 is accepted when `load-crowd-pvp` passes the gate; its cutover landed on main before the gate, on the owner's decision.

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

| #   | Delivers                                                                                                                                                                                                                                          | Needs                    |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| P0  | `tools/load/core`, window reading, gate, `app` on an in-memory protocol                                                                                                                                                                           | —                        |
| P1  | login part of `protocol-p267` (the whole `c621` exchange up to `PlayOk`); lifecycle steps, measured phase and final state in the core; the `load` profiles of the login server and the database commands; `load-login-storm` up to `GAME_CONNECT` | `protocols/login/c621`   |
| P2  | game part: entering the world, `load-roam`, the integration test                                                                                                                                                                                  | first `game/p267` codecs |
| P3  | combat codecs (§3), `load-pve`, `load-crowd-pvp`, the compose profile `load` with cpusets and the environment check, the trust checks, the legacy baseline (also E1's baseline repeated with bots), reading `l2nx.engine.jdbc.on.loop`            | P2                       |
| P4  | dev portal page (§11)                                                                                                                                                                                                                             | P3 numbers               |

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
