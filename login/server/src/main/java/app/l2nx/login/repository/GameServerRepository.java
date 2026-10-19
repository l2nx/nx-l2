/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.repository;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class GameServerRepository {
    private static final String SELECT_ALL = "SELECT * FROM gameservers";
    private static final String INSERT = "INSERT INTO gameservers (hexid,server_id,host) values (?,?,?)";

    private final JdbcClient jdbcClient;

    public GameServerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Map<Integer, String> findHexIds() {
        final Map<Integer, String> hexIds = new HashMap<>();
        jdbcClient.sql(SELECT_ALL).query(rs -> {
            hexIds.put(rs.getInt("server_id"), rs.getString("hexid"));
        });
        return hexIds;
    }

    public void create(int serverId, String hexId, String host) {
        jdbcClient.sql(INSERT).param(hexId).param(serverId).param(host).update();
    }
}
