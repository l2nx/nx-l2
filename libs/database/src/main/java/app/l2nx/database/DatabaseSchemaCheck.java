/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.database;

import java.util.regex.Pattern;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Checks the server's schema before Liquibase migrates it. The operator creates the schema and the role that owns it;
 * without this check a missing schema surfaces as a Liquibase error that names neither.
 */
public class DatabaseSchemaCheck implements BeanPostProcessor {
    private static final Pattern SCHEMA_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private final String schema;

    public DatabaseSchemaCheck(String schema) {
        this.schema = schema;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (bean instanceof SpringLiquibase liquibase) {
            check(liquibase.getDataSource());
        }
        return bean;
    }

    void check(DataSource dataSource) {
        if (schema == null || !SCHEMA_NAME.matcher(schema).matches()) {
            throw new IllegalStateException(
                    "l2nx.database.schema must match " + SCHEMA_NAME.pattern() + ", got '" + schema + "'.");
        }

        final JdbcClient jdbc = JdbcClient.create(dataSource);
        final String role = jdbc.sql("SELECT current_user").query(String.class).single();
        final boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = ?)")
                .param(schema)
                .query(Boolean.class)
                .single();
        if (!exists) {
            throw new IllegalStateException("Schema '" + schema + "' does not exist. Create it for the server's role: "
                    + "CREATE SCHEMA " + schema + " AUTHORIZATION " + role + ";");
        }

        final boolean writable = jdbc.sql("SELECT has_schema_privilege(?, 'CREATE')")
                .param(schema)
                .query(Boolean.class)
                .single();
        if (!writable) {
            throw new IllegalStateException("Role '" + role + "' cannot create tables in schema '" + schema
                    + "'. Make it the owner: ALTER SCHEMA " + schema + " OWNER TO " + role + ";");
        }
    }
}
