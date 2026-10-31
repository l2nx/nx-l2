/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessageTest.body;
import static app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessageTest.d;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.protocol.game.p267.serverpackets.reason.CharCreateFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharDeleteFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.LoginFailReason;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FailPacketsTest {
    static Stream<Arguments> packets() {
        return Stream.of(
                Arguments.of(new LoginFail(LoginFailReason.SYSTEM_ERROR_LOGIN_LATER), "0a" + d(1)),
                Arguments.of(new CharCreateFail(CharCreateFailReason.REASON_NAME_ALREADY_EXISTS), "10" + d(2)),
                Arguments.of(
                        new CharDeleteFail(CharDeleteFailReason.REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED), "1e" + d(3)));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("packets")
    void writeImpl_shouldWriteTheReasonCode(ServerPacket packet, String expected) {
        assertThat(body(packet)).isEqualTo(expected);
    }
}
