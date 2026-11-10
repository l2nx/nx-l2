/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import app.l2nx.engine.core.loop.Loop;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;

// TODO: drop with JdbcOnLoopListener once E4 moves JDBC off the loop (#69).
final class JdbcOnLoopStatementListener implements QueryExecutionListener {
    static final String METER = "l2nx.engine.jdbc.on.loop.statements";

    private final Meter.MeterProvider<Counter> counters;

    JdbcOnLoopStatementListener(MeterRegistry registry) {
        this.counters = Counter.builder(METER)
                .description("Statements loop threads ran, by kind")
                .withRegistry(registry);
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {}

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        final Loop loop = Loop.current();
        if (loop == null) {
            return;
        }
        for (QueryInfo query : queryInfoList) {
            counters.withTags("loop", loop.getName(), "kind", kind(query.getQuery()))
                    .increment(Math.max(1, query.getParametersList().size()));
        }
    }

    // QueryUtils.getQueryType of datasource-proxy takes any statement starting with an "s" for a select, SET included.
    static String kind(String sql) {
        final String statement = sql.stripLeading();
        if (startsWith(statement, "SELECT")) {
            return "read";
        }
        if (startsWith(statement, "INSERT") || startsWith(statement, "UPDATE") || startsWith(statement, "DELETE")) {
            return "write";
        }
        return "other";
    }

    private static boolean startsWith(String statement, String keyword) {
        return statement.regionMatches(true, 0, keyword, 0, keyword.length());
    }
}
