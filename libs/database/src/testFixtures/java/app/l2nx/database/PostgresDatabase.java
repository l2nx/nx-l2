/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container per test JVM. Each caller gets its own role and schema, as an operator would create them,
 * so test classes neither share rows nor need cleanup.
 *
 * <p>Every data source handed out is a connection pool owned by the caller: one held in a static field lives as long
 * as the JVM, one made per test is closed after that test. An unpooled source opens a TCP connection per statement,
 * and the thousands of ports a module run leaves in TIME_WAIT can exhaust the host's ephemeral range, which fails a
 * later connect with {@code BindException}.
 */
public final class PostgresDatabase {
    public static final String IMAGE = "postgres:18.7";
    private static final String PASSWORD = "l2nx";
    // Fixed, not the host's processor count: a test may hold several connections at once (a key holder next to the
    // transactions under test) and the tests of a class share one pool, so a pool sized to a 4-core runner starves.
    // Twice JUnit's parallelism on a 16-thread machine; MAX_CONNECTIONS leaves room for the pools one JVM keeps open.
    private static final int POOL_SIZE = 32;
    private static final int MAX_CONNECTIONS = 400;

    private static final PostgreSQLContainer CONTAINER = start();
    private static final JdbcClient ADMIN =
            JdbcClient.create(pool("admin", CONTAINER.getUsername(), CONTAINER.getPassword(), null));

    private PostgresDatabase() {}

    public static PostgreSQLContainer container() {
        return CONTAINER;
    }

    /**
     * @return a pool connected as that role, with the schema as its search path
     */
    public static HikariDataSource migratedSchema(String prefix, String changelog) {
        final String schema =
                prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final HikariDataSource dataSource = createSchema(schema);
        migrate(dataSource, schema, changelog);
        return dataSource;
    }

    /**
     * Migrates a schema of the container's superuser ahead of a server start, so a test can store the state the
     * server then loads.
     *
     * @return a pool connected as the superuser, with the schema as its search path
     */
    public static HikariDataSource migratedSuperuserSchema(String schema, String changelog) {
        ensureSuperuserSchema(schema);
        final HikariDataSource dataSource = pool(schema, CONTAINER.getUsername(), CONTAINER.getPassword(), schema);
        migrate(dataSource, schema, changelog);
        return dataSource;
    }

    private static void migrate(HikariDataSource dataSource, String schema, String changelog) {
        final SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:" + changelog);
        liquibase.setDefaultSchema(schema);
        liquibase.setLiquibaseSchema(schema);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            dataSource.close();
            throw new IllegalStateException("Unable to migrate schema " + schema, e);
        }
    }

    public static void ensureSuperuserSchema(String schema) {
        ADMIN.sql("CREATE SCHEMA IF NOT EXISTS " + schema).update();
    }

    /**
     * @return a pool connected as the schema's new owner role, with the schema as its search path
     */
    public static HikariDataSource createSchema(String schema) {
        ADMIN.sql("CREATE ROLE " + schema + " LOGIN PASSWORD '" + PASSWORD + "'")
                .update();
        ADMIN.sql("CREATE SCHEMA " + schema + " AUTHORIZATION " + schema).update();
        return pool(schema, schema, PASSWORD, schema);
    }

    private static HikariDataSource pool(String name, String user, String password, String schema) {
        final HikariConfig config = new HikariConfig();
        config.setPoolName(name);
        config.setJdbcUrl(CONTAINER.getJdbcUrl());
        config.setUsername(user);
        config.setPassword(password);
        config.setSchema(schema);
        config.setMaximumPoolSize(POOL_SIZE);
        // Connections open on demand: a pool made per test would otherwise open all of them up front.
        config.setMinimumIdle(0);
        return new HikariDataSource(config);
    }

    private static PostgreSQLContainer start() {
        final PostgreSQLContainer container = new PostgreSQLContainer(IMAGE);
        // Testcontainers' own default command, plus the connection ceiling.
        container.setCommand("postgres", "-c", "fsync=off", "-c", "max_connections=" + MAX_CONNECTIONS);
        container.start();
        return container;
    }
}
