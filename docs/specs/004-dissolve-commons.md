# 004. Dissolve `libs/commons`

**One-time:** archived when `libs/commons` is gone and its lasting facts are folded into spec 001 §4,
`docs/provenance.md`, `CLAUDE.md` and README.

## 1. Why

`libs/commons` is a dump module ("utilities") with no single purpose: a NIO transport, L2 ciphers, a cron parser, an
XML reader base, a static thread pool, a static connection holder, string and random helpers, Spring Boot wiring.
A shared dump has no owner and grows by default. The module is removed; no `utils` module replaces it under another
name.

Rule for every class: it is replaced by a standard library, or it moves to exactly one module with one purpose. No
class is copied into both `login` and a chronicle.

All 35 files of `libs/commons` are permissive (no GPLv3 header), so moving them into `libs/*` keeps the rule of
spec 001 §2 (`libs/*` holds no GPLv3 file).

## 2. Placement per class

### 2.1 Replaced by libraries everywhere

| Class                                            | Replacement                                                                                                      |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| `Rnd`                                            | `ThreadLocalRandom` (see §2.5)                                                                                   |
| `StringUtil`, `TimeUtil`, `HexUtil`, `TraceUtil` | JDK (`String`, `java.time`, `HexFormat`, `Throwable`); Apache commons-lang3 only where the JDK has no equivalent |
| `SchedulingPattern`                              | RFC 5545 RRULE via `org.dmfs:lib-recur`, the format the L2NX platform already uses for schedules (see §2.6)      |
| `BlowfishEngine`                                 | JCE `Blowfish/ECB/NoPadding` behind a little-endian word swap (see §2.4)                                         |

### 2.2 Replaced by Spring in `login`

| Class             | Replacement                                                                                  |
| ----------------- | -------------------------------------------------------------------------------------------- |
| `DatabaseFactory` | repositories on `JdbcClient` (accounts, account data, game servers)                          |
| `ThreadPool`      | the `TaskScheduler` auto-configured by Spring Boot                                           |
| `ConfigReader`    | `@ConfigurationProperties`; the login half of [#17](https://github.com/l2nx/nx-l2/issues/17) |
| `IXmlReader`      | `GameServerTable` reads its XML with JDK StAX                                                |

`JdbcClient` alone, no Spring Data JDBC: login has a handful of point queries on three tables, no paging, no CRUD
API, so the aggregate semantics of Spring Data JDBC (`isNew`, child rows rewritten on save) bring nothing but risk.
The game's persistence technology is chosen at stage 3.

### 2.3 Moved with `git mv`, replaced later

| What                                                                                       | Target                                 | Replaced by                                                               |
| ------------------------------------------------------------------------------------------ | -------------------------------------- | ------------------------------------------------------------------------- |
| `network/*` (17 files)                                                                     | `libs/network`                         | Netty transport, stage 4                                                  |
| `BlowfishEngine`, `NewCrypt`                                                               | `libs/network`, as cipher primitives   | §2.4; the L2 protocol on top stays in `login` and the chronicle           |
| `ThreadPool`, `ThreadProvider`, `ThreadPriority`, `ThreadConfig`                           | `high-five/server`, scheduling package | partition deadlines, step E7 of spec 003                                  |
| `IXmlReader`                                                                               | `high-five/server`, datapack loading   | XML library chosen in [#22](https://github.com/l2nx/nx-l2/issues/22)      |
| `DatabaseFactory`                                                                          | `high-five/server`                     | repositories, stage 3 (PostgreSQL baseline)                               |
| `ConfigReader`                                                                             | `high-five/server`                     | Spring Boot configuration, [#17](https://github.com/l2nx/nx-l2/issues/17) |
| `ServerExit`, `ServerExitCode`, `l2nx-defaults.yaml`, `log4j2-spring.xml`, `BootSmokeTest` | `libs/boot` (`app.l2nx.boot`)          | — (§2.3.1)                                                                |

`ThreadPool` stays a single entry point on purpose: step E6 of spec 003 routes every game task through it into the
partition inbox without touching its 189 callers. Unrolling it into direct `TaskScheduler` calls now would remove
that seam.

#### 2.3.1 `libs/boot`

One module built as a Spring Boot starter (the autoconfigure and starter halves merged; nobody needs them apart):

- **Beans:** an `@AutoConfiguration` listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  declares `ServerExit` with `@ConditionalOnMissingBean`. A dependency on the module is enough; no package scan.
- **Shared defaults:** an `EnvironmentPostProcessor` adds `l2nx-defaults.yaml` as the lowest-priority property source.
  The servers drop `spring.config.import`; any key in their `application.yaml` still wins.
- **Logging:** `log4j2-spring.xml` ships in the jar; operators replace it with `logging.config`.
- **Tests:** `BootSmokeTest` lives in the module's test fixtures.
- **Scan:** with the shared beans auto-configured, `scanBasePackages` narrows to `app.l2nx.login` and
  `app.l2nx.highfive`.

Further shared infrastructure (`libs/telemetry`) gets its own auto-configuration in its own module.

### 2.4 Blowfish

The L2 client packs each 32-bit Blowfish word little-endian; JCE is big-endian. Swapping the bytes of every 4-byte
word before and after a JCE `Blowfish/ECB/NoPadding` pass yields the same bytes as the current engine.

- Before the old engine is deleted, golden vectors (keys, plain and cipher blocks, including the static login key)
  are captured from it and committed as test data. The JCE path must match them byte for byte.
- A JMH benchmark compares both on packet-sized buffers. If JCE is slower, the engine stays in `libs/network`
  under the same golden test and the replacement is dropped.
- `NewCrypt` splits into generic primitives (`PacketCipher` implementations: Blowfish-LE, XOR checksum) in
  `libs/network`; which primitive a connection uses stays in `login` and the chronicle.

### 2.5 `Rnd` to `ThreadLocalRandom`

Not a blind rename. `Rnd.get(origin, bound)` includes `bound`; `nextInt(origin, bound)` excludes it. `Rnd` returns
`0` for `bound <= 0` and `origin` for `origin >= bound`, where `ThreadLocalRandom` throws. Every call site is
checked: an inclusive bound becomes `bound + 1`, and where an argument can reach the guarded range the old
behaviour is kept explicitly. Step E5 of spec 003 then replaces one JDK pattern with the injected RNG.

### 2.6 Schedules to RRULE

The 7 callers (six custom events, `UndergroundColiseumManager`) read cron strings from config
(`<schedule pattern="0 20 * * *"/>`); they switch to RRULE (`FREQ=DAILY;BYHOUR=20;BYMINUTE=0`), evaluated in the
server time zone. The `~N` random and `+N` offset modifiers of `SchedulingPattern` are used by no config and are not
carried over. A random window, when a feature needs one, is a separate field next to the RRULE with the platform's
semantics: a point drawn from `[base, base + jitter)`. Delay-based respawns (`respawnDelay` + `respawnRandom`) are
not schedules and do not change.

## 3. Order of work

Each step is a commit (or a move commit followed by a change commit) after which the build, the tests and both
servers' boot smoke tests pass.

1. **`libs/boot`.** Move, then the auto-configuration and the `EnvironmentPostProcessor`; drop
   `spring.config.import`, narrow `scanBasePackages`. Check: boot smoke tests; a test that an `application.yaml`
   key overrides a shared default.
2. **`libs/network`.** Move `network/*`, `BlowfishEngine`, `NewCrypt`; then §2.4. Check: golden vectors, JMH.
3. **`login` on Spring** (§2.2). Login goes first so the classes of §2.3 become game-only. Check: repository tests on
   Testcontainers MySQL; tests for the login-timeout purge and the scheduled restart; a configuration binding
   test where an invalid value fails startup.
4. **Library replacements**, one commit per class (§2.1). Check: `Rnd` call sites reviewed one by one against §2.5;
   a test that every shipped cron string and its RRULE give the same next fire times.
5. **Game moves** (§2.3) with `git mv`.
6. **Removal and docs.** Delete `libs/commons` and its `settings.gradle.kts` entry. Spec 001 §4: drop `commons` and
   `db` (pool and Liquibase are Spring Boot auto-configuration, nothing of ours is left), add `boot`. Update
   `docs/provenance.md` (new paths), `CLAUDE.md`, README. Archive this spec.

Final check: the local stack with a High Five client: log in, create a character, fight, teleport, restart the
server by command. The bot harness (E0) is not there yet, so this check is manual.

## 4. Deferred

- XML library for the datapack loaders: [#22](https://github.com/l2nx/nx-l2/issues/22).
- Game half of moving settings to Spring Boot configuration: [#17](https://github.com/l2nx/nx-l2/issues/17).
