/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.pipeline;

import app.l2nx.engine.core.session.Session;
import app.l2nx.engine.core.session.SessionFactory;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.GameClientAdapter;
import app.l2nx.highfive.network.GameConnection;
import app.l2nx.highfive.network.protocol.GameProtocolRegistry;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.channel.guard.ConnectionDeadlines;
import app.l2nx.network.channel.guard.IdlePolicy;
import app.l2nx.network.codec.CipherDecoder;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.crypt.GameFrameCipher;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class GameChannelInitializer implements Consumer<Channel> {
    private final NetworkProperties properties;
    private final GameProtocolRegistry registry;
    private final SessionFactory sessions;
    private final Function<GameConnection, GameClient> clients;
    private final Supplier<byte[]> keys;

    public GameChannelInitializer(
            NetworkProperties properties,
            GameProtocolRegistry registry,
            SessionFactory sessions,
            Function<GameConnection, GameClient> clients,
            Supplier<byte[]> keys) {
        this.properties = properties;
        this.registry = registry;
        this.sessions = sessions;
        this.clients = clients;
        this.keys = keys;
    }

    @Override
    public void accept(Channel channel) {
        final GameFrameCipher cipher = new GameFrameCipher();
        final GameConnection connection = new GameConnection(channel, cipher, properties);
        channel.attr(GameConnection.KEY).set(connection);
        final GameClient client = clients.apply(connection);

        final ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(PipelineNames.FRAME_DECODER, Frames.decoder((int)
                properties.maxFrame().toBytes()));
        pipeline.addLast(PipelineNames.CIPHER_DECODER, new CipherDecoder(cipher));
        pipeline.addLast(PipelineNames.FRAME_ENCODER, new FrameEncoder(cipher));
        IdlePolicy.install(pipeline, properties);
        pipeline.addLast(PipelineNames.OUTBOUND, new OutboundQueue(properties));
        pipeline.addLast(
                PipelineNames.DEADLINES,
                new ConnectionDeadlines(properties.handshakeTimeout(), properties.authTimeout()));
        pipeline.addLast(PipelineNames.HANDSHAKE, new VersionHandshakeDecoder(registry, keys));
        pipeline.addLast(PipelineNames.PACKET, GamePacketDecoder.INSTANCE);
        pipeline.addLast(PipelineNames.SESSION, GameSessionHandler.INSTANCE);
        pipeline.addLast(PipelineNames.TAIL, TailExceptionHandler.INSTANCE);

        // Created last, so a step above that throws leaves no session counted open; the shutdown waits for each one.
        final Session session = sessions.create(connection);
        connection.bind(session, client);
        session.attach(client);
        session.bindPort(new GameClientAdapter(connection));
        session.onClosed(() -> {
            if (client != null) {
                client.onDisconnection();
            }
        });
    }
}
