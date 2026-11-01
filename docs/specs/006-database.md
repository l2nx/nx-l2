# 006 — Database: schemas, migrations, access

How the servers lay out their data in PostgreSQL, how the schema evolves and how code reaches it. Why PostgreSQL:
[spec 001 §5.1](001-project-design.md#51-database). Identity, value transactions and write-behind:
[spec 003, persistence](003-engine-core/persistence.md). Introduced at stage 3 (spec 001 §11), which moved both
servers from MariaDB.

## 1. One database, a schema per server

- **PostgreSQL 18 or newer;** `compose.yaml` and Testcontainers use the latest release (18.6 today). Pattern
  matching (`LIKE`) on a column with a nondeterministic collation (§3) needs 18.
- **Every server process owns one schema** in a shared database: `login`, and one schema per game server (`game` by
  default; `game_1`, `game_2`, ... when several game servers share the database).
- **One setting names the schema:** `l2nx.database.schema`, defaulted in each server's own `application.yaml`
  (`login`, `game`) and referenced by `spring.datasource.hikari.schema`, `spring.liquibase.default-schema` and
  `spring.liquibase.liquibase-schema`. The name must match `[a-z_][a-z0-9_]{0,62}`. Before Liquibase runs, the server
  checks `pg_namespace`; a missing schema fails the start with a message naming the schema and the role to create.
- **SQL is written without a schema prefix.** pgjdbc turns the connection schema into `search_path`, so the code
  never knows its schema name and the same game code and changelog serve every game schema.
- **No foreign keys and no reads across schemas.** With no cross-schema dependency, moving the login server to its
  own database or host is a configuration change.
- **Accounts are addressed by UUID across the login↔game connection.** `PlayerAuthResponse` carries the account's
  UUID and login; every game→login packet that names an account (`PlayerInGame`, `PlayerLogout`, `ChangeAccessLevel`,
  `RequestTempBan`, `PlayerTracert`, ...) addresses it by UUID. The game schema stores `account_id uuid` without a
  foreign key and does not store the login: a character is created only for the account of the session. An admin
  command acting on an account names one of its characters; the game server takes the account from
  `characters.account_id`, so it needs no lookup on the login server. The protocol changes together with the game
  server's switch (§7). A login server accepts a password change or a route report only for an account on the game
  server that sent it, since the player acts for itself there; an access-level change or a temporary ban applies to
  any account, online or not, since the game server resolved it from its own characters, and is logged with the game
  server's id. Every refusal is logged; none is ignored silently ([spec 008 §10](008-network.md#10-gs-link)).
- **A role per server** (`l2nx_login`, `l2nx_game_1`, ...) owns only its schema. A local development setup may use
  one role.
- **The operator creates the roles and schemas,** not the server: creating them is a DBA operation, and a server role
  with `CREATE` on the database would hold a privilege it needs once. `compose.yaml` does it with an init script for
  local runs; the public install page gives the SQL.
- **Session timeouts come from the server, not the role:** the shared defaults pass the pgjdbc property
  `options=-c idle_in_transaction_session_timeout=60s` through `spring.datasource.hikari.data-source-properties`, so it
  survives an operator's own JDBC URL and a connection left in a transaction cannot hold locks indefinitely whatever
  the role is. Hikari keeps `auto-commit=true`; transactions are opened explicitly (§5).
- **Pools fit the database's connection limit.** Both pools are fixed-size, as Hikari recommends (`minimum-idle`
  left at its default, the maximum): the game server 20 connections, since game threads still run JDBC directly
  until write-behind (spec 003), the login server 5. Every process on one database adds its maximum, and the sum
  stays well below `max_connections` (100 by default, 3 reserved for superusers); an operator who raises a pool or
  adds game servers raises `max_connections` with it.

## 2. Migrations

- **Liquibase.** Liquibase 5 is licensed under FSL-1.1-ALv2; the decision and its reason are in
  [spec 001 §2](001-project-design.md#2-legal-and-licensing).
- **Liquibase per schema.** Each server runs its own changelog at start; `DATABASECHANGELOG` and its lock live inside
  the server's schema, so game servers sharing a database migrate independently.
- **Changelogs:** `login/server/src/main/resources/db/liquibase/` and
  `chronicles/high-five/server/src/main/resources/db/liquibase/` (the schema belongs to the chronicle), wired by
  `spring.liquibase.change-log` and `includeAll`. Formatted SQL, files `NNN-<name>.sql` zero-padded (`includeAll`
  sorts alphabetically), changeset ids `login:NNN-<purpose>` and `game:NNN-<purpose>`. Until the package rework (E10)
  the changesets of every chronicle feature live in this one directory and sequence.
- **An engine module owns its tables** (`engine/core` value transactions, `engine/mail`): its changelog ships in the
  module's jar under `db/engine/<module>/` with changeset ids `<module>:NNN-<purpose>`, and the server changelog
  includes it explicitly after the chronicle baseline (the module cannot know a chronicle's `includeAll` directory).
  A chronicle table that references a module table (`items.mail_id`) gets that foreign key in a chronicle changeset
  after the include. The server changelog runs, in order: the chronicle directory (`includeAll`, `maxDepth: 1`), the
  engine modules' changelogs, then the chronicle's `db/liquibase/after-engine/` directory for such changesets.
- **Changes to a populated table** start with `SET LOCAL lock_timeout = '5s'` (a session-level `SET` would stay on the
  pooled connection after the migration) and add constraints `NOT VALID` first; indexes on populated tables are built
  `CONCURRENTLY` in a changeset with `runInTransaction:false`.
- **Before 1.0 there is one baseline per server** (`000-baseline.sql`: changeset `NNN-baseline` for the schema,
  `NNN-seed` for the seeded rows) and it is edited in place; there are no
  migrations on top. Upgrading between pre-1.0 versions means recreating the database, and release notes say so. A
  Liquibase checksum error on an existing pre-1.0 database means exactly this.
- **From 1.0 the baseline is frozen.** Changes are new `NNN-<name>.sql` files, applied changesets are never edited,
  and migrations are forward-only: the operator backs up before upgrading, a downgrade is not supported.
- The imported `sql/` directories (`login/server/sql`, `chronicles/high-five/datapack/sql`) and `spring.sql.init` are
  removed; Liquibase is the only source of the schema.

## 3. Schema

The baseline keeps the table set of the imported High Five tree and brings it to the canon; reworking tables into
component tables (`{component}_{purpose}`, [persistence §6](003-engine-core/persistence.md#6-schema-rules)) happens
later, together with the module that owns them.

- **Canon:** plural `snake_case` table names (`clan_data` → `clans`), `snake_case` columns (`charId` →
  `character_id`), boolean columns without `is_`, no reserved words as names.
- **Types:** `timestamptz` instead of epoch milliseconds, `boolean` instead of `TINYINT`, `text` instead of
  `VARCHAR(n)`, no UNSIGNED, MEDIUMINT or ENUM.
- **Keys:**
  - entities (accounts, characters, clans, items, mail, ...) are keyed by UUIDv7
    ([persistence §1](003-engine-core/persistence.md#1-persistent-identity));
  - the state of a datapack object (a castle, a raid boss, a territory) is keyed by its datapack `int` id (§4);
  - a row that is not an entity of its own gets a generated identity: `integer` when the client sees the id or a
    bypass carries it (crests, forums, topics, item auctions, lotteries, announcements, community board favourites),
    `bigint` otherwise. A crest id is never reused: the client caches crest images by id;
  - a seeded row with a UUID key gets a literal UUID in the changeset.
- **Stored references to an object id** (shortcuts, pet ↔ control item, mail attachments, auction items) store the
  UUID and resolve the wire handle on load.
- **Handles at the database boundary.** Game code keeps addressing objects by their wire handle; the
  `IdentityRegistry` (`engine/core`, package `app.l2nx.engine.core.identity`, with the UUIDv7 generator and the
  wire-handle allocator) maps the UUID of every loaded or referenced entity to its handle, and repositories
  translate. A new entity gets its UUID at its first insert. A reference to another entity takes that entity's
  existing UUID and never mints one: a released handle cannot turn into a new identity.
- **Hot tables** (`items`, `characters`, `character_variables`, `item_variables`, `character_skill_saves`) have
  `fillfactor = 80` and `autovacuum_vacuum_scale_factor = 0.05`: room for updates in place, and vacuum keeps up with
  the steady stream of small updates.
- **Constraints:** foreign keys inside a schema, each with an index on the referencing column; `CHECK` on value
  ranges; a missing value is `NULL` with `CHECK (col <> '')` on text columns. Every imported `DEFAULT ''` becomes a
  nullable column or a required one; `gameservers.host` is dropped, nothing reads it.
- **Account passwords** are the output of Spring Security's `DelegatingPasswordEncoder`: an Argon2id hash with its
  `{argon2}` id, never a bare digest; `CHECK (password LIKE '{%}_%')` keeps any value without an encoder id out
  ([spec 008 §9](008-network.md#9-login)). A database holding hashes of another form is recreated (§2).
- **Case-insensitive names** (account logins, character and clan names) are a `UNIQUE` constraint on a column with an
  ICU nondeterministic collation created in each schema's baseline:
  `CREATE COLLATION case_insensitive (provider = icu, locale = 'und-u-ks-level2', deterministic = false)` —
  case-insensitive, accent-sensitive. No `citext`: it needs `CREATE EXTENSION`, a database-level privilege. A
  character name is unique within its game schema; game servers sharing a database do not share a name space.
- **ICU upgrades:** a PostgreSQL image with a new ICU version can reorder collated values. The upgrade runbook on the
  public install page runs `REINDEX` on the indexes of collated columns and `ALTER COLLATION … REFRESH VERSION`.

## 4. Static data and state

- **The database holds world state only.** Data authored by designers lives in the datapack: spawn points of raid
  bosses, grand bosses, fortresses and territories, random spawns, siege guards of castles, fortresses and clan
  halls, Dimensional Rift rooms, and the static attributes of castles, fortresses, clan halls,
  siegable clan halls and territories (names, locations, grades, lease) are XML files loaded at start.
- **Tables that mixed both keep only the state,** keyed by the datapack id:

  | State table                 | Holds                                                                 |
  | --------------------------- | --------------------------------------------------------------------- |
  | `raid_boss_states`          | respawn time, HP, MP                                                  |
  | `grand_boss_states`         | status, respawn time, HP, MP, position and heading of a living boss   |
  | `castle_states`             | owner, treasury, tax, siege date and the other values the game writes |
  | `fort_states`               | owner, state, supply and siege values the game writes                 |
  | `clan_hall_states`          | owner, lease paid until, auction values the game writes               |
  | `siegable_clan_hall_states` | owner, next siege                                                     |
  | `territory_states`          | owned wards                                                           |

  The exact columns are what the game code writes today; values it only reads move to XML. A living grand boss is
  restored at its stored position; the datapack spawn point is used after a respawn.

- **State rows are created by an upsert on first write,** not seeded: the datapack stays the only list of castles,
  bosses and halls. Mercenaries placed by a castle owner are player state and stay in their table.
- **Spawns are not edited at run time.** The admin commands that wrote spawns into the database or the datapack
  files are removed: `admin_recall_npc`, `admin_delete`, `admin_delete_group`, `admin_deleteNpcByObjectId`,
  `admin_add_guard`, together with the spawn-writing paths of `RaidBossSpawnManager`, `SiegeGuardManager` and
  `SpawnData`. An NPC spawned by a GM lives until restart; a permanent spawn is a datapack change.
- **Liquibase seeds only state that is not tied to the datapack:** the Seven Signs status row, the root forums and
  the initial Hellbound level and trust (Hellbound starts open, as upstream ships it). The imported welcome row in
  `announcements` is not seeded. The imported herb drop groups were never read and are dropped.
- The new loaders use the datapack loader in use at the time; [#22](https://github.com/l2nx/nx-l2/issues/22) moves
  them with the others.

## 5. Access from code

- **`libs/database`** holds what both servers share about the database and nothing else: the schema check that runs
  before Liquibase, the `Instant` ↔ `timestamptz` bridge, and the PostgreSQL test fixtures. `libs/boot` stays Spring
  Boot wiring.
- **Repositories** are `@Repository` beans on `JdbcClient` in the server module, one per owning table or group of
  tables, as in the login server. No SQL outside a repository; `DatabaseFactory` is removed.
- **A `getInstance()` singleton whose SQL moves into a repository becomes a bean in the same change.** Its
  `getInstance()` stays as a static bridge, bound in the bean's constructor; called before the context created the
  bean, it throws `IllegalStateException`. Its callers do not change; they move to constructor injection with the
  package rework ([spec 003](003-engine-core/spec.md), E10). The login server has no such bridges: its components
  reach each other by constructor injection only.
- **Entities and packets never get a repository injected** ([wiring §1](003-engine-core/wiring-layout.md)): until
  write-behind (E4) takes their persistence, they call the bean that owns their data (`Player` → the player
  persistence bean) through its bridge.
- **Scripts own no SQL.** The tables used by scripts (transmogrification, birthdays, community board, conquerable
  halls, admin commands) get repositories and an owning bean in the server module; scripts call that bean through
  its bridge, and through `ScriptContext` once it exists.
- **Writes that belong together run in one transaction:** a write inside one repository (the DELETE + INSERT pairs
  of skills, effects and shortcuts) is a `@Transactional` repository method; a write across repositories (a
  character save with its subclasses) runs in the owning bean's `TransactionTemplate`. In-memory changes that follow
  such a write are applied after the commit, so a rollback leaves memory as it was. Item and currency transfers are
  value transactions ([persistence.md §3](003-engine-core/persistence.md#3-value-transactions)); mail is the first.

## 6. Tests

- Testcontainers PostgreSQL; the container and per-test schemas come from the test fixtures of `libs/database`.
- Every repository method has an integration test.
- Each changelog bootstraps from an empty database.
- Two game schemas with two roles in one database migrate independently, and neither role can read the other's
  tables.
- The boot smoke tests of both servers run on PostgreSQL.
- The static data loaders load the whole datapack.
