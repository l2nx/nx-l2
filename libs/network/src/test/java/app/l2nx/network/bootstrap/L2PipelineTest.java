/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.metrics.NetworkMetrics;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class L2PipelineTest {
    @Nested
    class Install {
        @Test
        void install_shouldAddTheSharedHandlersInTheirOrder() {
            final EmbeddedChannel channel = new EmbeddedChannel();

            final OutboundQueue actual = L2Pipeline.install(
                    channel.pipeline(), FrameCipher.NONE, NetworkProperties.defaults(), NetworkMetrics.noop());

            assertThat(channel.pipeline().toMap().keySet())
                    .containsExactly(
                            PipelineNames.FRAME_DECODER,
                            PipelineNames.CIPHER_DECODER,
                            PipelineNames.FRAME_ENCODER,
                            PipelineNames.IDLE,
                            PipelineNames.IDLE_POLICY,
                            PipelineNames.OUTBOUND,
                            PipelineNames.DEADLINES);
            assertThat(actual).isSameAs(channel.pipeline().get(PipelineNames.OUTBOUND));
            channel.finishAndReleaseAll();
        }
    }
}
