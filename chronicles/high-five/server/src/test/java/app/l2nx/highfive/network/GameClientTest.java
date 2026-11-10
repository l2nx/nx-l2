/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.managers.PlayerPersistenceManager;
import app.l2nx.highfive.partition.OfflineRows;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import app.l2nx.network.test.TestChannels;
import app.l2nx.protocol.game.p267.crypt.GameFrameCipher;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;

// FloodProtectors of a new client read the game clock.
@ExtendWith(ClockBridgeExtension.class)
@ResourceLock(ClockBridgeExtension.LOCK)
class GameClientTest {
    private static final int OBJECT_ID = 7;

    private final EmbeddedChannel channel = TestChannels.embedded();
    private final GameClient client;

    GameClientTest() {
        final NetworkMetrics metrics = Connections.metrics(channel);
        final OutboundQueue outbound = new OutboundQueue(NetworkProperties.defaults(), metrics);
        channel.pipeline().addLast(PipelineNames.OUTBOUND, outbound);
        client = new GameClient(
                new GameConnection(channel, new GameFrameCipher(), outbound, metrics, NetworkProperties.defaults()));
    }

    @AfterEach
    void tearDown() {
        channel.finishAndReleaseAll();
    }

    @Nested
    class CloseNow {
        @Test
        void closeNow_shouldKeepTheLastFrame_whenACloseOnWriteIsPending() {
            OutboundQueue.of(channel).sendAndClose(TestChannels.frame(0x20));

            client.closeNow();
            channel.runPendingTasks();

            final ByteBuf actual = channel.readOutbound();
            assertThat(actual).as("the close-on-write frame").isNotNull();
            actual.release();
            assertThat(channel.isOpen()).isFalse();
        }

        @Test
        void closeNow_shouldClose_whenNothingIsPending() {
            client.closeNow();
            channel.runPendingTasks();

            assertThat(channel.isOpen()).isFalse();
        }
    }

    @Nested
    class MarkToDeleteChar {
        @Test
        void markToDeleteChar_shouldAnswerLoadedAndKeepTheRow_whenTheCharacterIsLoaded() {
            client.setCharSelection(List.of(CharacterSummary.builder()
                    .objectId(OBJECT_ID)
                    .paperdoll(List.of())
                    .build()));

            try (MockedStatic<OfflineRows> rows = mockStatic(OfflineRows.class);
                    MockedStatic<PlayerPersistenceManager> persistence = mockStatic(PlayerPersistenceManager.class)) {
                rows.when(() -> OfflineRows.isLoaded(OBJECT_ID)).thenReturn(true);

                assertThat(client.markToDeleteChar(0)).isEqualTo((byte) 3);
                persistence.verifyNoInteractions();
            }
        }
    }

    @Nested
    class Restore {
        @Test
        void restore_shouldKeepTheDeletionTime_whenTheCharacterIsLoaded() {
            client.setCharSelection(List.of(CharacterSummary.builder()
                    .objectId(OBJECT_ID)
                    .paperdoll(List.of())
                    .build()));

            try (MockedStatic<OfflineRows> rows = mockStatic(OfflineRows.class);
                    MockedStatic<PlayerPersistenceManager> persistence = mockStatic(PlayerPersistenceManager.class)) {
                rows.when(() -> OfflineRows.isLoaded(OBJECT_ID)).thenReturn(true);

                client.restore(0);

                persistence.verifyNoInteractions();
            }
        }
    }
}
