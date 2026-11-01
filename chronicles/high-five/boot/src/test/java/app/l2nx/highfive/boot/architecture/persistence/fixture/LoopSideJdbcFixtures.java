/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.persistence.fixture;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Each nested class stands in for a loop-side class; only {@link Clean} touches no database. */
public final class LoopSideJdbcFixtures {
    private LoopSideJdbcFixtures() {}

    public static final class Clean {
        private final List<String> names = List.of();

        int size() {
            return names.size();
        }
    }

    public static final class UsesJdbcClient {
        private JdbcClient jdbc;
    }

    public static final class UsesJdbcTemplate {
        int count(JdbcTemplate jdbc) {
            return jdbc.queryForObject("SELECT 1", Integer.class);
        }
    }

    public static final class UsesDataSource {
        private DataSource dataSource;
    }

    public static final class UsesRepository {
        private final ItemRepository items = new ItemRepository();

        int count() {
            return items.count();
        }
    }

    public static final class ItemRepository {
        int count() {
            return 0;
        }
    }
}
