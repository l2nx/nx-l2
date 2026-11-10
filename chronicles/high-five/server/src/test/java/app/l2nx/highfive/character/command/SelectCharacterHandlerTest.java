/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.character.command;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionState;
import app.l2nx.highfive.character.CharacterLoader;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.data.xml.SecondaryAuthData;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.util.FloodProtectors;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class SelectCharacterHandlerTest {
    private static final int SLOT = 0;

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final CharacterLoader loader = mock(CharacterLoader.class);
    private final SelectCharacterHandler handler = new SelectCharacterHandler(registry, loader);
    private final GameClient client = mock(GameClient.class);
    private final Session session = mock(Session.class);
    private final UUID characterId = EntityIds.next();
    private final SecondaryAuthData secondaryAuth = mock(SecondaryAuthData.class);
    private MockedStatic<SecondaryAuthData> secondaryAuths;

    @BeforeEach
    void setUp() {
        final FloodProtectors flood = mock(FloodProtectors.class);
        when(flood.canSelectCharacter()).thenReturn(true);
        when(client.getFloodProtectors()).thenReturn(flood);
        when(client.session()).thenReturn(session);
        final CharacterSummary summary = mock(CharacterSummary.class);
        when(summary.objectId()).thenReturn(registry.handleOf(characterId));
        when(client.getCharSelection(SLOT)).thenReturn(summary);
        // A closing session ends the selection right after the wait, before any world state is touched.
        when(client.getConnectionState()).thenReturn(SessionState.CLOSING);
        secondaryAuths = mockStatic(SecondaryAuthData.class);
        secondaryAuths.when(SecondaryAuthData::getInstance).thenReturn(secondaryAuth);
    }

    @AfterEach
    void tearDown() {
        secondaryAuths.close();
    }

    @Nested
    class Handle {
        @Test
        void handle_shouldSelectAtOnce_whenNothingIsInFlight() {
            WorldLoopExtension.runInPass(() -> handler.handle(client, new SelectCharacter(SLOT)));

            verify(client).getConnectionState();
        }

        @Test
        void handle_shouldWaitForTheValueTransactionInFlight_beforeLoadingTheCharacter() {
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.inFlightCharacters().begin(characterId);
                handler.handle(client, new SelectCharacter(SLOT));
            });
            verify(client, never()).getConnectionState();

            verify(session).hold();
            verify(session, never()).release();

            WorldLoopExtension.runInPass(
                    () -> WorldPartition.inFlightCharacters().end(characterId));

            verify(client).getConnectionState();
            verify(client, never()).getPlayerLock();
            verify(session).release();
        }

        @Test
        void handle_shouldHoldTheSessionAndReleaseIt_whenTheSelectionStartsNoLoad() {
            WorldLoopExtension.runInPass(() -> handler.handle(client, new SelectCharacter(SLOT)));

            final InOrder order = inOrder(session);
            order.verify(session).hold();
            order.verify(session).release();
        }

        @Test
        void handle_shouldWaitForAnotherLoadOfTheCharacter_beforeSelecting() {
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.loadedCharacters().startLoading(characterId);
                handler.handle(client, new SelectCharacter(SLOT));
            });
            verify(client, never()).getConnectionState();

            WorldLoopExtension.runInPass(() -> WorldPartition.loadedCharacters().loadEnded(characterId));

            verify(client).getConnectionState();
            verify(session).release();
        }
    }
}
