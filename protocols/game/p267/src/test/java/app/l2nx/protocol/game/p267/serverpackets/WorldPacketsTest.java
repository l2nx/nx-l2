/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.f;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.serverpackets.head.CharInfoHead;
import app.l2nx.protocol.game.p267.serverpackets.head.NpcInfoHead;
import app.l2nx.protocol.game.p267.serverpackets.head.UserInfoHead;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WorldPacketsTest {
    @Nested
    class DeleteObjectCodec {
        @Test
        void write_shouldMakeTheObjectDisappear() {
            final DeleteObject packet = new DeleteObject(5);

            assertThat(read(d(5) + d(1), DeleteObject::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("08" + d(5) + d(1));
        }

        @Test
        void read_shouldFail_whenTheTailIsMissing() {
            assertThatThrownBy(() -> read(d(5), DeleteObject::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class MoveToLocationCodec {
        @Test
        void write_shouldPutTheTargetInFrontOfTheOrigin() {
            final String body = d(5) + d(-71000) + d(258000) + d(-3100) + d(-71338) + d(258271) + d(-3104);
            final MoveToLocation packet = MoveToLocation.builder()
                    .objectId(5)
                    .targetX(-71000)
                    .targetY(258000)
                    .targetZ(-3100)
                    .originX(-71338)
                    .originY(258271)
                    .originZ(-3104)
                    .build();

            assertThat(read(body, MoveToLocation::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("2f" + body);
        }
    }

    @Nested
    class StopMoveCodec {
        @Test
        void write_shouldRoundTripPositionAndHeading() {
            final String body = d(5) + d(1) + d(-2) + d(3) + d(4);
            final StopMove packet =
                    StopMove.builder().objectId(5).x(1).y(-2).z(3).heading(4).build();

            assertThat(read(body, StopMove::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("47" + body);
        }
    }

    @Nested
    class TeleportToLocationCodec {
        @Test
        void write_shouldFadeInFrontOfTheHeading() {
            final String body = d(5) + d(1) + d(-2) + d(3) + d(0) + d(4);
            final TeleportToLocation packet = TeleportToLocation.builder()
                    .objectId(5)
                    .x(1)
                    .y(-2)
                    .z(3)
                    .heading(4)
                    .build();

            assertThat(read(body, TeleportToLocation::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("22" + body);
        }

        @Test
        void read_shouldFail_whenTheHeadingIsMissing() {
            assertThatThrownBy(() -> read(d(5) + d(1) + d(-2) + d(3) + d(0), TeleportToLocation::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class MoveToPawnCodec {
        @Test
        void read_shouldGiveBackWhatWriteWrote() {
            final MoveToPawn expected = MoveToPawn.builder()
                    .objectId(1)
                    .targetObjectId(2)
                    .distance(40)
                    .x(3)
                    .y(-4)
                    .z(5)
                    .targetX(6)
                    .targetY(-7)
                    .targetZ(8)
                    .build();

            assertThat(read(d(1) + d(2) + d(40) + d(3) + d(-4) + d(5) + d(6) + d(-7) + d(8), MoveToPawn::read))
                    .isEqualTo(expected);
        }
    }

    @Nested
    class CharInfoHeadCodec {
        @Test
        void read_shouldFindTheDeadFlagPastTheTitle() {
            final CharInfoHead expected = CharInfoHead.builder()
                    .x(1)
                    .y(-2)
                    .z(3)
                    .vehicleId(4)
                    .objectId(5)
                    .name("Bot")
                    .dead(true)
                    .build();
            final String body = d(1) + d(-2) + d(3) + d(4) + d(5) + s("Bot") + "00".repeat(284) + s("Title")
                    + "00".repeat(19) + "01";

            assertThat(read(body, CharInfoHead::read)).isEqualTo(expected);
        }

        @Test
        void read_shouldFail_whenTheNameHasNoTerminator() {
            assertThatThrownBy(() -> read(d(1) + d(-2) + d(3) + d(4) + d(5) + "4200", CharInfoHead::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class UserInfoHeadCodec {
        @Test
        void read_shouldReadTheRunSpeedAfterTheName() {
            final UserInfoHead expected = UserInfoHead.builder()
                    .x(1)
                    .y(-2)
                    .z(3)
                    .vehicleId(0)
                    .objectId(5)
                    .name("Bot")
                    .baseRunSpeed(120)
                    .moveMultiplier(1.1)
                    .build();
            final String body = d(1)
                    + d(-2)
                    + d(3)
                    + d(0)
                    + d(5)
                    + s("Bot")
                    + "00".repeat(456)
                    + d(120)
                    + "00".repeat(28)
                    + f(1.1)
                    + d(0);

            final UserInfoHead actual = read(body, UserInfoHead::read);

            assertThat(actual).isEqualTo(expected);
            assertThat(actual.runSpeed()).isCloseTo(132.0, within(1e-9));
        }
    }

    @Nested
    class NpcInfoHeadCodec {
        @Test
        void read_shouldTakeTheOffsetOffTheNpcId() {
            final NpcInfoHead expected = NpcInfoHead.builder()
                    .objectId(5)
                    .npcId(20120)
                    .attackable(true)
                    .x(1)
                    .y(-2)
                    .z(3)
                    .dead(true)
                    .build();
            final String body = d(5) + d(1020120) + d(1) + d(1) + d(-2) + d(3) + "00".repeat(95) + "01";

            assertThat(read(body, NpcInfoHead::read)).isEqualTo(expected);
        }

        @Test
        void read_shouldFail_whenThePositionIsCut() {
            assertThatThrownBy(() -> read(d(5) + d(1020120) + d(1) + d(1), NpcInfoHead::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }
}
