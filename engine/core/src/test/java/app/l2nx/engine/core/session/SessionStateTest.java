/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionStateTest {
    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
        "CONNECTED,AUTHED,true",
        "CONNECTED,CLOSING,true",
        "CONNECTED,DISCONNECTED,false",
        "AUTHED,CONNECTED,false",
        "AUTHED,CLOSING,true",
        "CLOSING,DISCONNECTED,true",
        "CLOSING,AUTHED,false",
        "DISCONNECTED,CONNECTED,false"
    })
    void canMoveTo_shouldFollowTheSessionLifecycle(SessionState from, SessionState to, boolean expected) {
        assertThat(from.canMoveTo(to)).isEqualTo(expected);
    }
}
