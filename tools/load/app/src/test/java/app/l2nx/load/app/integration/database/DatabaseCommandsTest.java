/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.integration.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.load.app.LoadProperties;
import app.l2nx.load.app.database.DatabaseCommands;
import app.l2nx.load.app.database.DatabaseStatements;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DatabaseCommandsTest {
    private final LoadProperties.Database settings = LoadPostgres.uniqueDatabase();
    private final DatabaseCommands database =
            new DatabaseCommands(LoadProperties.builder().database(settings).build());
    private final DatabaseCommands.TemplateNames names = DatabaseCommands.TemplateNames.of(settings.template());

    @Nested
    class Create {
        @Test
        void create_shouldCreateTheSchemasOwnedByTheServerRoles() throws SQLException {
            database.create();

            final List<String> actual = new ArrayList<>();
            try (Connection connection = LoadPostgres.connect(settings, settings.name());
                    Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("""
                        SELECT n.nspname || ':' || r.rolname FROM pg_namespace n
                        JOIN pg_roles r ON r.oid = n.nspowner
                        WHERE n.nspname IN ('login', 'game') ORDER BY n.nspname\
                        """)) {
                while (result.next()) {
                    actual.add(result.getString(1));
                }
            }
            assertThat(actual).containsExactly("game:l2nx_game", "login:l2nx_login");
        }

        @Test
        void create_shouldFail_whenTheDatabaseExists() throws SQLException {
            database.create();

            assertThatThrownBy(database::create)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(settings.name() + " exists already");
        }

        @Test
        void create_shouldLeaveNoDatabase_whenASchemaOwnerIsMissing() throws SQLException {
            final LoadProperties.Database missingOwner = settings.toBuilder()
                    .schemas(List.of(LoadProperties.Schema.builder()
                            .name("login")
                            .owner("no_such_role")
                            .build()))
                    .build();
            final DatabaseCommands creator = new DatabaseCommands(
                    LoadProperties.builder().database(missingOwner).build());

            assertThatThrownBy(creator::create).isInstanceOf(SQLException.class);

            assertThat(exists(settings.name())).isFalse();
        }
    }

    @Nested
    class Snapshot {
        @Test
        void snapshot_shouldReplaceTheTemplateWithTheDatabase() throws SQLException {
            database.create();
            database.snapshot();
            createTable(settings.name(), "login.seeded");

            database.snapshot();

            assertThat(isTemplate(settings.template())).isTrue();
            assertThat(tableExists(settings.template(), "seeded")).isTrue();
        }

        @Test
        void snapshot_shouldKeepTheOldTemplate_whenTheDatabaseHasOpenConnections() throws SQLException {
            database.create();
            database.snapshot();

            try (Connection server = LoadPostgres.connect(settings, settings.name())) {
                assertThatThrownBy(database::snapshot)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining(settings.name())
                        .hasMessageContaining("stop the servers");
            }

            assertThat(isTemplate(settings.template())).isTrue();
        }

        @Test
        void snapshot_shouldKeepTheOldTemplate_whenTheCopyFails() throws SQLException {
            database.create();
            database.snapshot();
            createTable(settings.name(), "login.seeded");
            // A role that owns the template but may not copy the database: the copy fails, the drop would not.
            final String role = settings.name() + "_snapshotter";
            execute(null, "CREATE ROLE " + role + " LOGIN CREATEDB PASSWORD 'snapshotter'");
            execute(null, "ALTER DATABASE " + settings.template() + " OWNER TO " + role);
            final DatabaseCommands snapshotter = new DatabaseCommands(LoadProperties.builder()
                    .database(settings.toBuilder()
                            .user(role)
                            .password("snapshotter")
                            .build())
                    .build());

            assertThatThrownBy(snapshotter::snapshot).isInstanceOf(SQLException.class);

            assertThat(isTemplate(settings.template())).isTrue();
            assertThat(tableExists(settings.template(), "seeded"))
                    .as("still the old copy")
                    .isFalse();
            assertThat(exists(names.staging())).isFalse();
        }

        @Test
        void snapshot_shouldDropALeftoverStagingCopy_whenAnEarlierSnapshotFailed() throws SQLException {
            database.create();
            execute(null, "CREATE DATABASE " + names.staging() + " IS_TEMPLATE true");
            createTable(names.staging(), "leftover");
            createTable(settings.name(), "login.seeded");

            database.snapshot();

            assertThat(exists(names.staging())).isFalse();
            assertThat(isTemplate(settings.template())).isTrue();
            assertThat(tableExists(settings.template(), "seeded")).isTrue();
            assertThat(tableExists(settings.template(), "leftover")).isFalse();
        }

        @Test
        void snapshot_shouldDropALeftoverRetiredTemplate_whenAnEarlierSnapshotFailedToDropIt() throws SQLException {
            database.create();
            database.snapshot();
            execute(null, "CREATE DATABASE " + names.retired() + " IS_TEMPLATE true");
            createTable(settings.name(), "login.seeded");

            database.snapshot();

            assertThat(exists(names.retired())).isFalse();
            assertThat(isTemplate(settings.template())).isTrue();
            assertThat(tableExists(settings.template(), "seeded")).isTrue();
        }

        @Test
        void snapshot_shouldRestoreTheOldTemplate_whenTheNewOneCannotTakeItsName() throws SQLException {
            database.create();
            database.snapshot();
            createTable(settings.name(), "login.seeded");

            try (Connection admin =
                    failing(LoadPostgres.connect(settings, null), Set.of(swap(names.staging(), names.template())))) {
                assertThatThrownBy(() -> DatabaseCommands.snapshot(admin, settings.name(), names))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining(INJECTED);
            }

            assertThat(isTemplate(settings.template())).isTrue();
            assertThat(tableExists(settings.template(), "seeded"))
                    .as("still the old copy")
                    .isFalse();
            assertThat(exists(names.retired())).isFalse();
            assertThat(exists(names.staging())).isFalse();
        }

        @Test
        void snapshot_shouldKeepTheOldTemplateRetired_whenTheRestoreFailsToo() throws SQLException {
            database.create();
            database.snapshot();
            createTable(settings.name(), "login.seeded");
            final Set<String> failures =
                    Set.of(swap(names.staging(), names.template()), swap(names.retired(), names.template()));

            try (Connection admin = failing(LoadPostgres.connect(settings, null), failures)) {
                assertThatThrownBy(() -> DatabaseCommands.snapshot(admin, settings.name(), names))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(e.getSuppressed())
                                .singleElement()
                                .satisfies(suppressed -> assertThat(suppressed).hasMessageContaining(INJECTED)));
            }

            assertThat(exists(settings.template())).isFalse();
            assertThat(isTemplate(names.retired())).as("the old template, kept").isTrue();
            assertThat(tableExists(names.retired(), "seeded")).isFalse();
        }

        @Test
        void snapshot_shouldDropTheRetiredTemplate_whenAFailedRestoreLeftOnlyIt() throws SQLException {
            database.create();
            database.snapshot();
            execute(null, "ALTER DATABASE " + settings.template() + " RENAME TO " + names.retired());
            createTable(settings.name(), "login.seeded");

            database.snapshot();

            assertThat(isTemplate(settings.template())).isTrue();
            assertThat(tableExists(settings.template(), "seeded")).isTrue();
            assertThat(exists(names.retired())).isFalse();
        }

        @Test
        void snapshot_shouldFail_whenTheDatabaseDoesNotExist() {
            assertThatThrownBy(database::snapshot)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(settings.name() + " does not exist");
        }
    }

    @Nested
    class Reset {
        @Test
        void reset_shouldRecreateTheDatabaseFromTheTemplate() throws SQLException {
            database.create();
            database.snapshot();
            createTable(settings.name(), "login.played");

            database.reset();

            assertThat(tableExists(settings.name(), "played")).isFalse();
            assertThat(exists(settings.name())).isTrue();
        }

        @Test
        void reset_shouldFail_whenTheDatabaseHasOpenConnections() throws SQLException {
            database.create();
            database.snapshot();

            try (Connection server = LoadPostgres.connect(settings, settings.name())) {
                assertThatThrownBy(database::reset)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("stop the servers");
            }

            assertThat(exists(settings.name())).isTrue();
        }

        @Test
        void reset_shouldFail_whenTheTemplateDoesNotExist() throws SQLException {
            database.create();

            assertThatThrownBy(database::reset)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(settings.template() + " does not exist");
        }
    }

    private static final String INJECTED = "injected failure";

    private static String swap(String from, String to) {
        return DatabaseStatements.renameDatabase(from, to);
    }

    private static Connection failing(Connection connection, Set<String> failures) {
        return proxy(Connection.class, (proxy, method, args) -> {
            final Object result = invoke(connection, method, args);
            if (method.getName().equals("createStatement") && result instanceof Statement statement) {
                return proxy(Statement.class, (statementProxy, statementMethod, statementArgs) -> {
                    if (statementMethod.getName().equals("execute") && failures.contains(statementArgs[0])) {
                        throw new SQLException(INJECTED + ": " + statementArgs[0]);
                    }
                    return invoke(statement, statementMethod, statementArgs);
                });
            }
            return result;
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private boolean exists(String name) throws SQLException {
        return LoadPostgres.exists(settings, name);
    }

    private boolean isTemplate(String name) throws SQLException {
        return queryBoolean(null, "SELECT datistemplate FROM pg_database WHERE datname = ?", name);
    }

    private boolean tableExists(String database, String table) throws SQLException {
        return queryBoolean(database, "SELECT count(*) > 0 FROM pg_tables WHERE tablename = ?", table);
    }

    private boolean queryBoolean(String database, String sql, String parameter) throws SQLException {
        try (Connection connection = LoadPostgres.connect(settings, database);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void createTable(String database, String table) throws SQLException {
        execute(database, "CREATE TABLE " + table + " (id int)");
    }

    /** @param database {@code null} runs on the admin database */
    private void execute(String database, String sql) throws SQLException {
        try (Connection connection = LoadPostgres.connect(settings, database);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
