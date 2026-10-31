/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.action.view.ActionCancelled;
import app.l2nx.highfive.data.xml.ItemData;
import app.l2nx.highfive.data.xml.NpcData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.entity.item.ItemTemplate;
import app.l2nx.highfive.mechanics.skill.Skill;
import app.l2nx.highfive.message.MessageKey;
import app.l2nx.highfive.message.MessageParam;
import app.l2nx.highfive.message.view.MessageShown;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.protocol.game.p267.serverpackets.ActionFailed;
import app.l2nx.protocol.game.p267.serverpackets.SystemMessage;
import app.l2nx.protocol.game.p267.serverpackets.SystemMessageParam;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

class MessageMappersTest {
    @Nested
    class ActionCancelledMapping {
        @Test
        void map_shouldBeActionFailed() {
            assertThat(new ActionCancelledMapper().map(ActionCancelled.INSTANCE, null))
                    .isSameAs(ActionFailed.INSTANCE);
        }

        @Test
        void serialize_shouldWriteTheActionFailedOpcode() {
            assertThat(payload(P267Mappers.serialize(ActionCancelled.INSTANCE, null, UnpooledByteBufAllocator.DEFAULT)))
                    .isEqualTo("1f");
        }
    }

    @Nested
    class MessageShownMapping {
        @Test
        void map_shouldBeTheSystemMessageOfTheKey_whenThereAreNoParameters() {
            final MessageShown event = MessageShown.of(MessageKey.CANNOT_RESTART_IN_COMBAT);

            assertThat(new MessageShownMapper().map(event, null))
                    .isEqualTo(
                            new SystemMessage(SystemMessageId.YOU_CANNOT_RESTART_WHILE_IN_COMBAT.getId(), List.of()));
        }

        @Test
        void map_shouldWriteTheAccountKickMessage() {
            final MessageShown event = MessageShown.of(MessageKey.ACCOUNT_LOGGED_IN_ELSEWHERE);

            assertThat(new MessageShownMapper().map(event, null))
                    .isEqualTo(new SystemMessage(
                            SystemMessageId.ANOTHER_PERSON_HAS_LOGGED_IN_WITH_THE_SAME_ACCOUNT.getId(), List.of()));
        }

        // S1_2 ("$s1") takes one parameter, so filling it never grows a shared message id's parameter count.
        @ParameterizedTest
        @MethodSource("parameters")
        void map_shouldTurnTheParameterIntoItsWireParameter(MessageParam param, SystemMessageParam expected) {
            assertThat(MessageShownMapper.map(SystemMessageId.S1_2, List.of(param), null))
                    .isEqualTo(new SystemMessage(SystemMessageId.S1_2.getId(), List.of(expected)));
        }

        static Stream<Arguments> parameters() {
            return Stream.of(
                    argumentSet("text", new MessageParam.Text("x"), new SystemMessageParam.Text("x")),
                    argumentSet("number", new MessageParam.Numeric(5), new SystemMessageParam.LongNumber(5)),
                    argumentSet(
                            "player name",
                            new MessageParam.PlayerName("Alice"),
                            new SystemMessageParam.PlayerName("Alice")));
        }

        @Test
        void serialize_shouldWriteTheIdAndAnEmptyParameterList() {
            final ByteBuf frame = P267Mappers.serialize(
                    MessageShown.of(MessageKey.CANNOT_RESTART_IN_COMBAT), null, UnpooledByteBufAllocator.DEFAULT);

            assertThat(payload(frame))
                    .isEqualTo("62" + littleEndian(SystemMessageId.YOU_CANNOT_RESTART_WHILE_IN_COMBAT.getId())
                            + "00000000");
        }
    }

    @Nested
    class TemplateParameters {
        private static final int ID = 20;

        private final NpcData npcs = mock(NpcData.class);
        private final SkillData skills = mock(SkillData.class);
        private final ItemData items = mock(ItemData.class);
        private MockedStatic<NpcData> npcData;
        private MockedStatic<SkillData> skillData;
        private MockedStatic<ItemData> itemData;

        @BeforeEach
        void mockData() {
            npcData = mockStatic(NpcData.class);
            npcData.when(NpcData::getInstance).thenReturn(npcs);
            skillData = mockStatic(SkillData.class);
            skillData.when(SkillData::getInstance).thenReturn(skills);
            itemData = mockStatic(ItemData.class);
            itemData.when(ItemData::getInstance).thenReturn(items);
        }

        @AfterEach
        void releaseData() {
            itemData.close();
            skillData.close();
            npcData.close();
        }

        @Test
        void map_shouldWriteTheServerSideName_whenTheNpcUsesOne() {
            final NpcTemplate template = mock(NpcTemplate.class);
            when(template.isUsingServerSideName()).thenReturn(true);
            when(template.getName()).thenReturn("Custom Guard");
            when(npcs.getTemplate(ID)).thenReturn(template);

            assertThat(map(new MessageParam.NpcTemplate(ID))).isEqualTo(new SystemMessageParam.Text("Custom Guard"));
        }

        @Test
        void map_shouldWriteTheOffsetNpcId_whenTheClientNamesTheNpc() {
            final NpcTemplate template = mock(NpcTemplate.class);
            when(template.getId()).thenReturn(ID);
            when(npcs.getTemplate(ID)).thenReturn(template);

            assertThat(map(new MessageParam.NpcTemplate(ID))).isEqualTo(new SystemMessageParam.NpcName(1_000_000 + ID));
        }

        @Test
        void map_shouldWriteTheOffsetNpcId_whenTheNpcIsUnknown() {
            assertThat(map(new MessageParam.NpcTemplate(ID))).isEqualTo(new SystemMessageParam.NpcName(1_000_000 + ID));
        }

        @Test
        void map_shouldWriteTheServerSideName_whenTheSkillShowsAsAnother() {
            final Skill skill = mock(Skill.class);
            when(skill.getId()).thenReturn(ID);
            when(skill.getDisplayId()).thenReturn(ID + 1);
            when(skill.getName()).thenReturn("Custom Strike");
            when(skills.getSkill(ID, 3)).thenReturn(skill);

            assertThat(map(skill(ID, 3))).isEqualTo(new SystemMessageParam.Text("Custom Strike"));
        }

        @Test
        void map_shouldWriteTheSkillIdAndLevel_whenTheClientNamesTheSkill() {
            final Skill skill = mock(Skill.class);
            when(skill.getId()).thenReturn(ID);
            when(skill.getDisplayId()).thenReturn(ID);
            when(skill.getLevel()).thenReturn(3);
            when(skills.getSkill(ID, 3)).thenReturn(skill);

            assertThat(map(skill(ID, 3)))
                    .isEqualTo(SystemMessageParam.SkillName.builder()
                            .id(ID)
                            .level(3)
                            .build());
        }

        @Test
        void map_shouldWriteTheSkillIdAndLevel_whenTheSkillIsUnknown() {
            assertThat(map(skill(ID, 3)))
                    .isEqualTo(SystemMessageParam.SkillName.builder()
                            .id(ID)
                            .level(3)
                            .build());
        }

        @Test
        void map_shouldWriteTheServerSideName_whenTheItemShowsAsAnother() {
            final ItemTemplate template = mock(ItemTemplate.class);
            when(template.getDisplayId()).thenReturn(ID + 1);
            when(template.getName()).thenReturn("Custom Sword");
            when(items.getTemplate(ID)).thenReturn(template);

            assertThat(map(new MessageParam.ItemTemplate(ID))).isEqualTo(new SystemMessageParam.Text("Custom Sword"));
        }

        @Test
        void map_shouldWriteTheItemId_whenTheClientNamesTheItem() {
            final ItemTemplate template = mock(ItemTemplate.class);
            when(template.getDisplayId()).thenReturn(ID);
            when(items.getTemplate(ID)).thenReturn(template);

            assertThat(map(new MessageParam.ItemTemplate(ID))).isEqualTo(new SystemMessageParam.ItemName(ID));
        }

        @Test
        void map_shouldWriteTheBareItemId_whenTheItemIsUnknown() {
            assertThat(map(new MessageParam.ItemTemplate(ID))).isEqualTo(new SystemMessageParam.ItemName(ID));
        }

        private static MessageParam.Skill skill(int id, int level) {
            return MessageParam.Skill.builder().id(id).level(level).build();
        }

        private static SystemMessageParam map(MessageParam param) {
            final SystemMessage message =
                    (SystemMessage) MessageShownMapper.map(SystemMessageId.S1_2, List.of(param), null);
            assertThat(message.params()).hasSize(1);
            return message.params().getFirst();
        }
    }

    @Nested
    class Id {
        @ParameterizedTest
        @EnumSource(MessageKey.class)
        void id_shouldMapEveryKeyToAHighFiveMessage(MessageKey key) {
            assertThat(P267Messages.id(key)).isNotNull();
        }
    }

    private static String payload(ByteBuf frame) {
        try {
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(frame, 2, frame.readableBytes() - 2));
        } finally {
            frame.release();
        }
    }

    private static String littleEndian(int value) {
        return HexFormat.of()
                .formatHex(new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24)});
    }
}
