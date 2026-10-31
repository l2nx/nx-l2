/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ClientPort;
import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.character.CharacterService;
import app.l2nx.highfive.character.view.RestartAnswered;
import app.l2nx.highfive.data.sql.OfflineTraderTable;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.session.GameContexts;
import app.l2nx.highfive.taskmanagers.AttackStanceTaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

class RestartHandlerTest {
    private final CharacterService characters = mock(CharacterService.class);
    private final RestartHandler handler = new RestartHandler(characters);
    private final GameClient client = mock(GameClient.class);
    private final Player player = mock(Player.class);
    private final ClientPort playerPort = mock(ClientPort.class);
    private final ClientPort clientPort = mock(ClientPort.class);
    private final AttackStanceTaskManager stances = mock(AttackStanceTaskManager.class);

    @BeforeEach
    void setUp() {
        when(player.getClient()).thenReturn(client);
        when(player.getPort()).thenReturn(playerPort);
        when(client.port()).thenReturn(clientPort);
        when(player.getActiveEnchantItemId()).thenReturn(Player.ID_NONE);
        when(player.getActiveEnchantAttrItemId()).thenReturn(Player.ID_NONE);
    }

    @Nested
    class Context {
        @Test
        void context_shouldBeACharacterInTheWorld() {
            assertThat(handler.context()).isSameAs(GameContexts.WORLD);
        }
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldRefuseWithAMessage_whenThePlayerIsInCombat() {
            when(stances.hasAttackStanceTask(player)).thenReturn(true);

            try (MockedStatic<AttackStanceTaskManager> stance = mockStatic(AttackStanceTaskManager.class)) {
                stance.when(AttackStanceTaskManager::getInstance).thenReturn(stances);

                handler.handle(player, Restart.INSTANCE);
            }

            final InOrder order = inOrder(playerPort);
            order.verify(playerPort).emit(MessageShown.of(MessageKey.CANNOT_RESTART_IN_COMBAT));
            order.verify(playerPort).emit(RestartAnswered.REFUSED);
            order.verify(playerPort).emit(ActionCancelled.INSTANCE);
            verifyNoInteractions(characters);
        }

        @Test
        void handle_shouldRefuseWithoutCancellingTheAction_whenThePlayerTrades() {
            when(player.isInStoreMode()).thenReturn(true);

            handler.handle(player, Restart.INSTANCE);

            verify(playerPort).emit(RestartAnswered.REFUSED);
            verify(playerPort, never()).emit(ActionCancelled.INSTANCE);
            verifyNoInteractions(characters);
        }

        @Test
        void handle_shouldAnswerAndShowTheList_whenThePlayerMayLeave() {
            when(player.canLogout()).thenReturn(true);
            final OfflineTraderTable traders = mock(OfflineTraderTable.class);
            final OlympiadManager olympiad = mock(OlympiadManager.class);
            final Disconnection disconnection = mock(Disconnection.class);

            try (MockedStatic<AttackStanceTaskManager> stance = mockStatic(AttackStanceTaskManager.class);
                    MockedStatic<OfflineTraderTable> offline = mockStatic(OfflineTraderTable.class);
                    MockedStatic<OlympiadManager> olympiads = mockStatic(OlympiadManager.class);
                    MockedStatic<Disconnection> disconnections = mockStatic(Disconnection.class)) {
                stance.when(AttackStanceTaskManager::getInstance).thenReturn(stances);
                offline.when(OfflineTraderTable::getInstance).thenReturn(traders);
                olympiads.when(OlympiadManager::getInstance).thenReturn(olympiad);
                disconnections.when(() -> Disconnection.of(client, player)).thenReturn(disconnection);

                handler.handle(player, Restart.INSTANCE);
            }

            final InOrder order = inOrder(disconnection, clientPort, characters);
            order.verify(disconnection).storeAndDelete();
            order.verify(clientPort).emit(RestartAnswered.ACCEPTED);
            order.verify(characters).showCharacterList(client);
            verify(playerPort, never()).emit(any());
        }

        @Test
        void handle_shouldStopAfterTheOfflineHandOver_whenThePlayerEntersOfflineMode() {
            when(player.canLogout()).thenReturn(true);
            final OfflineTraderTable traders = mock(OfflineTraderTable.class);
            when(traders.enteredOfflineMode(player)).thenReturn(true);
            final OlympiadManager olympiad = mock(OlympiadManager.class);

            try (MockedStatic<AttackStanceTaskManager> stance = mockStatic(AttackStanceTaskManager.class);
                    MockedStatic<OfflineTraderTable> offline = mockStatic(OfflineTraderTable.class);
                    MockedStatic<OlympiadManager> olympiads = mockStatic(OlympiadManager.class)) {
                stance.when(AttackStanceTaskManager::getInstance).thenReturn(stances);
                offline.when(OfflineTraderTable::getInstance).thenReturn(traders);
                olympiads.when(OlympiadManager::getInstance).thenReturn(olympiad);

                handler.handle(player, Restart.INSTANCE);
            }

            verifyNoInteractions(clientPort, characters);
        }
    }
}
