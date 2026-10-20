/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.integration.repository;

import app.l2nx.database.JdbcTimes;
import app.l2nx.database.PostgresDatabase;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fixtures are inserted with plain SQL, never through the repositories under test.
 */
public abstract class RepositoryTest {
    protected static final DataSource DATA_SOURCE =
            PostgresDatabase.migratedSchema("game", "db/liquibase-changelog.yaml");
    protected static final JdbcClient JDBC = JdbcClient.create(DATA_SOURCE);
    protected static final JdbcTemplate JDBC_TEMPLATE = new JdbcTemplate(DATA_SOURCE);
    protected static final TransactionTemplate TRANSACTIONS =
            new TransactionTemplate(new DataSourceTransactionManager(DATA_SOURCE));
    protected static final Instant NOW = Instant.parse("2027-01-15T08:00:00Z");

    protected final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));

    protected static UUID insertCharacter() {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO characters (
                id, account_id, name, race, sex, face, hair_style, hair_color, class_id, base_class_id,
                character_level, max_hp, current_hp, max_cp, current_cp, max_mp, current_mp,
                x, y, z, created_at, last_access_at)
            VALUES (?, ?, ?, 'HUMAN', 'MALE', 0, 0, 0, 0, 0, 1, 100, 100, 50, 50, 30, 30, 0, 0, 0, ?, ?)\
            """)
                .param(id)
                .param(EntityIds.next())
                .param("c" + id.toString().replace("-", "").substring(16))
                .param(JdbcTimes.utc(NOW))
                .param(JdbcTimes.utc(NOW))
                .update();
        return id;
    }

    protected static UUID insertClan() {
        final UUID id = EntityIds.next();
        JDBC.sql("INSERT INTO clans (id, name, leader_character_id) VALUES (?, ?, ?)")
                .param(id)
                .param("k" + id.toString().replace("-", "").substring(16))
                .param(insertCharacter())
                .update();
        return id;
    }

    protected static UUID insertItem(UUID ownerId, int itemTemplateId, long count) {
        final UUID id = EntityIds.next();
        JDBC.sql("""
            INSERT INTO items (
                id, owner_id, item_template_id, count, enchant_level, location, location_slot,
                custom_type1, custom_type2)
            VALUES (?, ?, ?, ?, 0, 'INVENTORY', 0, 0, 0)\
            """)
                .param(id)
                .param(ownerId)
                .param(itemTemplateId)
                .param(count)
                .update();
        return id;
    }
}
