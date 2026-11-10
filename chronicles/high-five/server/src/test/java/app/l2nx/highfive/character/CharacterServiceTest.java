/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.persistence.query.BackgroundQueries;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.character.repository.CharacterSlots;
import app.l2nx.highfive.character.view.CharacterListShown;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.OfflineRows;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.repository.character.model.CharacterSlotRow;
import app.l2nx.highfive.session.SessionKey;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.TaskScheduler;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
@ResourceLock("CharacterService")
class CharacterServiceTest {
    private final UUID accountId = UUID.randomUUID();
    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final CharacterSlots slots = mock(CharacterSlots.class);
    private final List<Runnable> executor = new ArrayList<>();
    private final CharacterService service =
            new CharacterService(slots, new BackgroundQueries(executor::add, mock(TaskScheduler.class)), registry);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);
    private final ClientPort clientPort = mock(ClientPort.class);

    CharacterServiceTest() {
        when(client.getAccountId()).thenReturn(accountId);
        when(client.getAccountName()).thenReturn("alice");
        when(client.getSessionId())
                .thenReturn(SessionKey.builder()
                        .loginOk1(1)
                        .loginOk2(2)
                        .playOk1(3)
                        .playOk2(4)
                        .build());
        when(client.port()).thenReturn(clientPort);
        when(client.session()).thenReturn(session);
    }

    private void runRead() {
        final List<Runnable> tasks = new ArrayList<>(executor);
        executor.clear();
        tasks.forEach(Runnable::run);
        WorldLoopExtension.runInPass(() -> {});
    }

    private static StoredCharacterSlot slot(CharacterSlotRow row) {
        return StoredCharacterSlot.builder().character(row).paperdoll(List.of()).build();
    }

    private static CharacterSummary summary(int objectId, long lastAccess) {
        return CharacterSummary.builder()
                .objectId(objectId)
                .lastAccess(lastAccess)
                .paperdoll(List.of())
                .build();
    }

    @Nested
    class ShowCharacterList {
        @Test
        void showCharacterList_shouldKeepAndShowTheListWithTheMostRecentPreselected() {
            final StoredCharacterSlot older =
                    slot(CharacterSlotRow.builder().id(EntityIds.next()).build());
            final StoredCharacterSlot newer =
                    slot(CharacterSlotRow.builder().id(EntityIds.next()).build());
            final CharacterSummary first = summary(1, 5);
            final CharacterSummary second = summary(2, 9);
            when(slots.findByAccount(accountId)).thenReturn(List.of(older, newer));
            when(slots.summarize(older)).thenReturn(first);
            when(slots.summarize(newer)).thenReturn(second);

            WorldLoopExtension.runInPass(() -> service.showCharacterList(client));
            runRead();

            verify(client).setCharSelection(List.of(first, second));
            verify(clientPort)
                    .emit(CharacterListShown.builder()
                            .login("alice")
                            .playOk1(3)
                            .characters(List.of(first, second))
                            .activeSlot(1)
                            .build());
        }

        @Test
        void showCharacterList_shouldReadOffTheLoopAndHoldTheSession_untilTheListIsSet() {
            when(slots.findByAccount(accountId)).thenReturn(List.of());

            WorldLoopExtension.runInPass(() -> service.showCharacterList(client, 0));
            verify(slots, never()).findByAccount(accountId);
            verify(session, never()).release();
            runRead();

            final InOrder order = inOrder(session, client);
            order.verify(session).hold();
            order.verify(client).setCharSelection(List.of());
            order.verify(session).release();
        }

        @Test
        void showCharacterList_shouldCloseWithServerCloseAndRelease_whenTheReadFails() {
            when(slots.findByAccount(accountId)).thenThrow(new DataAccessResourceFailureException("database down"));

            WorldLoopExtension.runInPass(() -> service.showCharacterList(client));
            runRead();

            verify(clientPort).emitAndClose(ServerClosed.INSTANCE);
            verify(client, never()).setCharSelection(any());
            verify(session).release();
        }

        @ParameterizedTest(name = "due {0} min before now -> deleted")
        @ValueSource(longs = {1440, 0})
        void showCharacterList_shouldDeleteAnExpiredCharacterAndLeaveItOut(long minutesBeforeNow) {
            final UUID characterId = EntityIds.next();
            final StoredCharacterSlot expired = slot(CharacterSlotRow.builder()
                    .id(characterId)
                    .deleteAt(ClockBridgeExtension.WALL_START.minus(Duration.ofMinutes(minutesBeforeNow)))
                    .build());
            when(slots.findByAccount(accountId)).thenReturn(List.of(expired));

            try (MockedStatic<GameClient> clients = mockStatic(GameClient.class);
                    MockedStatic<ClanTable> clans = mockStatic(ClanTable.class);
                    MockedStatic<OfflineRows> rows = mockStatic(OfflineRows.class)) {
                clans.when(ClanTable::getInstance).thenReturn(mock(ClanTable.class));

                WorldLoopExtension.runInPass(() -> service.showCharacterList(client, 0));
                runRead();

                clients.verify(() -> GameClient.deleteCharByObjId(registry.handleOf(characterId)));
            }

            verify(slots, never()).summarize(expired);
            verify(client).setCharSelection(List.of());
        }

        @Test
        void showCharacterList_shouldKeepAnExpiredCharacter_whenItIsLoaded() {
            final UUID characterId = EntityIds.next();
            final StoredCharacterSlot expired = slot(CharacterSlotRow.builder()
                    .id(characterId)
                    .deleteAt(ClockBridgeExtension.WALL_START.minus(Duration.ofMinutes(1)))
                    .build());
            final CharacterSummary summary = summary(registry.handleOf(characterId), 0);
            when(slots.findByAccount(accountId)).thenReturn(List.of(expired));
            when(slots.summarize(expired)).thenReturn(summary);

            try (MockedStatic<GameClient> clients = mockStatic(GameClient.class);
                    MockedStatic<OfflineRows> rows = mockStatic(OfflineRows.class)) {
                rows.when(() -> OfflineRows.isLoaded(registry.handleOf(characterId)))
                        .thenReturn(true);

                WorldLoopExtension.runInPass(() -> service.showCharacterList(client, 0));
                runRead();

                clients.verify(() -> GameClient.deleteCharByObjId(anyInt()), never());
            }

            verify(client).setCharSelection(List.of(summary));
        }
    }

    @Nested
    class RefreshSlots {
        @Test
        void refreshSlots_shouldKeepTheListWithoutShowingIt() {
            when(slots.findByAccount(accountId)).thenReturn(List.of());

            WorldLoopExtension.runInPass(() -> service.refreshSlots(client));
            runRead();

            verify(client).setCharSelection(List.of());
            verify(clientPort, never()).emit(any());
            assertThat(executor).isEmpty();
        }
    }
}
