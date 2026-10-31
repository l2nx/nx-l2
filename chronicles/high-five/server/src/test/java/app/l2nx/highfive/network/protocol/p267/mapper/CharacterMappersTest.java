/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.character.CreateFailure;
import app.l2nx.highfive.character.DeleteFailure;
import app.l2nx.highfive.character.view.CharacterCreateFailed;
import app.l2nx.highfive.character.view.CharacterCreated;
import app.l2nx.highfive.character.view.CharacterDeleteFailed;
import app.l2nx.highfive.character.view.CharacterDeleted;
import app.l2nx.highfive.character.view.CharacterListShown;
import app.l2nx.highfive.character.view.CharacterSelected;
import app.l2nx.highfive.character.view.CharacterSummary;
import app.l2nx.highfive.character.view.ServerClosed;
import app.l2nx.highfive.character.view.WorldLeft;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.data.xml.ExperienceData;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.player.Sex;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.login.view.AuthenticationFailed;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.network.codec.Frames;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateFail;
import app.l2nx.protocol.game.p267.serverpackets.CharCreateOk;
import app.l2nx.protocol.game.p267.serverpackets.CharDeleteFail;
import app.l2nx.protocol.game.p267.serverpackets.CharDeleteSuccess;
import app.l2nx.protocol.game.p267.serverpackets.CharSelected;
import app.l2nx.protocol.game.p267.serverpackets.CharSelectionInfo;
import app.l2nx.protocol.game.p267.serverpackets.LeaveWorld;
import app.l2nx.protocol.game.p267.serverpackets.LoginFail;
import app.l2nx.protocol.game.p267.serverpackets.ServerClose;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharCreateFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.CharDeleteFailReason;
import app.l2nx.protocol.game.p267.serverpackets.reason.LoginFailReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

@ExtendWith(ClockBridgeExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
class CharacterMappersTest {
    private static final int LEVEL = 20;
    private static final long LEVEL_EXP = 10_000;
    private static final long NEXT_LEVEL_EXP = 14_000;

    @Nested
    class AuthenticationFailedMapping {
        @Test
        void map_shouldBeLoginFail_systemErrorLoginLater() {
            assertThat(new AuthenticationFailedMapper().map(AuthenticationFailed.INSTANCE, null))
                    .isEqualTo(new LoginFail(LoginFailReason.SYSTEM_ERROR_LOGIN_LATER));
        }

        @Test
        void serialize_shouldWriteTheLoginFailFrame() {
            final ByteBuf frame =
                    P267Mappers.serialize(AuthenticationFailed.INSTANCE, null, UnpooledByteBufAllocator.DEFAULT);
            try {
                // The frame keeps the 2 length bytes in front of the payload.
                assertThat(HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2)))
                        .isEqualTo("0a" + "01000000");
            } finally {
                frame.release();
            }
        }
    }

    @Nested
    class CharacterListShownMapping {
        @Test
        void map_shouldWriteTheCountsOfAnEmptyList() {
            final CharacterListShown event = CharacterListShown.builder()
                    .login("alice")
                    .playOk1(7)
                    .characters(List.of())
                    .activeSlot(CharacterListShown.NO_SLOT)
                    .build();

            assertThat(new CharacterListShownMapper().map(event, null))
                    .usingRecursiveComparison()
                    .isEqualTo(new CharSelectionInfo(ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT, List.of()));
        }

        @Test
        void map_shouldDeriveTheWireValuesOfASlot() {
            final CharacterSummary marked = summary()
                    .deleteTimer(ClockBridgeExtension.WALL_START
                            .plus(Duration.ofSeconds(90))
                            .toEpochMilli())
                    .build();
            final CharacterSummary banned = summary().accessLevel(-1).build();
            final CharacterListShown event = CharacterListShown.builder()
                    .login("alice")
                    .playOk1(7)
                    .characters(List.of(marked, banned))
                    .activeSlot(1)
                    .build();

            try (MockedStatic<ExperienceData> experience = mockStatic(ExperienceData.class)) {
                final ExperienceData table = mock(ExperienceData.class);
                when(table.getExpForLevel(LEVEL)).thenReturn(LEVEL_EXP);
                when(table.getExpForLevel(LEVEL + 1)).thenReturn(NEXT_LEVEL_EXP);
                experience.when(ExperienceData::getInstance).thenReturn(table);

                assertThat(new CharacterListShownMapper().map(event, null))
                        .usingRecursiveComparison()
                        .isEqualTo(new CharSelectionInfo(
                                ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT,
                                List.of(
                                        slot().deleteSeconds(90).active(false).build(),
                                        slot().deleteSeconds(-1).active(true).build())));
            }
        }
    }

    @Nested
    class SelectionEventsMapping {
        @Test
        void characterCreated_shouldBeCharCreateOk() {
            assertThat(new CharacterCreatedMapper().map(CharacterCreated.INSTANCE, null))
                    .isEqualTo(CharCreateOk.INSTANCE);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "CREATION_FAILED, REASON_CREATION_FAILED",
            "TOO_MANY_CHARACTERS, REASON_TOO_MANY_CHARACTERS",
            "NAME_ALREADY_EXISTS, REASON_NAME_ALREADY_EXISTS",
            "NAME_TOO_LONG, REASON_16_ENG_CHARS",
            "INCORRECT_NAME, REASON_INCORRECT_NAME"
        })
        void characterCreateFailed_shouldCarryTheClientReason(CreateFailure failure, CharCreateFailReason expected) {
            assertThat(new CharacterCreateFailedMapper().map(new CharacterCreateFailed(failure), null))
                    .isEqualTo(new CharCreateFail(expected));
        }

        @Test
        void characterDeleted_shouldBeCharDeleteSuccess() {
            assertThat(new CharacterDeletedMapper().map(CharacterDeleted.INSTANCE, null))
                    .isEqualTo(CharDeleteSuccess.INSTANCE);
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "CLAN_MEMBER, REASON_YOU_MAY_NOT_DELETE_CLAN_MEMBER",
            "CLAN_LEADER, REASON_CLAN_LEADERS_MAY_NOT_BE_DELETED"
        })
        void characterDeleteFailed_shouldCarryTheClientReason(DeleteFailure failure, CharDeleteFailReason expected) {
            assertThat(new CharacterDeleteFailedMapper().map(new CharacterDeleteFailed(failure), null))
                    .isEqualTo(new CharDeleteFail(expected));
        }

        @Test
        void serverClosedAndWorldLeft_shouldBeTheirFixedPackets() {
            assertThat(new ServerClosedMapper().map(ServerClosed.INSTANCE, null))
                    .isEqualTo(ServerClose.INSTANCE);
            assertThat(new WorldLeftMapper().map(WorldLeft.INSTANCE, null)).isEqualTo(LeaveWorld.INSTANCE);
        }

        @Test
        void characterSelected_shouldCarryEveryValueOfTheEvent() {
            final CharacterSelected event = CharacterSelected.builder()
                    .name("Alice")
                    .playerHandle(0x10000001)
                    .title("Knight")
                    .playOk1(7)
                    .clanId(0)
                    .female(true)
                    .race(Race.ELF)
                    .classId(18)
                    .x(-71338)
                    .y(258271)
                    .z(-3104)
                    .currentHp(80.5)
                    .currentMp(30.25)
                    .sp(1234L)
                    .exp(5000L)
                    .level(20)
                    .karma(0)
                    .pkKills(1)
                    .intelligence(21)
                    .strength(40)
                    .constitution(43)
                    .mentality(25)
                    .dexterity(30)
                    .wit(11)
                    .gameMinute(615)
                    .build();

            assertThat(new CharacterSelectedMapper().map(event, null))
                    .isEqualTo(CharSelected.builder()
                            .name("Alice")
                            .objectId(0x10000001)
                            .title("Knight")
                            .playOk1(7)
                            .clanId(0)
                            .female(true)
                            .race(Race.ELF.ordinal())
                            .classId(18)
                            .x(-71338)
                            .y(258271)
                            .z(-3104)
                            .currentHp(80.5)
                            .currentMp(30.25)
                            .sp(1234)
                            .exp(5000L)
                            .level(20)
                            .karma(0)
                            .pkKills(1)
                            .intelligence(21)
                            .strength(40)
                            .constitution(43)
                            .mentality(25)
                            .dexterity(30)
                            .wit(11)
                            .gameMinute(615)
                            .build());
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({
            "CHARACTER_CREATED, 0f01000000",
            "CHARACTER_CREATE_FAILED, 1003000000",
            "CHARACTER_DELETED, 1d",
            "CHARACTER_DELETE_FAILED, 1e03000000",
            "SERVER_CLOSED, 20",
            "WORLD_LEFT, 84"
        })
        void serialize_shouldWriteTheFrameThroughTheAdapterTable(SelectionEvent event, String expected) {
            assertThat(payload(event.event)).isEqualTo(expected);
        }
    }

    private enum SelectionEvent {
        CHARACTER_CREATED(CharacterCreated.INSTANCE),
        CHARACTER_CREATE_FAILED(new CharacterCreateFailed(CreateFailure.NAME_TOO_LONG)),
        CHARACTER_DELETED(CharacterDeleted.INSTANCE),
        CHARACTER_DELETE_FAILED(new CharacterDeleteFailed(DeleteFailure.CLAN_LEADER)),
        SERVER_CLOSED(ServerClosed.INSTANCE),
        WORLD_LEFT(WorldLeft.INSTANCE);

        private final ViewEvent event;

        SelectionEvent(ViewEvent event) {
            this.event = event;
        }
    }

    /** The payload behind the frame's length header, as lower-case hex. */
    private static String payload(ViewEvent event) {
        final ByteBuf frame = P267Mappers.serialize(event, null, UnpooledByteBufAllocator.DEFAULT);
        try {
            return HexFormat.of()
                    .formatHex(ByteBufUtil.getBytes(frame, Frames.HEADER, frame.readableBytes() - Frames.HEADER));
        } finally {
            frame.release();
        }
    }

    /** A weapon enchanted to 300 (the client shows at most 127) and a chest piece. */
    private static int[][] paperdoll() {
        final int[][] paperdoll = new int[Inventory.PAPERDOLL_TOTALSLOTS][3];
        paperdoll[Inventory.PAPERDOLL_RHAND][0] = 0x10000002;
        paperdoll[Inventory.PAPERDOLL_RHAND][1] = 2369;
        paperdoll[Inventory.PAPERDOLL_RHAND][2] = 300;
        paperdoll[Inventory.PAPERDOLL_CHEST][1] = 1146;
        return paperdoll;
    }

    private static CharacterSummary.CharacterSummaryBuilder summary() {
        return CharacterSummary.builder()
                .objectId(0x10000001)
                .name("Alice")
                .accessLevel(0)
                .level(LEVEL)
                .exp(LEVEL_EXP + 1_000)
                .sp(1_234)
                .maxHp(96)
                .currentHp(80.5)
                .maxMp(40)
                .currentMp(30.25)
                .karma(0)
                .pkKills(1)
                .pvpKills(2)
                .face(0)
                .hairStyle(1)
                .hairColor(2)
                .sex(Sex.FEMALE)
                .race(Race.HUMAN)
                .clanId(0)
                .classId(0)
                .baseClassId(0)
                .x(-71338)
                .y(258271)
                .z(-3104)
                .vitalityPoints(20_000)
                .augmentationId(0)
                .lastAccess(1)
                .paperdoll(paperdoll());
    }

    private static CharSelectionInfo.Slot.SlotBuilder slot() {
        final List<Integer> items = Arrays.stream(ServerPacket.paperdollOrder())
                .map(slot -> paperdoll()[slot][1])
                .boxed()
                .toList();
        return CharSelectionInfo.Slot.builder()
                .name("Alice")
                .objectId(0x10000001)
                .login("alice")
                .playOk1(7)
                .clanId(0)
                .sex(Sex.FEMALE.ordinal())
                .race(Race.HUMAN.ordinal())
                .baseClassId(0)
                .x(-71338)
                .y(258271)
                .z(-3104)
                .currentHp(80.5)
                .currentMp(30.25)
                .sp(1_234)
                .exp(LEVEL_EXP + 1_000)
                .expPercent(0.25f)
                .level(LEVEL)
                .karma(0)
                .pkKills(1)
                .pvpKills(2)
                .paperdollItemIds(items)
                .hairStyle(1)
                .hairColor(2)
                .face(0)
                .maxHp(96)
                .maxMp(40)
                .classId(0)
                .enchantEffect(127)
                .augmentationId(0)
                .vitalityPoints(20_000);
    }
}
