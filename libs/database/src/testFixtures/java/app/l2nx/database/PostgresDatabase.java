/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.database;

import java.util.UUID;
import javax.sql.DataSource;
import liquibase.exception.LiquibaseException;
import liquibase.integration.spring.SpringLiquibase;
import org.postgresql.Driver;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container per test JVM. Each caller gets its own role and schema, as an operator would create them,
 * so test classes neither share rows nor need cleanup.
 */
public final class PostgresDatabase {
    public static final String IMAGE = "postgres:18.6";
    private static final String PASSWORD = "l2nx";

    private static final PostgreSQLContainer CONTAINER = start();

    private PostgresDatabase() {}

    public static PostgreSQLContainer container() {
        return CONTAINER;
    }

    /**
     * @return a data source connected as that role, with the schema as its search path
     */
    public static DataSource migratedSchema(String prefix, String changelog) {
        final String schema =
                prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        final DataSource dataSource = createSchema(schema);
        migrate(dataSource, schema, changelog);
        return dataSource;
    }

    /**
     * Migrates a schema of the container's superuser ahead of a server start, so a test can store the state the
     * server then loads.
     *
     * @return a data source connected as the superuser, with the schema as its search path
     */
    public static DataSource migratedSuperuserSchema(String schema, String changelog) {
        ensureSuperuserSchema(schema);
        final DataSource dataSource = dataSource(CONTAINER.getUsername(), CONTAINER.getPassword(), schema);
        migrate(dataSource, schema, changelog);
        return dataSource;
    }

    private static void migrate(DataSource dataSource, String schema, String changelog) {
        final SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:" + changelog);
        liquibase.setDefaultSchema(schema);
        liquibase.setLiquibaseSchema(schema);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        try {
            liquibase.afterPropertiesSet();
        } catch (LiquibaseException e) {
            throw new IllegalStateException("Unable to migrate schema " + schema, e);
        }
    }

    public static void ensureSuperuserSchema(String schema) {
        JdbcClient.create(dataSource(CONTAINER.getUsername(), CONTAINER.getPassword(), null))
                .sql("CREATE SCHEMA IF NOT EXISTS " + schema)
                .update();
    }

    public static DataSource createSchema(String schema) {
        final JdbcClient admin = JdbcClient.create(dataSource(CONTAINER.getUsername(), CONTAINER.getPassword(), null));
        admin.sql("CREATE ROLE " + schema + " LOGIN PASSWORD '" + PASSWORD + "'")
                .update();
        admin.sql("CREATE SCHEMA " + schema + " AUTHORIZATION " + schema).update();
        return dataSource(schema, PASSWORD, schema);
    }

    private static DataSource dataSource(String user, String password, String schema) {
        final SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(Driver.class);
        dataSource.setUrl(CONTAINER.getJdbcUrl());
        dataSource.setUsername(user);
        dataSource.setPassword(password);
        if (schema != null) {
            dataSource.setSchema(schema);
        }
        return dataSource;
    }

    private static PostgreSQLContainer start() {
        final PostgreSQLContainer container = new PostgreSQLContainer(IMAGE);
        container.start();
        return container;
    }
}
