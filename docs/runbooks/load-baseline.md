# Load baseline

How a load run is prepared and repeated, how the gate is applied, and where the numbers of the E6 baseline are kept.
Method and criteria: [spec 009 §7 to §9](../specs/009-load-harness.md#7-measurement-and-report). Everything below runs
in the compose profile `load`; a run on the host with `bootRun` is for development only (see
[Development on the host](#development-on-the-host)).

## Services

| Service        | `cpuset` | `cpus` | Memory | Role                                                                                 |
| -------------- | -------- | ------ | ------ | ------------------------------------------------------------------------------------ |
| `game-server`  | 0-5      | 6      | 6 GB   | server under load; shares the network namespace of `login-server`                    |
| `harness`      | 6-9      | 4      | 3 GB   | load bots and database commands, started on demand, reports in `build/load/`         |
| `load-db`      | 10-11    | 2      | 2 GB   | PostgreSQL on `127.0.0.1:5433`, volume `l2nx-load-db-data`                           |
| `login-server` | 10-11    | 1.5    | 768 MB | idle in the steady phase                                                             |
| `prometheus`   | 10-11    | 1      | 2 GB   | scrapes both servers every second, UI on `127.0.0.1:9090`, light in the steady phase |

The split is made for 12 logical cores with SMT siblings numbered next to each other, so no two groups share a physical
core. Check the numbering with `cat /sys/devices/system/cpu/cpu0/topology/thread_siblings_list` inside any container
(`0-1` means adjacent). On another machine set the variables below; all limits together take 13.75 GB of the Docker VM.

| Variable                    | Default          | Meaning                                                              |
| --------------------------- | ---------------- | -------------------------------------------------------------------- |
| `L2NX_LOAD_GAME_CPUSET`     | `0-5`            | cores of `game-server`                                               |
| `L2NX_LOAD_GAME_CPUS`       | `6`              | CPU limit of `game-server`, equal to its core count                  |
| `L2NX_LOAD_HARNESS_CPUSET`  | `6-9`            | cores of `harness`                                                   |
| `L2NX_LOAD_HARNESS_CPUS`    | `4`              | CPU limit of `harness`, equal to its core count                      |
| `L2NX_LOAD_SHARED_CPUSET`   | `10-11`          | cores of `load-db`, `login-server` and `prometheus`                  |
| `L2NX_LOAD_SPRING_PROFILES` | `load`           | Spring profiles of both servers; `load,load-seed` while seeding      |
| `L2NX_GEODATA_DIR`          | `./geodata`      | geodata, mounted read-only; a missing directory fails the start      |
| `L2NX_PROMETHEUS_CONFIG`    | `prometheus.yml` | file in `docker/prometheus/`; `prometheus-host.yml` for host servers |

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
docker compose --profile load run --rm harness seed --l2nx.load.protocol=p267 --l2nx.load.bots=501 \
  --l2nx.load.ramp=180s --l2nx.load.intent-timeout=30s

docker compose --profile load rm -sf game-server login-server
docker compose --profile load run --rm harness db-snapshot
```

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
docker compose --profile load run --rm harness run --l2nx.load.protocol=p267 --l2nx.load.scenario=load-crowd-pvp \
  --l2nx.load.bots=500 --l2nx.load.ramp=120s --l2nx.load.seed=42 --l2nx.load.server-commit="$(git rev-parse HEAD)"
```

The report lands in `build/load/<run>/`. A server's logs go with its container: read them with
`docker compose --profile load logs game-server` before the next `rm`. A run with `up` below 1 loses its server values
(spec 009 §7); look at `http://127.0.0.1:9090/targets` first.

## Gate

Three runs as above, then:

```sh
docker compose --profile load run --rm harness gate \
  --reports=build/load/<run 1>,build/load/<run 2>,build/load/<run 3> \
  --baseline=build/load/<baseline run 1>,build/load/<baseline run 2>,build/load/<baseline run 3>
```

The exit code is 0 for a pass. The first three runs of a build become the baseline: run the gate without `--baseline`,
so criterion 3 is reported as not applicable. A baseline is valid for the setup it was taken on; the gate refuses a
report with another environment, CPU affinity, injection, protocol, intent timeout, lifecycle timeout or measured phase. Copy baseline
reports out of `build/` before a `clean`.

## Trust checks

Once, before the gate is trusted (spec 009 §9). Results go to [Results](#results).

**1. A real client in the spot.** Seed one load bot more than a run uses, and log in with the last seeded account, the
password being `l2nx.load.accounts.password`. Start the servers with the client ports published:

```sh
docker compose -f compose.yaml -f docker/compose.client.yaml --profile load up -d --wait login-server game-server
```

Point the client at `127.0.0.1:2106`, walk to the `load-crowd-pvp` spot and start the run: load bots must be visible,
walk, hit and cast. `login-server` hands the client `127.0.0.1` by matching the network gateway in
`docker/game-server/ipconfig.xml`. Should the client stop after the server list, compare that address with the source
address in the log of `login-server`. The published ports must be free of servers started on the host.

**2. Sensitivity.** The server on two physical cores must raise client p99 and fail the gate. The limit is set for the
`up` line only, so the harness keeps recording the affinity of the baseline and the gate compares the runs instead of
refusing them; keep these reports out of any baseline. Three runs as above with this start line, then the gate against
the baseline:

```sh
L2NX_LOAD_GAME_CPUSET=0-3 L2NX_LOAD_GAME_CPUS=4 docker compose --profile load up -d --wait login-server game-server
```

**3. Repeatability.** Three runs with one seed spread their p99 by at most 10%. A wider spread widens the thresholds or
the number of runs, recorded below with the reason.

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
  --l2nx.load.bots=500 --l2nx.load.ramp=120s --l2nx.load.seed=42

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

No numbers yet. Every row names the reports it comes from; the values are those of `summary.json`.

**Environment**

| Operating system, kernel | CPU model, logical cores | cpusets | JVM | Commit |
| ------------------------ | ------------------------ | ------- | --- | ------ |
|                          |                          |         |     |        |

**Baseline** (`load-crowd-pvp`, 500 load bots, seed 42)

| Run | Valid | World pass p99 | Over budget | `Attack` p99 | `UseSkill` p99 | `MoveTo` p99 | Timeouts | Stopped | Report |
| --- | ----- | -------------- | ----------- | ------------ | -------------- | ------------ | -------- | ------- | ------ |
| 1   |       |                |             |              |                |              |          |         |        |
| 2   |       |                |             |              |                |              |          |         |        |
| 3   |       |                |             |              |                |              |          |         |        |

**Trust checks**

| Check         | Date | Result | Reports |
| ------------- | ---- | ------ | ------- |
| Real client   |      |        |         |
| Sensitivity   |      |        |         |
| Repeatability |      |        |         |
