/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.decoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.movement.MoveInput;
import app.l2nx.highfive.movement.command.MoveBlocked;
import app.l2nx.highfive.movement.command.MoveTo;
import app.l2nx.highfive.movement.command.ReportPosition;
import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.clientpackets.CannotMoveAnymore;
import app.l2nx.protocol.game.p267.clientpackets.MoveToLocation;
import app.l2nx.protocol.game.p267.clientpackets.ValidatePosition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MovementDecodersTest {
    @Nested
    class MoveToLocationDecoding {
        @ParameterizedTest(name = "mode {0}")
        @CsvSource({"1,MOUSE", "0,KEYBOARD", "7,KEYBOARD"})
        void moveToLocation_shouldReadTargetOriginAndInput(int mode, MoveInput expected) {
            final MoveToLocation packet = MoveToLocation.builder()
                    .targetX(10)
                    .targetY(20)
                    .targetZ(30)
                    .originX(1)
                    .originY(2)
                    .originZ(3)
                    .movementMode(mode)
                    .build();

            final Object actual = decode(packet::encode, frame -> MovementDecoders.moveToLocation(frame, null));

            assertThat(actual)
                    .isEqualTo(MoveTo.builder()
                            .targetX(10)
                            .targetY(20)
                            .targetZ(30)
                            .originX(1)
                            .originY(2)
                            .originZ(3)
                            .input(expected)
                            .build());
        }

        @Test
        void moveToLocation_shouldFail_whenTheModeIsMissing() {
            final ByteBuf frame = Unpooled.buffer();
            for (int i = 0; i < 6; i++) {
                frame.writeIntLE(i);
            }
            try {
                assertThatThrownBy(() -> MovementDecoders.moveToLocation(frame, null))
                        .isInstanceOf(PacketDecodeException.class);
            } finally {
                frame.release();
            }
        }
    }

    @Nested
    class ValidatePositionDecoding {
        @Test
        void validatePosition_shouldDropTheVehicle() {
            final ValidatePosition packet = ValidatePosition.builder()
                    .x(5)
                    .y(6)
                    .z(7)
                    .heading(8)
                    .vehicleId(99)
                    .build();

            final Object actual = decode(packet::encode, frame -> MovementDecoders.validatePosition(frame, null));

            assertThat(actual)
                    .isEqualTo(
                            ReportPosition.builder().x(5).y(6).z(7).heading(8).build());
        }
    }

    @Nested
    class CannotMoveAnymoreDecoding {
        @Test
        void cannotMoveAnymore_shouldReadThePosition() {
            final CannotMoveAnymore packet =
                    CannotMoveAnymore.builder().x(5).y(6).z(7).heading(8).build();

            final Object actual = decode(packet::encode, frame -> MovementDecoders.cannotMoveAnymore(frame, null));

            assertThat(actual)
                    .isEqualTo(MoveBlocked.builder().x(5).y(6).z(7).heading(8).build());
        }
    }

    private static Object decode(Consumer<ByteBuf> encode, Function<ByteBuf, Object> decoder) {
        final ByteBuf frame = Unpooled.buffer();
        try {
            encode.accept(frame);
            frame.skipBytes(1);
            return decoder.apply(frame);
        } finally {
            frame.release();
        }
    }
}
