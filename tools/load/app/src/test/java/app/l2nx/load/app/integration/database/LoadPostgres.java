/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.integration.database;

import app.l2nx.database.PostgresDatabase;
import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.app.database.DatabaseCommands;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The shared PostgreSQL container with the two server roles, as {@code docker/postgres-init.sql} creates them. Each
 * caller takes its own database names, so tests neither share databases nor need cleanup.
 */
public final class LoadPostgres {
    private static final List<String> ROLES = List.of("l2nx_login", "l2nx_game");

    private static final PostgreSQLContainer CONTAINER = withRoles(PostgresDatabase.container());

    private LoadPostgres() {}

    public static LoadProperties.Database uniqueDatabase() {
        final String name =
                "l2nx_load_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return LoadProperties.Database.builder()
                .adminUrl(CONTAINER.getJdbcUrl())
                .user(CONTAINER.getUsername())
                .password(CONTAINER.getPassword())
                .name(name)
                .template(name + "_template")
                .schemas(List.of(
                        LoadProperties.Schema.builder()
                                .name("login")
                                .owner("l2nx_login")
                                .build(),
                        LoadProperties.Schema.builder()
                                .name("game")
                                .owner("l2nx_game")
                                .build()))
                .build();
    }

    /** @param database {@code null} connects to the admin database */
    public static Connection connect(LoadProperties.Database settings, String database) throws SQLException {
        return DatabaseCommands.connect(settings, database);
    }

    public static boolean exists(LoadProperties.Database settings, String database) throws SQLException {
        try (Connection connection = connect(settings, null);
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            statement.setString(1, database);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static PostgreSQLContainer withRoles(PostgreSQLContainer container) {
        try (Connection connection = DatabaseCommands.connect(adminSettings(container), null);
                Statement statement = connection.createStatement()) {
            for (String role : ROLES) {
                statement.execute("CREATE ROLE " + role);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create the server roles", e);
        }
        return container;
    }

    private static LoadProperties.Database adminSettings(PostgreSQLContainer container) {
        return LoadProperties.Database.builder()
                .adminUrl(container.getJdbcUrl())
                .user(container.getUsername())
                .password(container.getPassword())
                .build();
    }
}
