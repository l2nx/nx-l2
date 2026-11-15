/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.clock.ManualGameClock;
import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.character.repository.StoredCharacterRepository;
import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.character.model.OnlineStatus;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class CharacterLoaderTest {
    // A loading or built character stays in the loop's registry after a test, so each test gets its own loop.
    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    private final UUID characterId = EntityIds.next();
    private final UUID accountId = EntityIds.next();
    private final StoredCharacterRepository rows = mock(StoredCharacterRepository.class);
    private final List<Runnable> executor = new ArrayList<>();
    private final CharacterLoader loader = new CharacterLoader(
            rows,
            new BackgroundQueries(executor::add, mock(TaskScheduler.class)),
            mock(PlatformTransactionManager.class),
            new ManualGameClock(WorldLoopExtension.WALL_START, ZoneOffset.UTC));
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);
    private final ClientPort port = mock(ClientPort.class);
    private final StoredCharacter stored =
            StoredCharacter.builder().characterId(characterId).build();
    private final Player player = mock(Player.class);
    private final List<Player> built = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(client.session()).thenReturn(session);
        when(client.port()).thenReturn(port);
        when(client.getConnectionState()).thenReturn(SessionState.AUTHED);
        when(client.getAccountId()).thenReturn(accountId);
        when(rows.find(characterId)).thenReturn(Optional.of(stored));
    }

    private void runExecutor() {
        final List<Runnable> tasks = new ArrayList<>(executor);
        executor.clear();
        tasks.forEach(Runnable::run);
    }

    private boolean loading() {
        return WorldLoopExtension.callInPass(
                () -> WorldPartition.loadedCharacters().isLoading(characterId));
    }

    private boolean accountLoaded() {
        return WorldLoopExtension.callInPass(
                () -> WorldPartition.loadedGameAccounts().isLoaded(accountId));
    }

    private void load(Consumer<Player> onBuilt) {
        WorldLoopExtension.runInPass(() -> loader.load(characterId, client, onBuilt));
    }

    @Nested
    class Load {
        @Test
        void load_shouldMarkTheCharacterLoading_untilTheResult() {
            load(built::add);

            assertThat(loading()).isTrue();
            assertThat(accountLoaded())
                    .as("the account stays pinned while its snapshot is read")
                    .isTrue();
            verify(rows, never()).find(characterId);
            verify(session, never()).release();
        }

        @Test
        void load_shouldBuildRegisterAndReleaseTheSession_whenTheReadAnswers() {
            try (MockedStatic<Player> players = mockStatic(Player.class)) {
                players.when(() -> Player.load(stored)).thenReturn(player);
                load(built::add);

                runExecutor();
                WorldLoopExtension.runInPass(() -> {});

                assertThat(built).containsExactly(player);
                assertThat(WorldLoopExtension.callInPass(
                                () -> WorldPartition.loadedCharacters().find(characterId)))
                        .containsSame(player);
                assertThat(loading()).isFalse();
                assertThat(accountLoaded()).isFalse();
                verify(rows).updateOnlineStatus(eq(characterId), eq(OnlineStatus.ONLINE), any());
                verify(session).release();
            }
        }

        @Test
        void load_shouldCloseWithServerCloseAndEndTheLoad_whenTheReadFails() {
            when(rows.find(characterId)).thenThrow(new IllegalStateException("database down"));
            load(built::add);

            runExecutor();
            WorldLoopExtension.runInPass(() -> {});

            verify(port).emitAndClose(ServerClosed.INSTANCE);
            verify(session).release();
            assertThat(built).isEmpty();
            assertThat(loading()).isFalse();
            assertThat(accountLoaded()).isFalse();
        }

        @Test
        void load_shouldDiscardAndMarkOfflineBeforeEndingTheLoad_whenTheSessionClosedFirst() {
            load(built::add);
            when(client.getConnectionState()).thenReturn(SessionState.CLOSING);

            runExecutor();
            WorldLoopExtension.runInPass(() -> {});

            assertThat(built).isEmpty();
            verify(session).release();
            assertThat(loading()).as("a next load waits for the offline mark").isTrue();
            runExecutor();
            WorldLoopExtension.runInPass(() -> {});
            verify(rows).updateOnlineStatus(eq(characterId), eq(OnlineStatus.OFFLINE), any());
            assertThat(loading()).isFalse();
            assertThat(accountLoaded()).isFalse();
        }

        @Test
        void load_shouldCloseAndDiscard_whenTheBuildFails() {
            try (MockedStatic<Player> players = mockStatic(Player.class)) {
                players.when(() -> Player.load(stored)).thenThrow(new IllegalStateException("bad row"));
                load(built::add);

                runExecutor();
                WorldLoopExtension.runInPass(() -> {});
                runExecutor();
                WorldLoopExtension.runInPass(() -> {});

                verify(port).emitAndClose(ServerClosed.INSTANCE);
                verify(session).release();
                verify(rows).updateOnlineStatus(eq(characterId), eq(OnlineStatus.OFFLINE), any());
                assertThat(loading()).isFalse();
                assertThat(accountLoaded()).isFalse();
            }
        }

        @Test
        void load_shouldLogThePlayerOutAndEndTheLoad_whenTheSelectionFails() {
            final Disconnection disconnection = mock(Disconnection.class);
            try (MockedStatic<Player> players = mockStatic(Player.class);
                    MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                players.when(() -> Player.load(stored)).thenReturn(player);
                disconnections.when(() -> Disconnection.of(client, player)).thenReturn(disconnection);
                load(selected -> {
                    throw new IllegalStateException("broken listener");
                });

                runExecutor();
                WorldLoopExtension.runInPass(() -> {});

                verify(port).emitAndClose(ServerClosed.INSTANCE);
                verify(disconnection).storeAndDelete();
                verify(session).release();
                assertThat(loading()).isFalse();
                assertThat(accountLoaded()).isFalse();
            }
        }

        @Test
        void load_shouldStartTheRead_afterTheSaveWaitingForTheTransactionInFlight() {
            final List<String> order = new ArrayList<>();
            when(rows.find(characterId)).thenAnswer(invocation -> {
                order.add("read");
                return Optional.of(stored);
            });
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.inFlightCharacters().begin(characterId);
                WorldPartition.inFlightCharacters().whenIdle(characterId, () -> order.add("save"));
                loader.load(characterId, client, built::add);
            });
            assertThat(loading()).isTrue();
            assertThat(executor)
                    .as("no read while the transaction is in flight")
                    .isEmpty();

            WorldLoopExtension.runInPass(
                    () -> WorldPartition.inFlightCharacters().end(characterId));
            runExecutor();

            assertThat(order).containsExactly("save", "read");
        }

        @Test
        void load_shouldRunAWaitingSelection_afterTheBuiltPlayerReachedItsSession() {
            final List<String> order = new ArrayList<>();
            try (MockedStatic<Player> players = mockStatic(Player.class)) {
                players.when(() -> Player.load(stored)).thenReturn(player);
                load(selected -> order.add("selected"));
                WorldLoopExtension.runInPass(
                        () -> WorldPartition.loadedCharacters().whenLoaded(characterId, () -> order.add("next")));

                runExecutor();
                WorldLoopExtension.runInPass(() -> {});

                assertThat(order).containsExactly("selected", "next");
            }
        }
    }

    @Nested
    class ReadAndMarkOnline {
        @Test
        void readAndMarkOnline_shouldReadAgain_whenTheSnapshotFailsToSerialize() {
            doThrow(new PessimisticLockingFailureException("concurrent update"))
                    .doNothing()
                    .when(rows)
                    .updateOnlineStatus(eq(characterId), eq(OnlineStatus.ONLINE), any());

            final StoredCharacter actual = loader.readAndMarkOnline(characterId);

            assertThat(actual).isSameAs(stored);
            verify(rows, times(2)).find(characterId);
        }

        @Test
        void readAndMarkOnline_shouldReadAgain_whenTheFailureCarriesTheSerializationState() {
            final List<Integer> attempts = new ArrayList<>();
            doAnswer(invocation -> {
                        attempts.add(attempts.size());
                        if (attempts.size() == 1) {
                            throw new TransactionSystemException(
                                    "untranslated", new SQLException("could not serialize", "40001"));
                        }
                        return null;
                    })
                    .when(rows)
                    .updateOnlineStatus(eq(characterId), eq(OnlineStatus.ONLINE), any());

            assertThat(loader.readAndMarkOnline(characterId)).isSameAs(stored);
            assertThat(attempts).hasSize(2);
        }

        @Test
        void readAndMarkOnline_shouldFail_afterTheLastRetry() {
            final PessimisticLockingFailureException failure =
                    new PessimisticLockingFailureException("concurrent update");
            doThrow(failure).when(rows).updateOnlineStatus(eq(characterId), eq(OnlineStatus.ONLINE), any());

            assertThatThrownBy(() -> loader.readAndMarkOnline(characterId)).isSameAs(failure);
            verify(rows, times(CharacterLoader.READ_RETRIES + 1)).find(characterId);
        }

        @Test
        void readAndMarkOnline_shouldFailAtOnce_whenTheFailureIsNoSerializationFailure() {
            doThrow(new DataIntegrityViolationException("broken row"))
                    .when(rows)
                    .updateOnlineStatus(eq(characterId), eq(OnlineStatus.ONLINE), any());

            assertThatThrownBy(() -> loader.readAndMarkOnline(characterId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            verify(rows).find(characterId);
        }

        @Test
        void load_shouldCloseWithServerClose_whenEveryReadFailsToSerialize() {
            doThrow(new PessimisticLockingFailureException("concurrent update"))
                    .when(rows)
                    .updateOnlineStatus(eq(characterId), eq(OnlineStatus.ONLINE), any());
            load(built::add);

            runExecutor();
            WorldLoopExtension.runInPass(() -> {});

            verify(port).emitAndClose(ServerClosed.INSTANCE);
            verify(session).release();
            assertThat(loading()).isFalse();
        }
    }
}
