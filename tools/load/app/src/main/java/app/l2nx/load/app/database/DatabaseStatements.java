/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.database;

import java.util.regex.Pattern;

/**
 * The statements of the database commands. Identifiers cannot be bound as parameters, so every one is checked against
 * {@link DatabaseCommands#IDENTIFIER} and quoted; the pattern admits no quote, so the quoting cannot be broken out of.
 */
public final class DatabaseStatements {
    static final String EXISTS = "SELECT 1 FROM pg_database WHERE datname = ?";
    // Autovacuum workers are not counted: PostgreSQL stops them itself for a drop or a copy.
    static final String CONNECTIONS =
            "SELECT count(*) FROM pg_stat_activity WHERE datname = ? AND backend_type = 'client backend'";

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(DatabaseCommands.IDENTIFIER);

    private DatabaseStatements() {}

    static String createDatabase(String name) {
        return "CREATE DATABASE " + quoted(name);
    }

    static String createDatabase(String name, String template) {
        return "CREATE DATABASE " + quoted(name) + " TEMPLATE " + quoted(template);
    }

    static String createSchema(String name, String owner) {
        return "CREATE SCHEMA " + quoted(name) + " AUTHORIZATION " + quoted(owner);
    }

    public static String renameDatabase(String name, String newName) {
        return "ALTER DATABASE " + quoted(name) + " RENAME TO " + quoted(newName);
    }

    static String markTemplate(String name, boolean template) {
        return "ALTER DATABASE " + quoted(name) + " IS_TEMPLATE " + template;
    }

    // No WITH (FORCE): a database in use belongs to running servers, which must be stopped, not cut off.
    static String dropDatabase(String name) {
        return "DROP DATABASE IF EXISTS " + quoted(name);
    }

    /** @throws IllegalArgumentException when {@code identifier} does not match {@link DatabaseCommands#IDENTIFIER} */
    static String quoted(String identifier) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "Not an identifier matching " + DatabaseCommands.IDENTIFIER + ": " + identifier);
        }
        return '"' + identifier + '"';
    }
}
