/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.mail.fixture;

import app.l2nx.database.JdbcTimes;
import app.l2nx.database.PostgresDatabase;
import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.persistence.value.Holder;
import app.l2nx.engine.core.persistence.value.HolderType;
import app.l2nx.engine.core.persistence.value.JdbcValueTransactions;
import app.l2nx.engine.core.persistence.value.ValueOutcome;
import app.l2nx.engine.core.persistence.value.ValueTransaction;
import app.l2nx.engine.core.persistence.value.ValueTransactionSubmitter;
import app.l2nx.engine.mail.AttachmentState;
import app.l2nx.engine.mail.MailActor;
import app.l2nx.engine.mail.MailSettings;
import app.l2nx.engine.mail.MailType;
import app.l2nx.engine.mail.repository.MailAttachmentRepository;
import app.l2nx.engine.mail.repository.MailRepository;
import app.l2nx.engine.mail.repository.MailRow;
import app.l2nx.engine.mail.repository.MailWriter;
import app.l2nx.engine.mail.view.AttachmentSnapshot;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Every loop and executor runs work at once on the caller unless a test swaps them. Each world owns its schema's
 * connection pool, so a test closes the world it made.
 */
public final class MailWorld implements AutoCloseable {
    public static final Instant START = Instant.parse("2026-11-01T12:00:00Z");
    private static final Duration OUTCOME_TIMEOUT = Duration.ofSeconds(20);

    public final DataSource dataSource;
    private final HikariDataSource pool;
    public final JdbcClient jdbc;
    public final ManualGameClock clock = new ManualGameClock(START, ZoneOffset.UTC);
    public final MailSettings settings;
    public final MailRepository mails;
    public final MailAttachmentRepository attachments;
    public final TestItemStore store;
    public final JdbcValueTransactions transactions;
    public final FakeParticipants participants = new FakeParticipants();
    public final FakeItems items = new FakeItems();
    public final BackgroundQueries queries;
    public final ManualScheduler scheduler = new ManualScheduler();
    public final ValueTransactionSubmitter submitter;
    public final MailWriter writer;
    private final AtomicInteger handles = new AtomicInteger(0x10000000);

    public MailWorld(MailSettings settings) {
        this(settings, Runnable::run);
    }

    public MailWorld(MailSettings settings, Executor runnerExecutor) {
        this(settings, runnerExecutor, Runnable::run, UnaryOperator.identity());
    }

    /** @param wrapDataSource wraps the schema's data source for every part of the world, the test's own reads included */
    public MailWorld(
            MailSettings settings,
            Executor runnerExecutor,
            Executor queryExecutor,
            UnaryOperator<DataSource> wrapDataSource) {
        this.settings = settings;
        this.pool = PostgresDatabase.migratedSchema("mail", "db/mail-test-changelog.yaml");
        this.dataSource = wrapDataSource.apply(pool);
        this.jdbc = JdbcClient.create(dataSource);
        this.mails = new MailRepository(jdbc);
        this.attachments = new MailAttachmentRepository(jdbc);
        this.store = new TestItemStore(jdbc);
        this.transactions = new JdbcValueTransactions(
                new JdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource),
                runnerExecutor,
                clock.wallClock(),
                new SimpleMeterRegistry());
        this.queries = new BackgroundQueries(queryExecutor, scheduler);
        this.submitter = new ValueTransactionSubmitter(transactions, scheduler);
        this.writer = new MailWriter(settings, mails, attachments, store);
    }

    public <R> ValueOutcome<R> run(ValueTransaction<R> transaction) {
        final CompletableFuture<ValueOutcome<R>> outcome = new CompletableFuture<>();
        transactions.submit(transaction, Runnable::run, outcome::complete);
        return outcome.orTimeout(OUTCOME_TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
    }

    /** A character that is not in the world. */
    public UUID character(String name) {
        final UUID id = EntityIds.next();
        jdbc.sql("INSERT INTO characters (id, name) VALUES (?, ?)")
                .param(id)
                .param(name)
                .update();
        return id;
    }

    public void gm(UUID characterId) {
        jdbc.sql("UPDATE characters SET gm = true WHERE id = ?")
                .param(characterId)
                .update();
    }

    public MailActor online(String name) {
        return enter(character(name), name);
    }

    public MailActor enter(UUID characterId, String name) {
        final MailActor actor = participants.enter(characterId, handles.incrementAndGet(), name);
        items.register(actor.characterHandle(), characterId);
        return actor;
    }

    /** Puts an item into the inventory of an online character, in the database and in memory alike. */
    public FakeItems.Item give(MailActor actor, int itemTemplateId, long count) {
        final FakeItems.Item item = new FakeItems.Item(
                handles.incrementAndGet(), EntityIds.next(), actor.characterId(), itemTemplateId, count);
        store.insert(item.id(), actor.characterId(), HolderType.INVENTORY.name(), null, itemTemplateId, count);
        items.add(actor.characterHandle(), item);
        return item;
    }

    public FakeItems.Item store(MailActor actor, int itemTemplateId, long count) {
        final FakeItems.Item item = new FakeItems.Item(
                handles.incrementAndGet(), EntityIds.next(), actor.characterId(), itemTemplateId, count);
        store.insert(item.id(), actor.characterId(), HolderType.WAREHOUSE.name(), null, itemTemplateId, count);
        items.addToWarehouse(actor.characterHandle(), item);
        return item;
    }

    public MailRow.MailRowBuilder mail(UUID senderId, UUID receiverId) {
        final Instant now = clock.wallClock().instant();
        return MailRow.builder()
                .id(EntityIds.next())
                .type(MailType.PLAYER)
                .senderId(senderId)
                .senderName(senderId == null ? null : "Sender")
                .receiverId(receiverId)
                .receiverName("Receiver")
                .subject("Subject")
                .attachments(AttachmentState.NONE)
                .sentAt(now)
                .expiresAt(now.plus(settings.expiry()));
    }

    public UUID insert(MailRow row) {
        mails.insert(row);
        return row.id();
    }

    public UUID attach(UUID mailId, int itemTemplateId, long count) {
        final UUID id = EntityIds.next();
        store.insert(id, null, HolderType.MAIL.name(), mailId, itemTemplateId, count);
        final int slot = attachments.findByMail(mailId).size();
        attachments.insertAll(
                mailId,
                List.of(AttachmentSnapshot.builder()
                        .slot(slot)
                        .itemTemplateId(itemTemplateId)
                        .count(count)
                        .enchantOptions(List.of())
                        .build()));
        return id;
    }

    /** A stored mail whatever its visibility. */
    public MailRow row(UUID mailId) {
        return jdbc.sql("SELECT * FROM mails WHERE id = ?")
                .param(mailId)
                .query((rs, rowNum) -> MailRow.builder()
                        .id(rs.getObject("id", UUID.class))
                        .type(MailType.valueOf(rs.getString("type")))
                        .senderId(rs.getObject("sender_id", UUID.class))
                        .senderName(rs.getString("sender_name"))
                        .receiverId(rs.getObject("receiver_id", UUID.class))
                        .receiverName(rs.getString("receiver_name"))
                        .subject(rs.getString("subject"))
                        .body(rs.getString("body"))
                        .codAdena(rs.getObject("cod_adena", Long.class))
                        .attachments(AttachmentState.valueOf(rs.getString("attachments")))
                        .returnedFromId(rs.getObject("returned_from_id", UUID.class))
                        .sentAt(JdbcTimes.instant(rs, "sent_at"))
                        .expiresAt(JdbcTimes.instant(rs, "expires_at"))
                        .readAt(JdbcTimes.instant(rs, "read_at"))
                        .settledAt(JdbcTimes.instant(rs, "settled_at"))
                        .senderDeletedAt(JdbcTimes.instant(rs, "sender_deleted_at"))
                        .receiverDeletedAt(JdbcTimes.instant(rs, "receiver_deleted_at"))
                        .expiredAt(JdbcTimes.instant(rs, "expired_at"))
                        .build())
                .single();
    }

    /** Every stored mail the character received, whatever its visibility. */
    public List<MailRow> receivedBy(UUID receiverId) {
        return jdbc
                .sql("SELECT id FROM mails WHERE receiver_id = ? ORDER BY sent_at, id")
                .param(receiverId)
                .query(UUID.class)
                .list()
                .stream()
                .map(this::row)
                .toList();
    }

    public UUID give(UUID characterId, int itemTemplateId, long count) {
        final UUID id = EntityIds.next();
        store.insert(id, characterId, HolderType.INVENTORY.name(), null, itemTemplateId, count);
        return id;
    }

    public long count(Holder holder, int itemTemplateId) {
        return store.itemsOf(holder).stream()
                .filter(row -> row.itemTemplateId() == itemTemplateId)
                .mapToLong(TestItemStore.TestRow::count)
                .sum();
    }

    public long ledger(HolderType holderType, UUID holderId, int itemTemplateId) {
        return jdbc.sql("""
            SELECT coalesce(sum(delta), 0) FROM value_ledger
            WHERE holder_type = ? AND holder_id = ? AND item_template_id = ?
            """)
                .param(holderType.name())
                .param(holderId)
                .param(itemTemplateId)
                .query(Long.class)
                .single();
    }

    public int transactionsOf(String type) {
        return jdbc.sql("SELECT count(*) FROM value_transactions WHERE type = ?")
                .param(type)
                .query(Integer.class)
                .single();
    }

    @Override
    public void close() {
        pool.close();
    }
}
