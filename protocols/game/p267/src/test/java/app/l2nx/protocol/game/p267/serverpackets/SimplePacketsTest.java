/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharCreateFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharDeleteFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.LoginFailReason;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SimplePacketsTest {
    @Nested
    class EmptyPackets {
        @Test
        void write_shouldBeTheirOpcode() {
            assertThat(written(ActionFailed.INSTANCE::write)).isEqualTo("1f");
            assertThat(written(ServerClose.INSTANCE::write)).isEqualTo("20");
            assertThat(written(LeaveWorld.INSTANCE::write)).isEqualTo("84");
            assertThat(written(CharDeleteSuccess.INSTANCE::write)).isEqualTo("1d");
            assertThat(read("", ActionFailed::read)).isSameAs(ActionFailed.INSTANCE);
            assertThat(read("", ServerClose::read)).isSameAs(ServerClose.INSTANCE);
            assertThat(read("", LeaveWorld::read)).isSameAs(LeaveWorld.INSTANCE);
            assertThat(read("", CharDeleteSuccess::read)).isSameAs(CharDeleteSuccess.INSTANCE);
        }
    }

    @Nested
    class CharCreateOkCodec {
        @Test
        void write_shouldWriteItsConstant() {
            assertThat(written(CharCreateOk.INSTANCE::write)).isEqualTo("0f" + d(1));
            assertThat(read(d(1), CharCreateOk::read)).isSameAs(CharCreateOk.INSTANCE);
        }
    }

    @Nested
    class RestartResponseCodec {
        @Test
        void write_shouldWriteTheAnswerAsAnInt() {
            assertThat(written(new RestartResponse(true)::write)).isEqualTo("71" + d(1));
            assertThat(written(new RestartResponse(false)::write)).isEqualTo("71" + d(0));
            assertThat(read(d(1), RestartResponse::read)).isEqualTo(new RestartResponse(true));
        }
    }

    @Nested
    class LoginFailCodec {
        @Test
        void write_shouldWriteTheReasonAsAnInt() {
            assertThat(written(new LoginFail(LoginFailReason.SYSTEM_ERROR_LOGIN_LATER)::write))
                    .isEqualTo("0a" + d(1));
        }

        @ParameterizedTest
        @EnumSource(LoginFailReason.class)
        void read_shouldReadBackWhatWriteWrote(LoginFailReason reason) {
            assertThat(read(written(new LoginFail(reason)::write).substring(2), LoginFail::read))
                    .isEqualTo(new LoginFail(reason));
        }
    }

    @Nested
    class CharCreateFailCodec {
        @Test
        void write_shouldWriteTheReasonAsAnInt() {
            assertThat(written(new CharCreateFail(CharCreateFailReason.REASON_NAME_ALREADY_EXISTS)::write))
                    .isEqualTo("10" + d(2));
        }

        @ParameterizedTest
        @EnumSource(CharCreateFailReason.class)
        void read_shouldReadBackWhatWriteWrote(CharCreateFailReason reason) {
            assertThat(read(written(new CharCreateFail(reason)::write).substring(2), CharCreateFail::read))
                    .isEqualTo(new CharCreateFail(reason));
        }
    }

    @Nested
    class CharDeleteFailCodec {
        @Test
        void write_shouldWriteTheReasonAsAnInt() {
            assertThat(written(new CharDeleteFail(CharDeleteFailReason.REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED)::write))
                    .isEqualTo("1e" + d(3));
        }

        @ParameterizedTest
        @EnumSource(CharDeleteFailReason.class)
        void read_shouldReadBackWhatWriteWrote(CharDeleteFailReason reason) {
            assertThat(read(written(new CharDeleteFail(reason)::write).substring(2), CharDeleteFail::read))
                    .isEqualTo(new CharDeleteFail(reason));
        }
    }

    @Nested
    class ReasonPackets {
        @Test
        void read_shouldFail_whenTheReasonIsUnknown() {
            assertThatThrownBy(() -> read(d(11), LoginFail::read)).isInstanceOf(PacketDecodeException.class);
            assertThatThrownBy(() -> read(d(7), CharCreateFail::read)).isInstanceOf(PacketDecodeException.class);
            assertThatThrownBy(() -> read(d(0), CharDeleteFail::read)).isInstanceOf(PacketDecodeException.class);
        }

        @Test
        void read_shouldFail_whenTheReasonIsMissing() {
            assertThatThrownBy(() -> read("01", LoginFail::read)).isInstanceOf(PacketDecodeException.class);
        }
    }
}
