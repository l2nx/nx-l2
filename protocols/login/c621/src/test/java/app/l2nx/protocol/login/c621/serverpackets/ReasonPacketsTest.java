/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.login.c621.serverpackets;

import static app.l2nx.protocol.login.c621.GoldenBytes.read;
import static app.l2nx.protocol.login.c621.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.login.c621.serverpackets.reason.AccountKickedReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.LoginFailReason;
import app.l2nx.protocol.login.c621.serverpackets.reason.PlayFailReason;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReasonPacketsTest {
    @Nested
    class LoginFailCodec {
        @Test
        void write_shouldWriteTheOpcodeAndTheReasonByte() {
            final String actual = written(new LoginFail(LoginFailReason.REASON_SERVER_MAINTENANCE)::write);

            assertThat(actual).isEqualTo("01" + "10");
        }

        @ParameterizedTest
        @EnumSource(LoginFailReason.class)
        void read_shouldReadBackWhatWriteWrote(LoginFailReason reason) {
            final String body = written(new LoginFail(reason)::write).substring(2);

            assertThat(read(body, LoginFail::read)).isEqualTo(new LoginFail(reason));
        }

        @Test
        void read_shouldFail_whenTheReasonIsUnknown() {
            assertThatThrownBy(() -> read("03", LoginFail::read)).isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void read_shouldFail_whenTheReasonIsMissing() {
            assertThatThrownBy(() -> read("", LoginFail::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class PlayFailCodec {
        @Test
        void write_shouldWriteTheOpcodeAndTheReasonByte() {
            final String actual = written(new PlayFail(PlayFailReason.REASON_SERVER_OVERLOADED)::write);

            assertThat(actual).isEqualTo("06" + "0f");
        }

        @ParameterizedTest
        @EnumSource(PlayFailReason.class)
        void read_shouldReadBackWhatWriteWrote(PlayFailReason reason) {
            final String body = written(new PlayFail(reason)::write).substring(2);

            assertThat(read(body, PlayFail::read)).isEqualTo(new PlayFail(reason));
        }

        @Test
        void read_shouldFail_whenTheReasonIsUnknown() {
            assertThatThrownBy(() -> read("03", PlayFail::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class AccountKickedCodec {
        @Test
        void write_shouldWriteTheOpcodeAndTheReasonWord() {
            final String actual = written(new AccountKicked(AccountKickedReason.REASON_PERMANENTLY_BANNED)::write);

            assertThat(actual).isEqualTo("02" + "20000000");
        }

        @ParameterizedTest
        @EnumSource(AccountKickedReason.class)
        void read_shouldReadBackWhatWriteWrote(AccountKickedReason reason) {
            final String body = written(new AccountKicked(reason)::write).substring(2);

            assertThat(read(body, AccountKicked::read)).isEqualTo(new AccountKicked(reason));
        }

        @Test
        void read_shouldFail_whenTheWordIsShort() {
            assertThatThrownBy(() -> read("200000", AccountKicked::read)).isInstanceOf(PacketDecodeException.class);
        }
    }
}
