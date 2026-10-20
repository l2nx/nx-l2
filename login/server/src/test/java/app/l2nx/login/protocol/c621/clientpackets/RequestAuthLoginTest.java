/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621.clientpackets;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.login.enums.LoginFailReason;
import app.l2nx.login.network.LoginClient;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestAuthLoginTest {
    @Nested
    class Run {
        @ParameterizedTest(name = "block of {0} bytes")
        @ValueSource(ints = {128, 256})
        void run_shouldCloseWithAccessFailed_whenTheBlockCannotBeDecrypted(int length) throws Exception {
            final LoginClient client = clientWithKey();
            final byte[] larger = new byte[length];
            Arrays.fill(larger, (byte) 0xFF);
            final RequestAuthLogin packet = new RequestAuthLogin();
            final ByteBuf frame = Unpooled.wrappedBuffer(larger);
            try {
                packet.decode(client, frame);
            } finally {
                frame.release();
            }

            packet.run();

            verify(client).close(LoginFailReason.REASON_ACCESS_FAILED);
        }

        private LoginClient clientWithKey() throws Exception {
            final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(1024, RSAKeyGenParameterSpec.F4));
            final LoginClient client = mock(LoginClient.class);
            when(client.getRSAPrivateKey())
                    .thenReturn((RSAPrivateKey) generator.generateKeyPair().getPrivate());
            when(client.getIp()).thenReturn("203.0.113.7");
            return client;
        }
    }
}
