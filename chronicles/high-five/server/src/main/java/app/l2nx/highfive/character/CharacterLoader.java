/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import app.l2nx.engine.core.clock.GameClock;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.highfive.character.repository.StoredCharacterRepository;
import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** The online mark commits with the read, before the player exists, so the logout save never commits before it. */
@Slf4j
@Component
public class CharacterLoader {
    static final int READ_RETRIES = 3;
    private static final String SERIALIZATION_FAILURE = "40001";

    private final StoredCharacterRepository rows;
    private final BackgroundQueries queries;
    private final TransactionTemplate transactions;
    private final Clock wallClock;

    public CharacterLoader(
            StoredCharacterRepository rows,
            BackgroundQueries queries,
            PlatformTransactionManager transactionManager,
            GameClock clock) {
        this.rows = rows;
        this.queries = queries;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.wallClock = clock.wallClock();
    }

    /** The caller holds the session; every path here releases it. */
    public void load(UUID characterId, GameClient client, Consumer<Player> onBuilt) {
        if (!WorldPartition.loadedCharacters().startLoading(characterId)) {
            throw new IllegalStateException("Character " + characterId + " is loading already");
        }
        final UUID accountId = client.getAccountId();
        WorldPartition.loadedGameAccounts().pin(accountId);
        try {
            WorldPartition.inFlightCharacters()
                    .whenIdle(
                            characterId,
                            () -> queries.run(
                                    () -> readAndMarkOnline(characterId),
                                    WorldPartition::offer,
                                    stored -> built(characterId, accountId, client, stored, onBuilt),
                                    failure -> failed(characterId, accountId, client, failure)));
        } catch (RuntimeException e) {
            loadEnded(characterId, accountId);
            throw e;
        }
    }

    private void built(
            UUID characterId, UUID accountId, GameClient client, StoredCharacter stored, Consumer<Player> onBuilt) {
        if (client.getConnectionState() != SessionState.AUTHED) {
            client.session().release();
            discard(characterId, accountId);
            return;
        }
        final Player player;
        try {
            player = Player.load(stored);
        } catch (RuntimeException e) {
            log.error("Character {} could not be built from its rows", characterId, e);
            client.port().emitAndClose(ServerClosed.INSTANCE);
            client.session().release();
            discard(characterId, accountId);
            return;
        }
        WorldPartition.loadedCharacters().loaded(characterId, player);
        try {
            onBuilt.accept(player);
        } catch (RuntimeException e) {
            log.error("Character {} was built but its selection failed", characterId, e);
            client.port().emitAndClose(ServerClosed.INSTANCE);
            Disconnection.of(client, player).storeAndDelete();
        } finally {
            client.session().release();
            loadEnded(characterId, accountId);
        }
    }

    private void failed(UUID characterId, UUID accountId, GameClient client, RuntimeException failure) {
        loadEnded(characterId, accountId);
        log.error("Character {} could not be loaded", characterId, failure);
        client.port().emitAndClose(ServerClosed.INSTANCE);
        client.session().release();
    }

    // The load ends only after the offline mark, so a waiting next load never has its online mark overtaken.
    private void discard(UUID characterId, UUID accountId) {
        queries.run(
                () -> {
                    markOffline(characterId);
                    return characterId;
                },
                WorldPartition::offer,
                done -> loadEnded(characterId, accountId),
                failure -> {
                    log.error(
                            "Character {} stays marked online: its discarded load was not marked offline",
                            characterId,
                            failure);
                    loadEnded(characterId, accountId);
                });
    }

    private static void loadEnded(UUID characterId, UUID accountId) {
        WorldPartition.loadedCharacters().loadEnded(characterId);
        WorldPartition.loadedGameAccounts().unpin(accountId);
    }

    public Optional<StoredCharacter> read(UUID characterId) {
        return transactions.execute(status -> rows.find(characterId));
    }

    StoredCharacter readAndMarkOnline(UUID characterId) {
        for (int retry = 0; ; retry++) {
            try {
                return readAndMarkOnlineOnce(characterId);
            } catch (RuntimeException e) {
                if ((retry == READ_RETRIES) || !serializationFailure(e)) {
                    throw e;
                }
                log.debug("Character {} read again after a serialization failure", characterId, e);
            }
        }
    }

    private StoredCharacter readAndMarkOnlineOnce(UUID characterId) {
        return transactions.execute(status -> {
            final StoredCharacter stored = rows.find(characterId)
                    .orElseThrow(() -> new IllegalStateException("Character " + characterId + " has no row"));
            rows.updateOnlineStatus(characterId, OnlineStatus.ONLINE, wallClock.instant());
            return stored;
        });
    }

    private static boolean serializationFailure(RuntimeException failure) {
        if (failure instanceof PessimisticLockingFailureException) {
            return true;
        }
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if ((cause instanceof SQLException sql) && SERIALIZATION_FAILURE.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    void markOffline(UUID characterId) {
        transactions.executeWithoutResult(
                status -> rows.updateOnlineStatus(characterId, OnlineStatus.OFFLINE, wallClock.instant()));
    }
}
