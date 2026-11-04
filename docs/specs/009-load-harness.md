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
  population acting at a fixed rate, warm-up excluded, server meters queried from Prometheus over the measured phase
  with the PromQL a dashboard uses, repeated runs, a run invalid when the generator itself is saturated.
- **Load bots do not replace players.** They give a repeatable regression gate; real dynamics come from recorded
  sessions (§10) and play tests.

## 2. Layout

| Module                     | Holds                                                                                                                                                                                                             |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `protocols/login/c621`     | login protocol revision c621: wire records and codecs, frame cipher, credentials block                                                                                                                            |
| `protocols/game/p267`      | game protocol of the High Five client (267–273): wire records and codecs                                                                                                                                          |
| `tools/load/core`          | load bot model, the `ClientProtocol` and `ServerMeterWindow` ports, action scheduler, injection profiles, latency recording, report, gate                                                                         |
| `tools/load/protocol-<id>` | client adapter of one client: wire records to intents and observations; `protocol-p267` pairs `login/c621` with `game/p267`                                                                                       |
| `tools/load/app`           | Spring Boot command-line application: settings, load scenarios, seeding, the Prometheus client and the PromQL of the server values, the commands `run`, `gate`, `seed`, `db-create`, `db-snapshot` and `db-reset` |

`tools/load/core` depends on neither Spring nor a chronicle nor `protocols/*`; the report and the gate are pure code
there; Prometheus, `RestClient` and the commands belong to the application. Server, login server, load bots and
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
- **`CharInfo`, `UserInfo` and `NpcInfo` are read by their head only.** A load bot needs the object id, the position, the dead
  flag and, for a character, the name; the rest of these layouts stays in the legacy packets until their slices migrate, because
  moving them now would add allocations to the broadcast path this harness measures. `protocols/game/p267` holds a
  read-only head record of each, a deliberate exception to "each layout exists once": a golden test in the server
  module feeds the bytes of the real legacy packet to the head record, so a drift fails the build. `StatusUpdate` is the
  second exception, for the same reason: `broadcastStatusUpdate` serializes per listener, so the legacy packet keeps its
  own writing and a golden test proves its bytes equal the record's.

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
- **Intents** (sent): `Login`, `SelectServer`, `CreateCharacter`, `SelectCharacter`, `EnterWorld`, `Logout` (lifecycle, P1–P2);
  `MoveTo`, `Say` (`load-roam`, P2); `Attack`, `UseSkill`, `ResurrectAtRestartPoint`, `ResurrectInPlace` (P3); `AdminCommand`, `AdminTeleport` (`seed`).
- **Observations** (kept): its own object id and position (`Entered`, the echo of its own `ObjectMoved`; P2); the
  visible objects as `object id → kind, position, team, dead` (`ObjectSeen`, `ObjectGone`, `ObjectMoved` of others)
  and its own HP and MP (`StatusChanged`), alive or dead (`Died`, `Revived`), its own cast (`CastStarted`), in the model for `load-pve` and
  `load-crowd-pvp` (P3). Only what scenarios need.
- **Client duties:** what the game requires of a client and no scenario asks for stays in the adapter: `Appearing` after
  every `TeleportToLocation` (without it the server keeps the character teleporting) and a `ValidatePosition` once a
  second. The position it sends is dead-reckoned from the last move, since the server takes a client position further
  than one tick of running as the truth.
- **Decoding:** every frame is decrypted, since the XOR key rolls, but only the opcodes a scenario needs are decoded;
  with N load bots in one spot the generator receives about N² broadcasts. The harness times a frame from
  `channelRead` to its handling, so a generator slowed by inbound traffic shows in the report, not as server latency.
- **Acknowledgement:** the client adapter names the observation that confirms each intent (`MoveTo` the echo of its
  own movement, `Say` the chat echo; from P3 `Attack` the attack packet, `UseSkill` the start of the cast). It is matched
  by content (destination, target, skill id), so a late answer is not credited to the next intent of the kind; FIFO per
  kind remains for intents with no content to match, and for a `MoveTo` whose echo matches no destination, since on a
  route around an obstacle the server echoes the first node. A timed-out intent enters the latency histogram at the intent
  timeout, so a hanging tail raises p99 instead of leaving it. An intent
  without it within its timeout is recorded as a timeout. `ActionFailed` confirms nothing and rejects nothing: the
  server sends it after every click, so a refused action also ends as a timeout. A death drops the pending actions of a
  load bot, the death of a target those aimed at it.
- **Behaviour without actions:** a scenario's behaviour may have no action schedule; its load bots stay in the game
  until the ramp-down (`load-login-storm` with the final state `IN_GAME`).
- **A capability set per adapter:** the intents it expresses and the furthest lifecycle state it reaches. A run whose
  scenario needs more fails before any load bot starts; an intent a protocol cannot express is reported by the
  adapter, and a scenario skips the step or fails with that reason.
- **Identity check:** the adapter refuses, before the run, an account or password its wire format cannot carry.
- **Determinism:** one seed per run, each load bot draws from `SplittableRandom(seed, botIndex)`. Load bot inputs are
  reproducible; the order and timing of server answers are not.
- **Lifecycle deadline:** a load bot that stays in a state before its final state longer than `lifecycle-timeout` records
  a step timeout and stops on an error, so a server that never answers cannot leave it unnoticed.
- **Errors:** a packet that does not decode stops the load bot and logs the frame in hex; a dropped connection is not
  reconnected. Stopped load bots count in gate criterion 4 (§8), never in run validity.

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
| `load-pve`         | fighting monsters in a hunting spot, target = nearest alive; a dead load bot restarts at its restart point and walks back                                                                                                                                                                                                 |
| `load-crowd-pvp`   | the E6 gate: N characters placed in a circle in the arena zone `colosseum_battle1` (no karma), two teams by index parity, read from the character name; each attacks the nearest enemy and casts a skill every K seconds; a dead load bot gets up in place with a Feather of Blessing, since a wall divides the arena from the restart points of the colosseum, which also keeps the crowd dense. N = 500 by default |

`Attack` is sent only when a load bot has no live target: the server repeats the attack by itself, so a second click
would be acknowledged by a hit already on its way and record a false latency. `UseSkill` is the steady combat intent.
No combat intent is sent during a cast, which the load bot times by the hit time of its own `MagicSkillUse`: the server
answers an attack then with `ActionFailed` alone, so it would end as a timeout.

## 6. Accounts and characters

The servers under load run on their own database `l2nx_load` in the `load-db` service of the compose profile `load`,
which has its own volume and cpuset, so a run neither touches nor shares the development `l2nx-db` (Spring profile `load` of
each server: that database, connection limits raised for one address, no address ban after failed logins since every load bot shares
that address, accounts not created on login). A seeded
snapshot is kept as the template database `l2nx_load_template`, and every run starts from
`CREATE DATABASE … TEMPLATE`, so runs start from the same state and never touch other databases. The database name differs from the template's and
from the two temporary names `db-snapshot` derives from it (`<template>_new`, `<template>_old`).

| Command       | Does                                                                                                                                | Servers |
| ------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ------- |
| `db-create`   | an empty `l2nx_load` with the server schemas and their owners                                                                       | down    |
| `seed`        | one pass per character plan; the first makes the accounts; each sets up a character with level, class, skills, items and position   | up      |
| `db-snapshot` | `l2nx_load` copied to `l2nx_load_template` through a temporary copy, so the template is never lost                                  | down    |
| `db-reset`    | `l2nx_load` again from the template                                                                                                 | down    |

Seeding goes through the protocol like a run: accounts are created by the login server itself on the first login
(profile `load-seed` turns that on), characters by `CharacterCreate`, and level, class, skills, items and position by
admin commands (`SendBypassBuildCmd`, the packet a client sends for `//`: `target`, `set_level`, `setclass`,
`give_all_skills`, `create_item`, `move_to`) under admin rights that only `load-seed` grants; the echo of `move_to`,
the last command, proves the others ran. The harness writes no server table and knows neither the schema nor password
hashing, and the game code itself stores the result.

Each account holds two characters, so one template serves every scenario: slot 0 stands in the circle of
`load-crowd-pvp`, slot 1 in the hunting spot of `load-pve`. The kit is level 75, a Gladiator with Fatal Strike or a
Sorcerer with Prominence, alternating by pairs of indexes so that both teams get both. A run
whose load bots lack an account fails on `LoginFail` instead of creating it unmeasured. The template also holds the game
server's registration on the login server, so `l2nx.load.p267.server-id` names the server registered while seeding
(until [#84](https://github.com/l2nx/nx-l2/issues/84), a rebuilt game server registers under a new id).

## 7. Measurement and report

**Server meters:** the compose profile `load` runs Prometheus (`l2nx-prometheus` on `127.0.0.1:9090`, config
`docker/prometheus/prometheus.yml`, 30 days of retention) scraping the management ports of the game and login servers
every second as the jobs `game` and `login`. The harness finds it through `l2nx.load.prometheus.url` and
`l2nx.load.prometheus.targets[]` (`job`, as in the Prometheus config, and `metrics-url`, the server's
`/actuator/prometheus` as the harness reaches it). A `run` needs it: before any load bot starts, Prometheus must
answer `/-/ready`, its clock (`vector(time())`) must lie within `l2nx.load.prometheus.max-clock-skew` (default 1 s, one
scrape) of the harness's clock read around the query, since the harness takes the window on its own clock and
Prometheus evaluates it on the Docker VM's, every configured target's job must have an active target in
`/api/v1/targets` (its `scrapeInterval`, the longest of the job's targets, is kept for the run) and `up{job="<job>"}`
must be 1, or the run fails at once with the reason (both clock readings for a skew). A protocol states whether a
server stands behind it (`ClientProtocol.hasServer`): the `in-memory` protocol has none and reads no server meters;
`seed` reads none either. After the measured phase the harness waits up to 10 s until Prometheus has ingested a scrape
of every target at or after the window's end, then evaluates each value below through `/api/v1/query` with
`time=<window end>` and the range `R` = the window in milliseconds, the precision of `time`, so every range and
`offset` is exactly `[Rms]`. Prometheus takes each end of the window from the latest sample at or before it, so the
counted interval may begin and end up to one scrape interval before the window. These are the expressions a dashboard
over the same window uses, so the report and the dashboard agree; the window (start, end, `R`, the interval actually
queried) is in `summary.json`, so any value can be re-evaluated while Prometheus keeps the data. An answer without the
Prometheus JSON envelope (a proxy, a wrong URL) fails the query with its status and URL.

**Counters** (every `_count`, `_sum`, `_total` and `_bucket` below) are read as the exact difference between the
sample at the window's end and the sample at its start, written `Δ(c)` = `(c - ((c offset Rms) or (c * 0)))`. The
subtraction and the `or` match on the full label set, so every series is paired with its own start sample before the
`sum by`; a series registered inside the window (`l2nx.engine.jdbc.on.loop` on a loop's first JDBC call, a new GC
cause, a new legacy packet type) has no start sample and counts from 0, where `increase` would drop it. Counter resets
are not handled: the servers do not restart within a run.

**Maxima** (pass, session queue depth, GC pause) come from Micrometer's decaying `_max` gauge, which may still hold an
observation from up to two minutes before the window. The harness caps the gauge's maximum over the range at the upper
bound of the highest bucket that gained observations in the window (the smallest `le` whose `Δ` reaches that of
`+Inf`, from the bucket query in the table) and reports no maximum when the window has no observation (`Δ` of `+Inf`
is 0) or the meter has no buckets. All three meters are percentile histograms: the pass timer and the session queue
depth by their builders, `jvm.gc.pause` through `management.metrics.distribution.percentiles-histogram` in
`l2nx-defaults.yaml`.

| Meter                                                        | Report value                                           | PromQL (`R` ms, at the window end)                                                                                                                                                                                                                                                                                                                                                                                          |
| ------------------------------------------------------------ | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `l2nx.engine.pass` (percentile histogram)                    | passes, p50, p99, max per `loop`                       | `sum by (loop) Δ(l2nx_engine_pass_seconds_count)`; `histogram_quantile(0.5, sum by (loop, le) Δ(l2nx_engine_pass_seconds_bucket))` and the same with `0.99`; max: `max by (loop) (max_over_time(l2nx_engine_pass_seconds_max[Rms]))` capped by `sum by (loop, le) Δ(l2nx_engine_pass_seconds_bucket)`                                                                                                                       |
| `l2nx.engine.pass.phase`                                     | mean per `loop` and phase                              | `sum by (loop, phase) Δ(l2nx_engine_pass_phase_seconds_sum) / sum by (loop, phase) Δ(l2nx_engine_pass_phase_seconds_count)`                                                                                                                                                                                                                                                                                                 |
| `l2nx.engine.pass.over.budget`, `failures`, `timers.overdue` | count per `loop`                                       | `sum by (loop) Δ(l2nx_engine_pass_over_budget_total)`, the same for `l2nx_engine_failures_total` and `l2nx_engine_timers_overdue_total`                                                                                                                                                                                                                                                                                     |
| `l2nx.engine.ownership.violations`                           | count per `loop`                                       | `sum by (loop) Δ(l2nx_engine_ownership_violations_total)` |
| `l2nx.engine.jdbc.on.loop`                                   | calls, seconds and share of pass time per `loop`       | `sum by (loop) Δ(l2nx_engine_jdbc_on_loop_seconds_count)`; `sum by (loop) Δ(l2nx_engine_jdbc_on_loop_seconds_sum)`, divided by `sum by (loop) Δ(l2nx_engine_pass_seconds_sum)`                                                                                                                                                                                                                                                                                                                                                                   |
| `l2nx.engine.inbox.depth`                                    | max and p99 of the per-second samples                  | `max by (loop) (max_over_time(l2nx_engine_inbox_depth[Rms]))`; `max by (loop) (quantile_over_time(0.99, l2nx_engine_inbox_depth[Rms]))`                                                                                                                                                                                                                                                                                     |
| `l2nx.session.queue.depth` (percentile histogram)            | p99, max                                               | `histogram_quantile(0.99, sum by (le) Δ(l2nx_session_queue_depth_bucket))`; max: `max(max_over_time(l2nx_session_queue_depth_max[Rms]))` capped by `sum by (le) Δ(l2nx_session_queue_depth_bucket)`                                                                                                                                                                                                                         |
| `l2nx.legacy.packet`                                         | mean per packet class (`type`)                         | `sum by (type) Δ(l2nx_legacy_packet_seconds_sum) / sum by (type) Δ(l2nx_legacy_packet_seconds_count)`                                                                                                                                                                                                                                                                                                                       |
| `jvm.gc.pause`, `process.cpu.usage`, `jvm.threads.live`      | per job: GC p99, mean, max; CPU mean; live threads max | `histogram_quantile(0.99, sum by (job, le) Δ(jvm_gc_pause_seconds_bucket))`; `sum by (job) Δ(jvm_gc_pause_seconds_sum) / sum by (job) Δ(jvm_gc_pause_seconds_count)`; max: `max by (job) (max_over_time(jvm_gc_pause_seconds_max[Rms]))` capped by `sum by (job, le) Δ(jvm_gc_pause_seconds_bucket)`; `avg by (job) (avg_over_time(process_cpu_usage[Rms]))`; `max by (job) (max_over_time(jvm_threads_live_threads[Rms]))` |

Engine rows come from the game server only, which alone runs the loops; JVM and process rows are reported per job.
A loop line is built from the union of the pass and failure series, so the session drain, which has no pass timer,
reports its failures with zero passes. The report also records `process_start_time_seconds` of each server.

**Grafana is not in the profile `load`:** a value of `summary.json` is checked by evaluating the same expression in
the Prometheus UI (`http://127.0.0.1:9090/query`, evaluation time = the window end).

**Native Linux Docker** (servers started on the host only, with `L2NX_PROMETHEUS_CONFIG` selecting `prometheus-host.yml`): the servers bind `127.0.0.1` (`server.address` in `l2nx-defaults.yaml`), and on native Linux
`host.docker.internal` resolves through `host-gateway` to the Docker bridge address, so Prometheus cannot reach them.
The profile works as is on Docker Desktop. On native Linux both servers run with `--server.address=0.0.0.0` (the
management ports then need the host firewall) or with the bridge address, the same address set in each target's
`metrics-url`.

**Generator self-check:** the harness measures its own event loop lag (scheduled against actual action time). A p99
lag above 10 ms (the default limit) marks the run invalid: the generator, not the server, was saturated. The check is
on the measured lag, not on the operating system, so a host whose timers cannot fire on time produces invalid runs by
itself: on Windows the 15.6 ms timer tick puts the generator lag p99 over the default limit, so even `in-memory` runs
come out `INVALID` there. The same tick delays the server's own loop on Windows
([#123](https://github.com/l2nx/nx-l2/issues/123)).

**Profiling:** a separate run after the gate runs, so a profiler never touches their numbers. The server
image is a JRE without `jcmd`, so the server starts with `-XX:StartFlightRecording`; async-profiler in wall-clock mode
samples every thread with the `world` thread as its own root, because JFR's execution samples miss a thread waiting on a socket, which is where JDBC on
the loop spends its time. The recordings are kept with the run.

**Report** in `build/load/<run>/`: `summary.json` (scenario, its measured phase and final state, the number of load bots that reached it, seed, N, windows, server commit, environment, client
p50, p99, p99.9 and max per intent and per lifecycle step, timeout, rejection and stopped shares, the server window
`serverWindow` and the server values above), an `.hgrm` per intent and per lifecycle step,
`server-metrics-<job>-start.prom` and `server-metrics-<job>-end.prom`, the text exposition of each server fetched at
the window's start and end and kept raw for a later look (the numbers come from Prometheus; a failed fetch only loses
its file), and a table on the console. When Prometheus has no scrape at or after the window's end in time, or a query
fails, the server values are left out and the window stays in `summary.json`; the gate then fails criteria 1 and 2
for missing meters instead of the run losing its client results.
The server values are also left out when any target's `up` fell below 1 or has no series from one scrape before the
window's start to its end (`min_over_time(up{job="<job>"}[(R+S)ms])`, `S` the job's scrape interval), because a
failed scrape at the start leaves a stale marker where `offset` looks for the start sample, and `Δ` would then count
the whole accumulated counter.
The wait for the window's end retries a poll only on an I/O error or a 5xx answer; a refused query ends it at once and
leaves the server values out with the cause in the log. A measured ramp that has not ended by the drain (a load bot
still arriving) leaves them out too, with a warning, since its window would run on over the drain; the client results
stay, as they count every arrival by its intended start.

**Start:** the compose profile `load` runs everything; the commands are in
[load-baseline.md](../runbooks/load-baseline.md). Prepare once: `db-create`, the servers with the profiles `load,load-seed`,
`seed`, the servers stopped, `db-snapshot`. Before every run: `db-reset` with the servers down, fresh servers with the
profile `load`, then
`docker compose --profile load run --rm harness run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-crowd-pvp --l2nx.load.bots=500 --l2nx.load.seed=42`.
Always name the services: a bare `up` or `down` on the profile also recreates or removes the development `l2nx-db`. The
injection profile leaves the last load bot the ramp-down and drain time to reach its final state; a run ends as soon as
every load bot is done. `seed` writes no report.

## 8. E6 gate

The `gate` command compares the reports of a scenario with its baseline:

| #   | Criterion                                                                   | Threshold         |
| --- | --------------------------------------------------------------------------- | ----------------- |
| 1   | `l2nx.engine.pass` p99 over the measured phase, every loop                  | ≤ `pass-budget`   |
| 2   | Share of passes over budget                                                 | ≤ 0.1%            |
| 3   | Client action latency p99, every intent                                     | ≤ baseline × 1.10 |
| 4   | Timed-out intents; intents the server rejected; load bots stopped on errors | ≤ 0.5% each       |
| 5   | Load bots that did not reach the final state                                | ≤ 0.5%            |
| 6   | `l2nx.engine.ownership.violations`                                          | 0                 |

- Criteria 1 and 2 apply to runs whose final state is `IN_GAME`: before it the engine has nothing of the load bots to
  run, so they are reported as not applicable.
- A scenario runs three times, each run its own `run` from `db-reset` with fresh server processes (server state left
  by one run, such as accounts still marked on the login server, must not leak into the next), and the gate takes the median of each criterion's per-run values, except criterion 6: one violation in any run is a defect. An invalid run does
  not count; with fewer than three valid runs the gate fails with that reason.
- Thresholds live in the gate settings. The baseline numbers and the method behind them are kept in
  `docs/runbooks/load-baseline.md`.
- **Baseline:** a set of runs of the current build, under the same validity rules, of which the gate takes the median.
  There is no legacy baseline: the single writer is not going back, so its cost against the old pools decides nothing.
  The first three runs on a build become the baseline, criterion 3 is reported as not applicable for them and E6 is
  accepted on the absolute criteria; from then on criterion 3 guards against regressions. Its limit is the larger of
  baseline × 1.10 and baseline plus an absolute floor, and it covers lifecycle steps as well as intents.
- **Validity is about the generator only:** stopped load bots always count in criterion 4, so a server that drops them
  fails the gate instead of invalidating the run.
- **Where it runs:** one machine, Linux, the way load generators and game servers run in production. The server,
  the load harness and `load-db` are services of the compose profile `load`, each pinned to its own cores with
  `cpuset` and limited with `cpus`; the harness uses Netty's native epoll transport, whose timers fire with
  sub-millisecond precision. On a Windows or macOS workstation the same compose runs in Docker's Linux VM (WSL2 on
  Windows); there the cpusets pin VM vCPUs that the host may still move, which a baseline taken in the same setup
  absorbs. A native Windows run (`bootRun`) is for development: its timer granularity shows as generator lag and the
  self-check marks the run invalid. Raising the process timer resolution (`timeBeginPeriod`) is not used: it is
  Windows-only and measures a platform the server does not run on.
- **Same scenario only:** the gate compares reports of one scenario and final state with a baseline of the same;
  otherwise it fails with that reason and compares nothing.
- **Comparable runs only:** the report records the environment (operating system and kernel, CPU model, logical
  cores, the cpuset of each service, JVM version), and the gate fails when a report's environment, injection, protocol, intent timeout, lifecycle timeout or
  measured phase differs from the baseline's, or when a server process started before the previous run. The gate is relative to a baseline taken on the same machine; criteria 1 and 2 are absolute because the
  engine measures them itself.
- E6 is accepted when `load-crowd-pvp` passes the gate; its cutover landed on main before the gate, on the owner's decision.

## 9. Tests and trust

- **Codecs:** golden tests in `protocols/*` (§3).
- **Client adapter:** intent → expected frame bytes; golden server frames → expected observations; every intent has
  an acknowledgement.
- **Core** on a fake `ClientProtocol` with fault injection (`FakeProtocol`, test code of `tools/load/core`: stalls,
  slow answers, rejections, decode failures, drops, refused identities) and Netty `EmbeddedEventLoop`: latency taken
  from the intended time, including a stalled fake server (a 2 s stall must show in the latency); injection profile
  phases; the invalid mark on generator lag. The runnable `in-memory` protocol of the application
  (`InMemoryProtocol`) measures the generator's own overhead and drives the application's tests; core cannot depend
  on it, and it injects no faults.
- **Server meters:** the PromQL text of every value, the mapping of recorded `/api/v1/query` and `/api/v1/targets` answers, and one
  Testcontainers test where a real Prometheus scrapes a stub endpoint and the harness reads known quantiles back.
- **Gate:** report fixtures for pass, fail, invalid and too few runs.
- **One integration test:** Testcontainers PostgreSQL, login and game servers in the test context, 20 load bots in
  `load-roam` for 30 s; all reach `IN_GAME`, no errors, window snapshots written.

**Before the gate is trusted** (once, results in `load-baseline.md`):

1. A real client stands in the `load-crowd-pvp` spot: load bots are visible, walk, hit and cast.
2. Sensitivity: the same run with the server limited to two cores must raise client p99 and fail the gate.
3. Repeatability: three runs with one seed spread their p99 by at most 10%; a wider spread widens the thresholds or
   the number of runs, recorded with the reason.

## 10. Stages

| #   | Delivers                                                                                                                                                                                                                                                                                                                      | Needs                    |
| --- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------ |
| P0  | `tools/load/core`, gate, `app` on an in-memory protocol                                                                                                                                                                                                                                                                       | —                        |
| P1  | login part of `protocol-p267` (the whole `c621` exchange up to `PlayOk`); lifecycle steps, measured phase and final state in the core; the `load` profiles of the login server and the database commands; `load-login-storm` up to `GAME_CONNECT`; server meters from Prometheus (the compose profile `load` with Prometheus) | `protocols/login/c621`   |
| P2  | game part: entering the world, `load-roam`, the integration test                                                                                                                                                                                                                                                              | first `game/p267` codecs |
| P3  | combat codecs (§3), `load-pve`, `load-crowd-pvp`, the compose profile `load` gains the servers, the harness and `load-db` with cpusets, and the environment check; the trust checks, the baseline                                                   | P2                       |
| P4  | dev portal page (§11)                                                                                                                                                                                                                                                                                                         | P3 numbers               |

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
