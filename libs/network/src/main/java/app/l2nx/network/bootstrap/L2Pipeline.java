/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.network.bootstrap;

import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.channel.guard.ConnectionDeadlines;
import app.l2nx.network.channel.guard.IdlePolicy;
import app.l2nx.network.codec.CipherDecoder;
import app.l2nx.network.codec.FrameCipher;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import io.netty.channel.ChannelPipeline;

/**
 * Order is the invariant: idle detection sees only frames that decrypted, and the outbound queue sits above the frame
 * encoder, so every queued packet is framed and encrypted.
 */
public final class L2Pipeline {
    private L2Pipeline() {}

    public static void install(ChannelPipeline pipeline, FrameCipher cipher, NetworkProperties properties) {
        installCodec(pipeline, cipher, (int) properties.maxFrame().toBytes());
        IdlePolicy.install(pipeline, properties);
        pipeline.addLast(PipelineNames.OUTBOUND, new OutboundQueue(properties));
        pipeline.addLast(
                PipelineNames.DEADLINES,
                new ConnectionDeadlines(properties.handshakeTimeout(), properties.authTimeout()));
    }

    public static void installCodec(ChannelPipeline pipeline, FrameCipher cipher, int maxFrame) {
        pipeline.addLast(PipelineNames.FRAME_DECODER, Frames.decoder(maxFrame));
        pipeline.addLast(PipelineNames.CIPHER_DECODER, new CipherDecoder(cipher));
        pipeline.addLast(PipelineNames.FRAME_ENCODER, new FrameEncoder(cipher));
    }
}
