/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.character.repository.CharacterSlots;
import app.l2nx.highfive.character.view.CharacterListShown;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.data.sql.ClanTable;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.repository.character.model.CharacterRow;
import app.l2nx.highfive.session.SessionKey;
import java.time.Duration;
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
import org.mockito.MockedStatic;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock("CharacterService")
class CharacterServiceTest {
    private final UUID accountId = UUID.randomUUID();
    private final CharacterSlots slots = mock(CharacterSlots.class);
    private final CharacterService service = new CharacterService(slots);
    private final GameClient client = mock(GameClient.class);
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
    }

    @Nested
    class ShowCharacterList {
        @Test
        void showCharacterList_shouldKeepAndShowTheListWithTheMostRecentPreselected() {
            final CharacterRow older = CharacterRow.builder().objectId(1).build();
            final CharacterRow newer = CharacterRow.builder().objectId(2).build();
            final CharacterSummary first = CharacterSummary.builder()
                    .objectId(1)
                    .lastAccess(5)
                    .paperdoll(List.of())
                    .build();
            final CharacterSummary second = CharacterSummary.builder()
                    .objectId(2)
                    .lastAccess(9)
                    .paperdoll(List.of())
                    .build();
            when(slots.findByAccount(accountId)).thenReturn(List.of(older, newer));
            when(slots.summarize(older)).thenReturn(first);
            when(slots.summarize(newer)).thenReturn(second);

            service.showCharacterList(client);

            verify(client).setCharSelection(List.of(first, second));
            verify(clientPort)
                    .emit(CharacterListShown.builder()
                            .login("alice")
                            .playOk1(3)
                            .characters(List.of(first, second))
                            .activeSlot(1)
                            .build());
        }

        @ParameterizedTest(name = "due {0} min before now -> deleted")
        @ValueSource(longs = {1440, 0})
        void showCharacterList_shouldDeleteAnExpiredCharacterAndLeaveItOut(long minutesBeforeNow) {
            final CharacterRow expired = CharacterRow.builder()
                    .objectId(1)
                    .deleteAt(ClockBridgeExtension.WALL_START.minus(Duration.ofMinutes(minutesBeforeNow)))
                    .build();
            when(slots.findByAccount(accountId)).thenReturn(List.of(expired));

            try (MockedStatic<GameClient> clients = mockStatic(GameClient.class);
                    MockedStatic<ClanTable> clans = mockStatic(ClanTable.class)) {
                clans.when(ClanTable::getInstance).thenReturn(mock(ClanTable.class));

                service.showCharacterList(client, 0);

                clients.verify(() -> GameClient.deleteCharByObjId(1));
            }

            verify(slots, never()).summarize(expired);
            verify(client).setCharSelection(List.of());
        }
    }

    @Nested
    class RefreshSlots {
        @Test
        void refreshSlots_shouldKeepTheListWithoutShowingIt() {
            when(slots.findByAccount(accountId)).thenReturn(List.of());

            service.refreshSlots(client);

            verify(client).setCharSelection(List.of());
            verify(clientPort, never()).emit(any());
        }
    }
}
