/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.login.protocol.c621.clientpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoginClientPacketsTest {
    @Nested
    class NewPacket {
        @ParameterizedTest
        @EnumSource(
                value = LoginClientPackets.class,
                names = {"REQUEST_PI_AGREEMENT_CHECK", "REQUEST_PI_AGREEMENT"})
        void newPacket_shouldRefuse_whenTheOpcodeIsIgnored(LoginClientPackets entry) {
            assertThat(entry.isIgnored()).isTrue();
            assertThatIllegalStateException().isThrownBy(entry::newPacket);
        }

        @ParameterizedTest
        @EnumSource(
                value = LoginClientPackets.class,
                names = {"REQUEST_PI_AGREEMENT_CHECK", "REQUEST_PI_AGREEMENT"},
                mode = EnumSource.Mode.EXCLUDE)
        void newPacket_shouldCreateAFreshPacket_whenTheOpcodeHasAHandler(LoginClientPackets entry) {
            assertThat(entry.isIgnored()).isFalse();
            assertThat(entry.newPacket()).isNotNull().isNotSameAs(entry.newPacket());
        }
    }
}
