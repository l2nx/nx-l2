/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.c;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.h;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import app.l2nx.protocol.game.p267.serverpackets.Attack.Hit;
import app.l2nx.protocol.game.p267.serverpackets.MagicSkillUse.GroundLocation;
import app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.Attribute;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CombatPacketsTest {
    @Nested
    class AttackCodec {
        private static final Hit FIRST =
                Hit.builder().targetObjectId(7).damage(120).flags(0xF0).build();
        private static final Hit SECOND =
                Hit.builder().targetObjectId(8).damage(0).flags(0x80).build();

        private static Attack.AttackBuilder attack() {
            return Attack.builder()
                    .attackerObjectId(5)
                    .hit(FIRST)
                    .x(1)
                    .y(-2)
                    .z(3)
                    .extraHits(List.of())
                    .targetX(4)
                    .targetY(-5)
                    .targetZ(6);
        }

        @Test
        void write_shouldPutTheFirstHitInFrontOfTheAttackerPosition() {
            final String body = d(5) + d(7) + d(120) + c(0xF0) + d(1) + d(-2) + d(3) + h(0) + d(4) + d(-5) + d(6);
            final Attack packet = attack().build();

            assertThat(read(body, Attack::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("33" + body);
        }

        @Test
        void write_shouldPutTheExtraHitsBehindTheAttackerPosition() {
            final String body = d(5) + d(7) + d(120) + c(0xF0) + d(1) + d(-2) + d(3) + h(1) + d(8) + d(0) + c(0x80)
                    + d(4) + d(-5) + d(6);
            final Attack packet = attack().extraHits(List.of(SECOND)).build();

            assertThat(read(body, Attack::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("33" + body);
        }

        @Test
        void read_shouldFail_whenTheCountExceedsTheBody() {
            final String body = d(5) + d(7) + d(120) + c(0) + d(1) + d(-2) + d(3) + h(1000) + d(4) + d(-5) + d(6);

            assertThatThrownBy(() -> read(body, Attack::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class MagicSkillUseCodec {
        private static final String HEAD = d(5) + d(7) + d(1177) + d(3) + d(1500) + d(6000) + d(1) + d(-2) + d(3);
        private static final String TARGET = d(4) + d(-5) + d(6);

        private static MagicSkillUse.MagicSkillUseBuilder skillUse() {
            return MagicSkillUse.builder()
                    .objectId(5)
                    .targetObjectId(7)
                    .skillId(1177)
                    .skillLevel(3)
                    .hitTime(1500)
                    .reuseDelay(6000)
                    .x(1)
                    .y(-2)
                    .z(3)
                    .groundLocations(List.of())
                    .targetX(4)
                    .targetY(-5)
                    .targetZ(6);
        }

        @Test
        void write_shouldCountNoGroundLocation_whenThereIsNone() {
            final String body = HEAD + h(0) + h(0) + TARGET;
            final MagicSkillUse packet = skillUse().build();

            assertThat(read(body, MagicSkillUse::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("48" + body);
        }

        @Test
        void write_shouldPutTheGroundLocationInFrontOfTheTargetPosition() {
            final String body = HEAD + h(0) + h(1) + d(9) + d(-10) + d(11) + TARGET;
            final MagicSkillUse packet = skillUse()
                    .groundLocations(
                            List.of(GroundLocation.builder().x(9).y(-10).z(11).build()))
                    .build();

            assertThat(read(body, MagicSkillUse::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("48" + body);
        }

        @Test
        void read_shouldFail_whenTheCountExceedsTheBody() {
            assertThatThrownBy(() -> read(HEAD + h(0) + h(1000) + TARGET, MagicSkillUse::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class DieCodec {
        @Test
        void write_shouldKeepEachFlagInItsPlace() {
            final String body = d(5) + d(1) + d(0) + d(1) + d(0) + d(1) + d(0) + d(1);
            final Die packet = Die.builder()
                    .objectId(5)
                    .canTeleport(true)
                    .toCastle(true)
                    .sweepable(true)
                    .toFortress(true)
                    .build();

            assertThat(read(body, Die::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("00" + body);
        }

        @Test
        void read_shouldFail_whenTheLastFlagIsMissing() {
            assertThatThrownBy(() -> read(d(5) + d(1) + d(0) + d(1) + d(0) + d(1) + d(0), Die::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class ReviveCodec {
        @Test
        void write_shouldRoundTripTheObject() {
            final Revive packet = new Revive(5);

            assertThat(read(d(5), Revive::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("01" + d(5));
        }
    }

    @Nested
    class StatusUpdateCodec {
        @Test
        void write_shouldCountTheAttributes() {
            final String body = d(5) + d(2) + d(0x0a) + d(1000) + d(0x09) + d(750);
            final StatusUpdate packet = StatusUpdate.builder()
                    .objectId(5)
                    .attributes(List.of(
                            Attribute.builder()
                                    .id(StatusUpdate.MAX_HP)
                                    .value(1000)
                                    .build(),
                            Attribute.builder()
                                    .id(StatusUpdate.CUR_HP)
                                    .value(750)
                                    .build()))
                    .build();

            assertThat(read(body, StatusUpdate::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("18" + body);
        }

        @Test
        void read_shouldFail_whenTheCountExceedsTheBody() {
            assertThatThrownBy(() -> read(d(5) + d(2) + d(0x09) + d(750), StatusUpdate::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class MyTargetSelectedCodec {
        @ParameterizedTest(name = "color={0}")
        @ValueSource(ints = {0, 9, -9})
        void write_shouldRoundTripTheColor(int color) {
            final String body = d(5) + h(color) + d(0);
            final MyTargetSelected packet =
                    MyTargetSelected.builder().objectId(5).color(color).build();

            assertThat(read(body, MyTargetSelected::read)).isEqualTo(packet);
            assertThat(written(packet::write)).isEqualTo("b9" + body);
        }

        @Test
        void read_shouldFail_whenTheTailIsMissing() {
            assertThatThrownBy(() -> read(d(5) + h(0), MyTargetSelected::read))
                    .isInstanceOf(PacketDecodeException.class);
        }
    }
}
