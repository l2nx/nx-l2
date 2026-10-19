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
  game `.ini` settings stay. They move to Spring beans and Spring Boot configuration in later work:
  [#17](https://github.com/l2nx/nx-l2/issues/17) (settings), [#18](https://github.com/l2nx/nx-l2/issues/18)
  (architecture and wiring of the game code).

## 2. Build

- **Spring Boot 4.1.x**, versions from the Spring Boot BOM through the version catalog; Spring artifacts carry no
  versions of their own.
- **`nx-l2.spring-boot-conventions`** in `build-logic` (renamed from `nx-l2.launcher-conventions`) is applied by
  every `boot` module:
  - the `org.springframework.boot` plugin for the BOM and `springBoot { buildInfo() }`; `bootJar` is disabled, the
    plain `jar` and the Gradle `application` distribution stay (§8);
  - `com.gorylenko.gradle-git-properties` for the git section of `info`;
  - `spring-boot-starter-logging` excluded from every configuration; `spring-boot-starter-log4j2`, `log4j-jul` and
    `com.lmax:disruptor` instead (§6);
  - starters `actuator`, `webmvc` (Tomcat), `jdbc`, plus `micrometer-registry-prometheus`;
  - tests: `spring-boot-starter-test`, `spring-boot-testcontainers`, Testcontainers MariaDB.
- **Main classes:** `app.l2nx.login.boot.LoginApplication` and `app.l2nx.highfive.boot.HighFiveApplication`
  (`@SpringBootApplication`). `boot` stays the assembly module: it is the only one that sees `server` and
  `scripts` together. The login tools (`account-manager`, `game-server-register`) stay plain `main` classes with
  their own start scripts, without a Spring context.

## 3. Lifecycle

### Start

- The engine is started by a Spring `SmartLifecycle` bean in `server` (`GameServerLifecycle`,
  `LoginServerLifecycle`): `start()` runs the Mobius startup sequence unchanged (the `GameServer` and
  `LoginServer` constructors become `start()` / `stop()` methods), `stop()` runs the shutdown actions. Not bean
  initialisation (`@PostConstruct`): a minute-long datapack load there would block context creation, and the stop
  would have no ordered counterpart. Not `ApplicationRunner`: it has no stop. The two beans share no base class;
  `SmartLifecycle` is the shared contract, each bean is a thin adapter to its server.
- Phase `SmartLifecycle.DEFAULT_PHASE`: Tomcat starts in an earlier phase, so the management endpoints answer
  while the datapack loads (liveness `UP`, readiness `OUT_OF_SERVICE`). Boot sets readiness to
  `ACCEPTING_TRAFFIC` on `ApplicationReadyEvent`, after the engine has started and opened its client port.
- **A startup failure is an exception, never `System.exit`.** The exits in `LoginServer` (fatal init, bind
  failure), `GeoEngine` (geodata initialisation) and `ServerConfig` (subnet auto-configuration) throw instead.
  Boot logs `Application run failed`, closes the context (`stop()` shuts the `ThreadPool` executors down, so no
  non-daemon thread keeps a half-started process alive) and the process exits with code 1.

### Stop

- The Mobius JVM shutdown hook is removed. SIGTERM reaches Spring's shutdown hook, which closes the context.
- Close order, all standard: readiness `REFUSING_TRAFFIC` → engine `stop()` (players disconnected and saved,
  `saveData()`: Seven Signs, Olympiad, heroes, clans, manor, scripts, global variables; executors shut down) →
  Tomcat → `DataSource`. `Shutdown` no longer closes the pool; Spring closes it after the engine.
- `spring.lifecycle.timeout-per-shutdown-phase: 2m` (the 30 s default is too short to save a full server).
  Container and service definitions give the process more than that (§8).

### Countdown shutdown and restart

- `//server_shutdown`, `//server_restart`, `ServerRestartManager`, `PrecautionaryRestartManager` and the deadlock
  watcher keep the `Shutdown` countdown. At its end `Shutdown` calls `ServerExit.exit(ServerExitCode)`, so the
  shutdown actions run once, through `stop()`.
- `LoginServer.shutdown()` calls the same in place of `Runtime.exit`.
- **`ServerExit`** is a bean in `libs/commons`, shared by both servers:
  `System.exit(SpringApplication.exit(context, code))`. **`ServerExitCode`** is an enum next to it,
  `SHUTDOWN(0)` and `RESTART(2)`, implementing Spring's `ExitCodeGenerator`; the codes below live in this one
  place. Code 1 has no constant: Boot sets it on a failed start.

### Exit codes

| Code | Meaning                       | Supervisor       |
| ---- | ----------------------------- | ---------------- |
| 0    | stopped on purpose (shutdown) | does not restart |
| 1    | startup failure or crash      | restarts         |
| 2    | restart requested             | restarts         |

The process never restarts itself: the Mobius singletons hold static state, so a fresh context in the same JVM
is not possible. Restarting is the supervisor's job: `restart: on-failure` in Compose, `Restart=on-failure` with
`RestartForceExitStatus=2` in systemd.

## 4. Configuration

- **Standard Spring Boot locations:** defaults in `application.yaml` on the classpath of each `boot` module,
  operator overrides in `config/application.yaml` of the distribution (Spring Boot reads `./config/` by default,
  next to the `.ini` files), environment variables on top (`SPRING_DATASOURCE_PASSWORD`).
- **Moved to `application.yaml` at stage 2:** `spring.datasource.*`, `server.*`, `management.*`, `logging.*`,
  `spring.lifecycle.*`. `Database.ini` and `DatabaseConfig` are removed.
- **Stays in `.ini`:** every game and login setting, including thread pool sizes and the client ports, until
  [#17](https://github.com/l2nx/nx-l2/issues/17).

## 5. Database pool

- `spring-boot-starter-jdbc` creates the `HikariDataSource` from `spring.datasource.*`. Only
  `maximum-pool-size` and `pool-name` are set; the Mobius timeouts (connection 60 s, idle 5 min, lifetime
  10 min, minimum idle of a tenth of the pool) give way to the Hikari defaults.
- `DatabaseFactory` becomes a bean that receives the `DataSource`. Its static `getConnection()` stays for the
  existing callers until they receive the `DataSource` or a `JdbcClient` through the constructor.
- The driver stays `mysql-connector-j` against MariaDB; the database changes at stage 3.

## 6. Logging

- **Log4j2 behind Spring Boot.** `log4j2-spring.xml` on the classpath of each `boot` module; an operator replaces
  it with the standard `logging.config`.
- **JUL calls keep working** through `-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager`, which
  replaces `ServerLogManager`. `log.cfg`, `ServerLogManager` and the JUL handlers, formatters and filters in
  `commons/log` are removed. Moving the code to the SLF4J API is
  [#14](https://github.com/l2nx/nx-l2/issues/14).
- **Async throughout:** `AsyncLoggerContextSelector` with the LMAX Disruptor; logging does not block game
  threads and does not allocate on the hot path. Boot's `LoggingSystem` stops Log4j2 on shutdown, so the queue
  is flushed.
- **Console:** the Spring Boot default pattern; JSON through Boot's built-in structured logging
  (`logging.structured.format.console: ecs | logstash | gelf`).
- **Channels:** `audit`, `chat`, `item`, `enchant`, `gmaudit`, `olympiad`, `error` are named loggers with
  `additivity="false"`, each with its own `RollingFile` in `./log/`, rolled daily and by size. `PatternLayout`
  replaces the custom JUL formatters; the message text is built by the code, as today.

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
- The `ThreadPool` executors and the network executor are bound with Micrometer's `ExecutorServiceMetrics`
  (`executor.*`, tag `name`).
- Game meters: `l2nx.players.online` (gauge). New meters arrive with the dashboard or alert that needs them.

## 8. Packaging

- **Gradle `application` distribution** (`bin/`, `lib/*.jar`, `config/`, `data/`), as before; Spring Boot runs
  from the flat classpath. `bootJar` and `bootBuildImage` are not used: the datapack and the login tools rely on
  the distribution layout and relative paths (`./config`, `./data`, `./log`).
- **One `Dockerfile` per `boot` module** on top of `installDist`: `eclipse-temurin:25-jre` until the JDK is
  chosen ([#16](https://github.com/l2nx/nx-l2/issues/16)), a non-root user, `WORKDIR` at the distribution root,
  `HEALTHCHECK` on `/actuator/health/readiness`, geodata mounted as a volume.
- **`compose.yaml`:** the database as today; both servers under the `servers` profile with
  `restart: on-failure` and `stop_grace_period: 3m`.

## 9. Tests

- **Boot smoke test per server:** `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a Testcontainers MariaDB
  (`@ServiceConnection`, schema from the `sql/` directories) and the real datapack. It checks that the engine
  starts, readiness turns `UP`, the client port accepts TCP, `health`, `info` and `prometheus` answer, and closing
  the context runs the shutdown actions without `System.exit`. A login and game pair checks that the game server
  registers on the login server. Missing geodata is not an error (regions without a file are empty), so the test
  runs in CI as is.
- **Unit tests** for the lifecycle beans, `ServerExit` with `ServerExitCode`, and `DatabaseFactory`.
- CI runs them in `./gradlew build`; the GitHub-hosted runner provides Docker.
- At stage 3 the container becomes PostgreSQL and the same tests guard the move.

## 10. Documentation

- `README.md`: stage, quick start, management ports.
- `docs/public/{en,ru}/`: an operator page on configuration (`config/application.yaml`, environment variables),
  health and metrics, log channels, and restart by exit code with Compose and systemd examples.
