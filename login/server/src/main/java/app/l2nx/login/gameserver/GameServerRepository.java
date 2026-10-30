/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gameserver;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GameServerRepository {
    private static final String SELECT_ALL = "SELECT server_id, hex_id, chronicle FROM game_servers";
    private static final String INSERT = "INSERT INTO game_servers (server_id, hex_id) VALUES (?, ?)";
    private static final String UPDATE_CHRONICLE = "UPDATE game_servers SET chronicle = ? WHERE server_id = ?";

    private final JdbcClient jdbcClient;

    public List<StoredGameServer> findAll() {
        return jdbcClient
                .sql(SELECT_ALL)
                .query((rs, rowNum) -> StoredGameServer.builder()
                        .serverId(rs.getInt("server_id"))
                        .hexId(rs.getBytes("hex_id"))
                        .chronicle(rs.getString("chronicle"))
                        .build())
                .list();
    }

    public void create(int serverId, byte[] hexId) {
        jdbcClient.sql(INSERT).param(serverId).param(hexId).update();
    }

    public void updateChronicle(int serverId, String chronicle) {
        jdbcClient.sql(UPDATE_CHRONICLE).param(chronicle).param(serverId).update();
    }

    /** @param chronicle {@code null} until the game server first registered */
    @Builder(toBuilder = true)
    public record StoredGameServer(int serverId, byte[] hexId, String chronicle) {
        public StoredGameServer {
            hexId = Objects.requireNonNull(hexId, "hexId").clone();
        }

        @Override
        public byte[] hexId() {
            return hexId.clone();
        }

        // A record compares an array component by reference; the hex id is a value.
        @Override
        public boolean equals(Object other) {
            return (other instanceof StoredGameServer that)
                    && (serverId == that.serverId)
                    && Arrays.equals(hexId, that.hexId)
                    && Objects.equals(chronicle, that.chronicle);
        }

        @Override
        public int hashCode() {
            return (31 * Objects.hash(serverId, chronicle)) + Arrays.hashCode(hexId);
        }

        /** Never prints the hex id: it proves the game server's claim to its id. */
        @Override
        public String toString() {
            return "StoredGameServer[serverId=" + serverId + ", chronicle=" + chronicle + "]";
        }
    }
}
