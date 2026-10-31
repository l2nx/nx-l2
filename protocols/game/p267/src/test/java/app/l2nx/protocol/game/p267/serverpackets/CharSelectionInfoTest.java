/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.read;
import static app.l2nx.network.test.GoldenBytes.written;
import static app.l2nx.protocol.game.p267.WireHex.c;
import static app.l2nx.protocol.game.p267.WireHex.d;
import static app.l2nx.protocol.game.p267.WireHex.f;
import static app.l2nx.protocol.game.p267.WireHex.q;
import static app.l2nx.protocol.game.p267.WireHex.s;
import static app.l2nx.protocol.game.p267.WireHex.zeros;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.network.codec.PacketDecodeException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CharSelectionInfoTest {
    static List<Integer> paperdoll() {
        final List<Integer> items = new ArrayList<>(Collections.nCopies(CharSelectionInfo.PAPERDOLL_SLOTS, 0));
        items.set(7, 2369);
        items.set(10, 1146);
        items.set(14, 2369);
        return items;
    }

    static CharSelectionInfo.Slot slot() {
        return CharSelectionInfo.Slot.builder()
                .name("Alice")
                .objectId(0x10000001)
                .login("alice")
                .playOk1(7)
                .clanId(0)
                .sex(1)
                .race(0)
                .baseClassId(0)
                .x(-71338)
                .y(258271)
                .z(-3104)
                .currentHp(80.5)
                .currentMp(30.25)
                .sp(1234)
                .exp(836_000L)
                .expPercent(0.25)
                .level(20)
                .karma(0)
                .pkKills(1)
                .pvpKills(2)
                .paperdollItemIds(paperdoll())
                .hairStyle(1)
                .hairColor(2)
                .face(0)
                .maxHp(96)
                .maxMp(40)
                .deleteSeconds(0)
                .classId(0)
                .active(true)
                .enchantEffect(3)
                .augmentationId(0)
                .vitalityPoints(20000)
                .build();
    }

    static String slotBytes() {
        final StringBuilder paperdoll = new StringBuilder();
        paperdoll().forEach(item -> paperdoll.append(d(item)));
        return s("Alice")
                + d(0x10000001)
                + s("alice")
                + d(7)
                + d(0)
                + d(0)
                + d(1)
                + d(0)
                + d(0)
                + d(1)
                + d(-71338)
                + d(258271)
                + d(-3104)
                + f(80.5)
                + f(30.25)
                + d(1234)
                + q(836_000L)
                + f(0.25)
                + d(20)
                + d(0)
                + d(1)
                + d(2)
                + zeros(28)
                + paperdoll
                + d(1)
                + d(2)
                + d(0)
                + f(96)
                + f(40)
                + d(0)
                + d(0)
                + d(1)
                + c(3)
                + d(0)
                + zeros(20)
                + zeros(16)
                + d(20000);
    }

    @Nested
    class Write {
        @Test
        void write_shouldWriteTheHeaderOfAnEmptyList() {
            assertThat(written(new CharSelectionInfo(7, List.of())::write)).isEqualTo("09" + d(0) + d(7) + c(0));
        }

        @Test
        void write_shouldWriteEverySlotInTheLegacyLayout() {
            assertThat(written(new CharSelectionInfo(7, List.of(slot()))::write))
                    .isEqualTo("09" + d(1) + d(7) + c(0) + slotBytes());
        }

        @Test
        void write_shouldWriteTheShortestSlotInMinSlotBytes() {
            final CharSelectionInfo packet = new CharSelectionInfo(
                    7, List.of(slot().toBuilder().name(null).login(null).build()));

            assertThat(written(packet::write).length() / 2).isEqualTo(1 + 4 + 4 + 1 + CharSelectionInfo.MIN_SLOT_BYTES);
        }
    }

    @Nested
    class Read {
        @Test
        void read_shouldReadBackWhatWriteWrote() {
            final CharSelectionInfo packet = new CharSelectionInfo(
                    7,
                    List.of(
                            slot(),
                            slot().toBuilder()
                                    .name("Bob")
                                    .active(false)
                                    .deleteSeconds(-1)
                                    .build()));

            assertThat(read(written(packet::write).substring(2), CharSelectionInfo::read))
                    .isEqualTo(packet);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, Integer.MIN_VALUE, 2})
        void read_shouldFail_whenTheCountDoesNotFitTheBody(int count) {
            final String body = d(count) + d(7) + c(0) + slotBytes();

            assertThatThrownBy(() -> read(body, CharSelectionInfo::read))
                    .isInstanceOf(PacketDecodeException.class)
                    .hasMessageContaining("List count");
        }

        @Test
        void read_shouldFail_whenASlotIsCut() {
            final String body = d(1) + d(7) + c(0) + slotBytes().substring(0, 40);

            assertThatThrownBy(() -> read(body, CharSelectionInfo::read)).isInstanceOf(PacketDecodeException.class);
        }
    }

    @Nested
    class SlotValue {
        @Test
        void build_shouldRefuseAPaperdollOfAnotherSize() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() ->
                            slot().toBuilder().paperdollItemIds(List.of(1, 2)).build());
        }
    }
}
