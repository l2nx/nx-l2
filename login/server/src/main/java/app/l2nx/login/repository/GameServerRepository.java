/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.repository;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GameServerRepository {
    private static final String SELECT_ALL = "SELECT server_id, hex_id FROM game_servers";
    private static final String INSERT = "INSERT INTO game_servers (server_id, hex_id) VALUES (?, ?)";

    private final JdbcClient jdbcClient;

    public Map<Integer, String> findHexIds() {
        final Map<Integer, String> hexIds = new HashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            hexIds.put(rs.getInt("server_id"), rs.getString("hex_id"));
        });
        return hexIds;
    }

    public void create(int serverId, String hexId) {
        jdbcClient.sql(INSERT).param(serverId).param(hexId).update();
    }
}
