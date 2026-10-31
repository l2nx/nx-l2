/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.login.command.Authenticate;
import app.l2nx.highfive.session.SessionKey;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.clientpackets.AuthLogin;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

// Changes the default locale: nothing else may run meanwhile.
@Isolated
class LoginDecodersTest {
    @Test
    void authLogin_shouldLowerCaseTheLoginAndKeepTheKeysOfTheWire() {
        final ByteBuf body = body(authLogin("Alice"));
        try {
            assertThat(LoginDecoders.authLogin(body, null))
                    .isEqualTo(Authenticate.builder()
                            .login("alice")
                            .key(SessionKey.builder()
                                    .loginOk1(3)
                                    .loginOk2(4)
                                    .playOk1(1)
                                    .playOk2(2)
                                    .build())
                            .build());
        } finally {
            body.release();
        }
    }

    @Test
    void authLogin_shouldLowerCaseTheLogin_whenTheDefaultLocaleIsTurkish() {
        final Locale defaultLocale = Locale.getDefault();
        final ByteBuf body = body(authLogin("ADMIN"));
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(LoginDecoders.authLogin(body, null))
                    .isInstanceOfSatisfying(
                            Authenticate.class,
                            authenticate -> assertThat(authenticate.login()).isEqualTo("admin"));
        } finally {
            Locale.setDefault(defaultLocale);
            body.release();
        }
    }

    @Test
    void authLogin_shouldRefuseABlankLogin() {
        final ByteBuf body = body(authLogin("  "));
        try {
            assertThatThrownBy(() -> LoginDecoders.authLogin(body, null)).isInstanceOf(PacketDecodeException.class);
        } finally {
            body.release();
        }
    }

    private static AuthLogin authLogin(String login) {
        return AuthLogin.builder()
                .login(login)
                .playOk2(2)
                .playOk1(1)
                .loginOk1(3)
                .loginOk2(4)
                .build();
    }

    // The route lookup reads the opcode before a decoder runs.
    private static ByteBuf body(AuthLogin packet) {
        final ByteBuf frame = Unpooled.buffer();
        packet.encode(frame);
        frame.skipBytes(1);
        return frame;
    }
}
