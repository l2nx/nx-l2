# Legacy execution baseline

How to read the E1 meters of the High Five game server and record a baseline before E6 moves the world onto one
thread ([spec 003 execution.md §8](../specs/003-engine-core/execution.md#8-budget-and-metrics)).

## Meters

| Prometheus family              | Labels                                           | What                                                                 |
| ------------------------------ | ------------------------------------------------ | -------------------------------------------------------------------- |
| `l2nx_legacy_task_seconds`     | `pool`, `code_namespace`, `outcome`, `exception` | run time per `ThreadPool` task class: `_count`, `_sum`, `_max`       |
| `l2nx_legacy_task_all_seconds` |                                                  | run time of all tasks, one histogram (`_bucket`) for p95/p99         |
| `l2nx_legacy_task_lag_seconds` | `pool`                                           | start delay of scheduled tasks against their planned time, histogram |
| `l2nx_legacy_packet_seconds`   | `type`, `outcome`, `exception`                   | run time per client packet class: `_count`, `_sum`, `_max`           |
| `executor_*`                   | `name`                                           | pool size, active threads, queued tasks (spec 002)                   |

`pool` equals the executor `name`: `l2nx-scheduled`, `l2nx-high-priority-scheduled`, `l2nx-instant`.
`code_namespace` is the canonical name of the class that declares the task (`app.l2nx.highfive.entity.actor.holders.player.Duel.ScheduleStartDuelTask` for a member class,
as Spring's `tasks.scheduled.execution` writes it); lambdas, anonymous and local classes count as their declaring
class, a JDK dynamic proxy as `proxy`. `outcome` is `SUCCESS` or `ERROR`, `exception` the simple name of the exception class (the binary name for an anonymous class) or
`none` (the keys of Spring's `tasks.scheduled.execution`). `type` is the fully qualified packet class name; packets
carry `outcome` and `exception` as tasks do. `_max` decays over a two-minute window. The two histograms span 1 µs
to 30 s; `management.metrics.distribution.*` overrides their range.

The engine loop meters `l2nx_engine_*{loop="world"}` exist already, but until E6 routes game work into the world
loop it stays idle: read the baseline from `l2nx_legacy_*` and `executor_*` only.

## Queries

Window `[5m]` for live views; use the length of the scenario (`[15m]`) for a snapshot.

```promql
# Thread-seconds per second per task class: the top consumers.
topk(20, sum by (pool, code_namespace) (rate(l2nx_legacy_task_seconds_sum[5m])))

# Most frequent tasks and their mean run time.
topk(20, sum by (pool, code_namespace) (rate(l2nx_legacy_task_seconds_count[5m])))
sum by (pool, code_namespace) (rate(l2nx_legacy_task_seconds_sum[5m]))
  / sum by (pool, code_namespace) (rate(l2nx_legacy_task_seconds_count[5m]))

# Slowest single runs.
topk(20, max by (pool, code_namespace) (l2nx_legacy_task_seconds_max))

# Failing tasks per second, by class and exception.
sum by (pool, code_namespace, exception) (rate(l2nx_legacy_task_seconds_count{outcome="ERROR"}[5m]))

# p95 and p99 of all tasks.
histogram_quantile(0.95, sum by (le) (rate(l2nx_legacy_task_all_seconds_bucket[5m])))
histogram_quantile(0.99, sum by (le) (rate(l2nx_legacy_task_all_seconds_bucket[5m])))

# Run time per pool (thread-seconds per second).
sum by (pool) (rate(l2nx_legacy_task_seconds_sum[5m]))

# Start lag of scheduled tasks: p99 and mean, per pool.
histogram_quantile(0.99, sum by (le, pool) (rate(l2nx_legacy_task_lag_seconds_bucket[5m])))
sum by (pool) (rate(l2nx_legacy_task_lag_seconds_sum[5m])) / sum by (pool) (rate(l2nx_legacy_task_lag_seconds_count[5m]))

# Packets by total run time, rate and slowest run, over both outcomes.
topk(20, sum by (type) (rate(l2nx_legacy_packet_seconds_sum[5m])))
topk(20, sum by (type) (rate(l2nx_legacy_packet_seconds_count[5m])))
topk(20, max by (type) (l2nx_legacy_packet_seconds_max))

# Failing packets per second, by class and exception.
sum by (type, exception) (rate(l2nx_legacy_packet_seconds_count{outcome="ERROR"}[5m]))

# Pool saturation.
max by (name) (executor_queued_tasks{name=~"l2nx-.*"})
max by (name) (executor_active_threads{name=~"l2nx-.*"})

# Load one world thread would carry (E6): above 1 it cannot fit. Includes tasks that do not touch the world
# (database, maintenance), which overestimates; leaves out the world work below, which underestimates.
# "or vector(0)": before the first packet the packet family has no series, and "+" with an empty vector is empty.
sum(rate(l2nx_legacy_task_seconds_sum[5m])) + (sum(rate(l2nx_legacy_packet_seconds_sum[5m])) or vector(0))

# Series count per family; must stay flat once the scenario has touched every code path.
count by (__name__) ({__name__=~"l2nx_legacy_.*"})
```

The world-thread bound sees only `ThreadPool` tasks and packet runs. World work on threads of its own is not
measured and E6 moves it too, so the bound may underestimate:

- `GameTimeTaskManager`: its own thread, a 1 s loop that flips day and night (Shadow Sense on dark elves, the
  day/night event); the spawn change it hands to `ThreadPool` is measured.
- `DimensionalRift`: a `java.util.Timer` thread per rift that spawns and unspawns its rooms and teleports the party.
- `LoginServerThread`: the login link; its replies (account authentication, kicks) act on game clients on that
  thread.
- The network I/O threads of `libs/network`: packet parsing (`read()`) runs there, before the timed `run()`.

Not world work, also unmeasured: `DeadlockWatcher` and `Shutdown` (own threads), `IXmlReader`'s loading pool (startup
only).

Without Prometheus, the totals since start come from one scrape:

```sh
curl -s http://127.0.0.1:8777/actuator/prometheus | grep '^l2nx_legacy_task_seconds_sum' | sort -k2 -gr | head -20
curl -s http://127.0.0.1:8777/actuator/prometheus | grep '^l2nx_legacy_packet_seconds_sum' | sort -k2 -gr | head -20
```

## Bounded series check

```sh
curl -s http://127.0.0.1:8777/actuator/prometheus > scrape.txt
grep -c '^l2nx_legacy_' scrape.txt
grep -c '^l2nx_legacy_task_seconds_count.*outcome="SUCCESS"' scrape.txt   # (pool, class) pairs; bounded by code
grep -c '^l2nx_legacy_task_seconds_count.*outcome="ERROR"' scrape.txt     # (pool, class, exception) seen failing
grep -c '^l2nx_legacy_packet_seconds_count.*outcome="SUCCESS"' scrape.txt # packet classes seen; at most ~280
grep -c '^l2nx_legacy_task_all_seconds_bucket' scrape.txt                 # one fixed bucket set, 114 with +Inf
grep -E '^l2nx_legacy_task_seconds_count.*(Lambda|code_namespace="[^"]*\$)' scrape.txt | wc -l   # must be 0
```

Bounds: three task series (count, sum, max) per (pool, class) pair for `SUCCESS`, and the pairs are bounded by the
~560 task submission sites in the code; `ERROR` adds three per exception class a task actually throws. Histogram
buckets are one fixed set for all tasks and one per scheduled pool for lag. Repeat the count after 5 minutes and at
the end of the scenario: the numbers may grow only while new code paths run for the first time, never with the
number of players, objects or time.

## Recording a baseline

1. `docker compose up -d db`, then `./gradlew :login:boot:run` and `./gradlew :chronicles:high-five:boot:run`; wait
   for `curl -s http://127.0.0.1:8777/actuator/health/readiness` to report `UP`.
2. Start a throwaway Prometheus that scrapes the game server every 5 s. `prometheus.yml` (not committed):

   ```yaml
   global:
     scrape_interval: 5s
   scrape_configs:
     - job_name: l2nx-game
       metrics_path: /actuator/prometheus
       static_configs:
         - targets: ["127.0.0.1:8777"]
   ```

   ```sh
   docker run --rm --network host -v "$PWD/prometheus.yml:/etc/prometheus/prometheus.yml:ro" prom/prometheus:v3.5.0
   ```

   `--network host` works on Linux and on Docker Desktop with host networking enabled. Otherwise publish
   `-p 127.0.0.1:9090:9090` and set the target to `host.docker.internal:8777`. Check that the target is `UP` on
   `http://127.0.0.1:9090/targets`.

3. Idle window: 10 minutes after readiness with no player online.
4. Play window: 15 minutes with a real client: log in, enter the world, walk across a town and a hunting zone, fight
   monsters, use skills, chat, restart once. Note the start and end time.
5. Evaluate every query above at the end of each window with the window length, for example
   `curl -s http://127.0.0.1:9090/api/v1/query --data-urlencode 'query=topk(10, sum by (pool, code_namespace) (rate(l2nx_legacy_task_seconds_sum[15m])))'`,
   and run the bounded series check.
6. Add a row below. Keep the raw query output with the change that records it, not in the repository.

## Recorded baselines

| Date       | Commit           | Machine (cores, JDK)                              | Pool sizes (`Threads.ini`)                                  | Scenario                                                                                                                                                                                                                                                  | Top 5 tasks (thread-s/s)                                                                                                                                                                             | Top 5 packets (s/s)                                                                                                    | Task p95 / p99 | Lag p99 per pool                         | World-thread bound  | Series (`l2nx_legacy_*`)                                                                            |
| ---------- | ---------------- | ------------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | -------------- | ---------------------------------------- | ------------------- | --------------------------------------------------------------------------------------------------- |
| 2026-10-21 | `349680e21 + E1` | 12 logical (6-core Ryzen 5 5600X), Temurin 25.0.2 | defaults (`-1`): scheduled 48, high-priority 12, instant 24 | Idle: 10 min from 40 s after readiness, no player online                                                                                                                                                                                                  | `MovementTaskManager.Movement` 1.5e-3; `QuestTimer.ScheduleTimerTask` 9.6e-4; `walker.ArrivedTask` 3.0e-4; `TimerHolder` 2.2e-4; `EventDispatcher` (instant) 2.1e-4                                  | none (no client)                                                                                                       | 87 µs / 194 µs | scheduled 14.5 ms, high-priority 14.7 ms | 0.0042 (tasks only) | 540: 63 task pairs, 0 `ERROR`, 0 packet; flat from 5 to 10 min                                      |
| 2026-10-21 | `349680e21 + E1` | 12 logical (6-core Ryzen 5 5600X), Temurin 25.0.2 | defaults (`-1`): scheduled 48, high-priority 12, instant 24 | Play: 15 min, one client, follows the idle window: log in, enter world, Talking Island village and the field west of it, about 15 Gremlins, Young Keltirs and Wolves (GM-spawned) killed with Power Strike and Battle Roar, NPC dialog, chat, one restart | `MovementTaskManager.Movement` 2.1e-3; `QuestTimer.ScheduleTimerTask` 9.6e-4; `walker.ArrivedTask` 4.0e-4; `AttackableThinkTaskManager.AttackableThink` (high-priority) 3.9e-4; `TimerHolder` 3.2e-4 | `CharacterSelect` 2.8e-5; `EnterWorld` 1.7e-5; `MoveToLocation` 8.0e-6; `Action` 7.0e-6; `RequestMagicSkillUse` 6.6e-6 | 92 µs / 259 µs | scheduled 2.1 ms, high-priority 2.1 ms   | 0.0057              | 642: 81 task pairs, 16 packet classes, 0 `ERROR`, 0 lambda or `$` names; flat 20 s after the window |
