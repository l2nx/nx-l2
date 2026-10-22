# Legacy execution baseline

How the E1 baseline of the High Five game server was recorded before E6 moved the world onto one thread, and how to
read the world loop against it ([spec 003 execution.md §8](../specs/003-engine-core/execution.md#8-budget-and-metrics)).
The E1 task meters `l2nx.legacy.task`, `l2nx.legacy.task.all` and `l2nx.legacy.task.lag` timed the Mobius `ThreadPool`
and went with it at the E6 cutover; the rows they produced stay below as the baseline. From the cutover the same work
runs on the world loop and its meters time it.

## Meters

| Prometheus family                        | Labels                         | What                                                                                                                                  |
| ---------------------------------------- | ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| `l2nx_engine_pass_seconds`               | `loop`                         | run time of a pass, one histogram (`_bucket`) for p95/p99; the startup pass that loads the world is not timed                         |
| `l2nx_engine_pass_phase_seconds`         | `loop`, `phase`                | run time per phase: `inbox` (client packets, `execute` work), `deadlines` (timers), `systems`, `side_effects`, `replication`, `flush` |
| `l2nx_engine_pass_items`                 | `loop`, `kind`                 | items handled per pass: `message`, `input`, `timer`                                                                                   |
| `l2nx_engine_pass_over_budget_total`     | `loop`                         | passes longer than `l2nx.engine.pass-budget` (50 ms)                                                                                  |
| `l2nx_engine_timers`                     | `loop`                         | timers armed on the loop                                                                                                              |
| `l2nx_engine_timers_overdue_total`       | `loop`                         | timers fired after their deadline was already older than `pass-min` before the pass                                                   |
| `l2nx_engine_failures_total`             | `loop`, `phase`                | work that threw, per phase                                                                                                            |
| `l2nx_engine_ownership_violations_total` | `loop`                         | calls into loop-owned state from another thread; any value above zero is a defect                                                     |
| `l2nx_engine_jdbc_on_loop_seconds`       | `loop`                         | time the loop spends in JDBC calls until write-behind (E4) moves them off                                                             |
| `l2nx_legacy_packet_seconds`             | `type`, `outcome`, `exception` | run time per client packet class: `_count`, `_sum`, `_max`                                                                            |
| `executor_*`                             | `name`                         | pool size, active threads, queued tasks of the background and network pools (spec 002)                                                |
| `l2nx_session_queue_depth`               |                                | items in a session queue right after each enqueue, one histogram                                                                      |

`type` is the fully qualified packet class name; `outcome` is `SUCCESS` or `ERROR`, `exception` the simple name of the
exception class (the binary name for an anonymous class) or `none` (the keys of Spring's `tasks.scheduled.execution`).
The packet meter is timed by the session drain, one drain per client at a time: the `l2nx-game-packets` executor until
the client is authenticated, the world loop from then on. `_max` decays over a two-minute window.

## Queries

Window `[5m]` for live views; use the length of the scenario (`[15m]`) for a snapshot.

```promql
# Loop-seconds per second of the world loop: its share of one thread. Above 1 it cannot keep up. The E6 counterpart
# of the E1 world-thread bound, but measured: it holds every piece of world work and nothing else.
sum(rate(l2nx_engine_pass_seconds_sum{loop="world"}[5m]))

# p95 and p99 of a world pass, against pass-budget (50 ms).
histogram_quantile(0.95, sum by (le) (rate(l2nx_engine_pass_seconds_bucket{loop="world"}[5m])))
histogram_quantile(0.99, sum by (le) (rate(l2nx_engine_pass_seconds_bucket{loop="world"}[5m])))

# Share of passes over budget.
sum(rate(l2nx_engine_pass_over_budget_total{loop="world"}[5m]))
  / sum(rate(l2nx_engine_pass_seconds_count{loop="world"}[5m]))

# Where the pass goes: loop-seconds per second per phase.
sum by (phase) (rate(l2nx_engine_pass_phase_seconds_sum{loop="world"}[5m]))

# Timers fired per second and timers that fired late (the E6 counterpart of the E1 start lag).
sum(rate(l2nx_engine_pass_items_sum{loop="world", kind="timer"}[5m]))
sum(rate(l2nx_engine_timers_overdue_total{loop="world"}[5m]))

# Failing work per second, by phase.
sum by (phase) (rate(l2nx_engine_failures_total{loop="world"}[5m]))

# Must stay 0.
sum(l2nx_engine_ownership_violations_total)

# Loop-seconds per second spent in JDBC on the loops (#69): the share of the world loop the database takes until
# write-behind (E4) moves it off. Its sites are named in the log by the WARN "JDBC statement ran on loop".
sum by (loop) (rate(l2nx_engine_jdbc_on_loop_seconds_sum[5m]))

# Packets by total run time, rate and slowest run, over both outcomes.
topk(20, sum by (type) (rate(l2nx_legacy_packet_seconds_sum[5m])))
topk(20, sum by (type) (rate(l2nx_legacy_packet_seconds_count[5m])))
topk(20, max by (type) (l2nx_legacy_packet_seconds_max))

# Failing packets per second, by class and exception.
sum by (type, exception) (rate(l2nx_legacy_packet_seconds_count{outcome="ERROR"}[5m]))

# Saturation of the background and network pools.
max by (name) (executor_queued_tasks)
max by (name) (executor_active_threads)

# Session queue depth after each enqueue, across all sessions (the E0 gate reads it): p99 and maximum. A queue
# that keeps growing means its drain falls behind the client; the histogram spans 1 to inbound-queue-limit.
histogram_quantile(0.99, sum by (le) (rate(l2nx_session_queue_depth_bucket[1m])))
max_over_time(l2nx_session_queue_depth_max[1m])
```

The loop meters carry no task class, so they do not name the heavy task as `l2nx_legacy_task_seconds` did. A pass
over budget is logged with its phase breakdown; the code behind it is found with JDK Flight Recorder
(`jcmd <pid> JFR.start duration=120s filename=e6.jfr`).

Not measured by the loop: packet parsing (`read()`) on the Netty I/O threads, before the packet is queued, and the
static data parse pool of `IXmlReader` (startup only).

Without Prometheus, the totals since start come from one scrape:

```sh
curl -s http://127.0.0.1:8777/actuator/prometheus | grep '^l2nx_engine_pass_phase_seconds_sum'
curl -s http://127.0.0.1:8777/actuator/prometheus | grep '^l2nx_legacy_packet_seconds_sum' | sort -k2 -gr | head -20
```

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
   `curl -s http://127.0.0.1:9090/api/v1/query --data-urlencode 'query=sum(rate(l2nx_engine_pass_seconds_sum{loop="world"}[15m]))'`.
6. Add a row to the E6 table below. Keep the raw query output with the change that records it, not in the repository.

## Recorded baselines

### E1: thread pools, before the cutover

| Date       | Commit           | Machine (cores, JDK)                              | Pool sizes (`Threads.ini`)                                  | Scenario                                                                                                                                                                                                                                                  | Top 5 tasks (thread-s/s)                                                                                                                                                                             | Top 5 packets (s/s)                                                                                                    | Task p95 / p99 | Lag p99 per pool                         | World-thread bound  | Series (`l2nx_legacy_*`)                                                                            |
| ---------- | ---------------- | ------------------------------------------------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | -------------- | ---------------------------------------- | ------------------- | --------------------------------------------------------------------------------------------------- |
| 2026-10-21 | `349680e21 + E1` | 12 logical (6-core Ryzen 5 5600X), Temurin 25.0.2 | defaults (`-1`): scheduled 48, high-priority 12, instant 24 | Idle: 10 min from 40 s after readiness, no player online                                                                                                                                                                                                  | `MovementTaskManager.Movement` 1.5e-3; `QuestTimer.ScheduleTimerTask` 9.6e-4; `walker.ArrivedTask` 3.0e-4; `TimerHolder` 2.2e-4; `EventDispatcher` (instant) 2.1e-4                                  | none (no client)                                                                                                       | 87 µs / 194 µs | scheduled 14.5 ms, high-priority 14.7 ms | 0.0042 (tasks only) | 540: 63 task pairs, 0 `ERROR`, 0 packet; flat from 5 to 10 min                                      |
| 2026-10-21 | `349680e21 + E1` | 12 logical (6-core Ryzen 5 5600X), Temurin 25.0.2 | defaults (`-1`): scheduled 48, high-priority 12, instant 24 | Play: 15 min, one client, follows the idle window: log in, enter world, Talking Island village and the field west of it, about 15 Gremlins, Young Keltirs and Wolves (GM-spawned) killed with Power Strike and Battle Roar, NPC dialog, chat, one restart | `MovementTaskManager.Movement` 2.1e-3; `QuestTimer.ScheduleTimerTask` 9.6e-4; `walker.ArrivedTask` 4.0e-4; `AttackableThinkTaskManager.AttackableThink` (high-priority) 3.9e-4; `TimerHolder` 3.2e-4 | `CharacterSelect` 2.8e-5; `EnterWorld` 1.7e-5; `MoveToLocation` 8.0e-6; `Action` 7.0e-6; `RequestMagicSkillUse` 6.6e-6 | 92 µs / 259 µs | scheduled 2.1 ms, high-priority 2.1 ms   | 0.0057              | 642: 81 task pairs, 16 packet classes, 0 `ERROR`, 0 lambda or `$` names; flat 20 s after the window |

### E6: world loop, after the cutover

| Date | Commit | Machine (cores, JDK) | Scenario | World loop share | Pass p95 / p99 | Over budget | Top 3 phases (s/s) | Timers fired / overdue (per s) | JDBC on loop (s/s) | Top 5 packets (s/s) | Ownership violations |
| ---- | ------ | -------------------- | -------- | ---------------- | -------------- | ----------- | ------------------ | ------------------------------ | ------------------ | ------------------- | -------------------- |
