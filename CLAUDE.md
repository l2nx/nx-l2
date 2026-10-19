# nx-l2

Open-source Lineage 2 server: public L2J Mobius refactored in place. Docs are English throughout.

## Principle

**Minimum custom code, maximum reuse of industry standards.** Before writing infrastructure (host, config, logging,
metrics, health, persistence, migrations, tests), take the framework default or the established library and its
conventions as is: Spring Boot defaults, standard Actuator endpoints and Micrometer meter names, Log4j2, Liquibase,
Testcontainers. Custom code only for what is specific to Lineage 2. A departure from a standard is a decision
recorded in the spec with its reason, not a silent choice.

## Found bugs

**Every bug found and not fixed in the current change is filed at once**, so it is not lost in a report: a GitHub
issue in `l2nx/nx-l2` with issue type **Bug** (the org type, not a label), added to Project "L2NX" with status
Backlog, plus its line in [docs/TODO.md](docs/TODO.md). This includes pre-existing Mobius bugs noticed in passing
and review findings left out of scope. The issue holds the failure scenario with file and line.

## Pointers

- Design, module layout, stack, stages: [docs/specs/001-project-design.md](docs/specs/001-project-design.md).
- Specs index: [docs/CLAUDE.md](docs/CLAUDE.md); deferred work: [docs/TODO.md](docs/TODO.md).
- Import mapping from Mobius: [docs/provenance.md](docs/provenance.md).
- Code style (palantir, LF, `.git-blame-ignore-revs`): [docs/specs/005-code-formatting.md](docs/specs/005-code-formatting.md).
- Public pages (l2nx.dev/l2): `docs/public/{en,ru}/`.
