/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.GameFrameCipher;
import app.l2nx.network.test.TestChannels;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

// FloodProtectors of a new client read the game clock.
@ExtendWith(ClockBridgeExtension.class)
@ResourceLock(ClockBridgeExtension.LOCK)
class GameClientTest {
    private final EmbeddedChannel channel = TestChannels.embedded();
    private final GameClient client;

    GameClientTest() {
        channel.pipeline().addLast(PipelineNames.OUTBOUND, new OutboundQueue(NetworkProperties.defaults()));
        client = new GameClient(new GameConnection(channel, new GameFrameCipher(), NetworkProperties.defaults()));
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
}
