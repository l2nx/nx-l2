# Load baseline

How a load run is prepared and repeated, how the gate is applied, and where the numbers of the E6 baseline are kept.
Method and criteria: [spec 009 §7 to §9](../specs/009-load-harness.md#7-measurement-and-report). Everything below runs
in the compose profile `load`; a run on the host with `bootRun` is for development only (see
[Development on the host](#development-on-the-host)).

## Services

| Service        | `cpuset` | `cpus` | Memory | Role                                                                                 |
| -------------- | -------- | ------ | ------ | ------------------------------------------------------------------------------------ |
| `game-server`  | 0-3      | 4      | 6 GB   | server under load; shares the network namespace of `login-server`                    |
| `harness`      | 4-9      | 6      | 3 GB   | load bots and database commands, started on demand, reports in `build/load/`         |
| `load-db`      | 10-11    | 2      | 2 GB   | PostgreSQL on `127.0.0.1:5433`, volume `l2nx-load-db-data`                           |
| `login-server` | 10-11    | 1.5    | 768 MB | idle in the steady phase                                                             |
| `prometheus`   | 10-11    | 1      | 2 GB   | scrapes both servers every second, UI on `127.0.0.1:9090`, light in the steady phase |

The harness gets more cores than the server: every load bot decodes the broadcasts of all it sees, so the generator
work grows with the square of a spot, while the server's world loop is one thread. With 4 harness cores the generator
gave out at 1,250 load bots before the server did (generator lag p99 13.8 ms, world loop 57% busy).
The split is made for 12 logical cores with SMT siblings numbered next to each other, so no two groups share a physical
core. Check the numbering with `cat /sys/devices/system/cpu/cpu0/topology/thread_siblings_list` inside any container
(`0-1` means adjacent). On another machine set the variables below; all limits together take 13.75 GB of the Docker VM.

| Variable                    | Default          | Meaning                                                                                                                            |
| --------------------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `L2NX_LOAD_GAME_CPUSET`     | `0-3`            | cores of `game-server`                                                                                                             |
| `L2NX_LOAD_GAME_CPUS`       | `4`              | CPU limit of `game-server`, equal to its core count; `2` throttles the whole process, the loop included, for the sensitivity check |
| `L2NX_LOAD_HARNESS_CPUSET`  | `4-9`            | cores of `harness`                                                                                                                 |
| `L2NX_LOAD_HARNESS_CPUS`    | `6`              | CPU limit of `harness`, equal to its core count                                                                                    |
| `L2NX_LOAD_SHARED_CPUSET`   | `10-11`          | cores of `load-db`, `login-server` and `prometheus`                                                                                |
| `L2NX_LOAD_SPRING_PROFILES` | `load`           | Spring profiles of both servers; `load,load-seed` while seeding                                                                    |
| `L2NX_GEODATA_DIR`          | `./geodata`      | geodata, mounted read-only; a missing directory fails the start                                                                    |
| `L2NX_PROMETHEUS_CONFIG`    | `prometheus.yml` | file in `docker/prometheus/`; `prometheus-host.yml` for host servers                                                               |

**Always name the services.** The compose project also holds the development database `db`: a bare
`docker compose --profile load up` may recreate `l2nx-db` and `docker compose down` removes it. Stop the load services
with `rm -sf <services>` as below, never with `down`.

## Build

After every change of the servers or the harness; `up` does not rebuild, and an old image runs old code without a word:

```sh
./gradlew :login:boot:installDist :chronicles:high-five:boot:installDist :tools:load:app:installDist
docker compose --profile load build
```

Geodata lies in the repository root `geodata/`; a worktree without it sets `L2NX_GEODATA_DIR`.

## Prepare

Once, and again when a server's Liquibase baseline changes: the template then holds the old schema. Start again from an
empty database with `docker compose --profile load rm -sf load-db` and `docker volume rm l2nx-load-db-data`.

```sh
docker compose --profile load up -d --wait load-db prometheus
docker compose --profile load run --rm harness db-create

L2NX_LOAD_SPRING_PROFILES=load,load-seed docker compose --profile load up -d --wait login-server game-server
docker compose --profile load run --rm harness seed --l2nx.load.protocol=p267 --l2nx.load.bots=2001 \
  --l2nx.load.ramp=720s --l2nx.load.intent-timeout=30s

docker compose --profile load rm -sf game-server login-server
docker compose --profile load run --rm harness db-snapshot
```

Seed one account more than the top level of the capacity run and no fewer than the 1,000 of `load-login-storm`: a
load bot without an account fails its login. The last account is the real client's (trust check 1).

The game server's registration lives in two places that belong together: its row in the template and `hexid.txt` on the
volume `l2nx-load-game-state`, which outlives the containers. Removing the volume alone makes the next start register
under a new server id, and every load bot then fails on the server list; prepare again in that case.

## Each run

The host stays quiet during a run: no build, no other container under load. Every run starts from the template with
fresh server processes:

```sh
docker compose --profile load rm -sf game-server login-server
docker compose --profile load run --rm harness db-reset
docker compose --profile load up -d --wait login-server game-server
# load-crowd-pvp at the gate size N (see Results)
docker compose --profile load run --rm harness run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-crowd-pvp \
  --l2nx.load.bots=<N> --l2nx.load.ramp=120s --l2nx.load.seed=42 --l2nx.load.server-commit="$(git rev-parse HEAD)"
# load-login-storm: 1,000 logins over 60 s, measured over the ramp up to IN_GAME
docker compose --profile load run --rm harness run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-login-storm \
  --l2nx.load.bots=1000 --l2nx.load.ramp=60s --l2nx.load.seed=42 --l2nx.load.server-commit="$(git rev-parse HEAD)"
```

The report lands in `build/load/<run>/`. A server's logs go with its container: read them with
`docker compose --profile load logs game-server` before the next `rm`. A run with `up` below 1 loses its server values
(spec 009 §7); look at `http://127.0.0.1:9090/targets` first.

## Gate

Five runs of one scenario as above, then:

```sh
docker compose --profile load run --rm harness gate \
  --reports=build/load/<run 1>,…,build/load/<run 5> \
  --baseline=build/load/<baseline run 1>,…,build/load/<baseline run 5>
```

The exit code is 0 for a pass. The first five runs of a build become the baseline: run the gate without `--baseline`,
so criterion 3 is reported as not applicable. A baseline is valid for the setup it was taken on; the gate refuses a
report with another environment, CPU affinity, injection, protocol, intent timeout, lifecycle timeout or measured phase. It also
refuses a report of another `reportVersion` (retake the baseline after the harness changes what it measures) and a
capacity run. Copy baseline reports out of `build/` before a `clean`.

## Capacity

One run with levels (spec 009 §5) finds how many load bots one spot holds within the budget and where the harness
itself gives out. It is never gated. From a fresh `db-reset` and fresh servers:

```sh
docker compose --profile load run --rm harness run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-crowd-pvp \
  --l2nx.load.bots=500 --l2nx.load.ramp=120s --l2nx.load.warm-up=30s --l2nx.load.levels.increment=250 \
  --l2nx.load.levels.count=7 --l2nx.load.levels.duration=90s --l2nx.load.levels.ramp=30s --l2nx.load.seed=42
```

Levels 500, 750, …, 2,000, each measured for 60 s after its 30 s warm-up, about 16 minutes. `summary.json` gives
`levels[]` (server window, pass p99 and share over budget per loop, busy share per loop, input p99 per class, generator
lag p99, valid) and `capacity` (`bots`, `limitedBy`, `reason`). A level with generator lag over
`l2nx.load.max-generator-lag-p99` is invalid: the capacity is then "at least that, limited by the harness". The gate
size N is the level whose `world` loop is 50 to 70% busy (`levels[].busyShare`, also on the console).

## Trust checks

With every new baseline, at the gate size N (spec 009 §9). Results go to [Results](#results).

**1. A real client in the spot.** Seed one load bot more than a run uses, and log in with the last seeded account, the
password being `l2nx.load.accounts.password`. Start the servers with the client ports published:

```sh
docker compose -f compose.yaml -f docker/compose.client.yaml --profile load up -d --wait login-server game-server
```

Point the client at `127.0.0.1:2106`, walk to the `load-crowd-pvp` spot and start the run: load bots must be visible,
walk, hit and cast. `login-server` hands the client `127.0.0.1` by matching the network gateway in
`docker/game-server/ipconfig.xml`. Should the client stop after the server list, compare that address with the source
address in the log of `login-server`. The published ports must be free of servers started on the host.

**2. Sensitivity.** Five runs of each gated scenario with the game server under a CPU quota of half its cores on its
usual cpuset must fail criteria 1 and 3 against the baseline on measured values. The world loop is one thread, so fewer
cores alone do not slow it; the quota throttles the whole process. A quota of half a core drowns the server at the gate
size and loses its meters. It is set on the `up` line only and is not part of the environment the gate compares; keep
these reports out of any baseline:

```sh
L2NX_LOAD_GAME_CPUS=2 docker compose --profile load up -d --wait login-server game-server
```

**3. Repeatability.** An A/A gate: five more runs of the same build, gated against the baseline, must pass. A false
failure widens the failing threshold, recorded below with the reason.

## Profiling

A separate run after the gate runs, so a profiler never touches their numbers (spec 009 §7). `docker/compose.profiling.yaml`
starts the same image with JFR on and mounts async-profiler from `build/async-profiler/`. Get the profiler once:

```sh
mkdir -p build/load/profiling build/async-profiler
curl -fsSL -o build/async-profiler.tar.gz \
  https://github.com/async-profiler/async-profiler/releases/download/v4.6/async-profiler-4.6-linux-x64.tar.gz
echo "1e79ed54d8952889a2e74f993d22ee925e3322460d4f37a2ed95bfb88585da36  build/async-profiler.tar.gz" | sha256sum -c
tar -xzf build/async-profiler.tar.gz -C build/async-profiler --strip-components=1
```

The run, with the wall-clock samples taken during the steady phase from a second terminal:

```sh
docker compose --profile load rm -sf game-server login-server
docker compose --profile load run --rm harness db-reset
docker compose -f compose.yaml -f docker/compose.profiling.yaml --profile load up -d --wait login-server game-server
docker compose --profile load run --rm harness run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-crowd-pvp \
  --l2nx.load.bots=<N> --l2nx.load.ramp=120s --l2nx.load.seed=42

docker compose --profile load exec --user l2nx game-server \
  /opt/async-profiler/bin/asprof -d 120 -e wall -i 5ms -t -f /opt/game-server/profiling/wall-%t.jfr 1
```

In Git Bash on Windows, prefix the `exec` line with `MSYS_NO_PATHCONV=1`, or the container paths get rewritten.

JFR writes `game-server.jfr` when the server stops, so stop it inside its grace period and keep the recordings with the
run:

```sh
docker compose --profile load rm -sf game-server login-server
mv build/load/profiling/* build/load/<run>/
```

The flame graph has one root per thread with `-t`, the loop being `l2nx-loop-world`:

```sh
docker run --rm -v "$PWD/build:/b" --entrypoint /b/async-profiler/bin/jfrconv l2nx/load \
  --threads -o html /b/load/<run>/wall-<time>.jfr /b/load/<run>/wall.html
```

- **Capabilities:** none. Wall-clock mode samples with signals, not with `perf_event_open`, so it runs under Docker's
  default seccomp profile as the non-root user of the image, on Docker Desktop too. `asprof` brings its own attach, so
  the JRE needs no `jcmd`, and `-XX:+EnableDynamicAgentLoading` only silences the JVM's warning about the attach.
- **Kernel frames** do not appear; time in a socket read shows on the native frame of the read, which is enough to see
  JDBC on the loop.
- **JFR settings:** `L2NX_LOAD_JFR_SETTINGS` takes another configuration than `profile`.

## Native Linux

Bind mounts keep the host's ownership there, and the containers write as the images' non-root user: run
`mkdir -p build/load/profiling && chmod -R a+w build/load` before the first run.

## Development on the host

Servers started with `bootRun` or from the distribution use the published `load-db` port (Spring profile `load`), and
Prometheus scrapes them through `host.docker.internal`:

```sh
L2NX_PROMETHEUS_CONFIG=prometheus-host.yml docker compose --profile load up -d load-db prometheus
```

## Results

Every row names the reports it comes from; the values are those of `summary.json`. Reports are kept outside the
repository, under their names.

### Gate v2 (report version 2)

Reports are kept outside the repository, under their names (`gate-v2` set of 2026-11-09).

**Environment**

| Operating system, kernel             | CPU model, logical cores        | cpusets                                                      | JVM                  | Commit      |
| ------------------------------------ | ------------------------------- | ------------------------------------------------------------ | -------------------- | ----------- |
| Docker Desktop, WSL2 6.6.87.2, Linux | AMD Ryzen 5 5600X, 12 (6 + SMT) | game-server 0-3, harness 4-9, load-db login prometheus 10-11 | OpenJDK 25.0.4.1 LTS | `f69f2d318` |

**Capacity** (`load-crowd-pvp`, levels of 250 from 500, 30 s warm-up and 60 s measured per level; report
`load-crowd-pvp-20261109-231250-42`)

| Load bots | World loop busy | Pass p99 | Over budget | Timeouts | Generator lag p99 |
| --------- | --------------- | -------- | ----------- | -------- | ----------------- |
| 500       | 11%             | 1.2 ms   | 0           | 0.04%    | 0.12 ms           |
| 750       | 17%             | 2.3 ms   | 0           | 0.08%    | 0.14 ms           |
| 1,000     | 31%             | 9.1 ms   | 0           | 0.03%    | 0.16 ms           |
| 1,250     | 72%             | 46.0 ms  | 0.68%       | 0.03%    | 0.24 ms           |
| 1,500     | 94%             | 268 ms   | 22.9%       | 12.2%    | 0.30 ms           |
| 1,750     | 101%            | 2,337 ms | 100%        | 24.6%    | 0.38 ms           |
| 2,000     | 100%            | 3,844 ms | 100%        | 59.1%    | 0.42 ms           |

Capacity **1,000** load bots in one spot, limited by the pass budget (level 1,250: pass p99 46 ms, 0.68% of passes
over budget). The gate size **N = 1,000**: the highest level within the budget; 1,250 would fail criterion 2 by itself.
With the earlier split (game-server 0-5, harness 6-9) the generator gave out first, at 1,250 (lag p99 13.8 ms).

The generator lag of every report up to 2026-11-16 was taken from load bot tasks (how late an action, connect or leave
ran against its intended time); later reports take it from the 1 ms probe of every event loop (spec 009, generator
self-check). The probe also counts the idle stretches between actions, so its p99 is not comparable with these
numbers; a new baseline measures it afresh.

**Baseline `load-crowd-pvp`** (N = 1,000, ramp 120 s, steady 120 s, seed 42; gate without `--baseline`: PASS)

| Run | Pass p99 | Over budget | World loop busy | Input p99: `ReportPosition`, `Action`, `RequestMagicSkillUse` | Timeouts | Report                              |
| --- | -------- | ----------- | --------------- | ------------------------------------------------------------- | -------- | ----------------------------------- |
| 1   | 7.95 ms  | 0           | 27.5%           | 9.5, 9.7, 10.0 ms                                             | 0.052%   | `load-crowd-pvp-20261109-233023-42` |
| 2   | 9.54 ms  | 0           | 30.0%           | 10.1, 11.4, 11.7 ms                                           | 0.031%   | `load-crowd-pvp-20261109-233608-42` |
| 3   | 10.59 ms | 1           | 32.1%           | 12.5, 13.0, 13.8 ms                                           | 0.032%   | `load-crowd-pvp-20261109-234151-42` |
| 4   | 11.87 ms | 3           | 35.4%           | 16.9, 17.4, 19.0 ms                                           | 0.037%   | `load-crowd-pvp-20261109-234735-42` |
| 5   | 8.74 ms  | 0           | 28.8%           | 10.6, 11.3, 11.7 ms                                           | 0.015%   | `load-crowd-pvp-20261109-235319-42` |

**Baseline `load-login-storm`** (1,000 logins over 60 s up to `IN_GAME`, seed 42; gate without `--baseline`: PASS)

| Run | Pass p99 | Over budget | World loop busy | JDBC share of the loop | Timeouts | Report                                |
| --- | -------- | ----------- | --------------- | ---------------------- | -------- | ------------------------------------- |
| 1   | 20.85 ms | 2           | 44.2%           | 42.6%                  | 0        | `load-login-storm-20261109-235908-42` |
| 2   | 20.43 ms | 3           | 43.4%           | 42.1%                  | 0        | `load-login-storm-20261110-000347-42` |
| 3   | 19.94 ms | 6           | 43.0%           | 43.2%                  | 0        | `load-login-storm-20261110-000825-42` |
| 4   | 20.29 ms | 9           | 43.3%           | 42.4%                  | 0        | `load-login-storm-20261110-001304-42` |
| 5   | 61.52 ms | 138         | 44.2%           | 45.6%                  | 0.2%     | `load-login-storm-20261110-001743-42` |

JDBC is 42 to 46% of the world loop's busy time in the login ramp ([#204](https://github.com/l2nx/nx-l2/issues/204)).

**Trust checks**

| Check                   | Date       | Result                                                                                                                                                                                                                                                                                                                                                                                 | Reports                    |
| ----------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| Real client             | -          | not run yet at N = 1,000                                                                                                                                                                                                                                                                                                                                                               | -                          |
| Sensitivity, `cpus` 0.5 | 2026-11-10 | **failed as expected, but on missing meters**: at N = 1,000 the server drowns (97.6% timeouts) and Prometheus gets no scrape; `load-login-storm` fails on measured values (pass p99 20 s, `EnterWorld` input p99 26.7 s)                                                                                                                                                               | `crowd-slow`, `storm-slow` |
| Sensitivity, `cpus` 2   | 2026-11-10 | **failed on measured values**: `load-crowd-pvp` pass p99 83.5 ms against the 50 ms budget, over budget 4.2%, input p99 180 to 192 ms against limits of 11.7 to 13.7 ms, timeouts 22.9%                                                                                                                                                                                                 | `crowd-cpu2`               |
| Repeatability, spread   | 2026-11-10 | **failed**: (max − min) / median of the five baseline runs is 41% for the pass p99 and 68 to 77% for input p99 of `load-crowd-pvp`; `load-login-storm` 4 runs within 3% on the pass p99 and one outlier (61.5 ms). All phases of a slow run slow down together (mean `deadlines` 0.51 to 0.78 ms) on fresh servers and data, so the spread is the host's speed, not the server's state | the baselines              |
| Repeatability, A/A gate | 2026-11-10 | five more runs of the same build against the baseline: `load-crowd-pvp` **PASS** (pass p99 9.27 against 9.54 ms; input p99 within 15% of their limits); `load-login-storm` **FAIL on one line**: `AuthenticationResult` input p99 2.54 ms against the limit 2.43 ms (baseline 1.93 ms + 0.5 ms floor), the other 12 lines pass; the floor is 1 ms since                                | `crowd-aa`, `storm-aa`     |

**Network hand-off** ([#194](https://github.com/l2nx/nx-l2/issues/194): one event loop wakeup per pass, connection
references held by the connection; same setup as the gate v2 baseline; reports `load-crowd-pvp-20261110-033826-42`
(capacity) and `load-crowd-pvp-20261110-035520-42` (wall-clock profile of the steady phase at N = 1,000))

| Load bots | World loop busy, before → after | Pass p99, before → after | Over budget, before → after |
| --------- | ------------------------------- | ------------------------ | --------------------------- |
| 1,000     | 31% → 26%                       | 9.1 → 5.5 ms             | 0 → 0                       |
| 1,250     | 72% → 59%                       | 46.0 → 24.4 ms           | 0.68% → 0.07%               |
| 1,500     | 94% → 94%                       | 268 → 304 ms             | 22.9% → 21.3%               |

Capacity 1,000 → **1,250** load bots. In the steady profile the hand-off to the event loops (`execute`) falls from about
54% of the loop's busy time (gate v1 profile, 500 load bots) to 2.4% (plus 0.2% `lazyExecute`), attribute lookups from
about 8% to 0, `serializeLegacy` from about 20% to 5.2%.

**#194 and #204 against the baseline** (five runs each on the uncommitted change of 2026-11-10 after the review fixes,
gate against the gate v2 baseline above; reports listed in `crowd-after.ids` and `storm-after.ids` of the `gate-v2` set)

| Scenario           | Gate | Pass p99 median, before → after | World loop busy | Input p99, before → after                                                                | Loop JDBC per run      |
| ------------------ | ---- | ------------------------------- | --------------- | ---------------------------------------------------------------------------------------- | ---------------------- |
| `load-crowd-pvp`   | PASS | 9.54 → 5.55 ms                  | 30% → 25%       | `Action` 11.4 → 8.3 ms, `ReportPosition` 10.6 → 8.1 ms                                   | 0                      |
| `load-login-storm` | PASS | 20.43 → 11.64 ms                | 44% → 20%       | `SelectCharacter` 51.9 → 5.4 ms, `ListCharacters` 27.1 → 5.6 ms, `EnterWorld` 47 → 22 ms | 0 reads, ~4,950 writes |

With the 0.5 ms floor `load-login-storm` failed on one line of the unchanged login path, `AuthenticationResult` input
p99 2.81 ms against 2.43 ms, the same noise as the A/A run; with the 1 ms floor the gate passes, and so does the A/A
run. The background executor stays idle in that ramp: at most 1 queued task and 7 active threads of
`applicationTaskExecutor`, no value transaction in the window, so `spring.task.execution.pool.*` stays at the Boot
default. The loop reads nothing from the database
during the login ramp; the writes left are the listed exceptions until E4 (about five per login, 21% of the loop's
busy time).

### Gate v1 (superseded by report version 2)

**Environment**

| Operating system, kernel             | CPU model, logical cores        | cpusets                                                      | JVM                  | Commit                                                                   |
| ------------------------------------ | ------------------------------- | ------------------------------------------------------------ | -------------------- | ------------------------------------------------------------------------ |
| Docker Desktop, WSL2 6.6.87.2, Linux | AMD Ryzen 5 5600X, 12 (6 + SMT) | game-server 0-5, harness 6-9, load-db login prometheus 10-11 | OpenJDK 25.0.4.1 LTS | `49143aad6` (reports carry `f3fa05794`, the same tree before the rebase) |

**Baseline** (`load-crowd-pvp`, 500 load bots, ramp 120 s, steady 120 s, seed 42; gate without `--baseline`: PASS)

| Run | Valid | World pass p99 | Over budget | `Attack` p99 | `UseSkill` p99 | `MoveTo` p99 | Timeouts | Stopped | Report                              |
| --- | ----- | -------------- | ----------- | ------------ | -------------- | ------------ | -------- | ------- | ----------------------------------- |
| 1   | yes   | 1.58 ms        | 0           | 954 ms       | 1,260 ms       | none sent    | 0.021%   | 0       | `load-crowd-pvp-20261104-185239-42` |
| 2   | yes   | 2.19 ms        | 0           | 944 ms       | 1,258 ms       | none sent    | 0.052%   | 0       | `load-crowd-pvp-20261104-185834-42` |
| 3   | yes   | 1.52 ms        | 0           | 1,060 ms     | 1,258 ms       | none sent    | 0.052%   | 0       | `load-crowd-pvp-20261107-195037-42` |

`Attack` and `UseSkill` p99 are set by game timing, not by the server: an attack waits for the attack speed and the
second click 150 ms later, a skill for its cast time (`UseSkill` p99 equals the 1.26 s hit time in every run).
JDBC on the loop is 0 in all three steady phases.

**Trust checks**

| Check         | Date       | Result                                                                                                                                                                                                                                                                                                 | Reports                                                                                                   |
| ------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| Real client   | 2026-11-07 | passed: account `load500` in the arena; load bots visible, run up, hit and cast; the client was killed and offered `To Village` and `Use Feather`                                                                                                                                                      | `load-crowd-pvp-20261107-205017-42` (with `client-*.png`)                                                 |
| Sensitivity   | 2026-11-07 | **failed**: on two physical cores (`0-3`) the gate passes, world pass p99 1.11 ms; only on one physical core (`0-1`) it fails, on `ResurrectInPlace` p99 24.9 ms against 17.3 ms                                                                                                                       | `0-3`: `…20261107-195659-42`, `…200246-42`, `…200834-42`; `0-1`: `…201448-42`, `…202040-42`, `…202633-42` |
| Repeatability | 2026-11-07 | **failed**: p99 spread over the baseline runs is `Attack` 12.1%, `UseSkill` 0.2%, `ResurrectInPlace` 176% (6.3 to 19.1 ms, a spread of 12.8 ms; it passed only because the 10 ms floor is added to the baseline, the limit being the larger of baseline × 1.10 and baseline + 10 ms), world pass 42.6% | the baseline runs                                                                                         |

**Where the world pass goes** (wall-clock profile of `l2nx-loop-world`, 5 ms interval, 500 load bots; shares of the
loop's busy time)

| Phase                   | Loop busy | Biggest parts                                                                                                                                       | Report                              |
| ----------------------- | --------- | --------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| `load-crowd-pvp` ramp   | 12.3%     | JDBC 31% (`SelectCharacterHandler` 25%, `EnterWorldHandler` 34% inclusive), packet sends 26%                                                        | `load-crowd-pvp-20261107-203242-42` |
| `load-crowd-pvp` steady | 5.5%      | packet sends 46% (broadcast over visible players), per-receiver serialization 20%, Netty attribute lookups per packet 7%, `WorldPartition.bound` 7% | `load-crowd-pvp-20261107-203242-42` |
| `load-pve` ramp         | 12.3%     | JDBC 37%                                                                                                                                            | `load-pve-20261107-203830-42`       |
| `load-pve` steady       | 5.9%      | movement 44% (region switch and reveal 33%), packet sends 44%, per-receiver serialization 31%                                                       | `load-pve-20261107-203830-42`       |
