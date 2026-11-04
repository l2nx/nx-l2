/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.c;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CombatPacketsTest {
    @Nested
    class ActionCodec {
        @Test
        void decode_shouldRoundTripTargetOriginAndAction() {
            final String body = d(0x10000001) + d(-71338) + d(258271) + d(-3104) + c(1);
            final Action packet = Action.builder()
                    .objectId(0x10000001)
                    .originX(-71338)
                    .originY(258271)
                    .originZ(-3104)
                    .actionId(1)
                    .build();

            assertThat(read(body, Action::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("1f" + body);
        }

        @Test
        void decode_shouldFail_whenTheActionIsMissing() {
            assertThatThrownBy(() -> read(d(1) + d(2) + d(3) + d(4), Action::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class AttackRequestCodec {
        @Test
        void decode_shouldRoundTripTargetOriginAndClick() {
            final String body = d(0x10000001) + d(-71338) + d(258271) + d(-3104) + c(0);
            final AttackRequest packet = AttackRequest.builder()
                    .objectId(0x10000001)
                    .originX(-71338)
                    .originY(258271)
                    .originZ(-3104)
                    .attackId(0)
                    .build();

            assertThat(read(body, AttackRequest::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("32" + body);
        }

        @Test
        void decode_shouldFail_whenTheClickIsMissing() {
            assertThatThrownBy(() -> read(d(1) + d(2) + d(3) + d(4), AttackRequest::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class RequestMagicSkillUseCodec {
        @ParameterizedTest(name = "ctrl={0} shift={1}")
        @CsvSource({"false,false", "true,false", "false,true", "true,true"})
        void decode_shouldRoundTripSkillAndModifiers(boolean ctrl, boolean shift) {
            final String body = d(1177) + d(ctrl ? 1 : 0) + c(shift ? 1 : 0);
            final RequestMagicSkillUse packet = RequestMagicSkillUse.builder()
                    .skillId(1177)
                    .ctrlPressed(ctrl)
                    .shiftPressed(shift)
                    .build();

            assertThat(read(body, RequestMagicSkillUse::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("39" + body);
        }

        @Test
        void decode_shouldFail_whenShiftIsMissing() {
            assertThatThrownBy(() -> read(d(1177) + d(0), RequestMagicSkillUse::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class RequestRestartPointCodec {
        @Test
        void decode_shouldRoundTripThePointType() {
            final RequestRestartPoint packet = new RequestRestartPoint(4);

            assertThat(read(d(4), RequestRestartPoint::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("7d" + d(4));
        }

        @Test
        void decode_shouldFail_whenThePointTypeIsMissing() {
            assertThatThrownBy(() -> read("", RequestRestartPoint::decode)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class AppearingCodec {
        @Test
        void decode_shouldRoundTripTheEmptyBody() {
            assertThat(read("", Appearing::decode)).isSameAs(Appearing.INSTANCE);
            assertThat(written(Appearing.INSTANCE::encode)).isEqualTo("3a");
        }
    }
}
