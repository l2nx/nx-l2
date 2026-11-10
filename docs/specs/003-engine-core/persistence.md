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
- **Background queries:** `BackgroundQueries` runs database work that moves no value on the same executor and hands
  the result to a loop. `run(query, resultLoop, onResult)` logs a failure and delivers nothing, for a caller that
  holds nothing until the answer; `run(query, resultLoop, onResult, onFailure)` delivers the failure (a query error or
  a task the executor refuses) to the loop, for a caller that holds state until the answer, such as character
  loading; `runAfter(pause, query, resultLoop, onResult)` runs a query once after a pause on the scheduler, for a
  retry that waits for state on the loop (mail for a loading character); `runUntilAnswered` reads what memory must
  follow after a commit again until the database answers ([#204](https://github.com/l2nx/nx-l2/issues/204)).

### 3.1 Online and offline sides

- **An online character's items are owned by the partition;** the database lags them until the next save. The
  transaction writes their absolute state taken from memory under an owner condition and the ledger records the
  delta.
- **An offline character's items are owned by the database:** the transaction changes them by delta under
  `CHECK (count >= 0)`, merging into an existing stack or inserting a row.
- **In-flight registry:** while a transaction is in flight for a character, the partition does not save its reserved
  items and holds its world entry and logout until the outcome. A save of the pre-commit memory would otherwise undo
  a committed move, and a login would load rows the commit is about to change.
- **Loading:** from the selection until `loadEnded` (the player built from its rows and handed to its session, or the
  load failed), `LoadedCharacters` marks the character loading
  ([execution.md §7.1](execution.md#71-work-routed-into-the-world-partition-e6)); building the player (`loaded()`)
  does not end the mark. Selections queued behind a running load re-enter `whenLoaded` one at a time: a queued
  selection that starts a load again leaves the ones behind it waiting for that load, in arrival order. Its rows
  are being read off the loop, so nobody may change them: a change after the read would be lost on the next save
  from memory, and a deleted item would come back. A loading character is neither online nor offline; every writer
  of its offline rows treats it as busy (§6). The in-flight registry does not cover this window: it holds a load for
  a transaction, not a transaction for a load ([#204](https://github.com/l2nx/nx-l2/issues/204)).
- **Settled:** a character is settled once it has entered the world or left memory. Between the build and world
  entry the player owns its rows but is not in the world, where the loop writers look, so they wait for the settled
  state, not for the build ([#206](https://github.com/l2nx/nx-l2/issues/206)).
- **Pending marks** are checked in the mutating operations of the item containers, so every path that uses, drops,
  sells, destroys, equips or moves an item is covered, scripts included.
- **Awaited stacks:** a stackable template the target has no stack of at reservation is reserved too; a stack of it
  created meanwhile is not saved and merges into the committed row, so one stack exists in memory and in the database.
- **Unstored rows:** an item created after the last save travels with its whole row, and the transaction inserts it
  before moving it.
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
- **Writers of a character's offline rows** check both `LoadedCharacters` and the world before they write by
  database, and a loading or built but unsettled character (§3.1) is never written as offline
  ([#204](https://github.com/l2nx/nx-l2/issues/204), [#206](https://github.com/l2nx/nx-l2/issues/206)):
  - **value transactions** (mail: the cash on delivery of a receive, expiry into a warehouse): busy only while the
    character loads, since mail's `online` finds a built player; tried again after a pause
    ([spec 010 §3.4](../010-mail/spec.md#34-chronicle-spi));
  - **loop writers that pick the database for an absent character** (clan member removal and dissolution, pledge
    type, power grade, leader change, apprentice and sponsor resets, academy master assignment, castle circlet
    removal, olympiad passes, hero items, item auction delivery to the winner's warehouse, cursed weapon removal,
    `destroyItemsOfTemplate`, admin teleport, repair, clan penalties, punishment access level, friend deletion): wait
    until the character is settled, then decide online or offline again, so a character in the world takes the
    in-memory path (`OfflineRows.whenSettled`); `destroyItemsOfTemplate` deletes the rows of settled owners at once
    and runs a per-owner step for each busy owner once it settles; the item auction creates the won item at delivery,
    after the winner settles, so no world object waits unowned;
  - **freight sent to a receiver by the database:** a receiver that is loading, or in memory without being in the
    world (an offline trader of the account), owns its freight there; the sender gets `PLEASE_TRY_AGAIN_LATER` and
    nothing is written (`OfflineRows.isBusy`), a receiver in the world gets the items into its loaded container;
  - **character deletion and restore** from the lobby are refused while the character is loaded, since its save
    would overwrite the mark (`OfflineRows.isLoaded`), as is the expiry deletion of a marked character;
  - **daily resets on the loop** (skill reuse by `online_status`, recommendations): skip a busy character and apply
    to it once it is settled, since its read may predate the bulk write;
  - **known gap:** account variables of two loaded characters of one account are written by both saves
    ([#206](https://github.com/l2nx/nx-l2/issues/206));
  - not affected: tables a manager owns whole (heroes, nobles, Seven Signs, raid points, fishing championship,
    grand boss zones, couples), which the load does not read; offline traders, which are loaded players; the start-up
    cleanup, which runs before any load.
