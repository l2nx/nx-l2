# 003 — Engine core: identity and persistence

How game objects are identified, how values move without duplication, and how the rest of the state reaches
PostgreSQL. Part of [spec 003](spec.md). Database choice: [spec 001 §5.1](../001-project-design.md#51-database). Schemas, migrations and repositories:
[spec 006](../006-database.md).

## 1. Persistent identity

- **Everything persistent is keyed by UUIDv7:** accounts (the login server's table gets a UUIDv7 key; game tables
  reference it instead of `account_name`), characters, clans, items, mail, value
  operations, ledger entries, and every component table row that needs its own key.
- **Why UUIDv7 and not a `BIGINT` sequence:**
  - generated in process, without a database round trip or block allocation, so an item can be created on the
    simulation thread (drop, craft);
  - unique across servers: merging worlds and transferring characters copy rows without renumbering the ledger and
    every reference;
  - a global key towards the platform (adapter, web) without a server prefix;
  - time-ordered, so B-tree inserts stay local like a sequence's.

  The cost is 16 bytes instead of 8 per key, negligible at this database size.

- **Generation** is monotonic per RFC 9562 through an established library (`uuid-creator`); ids are never reused.
- **Introduced in the PostgreSQL baseline** (stage 3, migration step E2): the schema is written once with UUIDv7
  keys rather than migrated twice. This replaces the `int` character id in about sixty tables and in scripts, and the
  Mobius start-up scan that loads every used object id into a bit set.

## 2. Wire handles

The protocol carries object ids as 32-bit integers in every chronicle in scope (Interlude, High Five, Essence).
Persistent identity therefore never goes on the wire.

- **A wire handle** (`int`) is allocated when an object is loaded or spawned: a character at login, its items with
  it, an NPC at spawn. Packets, the partition registry and entity references use it.
- **One allocator per process,** shared by all partitions, so an entity keeps its handle across partitions.
- **Range** `0x10000000–0x7FFFFFFF` (about 1.88 billion), as the client expects.
- **Reuse after quarantine.** A released handle returns to the pool only after `handle-quarantine` (configurable,
  default 5 minutes), so a client still holding an old NPC's id cannot address a new object with it.
- **What changes for stored references:** everything the server stores by object id (shortcuts, pet ↔ control item,
  item links in mail and auctions) stores the persistent UUID and resolves the handle on load.
- **To verify on the client before E2 ships:** whether the client keeps anything keyed by an item's object id across
  sessions (UI state, caches). A different handle on the next login must not break it.

## 3. Value transactions

Trade, mail with attachments, warehouses, shops, auction, crafting that consumes materials, and any other transfer
of items or currency:

1. the partition marks the involved items `pending`: they cannot be used, dropped, sold or transferred;
2. one PostgreSQL transaction on the persistence executor applies the transfer: conditional `UPDATE` of ownership
   (`WHERE owner = expected`), balances, ledger rows, outbox rows;
3. the result returns to the partition inbox; on commit the partition applies the change and confirms to the
   players; on failure it rolls back the `pending` marks and tells the players.

Duplication by "two save points" (one side saved, the other not, then a crash) is structurally impossible: there is
one save point.

- **Balance plus ledger:** stackable values (adena, stackable items) keep a balance with `CHECK (amount >= 0)`; every
  movement also writes an append-only ledger row in the same transaction. The balance serves reads; the ledger serves
  audit and lets a duplication be removed precisely instead of rolling the world back.
- **Idempotency:** every value transaction carries an `idempotency_key` (`idempotencyKey` in code), the platform's
  name for a business identity stable across retries. The partition creates it (UUIDv7) for a client action; a script
  or the platform passes its own. A committed transaction is recorded in `value_transactions` (its own `id`, the
  key under a unique constraint, type, subject); a repeated request with the same key is not executed again and
  answers `Replayed` with the recorded subject and its ledger rows, so the caller can bring an online character's
  memory to the committed state. The runner claims the key before executing, so a concurrent request
  with the same key waits for the first one and replays it instead of finding its items already moved.
- **Outcomes** return to the partition inbox: `Committed`, `Replayed`, `Refused` (a condition failed inside the
  transaction, nothing changed), `Failed` (an error before the commit, nothing changed; the partition releases its
  marks) or `Unknown` (the commit outcome is not known; the partition keeps its marks and resolves it by looking the
  key up). The lookup claims the key itself under a short lock timeout: the claim waits for a transaction still
  committing on a lost connection, conflicts when it committed (`Replayed`) and otherwise is rolled back (`Failed`: the
  original can no longer commit); no answer within the timeout is `Unknown` again.
- **Durability:** value transactions commit synchronously.
- **Runner:** `engine/core` `persistence` runs the transaction on Boot's task executor in a `TransactionTemplate` at
  `READ COMMITTED`, so a conditional `UPDATE` racing a committed one re-reads the row instead of failing to serialize;
  no own executor or pool. Meter `l2nx.value.transaction` (timer, tags `type`, `outcome`).

### 3.1 Online and offline sides

- **An online character's items are owned by the partition;** the database lags them until the next save. The
  transaction writes their absolute state taken from memory under an owner condition and the ledger records the
  delta.
- **An offline character's items are owned by the database:** the transaction changes them by delta under
  `CHECK (count >= 0)`, merging into an existing stack or inserting a row.
- **In-flight registry:** while a transaction is in flight for a character, the partition does not save its reserved
  items and holds its world entry and logout until the outcome. A save of the pre-commit memory would otherwise undo
  a committed move, and a login would load rows the commit is about to change.
- **Pending marks** are checked in the mutating operations of the item containers, so every path that uses, drops,
  sells, destroys, equips or moves an item is covered, scripts included.
- **Apply after commit** changes memory without writing the database again, then releases the marks.

The first consumer is mail ([spec 010 §3.5](../010-mail/spec.md#35-value-transaction-runner-enginecore-persistence)); trade, warehouses, stores and
the other transfers move onto the same runner one by one.

## 4. Write-behind

Everything that is not a value transfer: position, experience, HP and MP, effects, skills, quest progress, settings.

- **Game-time state is stored without game-time instants,** since game time restarts with the process:
  - effects store their remaining duration and tick phase and stand still while the player is offline
    ([effects.md §9](effects.md#9-persistence));
  - cooldowns of skills and items store the wall-clock instant they end, so offline time counts; on load the
    remaining part becomes a game-time deadline.

- **Dirty tracking per component;** changes to the same key within the window are coalesced.
- **Window about 30 s** (`write-behind-interval`, configurable); a crash loses at most that much progress, never
  values.
- **Batches are written on the persistence executor,** ordered per character; routine autosave may use
  `SET LOCAL synchronous_commit = off`.
- **Logout and shutdown save synchronously:** a character is not released until its state is written (spec 002 stop
  order: engine stop saves players).
- **Change capture lags by the window.** The adapter's change capture reads the database, so the platform sees
  write-behind state up to one window late; value operations reach it at commit through the outbox (§5).
- **No JDBC on a partition thread.** A synchronous query on a loop thread is a defect and is reported by metrics and a
  warning. TODO: the legacy JDBC runs on the world loop from the E6 cutover until write-behind moves it off
  ([#69](https://github.com/l2nx/nx-l2/issues/69), [execution.md §7.1](execution.md#71-work-routed-into-the-world-partition-e6)).

## 5. Outbox

Events for the platform (adapter, Kafka) that follow from value operations are written into an outbox table in the
same transaction and published from there after commit. An event is neither lost after a commit nor sent for a
rolled-back operation.

## 6. Schema rules

- **Tables are owned by components:** each component or feature slice owns its typed tables and their Liquibase
  migrations, named `{component}_{purpose}`.
- **No new entries in key-value tables** (`character_variables`, `account_gsdata` and similar). Existing keys move to
  typed tables when their feature is reworked; flags that are only quest state may stay key-value.
- **A missing value is `NULL`,** never an empty string: text columns get `CHECK (col <> '')`.
- **Outside writers** (web, admin tools) do not update the tables of an online character; they send a command to the
  server, which applies it in the owning partition.
