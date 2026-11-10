/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.protocol.p267;

import app.l2nx.network.bootstrap.L2Pipeline;
import app.l2nx.network.channel.Connections;
import app.l2nx.network.codec.Frames;
import app.l2nx.protocol.login.c621.crypt.LoginFrameCipher;
import app.l2nx.protocol.login.c621.crypt.ScrambledModulus;
import app.l2nx.protocol.login.c621.serverpackets.Init;
import app.l2nx.protocol.login.c621.serverpackets.ServerList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelPipeline;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class LoginServerSide {
    public static final int MAX_FRAME = 16 * 1024;
    public static final int SESSION_ID = 0x1A2B3C4D;
    public static final int LOGIN_OK_1 = 0x11223344;
    public static final int LOGIN_OK_2 = 0x55667788;
    public static final int PLAY_OK_1 = 0x0A0B0C0D;
    public static final int PLAY_OK_2 = 0x01020304;
    public static final int SERVER_ID = 2;
    public static final KeyPair RSA = generateRsa();

    private static final byte[] BLOWFISH_KEY = {
        0x21, 0x32, 0x43, 0x54, 0x65, 0x76, 0x07, 0x18, 0x29, 0x3A, 0x4B, 0x5C, 0x6D, 0x7E, 0x0F, 0x10
    };
    private static final int XOR_KEY = 0x5EED5EED;

    private LoginServerSide() {}

    public static Init init() {
        return Init.builder()
                .sessionId(SESSION_ID)
                .scrambledModulus(ScrambledModulus.scramble(((RSAPublicKey) RSA.getPublic()).getModulus()))
                .blowfishKey(BLOWFISH_KEY)
                .build();
    }

    public static ServerList serverList(int... ids) {
        return serverListOn(7777, ids);
    }

    public static ServerList serverListOn(int gamePort, int... ids) {
        return ServerList.builder()
                .servers(Arrays.stream(ids)
                        .mapToObj(id -> ServerList.Server.builder()
                                .id(id)
                                .ip(new byte[] {127, 0, 0, 1})
                                .port(gamePort)
                                .maxPlayers(100)
                                .up(true)
                                .serverType(1)
                                .build())
                        .toList())
                .characters(List.of())
                .build();
    }

    public static void install(ChannelPipeline pipeline) {
        final LoginFrameCipher cipher = LoginFrameCipher.server(BLOWFISH_KEY, () -> XOR_KEY);
        L2Pipeline.installCodec(pipeline, cipher, MAX_FRAME, Connections.metrics(pipeline.channel()));
    }

    public static ByteBuf frame(ByteBufAllocator alloc, Consumer<ByteBuf> writer) {
        final ByteBuf frame = Frames.allocate(alloc, 64);
        writer.accept(frame);
        return frame;
    }

    private static KeyPair generateRsa() {
        try {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
