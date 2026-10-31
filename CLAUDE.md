# nx-l2

Open-source Lineage 2 server: public L2J Mobius refactored in place. Docs are English throughout.

## Principle

**Minimum custom code, maximum reuse of industry standards.** Before writing infrastructure (host, config, logging,
metrics, health, persistence, migrations, tests), take the framework default or the established library and its
conventions as is: Spring Boot defaults, standard Actuator endpoints and Micrometer meter names, Log4j2, Liquibase,
Testcontainers. Custom code only for what is specific to Lineage 2. A departure from a standard is a decision
recorded in the spec with its reason, not a silent choice.

## Invariants

- **Game code never reads the system clock or a global random generator.** Legacy code uses
  `app.l2nx.highfive.clock.ClockBridge` (game time vs wall-clock time per mechanic); an ArchUnit test in
  `chronicles/high-five/boot` enforces it. Design: [spec 003, execution](docs/specs/003-engine-core/execution.md).
- **Game code starts no threads of its own:** game work runs on the world loop (`WorldPartition`), work without game
  state goes to `BackgroundTasks`; no `Timer`, thread, executor, `*Async` future or parallel stream, since any other
  thread is a second writer and a timer thread dies silently on the first exception. Nor does it block: no `sleep`,
  `wait`, `join`, `park`, latch or future wait, since a blocked task freezes the whole world; a pause is a loop timer. The only exceptions are the
  static data parse pool (`IXmlReader`) and the `partition` bridges (ArchUnit `ScheduledWorkRules`, same module).
- **Chronicle slices keep one layout.** A migrated slice `app.l2nx.highfive.<slice>` has its API in the root and only
  the fixed subpackages, commands, handlers and view events are checked by their marker types
  (`Command`, `CommandHandler`, `ViewEvent`), adapters live under `network/` and no slice depends on it;
  `SliceLayoutArchitectureTest` in `chronicles/high-five/boot` checks it with Spring Modulith and ArchUnit, legacy violations frozen in
  `src/test/archunit-store`. Design: [spec 003, wiring and layout §4](docs/specs/003-engine-core/wiring-layout.md#4-layout).

## Found bugs

**Every bug found and not fixed in the current change is filed at once**, so it is not lost in a report: a GitHub
issue in `l2nx/nx-l2` with issue type **Bug** (the org type, not a label), added to Project "L2NX" with status
Backlog, plus its line in [docs/TODO.md](docs/TODO.md). This includes pre-existing Mobius bugs noticed in passing
and review findings left out of scope. The issue holds the failure scenario with file and line.

## Pointers

- Design, module layout, stack, stages: [docs/specs/001-project-design.md](docs/specs/001-project-design.md).
- Specs index: [docs/CLAUDE.md](docs/CLAUDE.md); deferred work: [docs/TODO.md](docs/TODO.md).
- Developer runbooks (metrics queries, baselines): `docs/runbooks/`.
- Database (schema per server, Liquibase baseline edited in place before 1.0, UUIDv7 keys): [docs/specs/006-database.md](docs/specs/006-database.md).
- Import mapping from Mobius: [docs/provenance.md](docs/provenance.md).
- Code style (palantir, LF): [docs/specs/005-code-formatting.md](docs/specs/005-code-formatting.md).
- Java canon (Lombok allow-list, field naming, records, SLF4J): [docs/specs/007-java-conventions.md](docs/specs/007-java-conventions.md).
- Public pages (l2nx.dev/l2): `docs/public/{en,ru}/`.
