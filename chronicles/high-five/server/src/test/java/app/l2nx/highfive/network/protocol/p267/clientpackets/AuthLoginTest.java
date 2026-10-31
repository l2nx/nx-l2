/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.login.FakeLoginPort;
import app.l2nx.highfive.login.LoginService;
import app.l2nx.highfive.login.system.OnlineAccounts;
import app.l2nx.highfive.login.system.ServerListing;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.network.codec.PacketWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Locale;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

// Changes the default locale and binds the LoginService static bridge: nothing else may run meanwhile.
@Isolated
class AuthLoginTest {
    private final GameClient client = mock(GameClient.class);

    AuthLoginTest() {
        new LoginService(new FakeLoginPort(), new OnlineAccounts(), new ServerListing());
        when(client.isProtocolOk()).thenReturn(true);
    }

    @Nested
    class Run {
        @Test
        void run_shouldLowerCaseTheLoginAsTheLoginServerDoes_whateverTheDefaultLocale() {
            final Locale defaultLocale = Locale.getDefault();
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            try {
                final AuthLogin packet = new AuthLogin();
                packet.decode(client, frame("ADMIN"));

                packet.run();
            } finally {
                Locale.setDefault(defaultLocale);
            }

            verify(client).setAccountName("admin");
        }
    }

    private static ByteBuf frame(String login) {
        final PacketWriter out = new PacketWriter(Unpooled.buffer());
        out.writeString(login);
        out.writeInt(1);
        out.writeInt(2);
        out.writeInt(3);
        out.writeInt(4);
        return out.buffer();
    }
}
