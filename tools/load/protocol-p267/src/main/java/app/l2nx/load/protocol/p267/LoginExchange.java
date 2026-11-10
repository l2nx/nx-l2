/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import app.l2nx.load.bot.intent.IntentKind;
import app.l2nx.load.bot.intent.Login;
import app.l2nx.load.bot.model.LoadBotState;
import app.l2nx.load.protocol.SessionListener;
import app.l2nx.load.protocol.p267.game.GameTicket;
import app.l2nx.logging.RateLimitedLog;
import app.l2nx.network.bootstrap.L2Pipeline;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.channel.PipelineNames;
import app.l2nx.network.codec.Frames;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerList;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import app.l2nx.protocol.login.c621.crypt.Credentials;
import app.l2nx.protocol.login.c621.crypt.LoginFrameCipher;
import app.l2nx.protocol.login.c621.crypt.ScrambledModulus;
import app.l2nx.protocol.login.c621.serverpackets.AccountKicked;
import app.l2nx.protocol.login.c621.serverpackets.GGAuth;
import app.l2nx.protocol.login.c621.serverpackets.Init;
import app.l2nx.protocol.login.c621.serverpackets.LoginFail;
import app.l2nx.protocol.login.c621.serverpackets.LoginOk;
import app.l2nx.protocol.login.c621.serverpackets.LoginServerOpcode;
import app.l2nx.protocol.login.c621.serverpackets.LoginServerPacket;
import app.l2nx.protocol.login.c621.serverpackets.PlayFail;
import app.l2nx.protocol.login.c621.serverpackets.PlayOk;
import app.l2nx.protocol.login.c621.serverpackets.ServerList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * The c621 exchange of one load bot, from {@code Init} to {@code PlayOk}, on its channel's event loop. A listener call
 * may close the session synchronously, so {@link #detached} is checked after every one.
 */
@Slf4j
public final class LoginExchange extends ChannelInboundHandlerAdapter {
    private static final RateLimitedLog RATE = RateLimitedLog.everyTenSeconds();

    private static final int PAYLOAD_HINT = 1 + RequestAuthLogin.BLOCK;

    private final LoginFrameCipher cipher = LoginFrameCipher.client();
    private final int serverId;
    private final SessionListener listener;
    private final Consumer<GameTicket> onPlayOk;
    private Channel channel;
    private Phase phase = Phase.AWAIT_INIT;
    private int sessionId;
    private PublicKey credentialsKey;
    private int loginOk1;
    private int loginOk2;
    private String account;
    private InetSocketAddress gameServer;
    private boolean detached;

    /**
     * @param onPlayOk takes over once the login server let the load bot in; the login connection is closed by then
     */
    public LoginExchange(int serverId, SessionListener listener, Consumer<GameTicket> onPlayOk) {
        this.serverId = serverId;
        this.listener = listener;
        this.onPlayOk = onPlayOk;
    }

    public void install(Channel channel, int maxFrame) {
        this.channel = channel;
        L2Pipeline.installCodec(channel.pipeline(), cipher, maxFrame, Connections.metrics(channel));
        channel.pipeline().addLast(PipelineNames.SESSION, this);
    }

    public void login(Login intent) {
        if (detached) {
            return;
        }
        if (phase != Phase.AUTHENTICATED) {
            fail(cannotSend(IntentKind.LOGIN));
            return;
        }
        final RequestAuthLogin packet;
        try {
            // The High Five client sends the one-block layout; its login fits 14 bytes of UTF-8.
            packet = Credentials.builder()
                    .user(intent.account())
                    .password(intent.password())
                    .build()
                    .encrypt(credentialsKey, Credentials.Layout.ONE_BLOCK);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            fail(e);
            return;
        }
        account = intent.account();
        phase = Phase.AWAIT_LOGIN_OK;
        write(packet::encode);
    }

    public void selectServer() {
        if (detached) {
            return;
        }
        if (phase != Phase.SERVER_LIST) {
            fail(cannotSend(IntentKind.SELECT_SERVER));
            return;
        }
        phase = Phase.AWAIT_PLAY_OK;
        write(RequestServerLogin.builder()
                .loginOk1(loginOk1)
                .loginOk2(loginOk2)
                .serverId(serverId)
                .build()::encode);
    }

    /** The connect did not succeed; ignored once the session is closed, whose close fails a pending connect. */
    public void connectFailed(Throwable cause) {
        fail(cause);
    }

    /** Idempotent. */
    public void close() {
        detach();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (!detached) {
            listener.stateChanged(LoadBotState.LOGIN_CONNECT);
        }
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (!detached) {
            detached = true;
            phase = Phase.DONE;
            listener.stateChanged(LoadBotState.STOPPED);
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf frame)) {
            ctx.fireChannelRead(msg);
            return;
        }
        try {
            if (!detached) {
                read(frame);
            }
        } finally {
            frame.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        fail(cause);
    }

    private void read(ByteBuf frame) {
        final int start = frame.readerIndex();
        final LoginServerPacket packet;
        try {
            packet = decode(frame);
        } catch (PacketDecodeException e) {
            log.warn(
                    "A login server frame from {} did not decode ({}): {}",
                    channel.remoteAddress(),
                    e.getMessage(),
                    ByteBufUtil.hexDump(frame, start, frame.writerIndex() - start));
            fail(e);
            return;
        }
        handle(packet);
    }

    private static LoginServerPacket decode(ByteBuf frame) {
        if (!frame.isReadable()) {
            throw new PacketDecodeException("A login server frame without an opcode");
        }
        final int code = frame.readUnsignedByte();
        final LoginServerOpcode opcode = LoginServerOpcode.of(code);
        if (opcode == null) {
            throw new PacketDecodeException("Unknown login server opcode 0x" + Integer.toHexString(code));
        }
        return switch (opcode) {
            case INIT -> Init.read(frame);
            case LOGIN_FAIL -> LoginFail.read(frame);
            case ACCOUNT_KICKED -> AccountKicked.read(frame);
            case LOGIN_OK -> LoginOk.read(frame);
            case SERVER_LIST -> ServerList.read(frame);
            case PLAY_FAIL -> PlayFail.read(frame);
            case PLAY_OK -> PlayOk.read(frame);
            case GG_AUTH -> GGAuth.read(frame);
        };
    }

    private void handle(LoginServerPacket packet) {
        switch (packet) {
            case Init init when phase == Phase.AWAIT_INIT -> init(init);
            case GGAuth ggAuth when phase == Phase.AWAIT_GG_AUTH -> ggAuth(ggAuth);
            case LoginOk loginOk when phase == Phase.AWAIT_LOGIN_OK -> loginOk(loginOk);
            // The login server skips LoginOk when it shows no licence: the server list confirms the login.
            case ServerList serverList
            when phase == Phase.AWAIT_LOGIN_OK -> {
                phase = Phase.AWAIT_SERVER_LIST;
                listener.acknowledged(IntentKind.LOGIN);
                if (!detached) {
                    serverList(serverList);
                }
            }
            case ServerList serverList when phase == Phase.AWAIT_SERVER_LIST -> serverList(serverList);
            case LoginFail _, AccountKicked _ when phase == Phase.AWAIT_LOGIN_OK -> reject(IntentKind.LOGIN, packet);
            case PlayOk playOk when phase == Phase.AWAIT_PLAY_OK -> playOk(playOk);
            // A pair the login server does not accept, or a server it lists down, is answered with LoginFail.
            case PlayFail _, LoginFail _, AccountKicked _
            when phase == Phase.AWAIT_PLAY_OK -> reject(IntentKind.SELECT_SERVER, packet);
            default ->
                fail(new IllegalStateException("Unexpected " + packet + " from the login server in phase " + phase));
        }
    }

    private void init(Init init) {
        try {
            credentialsKey = ScrambledModulus.publicKey(init.scrambledModulus());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            fail(e);
            return;
        }
        cipher.sessionKey(init.blowfishKey());
        sessionId = init.sessionId();
        phase = Phase.AWAIT_GG_AUTH;
        write(new AuthGameGuard(sessionId)::encode);
    }

    private void ggAuth(GGAuth ggAuth) {
        if (ggAuth.response() != sessionId) {
            fail(new IllegalStateException(
                    "GGAuth answered session " + ggAuth.response() + ", Init gave " + sessionId));
            return;
        }
        phase = Phase.AUTHENTICATED;
        listener.stateChanged(LoadBotState.LOGIN_AUTH);
    }

    private void loginOk(LoginOk loginOk) {
        loginOk1 = loginOk.loginOk1();
        loginOk2 = loginOk.loginOk2();
        phase = Phase.AWAIT_SERVER_LIST;
        listener.acknowledged(IntentKind.LOGIN);
        if (detached) {
            return;
        }
        write(RequestServerList.builder().loginOk1(loginOk1).loginOk2(loginOk2).build()::encode);
    }

    private void serverList(ServerList serverList) {
        final ServerList.Server server = serverList.servers().stream()
                .filter(listed -> listed.id() == serverId)
                .findFirst()
                .orElse(null);
        if (server == null) {
            fail(new IllegalStateException("Game server " + serverId + " is not in the server list "
                    + serverList.servers().stream().map(ServerList.Server::id).toList()));
            return;
        }
        try {
            gameServer = new InetSocketAddress(InetAddress.getByAddress(server.ip()), server.port());
        } catch (UnknownHostException e) {
            fail(e);
            return;
        }
        phase = Phase.SERVER_LIST;
        listener.stateChanged(LoadBotState.SERVER_LIST);
    }

    private void playOk(PlayOk playOk) {
        phase = Phase.DONE;
        listener.acknowledged(IntentKind.SELECT_SERVER);
        if (detached) {
            return;
        }
        detach();
        onPlayOk.accept(GameTicket.builder()
                .account(account)
                .gameServer(gameServer)
                .loginOk1(loginOk1)
                .loginOk2(loginOk2)
                .playOk1(playOk.playOk1())
                .playOk2(playOk.playOk2())
                .build());
    }

    /** The login server answers a refusal and closes, so the load bot stops as on a dropped connection. */
    private void reject(IntentKind kind, LoginServerPacket refusal) {
        if (RATE.tryAcquire(refusal.toString())) {
            log.warn("The login server refused {} with {}", kind, refusal);
        }
        phase = Phase.DONE;
        listener.rejected(kind);
        if (!detached) {
            channel.close();
        }
    }

    private void fail(Throwable cause) {
        if (detached) {
            return;
        }
        detach();
        listener.failed(cause);
    }

    private void detach() {
        detached = true;
        phase = Phase.DONE;
        if (channel != null) {
            channel.close();
        }
    }

    private IllegalStateException cannotSend(IntentKind kind) {
        return new IllegalStateException("Protocol p267 cannot send " + kind + " in login phase " + phase);
    }

    private void write(Consumer<ByteBuf> packet) {
        final ByteBuf frame = Frames.allocate(channel.alloc(), PAYLOAD_HINT);
        packet.accept(frame);
        channel.writeAndFlush(frame, channel.voidPromise());
    }

    private enum Phase {
        AWAIT_INIT,
        AWAIT_GG_AUTH,
        AUTHENTICATED,
        AWAIT_LOGIN_OK,
        AWAIT_SERVER_LIST,
        SERVER_LIST,
        AWAIT_PLAY_OK,
        DONE
    }
}
