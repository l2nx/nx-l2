/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.gameserver;

import app.l2nx.login.config.LoginProperties;
import app.l2nx.login.gameserver.model.GameServerInfo;
import app.l2nx.login.gameserver.model.Registration;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GameServerRegistry {
    private final GameServerTable gameServerTable;
    private final GameServerRepository repository;
    private final LoginProperties properties;

    /**
     * A known hex id gets its server back; an unknown one takes the desired id, or the lowest free id when alternatives
     * are accepted, also when another server wins the desired id mid-registration.
     */
    public Registration register(int desiredId, byte[] hexId, boolean acceptAlternativeId) {
        final GameServerInfo registered = gameServerTable.getRegisteredGameServerById(desiredId);
        if ((registered != null) && Arrays.equals(registered.getHexId(), hexId)) {
            return new Registration.Accepted(registered);
        }
        final boolean acceptNew = properties.gameServer().acceptNew();
        final GameServerInfo info = new GameServerInfo(desiredId, hexId);
        if (registered == null) {
            if (!acceptNew) {
                return refused(
                        Registration.Reason.NOT_ACCEPTED,
                        "server id " + desiredId + " is not registered and new game servers are not accepted");
            }
            if (gameServerTable.register(desiredId, info)) {
                return stored(info);
            }
            if (!acceptAlternativeId) {
                return refused(
                        Registration.Reason.ID_TAKEN, "server id " + desiredId + " was taken during the registration");
            }
        } else if (!acceptNew || !acceptAlternativeId) {
            return refused(
                    Registration.Reason.HEX_ID_MISMATCH,
                    "the hex id does not match server id " + desiredId + " and no other id may be given");
        }
        if (!gameServerTable.registerWithFirstAvailableId(info)) {
            return refused(Registration.Reason.NO_FREE_ID, "no free server id is left");
        }
        return stored(info);
    }

    /** Stores the chronicle a game server registered with: the server list shows the server before it next connects. */
    public void storeChronicle(int id, String chronicle) {
        try {
            repository.updateChronicle(id, chronicle);
        } catch (DataAccessException e) {
            log.error("Error while saving the chronicle of game server {}!", id, e);
        }
    }

    /** A failed insert keeps the server for this run: it registers again under the same hex id after a restart. */
    private Registration stored(GameServerInfo info) {
        try {
            repository.create(info.getId(), info.getHexId());
        } catch (DataAccessException e) {
            log.error("Error while saving game server {}!", info.getId(), e);
        }
        return new Registration.Accepted(info);
    }

    private static Registration refused(Registration.Reason reason, String detail) {
        return Registration.Refused.builder().reason(reason).detail(detail).build();
    }
}
