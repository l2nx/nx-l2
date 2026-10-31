/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.network;

import app.l2nx.login.protocol.LoginVariant;
import app.l2nx.login.protocol.c621.LoginHandshakeHandler;
import app.l2nx.login.protocol.c621.LoginPacketDecoder;
import app.l2nx.login.protocol.c621.LoginRoutes;
import app.l2nx.login.protocol.c621.ServerListMapper;
import app.l2nx.network.channel.OutboundQueue;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.channel.TailExceptionHandler;
import app.l2nx.network.channel.guard.ConnectionDeadlines;
import app.l2nx.network.channel.guard.IdlePolicy;
import app.l2nx.network.codec.CipherDecoder;
import app.l2nx.network.codec.FrameEncoder;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.exec.BlockingTaskExecutor;
import app.l2nx.protocol.login.c621.crypt.LoginFrameCipher;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

public final class LoginChannelInitializer implements Consumer<Channel> {
    // Login sends a handful of packets per session; more waiting at once is a flood.
    private static final int LANE_LIMIT = 16;

    private final LoginVariant variant;
    private final NetworkProperties properties;
    private final BlockingTaskExecutor tasks;
    private final Supplier<LoginKeys> keys;
    private final RandomGenerator random;
    private final Consumer<LoginClient> releaseAccount;
    private final LoginRoutes routes;
    private final ServerListMapper serverLists;

    /**
     * @param keys called once per connection
     * @param releaseAccount receives each closed connection that did not move on to a game server; its account is
     *     {@code null} when it never authenticated
     */
    public LoginChannelInitializer(
            LoginVariant variant,
            NetworkProperties properties,
            BlockingTaskExecutor tasks,
            Supplier<LoginKeys> keys,
            RandomGenerator random,
            Consumer<LoginClient> releaseAccount,
            LoginRoutes routes,
            ServerListMapper serverLists) {
        this.variant = variant;
        this.properties = properties;
        this.tasks = tasks;
        this.keys = keys;
        this.random = random;
        this.releaseAccount = releaseAccount;
        this.routes = routes;
        this.serverLists = serverLists;
    }

    @Override
    public void accept(Channel channel) {
        final LoginClient client = new LoginClient(
                channel,
                variant,
                keys.get(),
                random.nextInt(),
                // A packet the lane rejects would leave its connection without an answer.
                tasks.newLane(LANE_LIMIT, task -> channel.close()),
                serverLists);
        channel.attr(LoginClient.KEY).set(client);
        channel.closeFuture().addListener(future -> {
            // A client that moved on to a game server keeps its session key for the game server's check.
            if (!client.hasJoinedGS()) {
                releaseAccount.accept(client);
            }
        });

        switch (variant) {
            case C621 -> installC621(channel.pipeline(), client);
        }
    }

    private void installC621(ChannelPipeline pipeline, LoginClient client) {
        final LoginFrameCipher cipher = LoginFrameCipher.server(client.getBlowfishKey(), random::nextInt);
        pipeline.addLast(PipelineNames.FRAME_DECODER, Frames.decoder((int)
                properties.maxFrame().toBytes()));
        pipeline.addLast(PipelineNames.CIPHER_DECODER, new CipherDecoder(cipher));
        pipeline.addLast(PipelineNames.FRAME_ENCODER, new FrameEncoder(cipher));
        IdlePolicy.install(pipeline, properties);
        pipeline.addLast(PipelineNames.OUTBOUND, new OutboundQueue(properties));
        pipeline.addLast(
                PipelineNames.DEADLINES,
                new ConnectionDeadlines(properties.handshakeTimeout(), properties.authTimeout()));
        pipeline.addLast(PipelineNames.HANDSHAKE, new LoginHandshakeHandler());
        pipeline.addLast(PipelineNames.PACKET, new LoginPacketDecoder(routes));
        pipeline.addLast(PipelineNames.TAIL, TailExceptionHandler.INSTANCE);
    }
}
