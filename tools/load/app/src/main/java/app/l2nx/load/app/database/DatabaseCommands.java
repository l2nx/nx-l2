/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.database;

import app.l2nx.load.app.LoadProperties;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.stereotype.Component;

/**
 * Each command runs from the admin database and closes no one's connections: a database in use belongs to running
 * servers, so the command fails before it changes anything.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseCommands {
    /** A lowercase PostgreSQL identifier of at most 63 bytes, the server's limit. */
    public static final String IDENTIFIER = "[a-z_][a-z0-9_]{0,62}";

    /** An {@link #IDENTIFIER} that leaves room for the suffixes of the {@link TemplateNames} {@code db-snapshot} uses. */
    public static final String TEMPLATE_IDENTIFIER = "[a-z_][a-z0-9_]{0,58}";

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile(TEMPLATE_IDENTIFIER);
    private static final String OBJECT_IN_USE = "55006";
    private static final String STOP_THE_SERVERS = "stop the servers first";

    private final LoadProperties properties;

    /** @throws IllegalStateException when the database exists already */
    public void create() throws SQLException {
        final LoadProperties.Database settings = properties.database();
        final String name = settings.name();
        try (Connection admin = connect(settings, null)) {
            if (exists(admin, name)) {
                throw new IllegalStateException(
                        "Database " + name + " exists already; db-reset recreates it from the template");
            }
            execute(admin, DatabaseStatements.createDatabase(name));
            try (Connection created = connect(settings, name)) {
                for (LoadProperties.Schema schema : settings.schemas()) {
                    execute(created, DatabaseStatements.createSchema(schema.name(), schema.owner()));
                }
            } catch (SQLException | RuntimeException e) {
                // A half-made database would block the next db-create.
                undo(admin, e, DatabaseStatements.dropDatabase(name));
                throw e;
            }
        }
        log.info("Database {} created with schemas {}", name, settings.schemas());
    }

    /**
     * The copy is made under the staging name and the old template steps aside under the retired name before the swap,
     * so a failure on the way leaves the old template in place.
     *
     * @throws IllegalStateException when the database does not exist or it or the template has open connections
     */
    public void snapshot() throws SQLException {
        final LoadProperties.Database settings = properties.database();
        try (Connection admin = connect(settings, null)) {
            snapshot(admin, settings.name(), TemplateNames.of(settings.template()));
        }
        log.info("Template {} made from database {}", settings.template(), settings.name());
    }

    public static void snapshot(Connection admin, String name, TemplateNames names) throws SQLException {
        if (!exists(admin, name)) {
            throw new IllegalStateException("Database " + name + " does not exist; db-create and seed make it");
        }
        requireUnused(admin, name);
        requireUnused(admin, names.template());
        // A staging copy left by an earlier failure.
        dropIfExists(admin, names.staging());
        execute(admin, DatabaseStatements.createDatabase(names.staging(), name));
        // Marked before the swap, so the swap is a single rename that either happens or not.
        execute(admin, DatabaseStatements.markTemplate(names.staging(), true));
        if (exists(admin, names.template())) {
            dropIfExists(admin, names.retired());
            execute(admin, DatabaseStatements.renameDatabase(names.template(), names.retired()));
            try {
                execute(admin, DatabaseStatements.renameDatabase(names.staging(), names.template()));
            } catch (SQLException | RuntimeException e) {
                restore(admin, e, names);
                throw e;
            }
        } else {
            execute(admin, DatabaseStatements.renameDatabase(names.staging(), names.template()));
        }
        // Also a retired template a failed restore left behind: the new template is in place now.
        dropIfExists(admin, names.retired());
    }

    /** Best effort, every failure suppressed into {@code failure}. */
    private static void restore(Connection admin, Throwable failure, TemplateNames names) {
        try {
            execute(admin, DatabaseStatements.renameDatabase(names.retired(), names.template()));
        } catch (SQLException | RuntimeException e) {
            failure.addSuppressed(e);
            log.error(
                    "The old template is left as {}: rename it back to {} by hand",
                    names.retired(),
                    names.template(),
                    e);
            return;
        }
        try {
            dropIfExists(admin, names.staging());
        } catch (SQLException | RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    /** PostgreSQL refuses to drop a database marked as a template, so the mark goes first. */
    private static void dropIfExists(Connection admin, String database) throws SQLException {
        if (exists(admin, database)) {
            execute(admin, DatabaseStatements.markTemplate(database, false));
            execute(admin, DatabaseStatements.dropDatabase(database));
        }
    }

    /** @throws IllegalStateException when the template does not exist or it or the database has open connections */
    public void reset() throws SQLException {
        final LoadProperties.Database settings = properties.database();
        final String name = settings.name();
        final String template = settings.template();
        try (Connection admin = connect(settings, null)) {
            if (!exists(admin, template)) {
                throw new IllegalStateException("Template " + template + " does not exist; db-snapshot makes it");
            }
            requireUnused(admin, name);
            requireUnused(admin, template);
            execute(admin, DatabaseStatements.dropDatabase(name));
            execute(admin, DatabaseStatements.createDatabase(name, template));
        }
        log.info("Database {} recreated from template {}", name, template);
    }

    /** @param database {@code null} connects to the admin database */
    public static Connection connect(LoadProperties.Database settings, String database) throws SQLException {
        final PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(settings.adminUrl());
        if (database != null) {
            source.setDatabaseName(database);
        }
        if (settings.user() != null) {
            source.setUser(settings.user());
        }
        if (settings.password() != null) {
            source.setPassword(settings.password());
        }
        return source.getConnection();
    }

    private static boolean exists(Connection admin, String database) throws SQLException {
        try (PreparedStatement statement = admin.prepareStatement(DatabaseStatements.EXISTS)) {
            statement.setString(1, database);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void requireUnused(Connection admin, String database) throws SQLException {
        try (PreparedStatement statement = admin.prepareStatement(DatabaseStatements.CONNECTIONS)) {
            statement.setString(1, database);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                final long connections = result.getLong(1);
                if (connections > 0) {
                    throw new IllegalStateException(
                            "Database " + database + " has " + connections + " open connections: " + STOP_THE_SERVERS);
                }
            }
        }
    }

    private static void undo(Connection admin, Throwable failure, String... statements) {
        for (String sql : statements) {
            try {
                execute(admin, sql);
            } catch (SQLException | RuntimeException e) {
                failure.addSuppressed(e);
            }
        }
    }

    /**
     * The template and the two names {@code db-snapshot} moves it through: the staging copy and the retired old
     * template. The database the template is made from must take none of them.
     */
    public record TemplateNames(String template, String staging, String retired) {
        static final String STAGING_SUFFIX = "_new";
        static final String RETIRED_SUFFIX = "_old";

        /** @throws IllegalArgumentException when {@code template} does not match {@link DatabaseCommands#TEMPLATE_IDENTIFIER} */
        public static TemplateNames of(String template) {
            if (template == null || !TEMPLATE_PATTERN.matcher(template).matches()) {
                throw new IllegalArgumentException(
                        "Not a template name matching " + TEMPLATE_IDENTIFIER + ": " + template);
            }
            return new TemplateNames(template, template + STAGING_SUFFIX, template + RETIRED_SUFFIX);
        }

        public boolean contains(String database) {
            return template.equals(database) || staging.equals(database) || retired.equals(database);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
            // A connection opened after the check: the server refuses the same way.
            if (OBJECT_IN_USE.equals(e.getSQLState())) {
                throw new IllegalStateException(e.getMessage() + ": " + STOP_THE_SERVERS, e);
            }
            throw e;
        }
    }
}
