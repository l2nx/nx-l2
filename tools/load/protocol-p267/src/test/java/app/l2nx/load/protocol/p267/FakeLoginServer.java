/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import app.l2nx.protocol.login.c621.clientpackets.AuthGameGuard;
import app.l2nx.protocol.login.c621.clientpackets.LoginClientOpcode;
import app.l2nx.protocol.login.c621.clientpackets.RequestAuthLogin;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerList;
import app.l2nx.protocol.login.c621.clientpackets.RequestServerLogin;
import app.l2nx.protocol.login.c621.crypt.Credentials;
import app.l2nx.protocol.login.c621.serverpackets.GGAuth;
import app.l2nx.protocol.login.c621.serverpackets.LoginFail;
import app.l2nx.protocol.login.c621.serverpackets.LoginOk;
import app.l2nx.protocol.login.c621.serverpackets.LoginServerPacket;
import app.l2nx.protocol.login.c621.serverpackets.PlayFail;
import app.l2nx.protocol.login.c621.serverpackets.PlayOk;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.PlayFailReason;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

final class FakeLoginServer implements AutoCloseable {
    private final String account;
    private final String password;
    private final boolean refusePlay;
    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final Channel serverChannel;

    FakeLoginServer(String account, String password, boolean refusePlay) {
        this.account = account;
        this.password = password;
        this.refusePlay = refusePlay;
        serverChannel = new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<>() {
                    @Override
                    protected void initChannel(Channel channel) {
                        LoginServerSide.install(channel.pipeline());
                        channel.pipeline().addLast(new Handler());
                    }
                })
                .bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .syncUninterruptibly()
                .channel();
    }

    InetSocketAddress address() {
        return (InetSocketAddress) serverChannel.localAddress();
    }

    @Override
    public void close() {
        serverChannel.close().syncUninterruptibly();
        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    private final class Handler extends SimpleChannelInboundHandler<ByteBuf> {
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            send(ctx, LoginServerSide.init());
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf payload) throws GeneralSecurityException {
            switch (LoginClientOpcode.of(payload.readUnsignedByte())) {
                case AUTH_GAME_GUARD ->
                    send(ctx, new GGAuth(AuthGameGuard.decode(payload).sessionId()));
                case REQUEST_AUTH_LOGIN -> {
                    final Credentials credentials =
                            Credentials.decrypt(LoginServerSide.RSA.getPrivate(), RequestAuthLogin.decode(payload));
                    if (account.equals(credentials.user()) && password.equals(credentials.password())) {
                        send(
                                ctx,
                                LoginOk.builder()
                                        .loginOk1(LoginServerSide.LOGIN_OK_1)
                                        .loginOk2(LoginServerSide.LOGIN_OK_2)
                                        .build());
                    } else {
                        sendAndClose(ctx, new LoginFail(LoginFailReason.REASON_USER_OR_PASS_WRONG));
                    }
                }
                case REQUEST_SERVER_LIST -> {
                    RequestServerList.decode(payload);
                    send(ctx, LoginServerSide.serverList(LoginServerSide.SERVER_ID));
                }
                case REQUEST_SERVER_LOGIN -> {
                    RequestServerLogin.decode(payload);
                    if (refusePlay) {
                        sendAndClose(ctx, new PlayFail(PlayFailReason.REASON_SERVER_OVERLOADED));
                    } else {
                        send(
                                ctx,
                                PlayOk.builder()
                                        .playOk1(LoginServerSide.PLAY_OK_1)
                                        .playOk2(LoginServerSide.PLAY_OK_2)
                                        .build());
                    }
                }
                case null, default -> ctx.close();
            }
        }

        private static void send(ChannelHandlerContext ctx, LoginServerPacket packet) {
            ctx.writeAndFlush(LoginServerSide.frame(ctx.alloc(), packet::write));
        }

        private static void sendAndClose(ChannelHandlerContext ctx, LoginServerPacket packet) {
            ctx.writeAndFlush(LoginServerSide.frame(ctx.alloc(), packet::write))
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
