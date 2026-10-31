/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.clientpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MovementPacketsTest {
    @Nested
    class MoveToLocationCodec {
        @Test
        void decode_shouldRoundTripTargetOriginAndMode() {
            final String body = d(-71000) + d(258000) + d(-3100) + d(-71338) + d(258271) + d(-3104) + d(1);
            final MoveToLocation packet = MoveToLocation.builder()
                    .targetX(-71000)
                    .targetY(258000)
                    .targetZ(-3100)
                    .originX(-71338)
                    .originY(258271)
                    .originZ(-3104)
                    .movementMode(MoveToLocation.MOUSE)
                    .build();

            assertThat(read(body, MoveToLocation::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("0f" + body);
        }

        @Test
        void decode_shouldFail_whenTheModeIsMissing() {
            assertThatThrownBy(() -> read(d(1) + d(2) + d(3) + d(4) + d(5) + d(6), MoveToLocation::decode))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class ValidatePositionCodec {
        @Test
        void decode_shouldRoundTripPositionHeadingAndVehicle() {
            final String body = d(1) + d(-2) + d(3) + d(4) + d(0);
            final ValidatePosition packet = ValidatePosition.builder()
                    .x(1)
                    .y(-2)
                    .z(3)
                    .heading(4)
                    .vehicleId(0)
                    .build();

            assertThat(read(body, ValidatePosition::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("59" + body);
        }
    }

    @Nested
    class CannotMoveAnymoreCodec {
        @Test
        void decode_shouldRoundTripPositionAndHeading() {
            final String body = d(1) + d(-2) + d(3) + d(4);
            final CannotMoveAnymore packet =
                    CannotMoveAnymore.builder().x(1).y(-2).z(3).heading(4).build();

            assertThat(read(body, CannotMoveAnymore::decode)).isEqualTo(packet);
            assertThat(written(packet::encode)).isEqualTo("47" + body);
        }
    }
}
