/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.model.LoadBotIdentity;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.protocol.ClientProtocol;
import app.l2nx.load.protocol.ClientSession;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.network.config.NetworkProperties;
import app.l2nx.network.config.Transport;
import app.l2nx.network.transport.ResolvedTransport;
import app.l2nx.network.transport.TransportResolver;
import app.l2nx.protocol.login.c621.crypt.Credentials;
import io.netty.channel.EventLoop;
import io.netty.channel.IoHandlerFactory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** The High Five client: login protocol c621, then game protocol p267. */
public final class P267Protocol implements ClientProtocol {
    public static final String ID = "p267";

    private static final Set<IntentKind> CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(IntentKind.LOGIN, IntentKind.SELECT_SERVER));

    // TODO: the game part takes the lifecycle on to IN_GAME.
    static final LoadBotState LAST_STATE = LoadBotState.GAME_CONNECT;

    private final P267Settings settings;
    private final ResolvedTransport transport;
    private final NetworkProperties network;

    public P267Protocol(P267Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        transport = TransportResolver.nativeAvailability().resolve(Transport.AUTO);
        network = NetworkProperties.defaults();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<IntentKind> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public LoadBotState lastState() {
        return LAST_STATE;
    }

    @Override
    public IoHandlerFactory ioHandlerFactory() {
        return transport.ioHandlerFactory();
    }

    /**
     * @throws IllegalArgumentException when the account or the password is blank or does not fit the one-block
     *     credentials layout the High Five client sends; the message never holds the password
     */
    @Override
    public void checkIdentity(LoadBotIdentity identity) {
        final Credentials credentials = Credentials.builder()
                .user(identity.account())
                .password(identity.password())
                .build();
        if (!credentials.fits(Credentials.Layout.ONE_BLOCK)) {
            throw new IllegalArgumentException("The account " + identity.account()
                    + " or its password does not fit the one-block credentials layout of the High Five client");
        }
    }

    /** {@code loop} must come from a group built on {@link #ioHandlerFactory()}. */
    @Override
    public ClientSession open(LoadBotIdentity identity, EventLoop loop, SessionListener listener) {
        final P267Session session = new P267Session(settings, transport, network, loop, listener);
        session.start();
        return session;
    }
}
