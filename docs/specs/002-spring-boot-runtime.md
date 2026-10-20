# 002 — Spring Boot runtime

How the login and game servers run on Spring Boot: build, lifecycle, shutdown and restart, host configuration,
the database pool, logging, the HTTP management surface, packaging and tests. Introduced at stage 2
([spec 001 §11](001-project-design.md#11-stages)).

## 1. Principles

- **Framework defaults first.** A Spring Boot default, a standard Actuator endpoint or a standard Micrometer meter
  is taken as is; a departure is written down here with its reason.
- **Spring is allowed in every module**, game logic included, together with Lombok and the other de-facto
  standards. The code is not kept framework-free.
- **Stage 2 changes the host, not the game.** The Mobius startup sequence, singletons (`getInstance()`) and the
  game `.ini` settings stay (the login server is already on Spring: `@ConfigurationProperties`, `JdbcClient`,
  `TaskScheduler`). The game code moves to Spring beans and Spring Boot configuration in later work:
  [#17](https://github.com/l2nx/nx-l2/issues/17) (settings), [spec 003](003-engine-core/wiring-layout.md)
  (architecture and wiring of the game code).

## 2. Build

- **Spring Boot 4.1.x**, versions from the Spring Boot BOM through the version catalog; Spring artifacts carry no
  versions of their own. `nx-l2.java-conventions` imports the BOM as a platform in every module, since Spring is
  allowed everywhere (§1).
- **`nx-l2.spring-boot-conventions`** in `build-logic` (renamed from `nx-l2.launcher-conventions`) is applied by
  every `boot` module:
  - the `org.springframework.boot` plugin for the BOM and `springBoot { buildInfo() }`; `bootJar` is disabled, the
    plain `jar` and the Gradle `application` distribution stay (§8);
  - `com.gorylenko.gradle-git-properties` for the git section of `info`;
  - `spring-boot-starter-logging` excluded from every configuration; `spring-boot-starter-log4j2` and
    `com.lmax:disruptor` instead (§6);
  - starters `actuator`, `webmvc` (Tomcat), `jdbc`, plus `micrometer-registry-prometheus`;
  - tests: `spring-boot-starter-test`, `spring-boot-testcontainers`, Testcontainers (`testcontainers-postgresql`, see §9).
- **Main classes:** `app.l2nx.login.boot.LoginApplication` and `app.l2nx.highfive.boot.HighFiveApplication`
  (`@SpringBootApplication(scanBasePackages = "app.l2nx.login")` and `"app.l2nx.highfive"`, so the beans of `server`
  are found; the beans of `libs/boot` come through auto-configuration, not scanning). `boot` stays the assembly module: it is the only one that sees `server` and `scripts` together.
- **No CLI tools.** The Mobius `account-manager` and `game-server-register` are removed with their start scripts
  and `console.cfg`.

## 3. Lifecycle

### Start

- `GameServer` and `LoginServer` are `@Component`s implementing Spring's `SmartLifecycle`: `start()` runs the
  Mobius startup sequence unchanged (the body of the former constructor), `stop()` runs the shutdown actions. Not
  bean initialisation (`@PostConstruct`): a minute-long datapack load there would block context creation, and the
  stop would have no ordered counterpart. Not `ApplicationRunner`: it has no stop. No adapter class and no shared
  base class: `SmartLifecycle` is the shared contract.
- Phase `SmartLifecycle.DEFAULT_PHASE`: Tomcat starts in an earlier phase, so the management endpoints answer
  while the datapack loads (liveness `UP`, readiness `OUT_OF_SERVICE`). Boot sets readiness to
  `ACCEPTING_TRAFFIC` on `ApplicationReadyEvent`, after the engine has started and opened its client port.
- **A startup failure is an exception, never `System.exit`.** The exits in `LoginServer` (fatal init, bind
  failure), `GeoEngine` (geodata initialisation) and `ServerConfig` (subnet auto-configuration) throw instead.
  Spring calls `stop()` only on components that started, so a failing `start()` shuts down what it has already
  started (the `ThreadPool` executors, listeners) before it rethrows; no non-daemon thread keeps a half-started
  process alive. Boot logs `Application run failed`, closes the context and the process exits with code 1.

### Stop

- The Mobius JVM shutdown hook is removed. SIGTERM reaches Spring's shutdown hook, which closes the context.
- Close order, all standard: readiness `REFUSING_TRAFFIC` → engine `stop()` (players disconnected and saved,
  `saveData()`: Seven Signs, Olympiad, heroes, clans, manor, scripts, global variables; executors shut down) →
  Tomcat → `DataSource`. `Shutdown` no longer closes the pool; Spring closes it after the engine.
- The game server stops accepting clients first, so nobody logs in during the save; the network closes after the
  save, in a `finally`.
- `stop()` is synchronous, so `spring.lifecycle.timeout-per-shutdown-phase` neither bounds nor extends it: the save
  runs to the end. Container and service definitions give the process a stop timeout well above the save time
  (Docker's default of 10 s is not enough).

### Countdown shutdown and restart

- `//server_shutdown`, `//server_restart`, `ServerRestartManager`, `PrecautionaryRestartManager` and the deadlock
  watcher keep the `Shutdown` countdown. At its end `Shutdown` calls `ServerExit.exit(ServerExitCode)`, so the
  shutdown actions run once, through `stop()`.
- `LoginServer.shutdown()` calls the same in place of `Runtime.exit`.
- **`ServerExit`** is a bean of `libs/boot` (auto-configuration `app.l2nx.boot.BootAutoConfiguration`), shared by both servers:
  `System.exit(SpringApplication.exit(context, code))` on a thread of its own (`server-exit`). A caller running on
  a pool thread (the scheduled restart) would otherwise close the context from inside the pool that
  the close waits for. **`ServerExitCode`** is an enum next to it,
  `SHUTDOWN(0)` and `RESTART(2)`, implementing Spring's `ExitCodeGenerator`; the codes below live in this one
  place. Code 1 has no constant: Boot sets it on a failed start.

### Exit codes

| Code | Meaning                       | Supervisor       |
| ---- | ----------------------------- | ---------------- |
| 0    | stopped on purpose (shutdown) | does not restart |
| 1    | startup failure or crash      | restarts         |
| 2    | restart requested             | restarts         |
| 143  | stopped by SIGTERM (128 + 15) | does not restart |

The process never restarts itself: the Mobius singletons hold static state, so a fresh context in the same JVM
is not possible. Restarting is the supervisor's job: `restart: on-failure` in Compose, `Restart=on-failure` with
`RestartForceExitStatus=2` and `SuccessExitStatus=143` in systemd. A JVM stopped by SIGTERM exits with 143; `docker stop`
and `systemctl stop` are not restarted.

## 4. Configuration

- **Standard Spring Boot locations:** defaults in `application.yaml` on the classpath of each `boot` module,
  operator overrides in `config/application.yaml` of the distribution (Spring Boot reads `./config/` by default,
  next to the `.ini` files), environment variables on top (`SPRING_DATASOURCE_PASSWORD`).
- **Shared defaults** (datasource, `server.address`, management) live once in
  `libs/boot/src/main/resources/l2nx-defaults.yaml`. An `EnvironmentPostProcessor` of `libs/boot`
  (`DefaultsEnvironmentPostProcessor`, registered in `META-INF/spring.factories`) adds it as the lowest-priority
  property source, so a server needs no `spring.config.import`; each server's `application.yaml` adds only its own keys (`spring.application.name`, `server.port`, pool size).
- **Moved to `application.yaml` at stage 2:** `spring.datasource.*`, `server.*`, `management.*`, `logging.*`. `Database.ini` and `DatabaseConfig` are removed.
- **Login settings are `application.yaml`:** `@ConfigurationProperties` under `l2nx.login.*` (client and game-server
  endpoints, ban, flood protection, scheduled restart, `auto-create-accounts`). `Server.ini`, `Network.ini` and `Threads.ini` are removed from the login distribution and Dockerfile.
- **Network settings of both servers are `l2nx.network.*`** from stage 4 ([spec 008 §3](008-network.md#3-configuration)):
  they replace `l2nx.login.network.*`, the connection limits of `l2nx.login.flood-protection` and the game server's
  `Network.ini`.
- **Stays in `.ini`:** every other game setting, including thread pool sizes and the client ports, until
  [#17](https://github.com/l2nx/nx-l2/issues/17).

## 5. Database pool

- `spring-boot-starter-jdbc` creates the `HikariDataSource` from `spring.datasource.*`. Only
  `maximum-pool-size` and `pool-name` are set; the Mobius timeouts (connection 60 s, idle 5 min, lifetime
  10 min, minimum idle of a tenth of the pool) give way to the Hikari defaults.
- Both servers read and write through repositories on `JdbcClient`; `DatabaseFactory` is removed
  ([spec 006 §5](006-database.md#5-access-from-code)).
- The driver is pgjdbc (PostgreSQL); the schema, its role and the Liquibase wiring are in [spec 006](006-database.md).

## 6. Logging

- **Log4j2 behind Spring Boot.** One `log4j2-spring.xml` in `libs/boot`, shared by both servers (a channel file
  is created on first use only); an operator replaces
  it with the standard `logging.config`.
- **The code logs through SLF4J** (`@Slf4j`, spec 007 §6). JDK platform logging that still goes to JUL reaches
  Log4j2 through the `Log4jBridgeHandler` that Boot installs (`log4j-jul` comes with `spring-boot-starter-log4j2`);
  no `java.util.logging.manager` flag is set. `log.cfg`, `ServerLogManager` and the JUL handlers, formatters and
  filters of the former `commons/log` are removed.
- **Async throughout:** `AsyncLoggerContextSelector` with the LMAX Disruptor; logging does not block game
  threads and does not allocate on the hot path. Boot's `LoggingSystem` stops Log4j2 on shutdown, so the queue
  is flushed.
- **Console:** the Spring Boot default pattern; JSON through Boot's built-in structured logging
  (`logging.structured.format.console: ecs | logstash | gelf`).
- **Channels:** `accounting`, `audit`, `chat`, `enchant.items`, `enchant.skills`, `gmaudit`, `item`, `olympiad`
  are named loggers with `additivity="false"`, each with its own `RollingFile` in `./log/`, rolled daily and by
  size. A class writes to a channel through its own `private static final Logger X_LOG` field (spec 007 §6).
  `PatternLayout` replaces the custom JUL formatters.
- **`server.log`** takes everything the console shows. Every file in `./log` is rolled daily and at 100 MB and
  compressed; compressed files older than 30 days are deleted.
- **`error.log`** takes every event at `WARN` and above (`ThresholdFilter`). Mobius kept there only records with
  an exception, through a filter of its own; Log4j2 has no standard filter for that, and the threshold is the
  standard equivalent.

## 7. HTTP management surface

- **Tomcat serves Actuator only;** there are no application controllers, so the management port is
  `server.port` itself. Defaults: `server.address: 127.0.0.1`, login `8106`, game `8777`.
- **No Spring Security on it.** Protection is the network: the port is bound to loopback (the Docker image binds
  all interfaces inside the container and publishes nothing by default). Exposing it publicly is a configuration
  error.

| Endpoint     | Purpose                                                                    | Access       |
| ------------ | -------------------------------------------------------------------------- | ------------ |
| `health`     | liveness and readiness probes; `db` in the readiness group                 | read-only    |
| `info`       | `build`, `git`, `java`, `os`, `process`                                    | read-only    |
| `prometheus` | scrape                                                                     | read-only    |
| `loggers`    | change log levels at runtime without restarting a server with players      | unrestricted |
| `threaddump` | thread dump without `jstack` or shell access; the server is deadlock-prone | read-only    |

`management.endpoints.access.default: none`, every endpoint above enabled explicitly, `show-details: always`.

Left out on purpose: `heapdump` (gigabytes of memory with credentials in it; `jcmd` exists), `env` and
`configprops` (leak secrets), `shutdown` (SIGTERM and the admin commands are enough), `metrics` (duplicates
`prometheus`).

### Metrics

- Pull only: Prometheus-compatible scrape of `/actuator/prometheus`; no push exporter.
- The standard Boot binders: JVM, process, system, `log4j2.events`, `hikaricp.*`, `tomcat.*`; percentile
  histograms for `jvm.gc.pause` and `hikaricp.connections.acquire`; common tag `application`
  (`${spring.application.name}`).
- The login scheduler is a Spring `TaskScheduler`; Boot binds its executor metrics (`executor.*`).
- The game `ThreadPool` executors and the network executors (I/O and packets) are bound with Micrometer's
  `ExecutorServiceMetrics` (`executor.*` gauges, tag `name`). They are bound, not wrapped with
  `ExecutorServiceMetrics.monitor`: the code uses them through the `ThreadPoolExecutor` API (`purge`, the queue),
  which a wrapper would hide, so the task timers `executor.execution` and `executor.idle` are not published.
- The login's blocking executor of virtual threads publishes `executor.*` with tag `name=l2nx-login-tasks`, timers
  included ([spec 008 §11](008-network.md#11-metrics)).
- Game meters: `l2nx.players.online` (gauge). New meters arrive with the dashboard or alert that needs them.

## 8. Packaging

- **Gradle `application` distribution** (`bin/`, `lib/*.jar`, `config/`, `data/`), as before; Spring Boot runs
  from the flat classpath. `bootJar` and `bootBuildImage` are not used: the datapack relies on the distribution
  layout and relative paths (`./config`, `./data`, `./log`).
- **One `Dockerfile` per `boot` module** on top of `installDist`: `eclipse-temurin:25-jre` until the JDK is
  chosen ([#16](https://github.com/l2nx/nx-l2/issues/16)), a non-root user, `WORKDIR` at the distribution root,
  `HEALTHCHECK` on `/actuator/health/readiness`, geodata mounted as a volume.
- **`compose.yaml`** keeps the database only. Running both servers in Compose needs the game network settings out of
  the `.ini` files: [#19](https://github.com/l2nx/nx-l2/issues/19).

## 9. Tests

- **Boot smoke test per server:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a Testcontainers database:
  a PostgreSQL Testcontainers container from the test fixtures of `libs/database`, wired with `@ServiceConnection`. The
  fixture creates the servers' default schemas (`login`, `game`) as an operator would, and the server's own Liquibase
  changelog migrates its schema at start; the datapack is the real one. It checks that the engine
  starts, readiness turns `UP`, the client port accepts TCP, `health`, `info` and `prometheus` answer, and closing
  the context runs the shutdown actions without `System.exit`. Missing geodata is not an error (regions without a
  file are empty), so the test runs in CI as is. A login and game pair cannot run in one JVM (both use the static
  state of the game server); the end-to-end registration test comes with
  [#19](https://github.com/l2nx/nx-l2/issues/19).
- **Shared base:** `BootSmokeTest` in the test fixtures of `libs/boot` (`app.l2nx.boot.integration`) holds the
  database container, the HTTP and port helpers; the smoke tests live in `<boot package>.integration`.
- **Unit tests** for the failure cleanup of `start()`: `LoginServerTest` occupies the client port, `GameServerTest`
  gives the engine a `DataSource` that always fails; both check that `start()` rethrows and leaves no pool threads.
  `ServerExit` has no unit test: it is one call into Spring Boot followed by `System.exit`.
- CI runs them in `./gradlew build`; the GitHub-hosted runner provides Docker.

## 10. Documentation

- `README.md`: stage, quick start, management ports.
- `docs/public/{en,ru}/`: an operator page on configuration (`config/application.yaml`, environment variables),
  health and metrics, log channels, the Docker image, and restart by exit code with Docker and systemd examples.
