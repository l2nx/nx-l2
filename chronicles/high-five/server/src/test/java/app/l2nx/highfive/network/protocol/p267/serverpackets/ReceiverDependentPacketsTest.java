/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.chat.ChatType;
import app.l2nx.highfive.config.custom.MultilingualSupportConfig;
import app.l2nx.highfive.data.xml.NpcNameLocalisationData;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo.NpcInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

@Isolated("toggles MultilingualSupportConfig.MULTILANG_ENABLE")
class ReceiverDependentPacketsTest {
    private static final int OBJECT_ID = 0x10000001;
    private static final int NPC_ID = 20120;
    private static final String LOCALISED = "Гремлин";

    private boolean multilang;

    @BeforeEach
    void remember() {
        multilang = MultilingualSupportConfig.MULTILANG_ENABLE;
    }

    @AfterEach
    void restore() {
        MultilingualSupportConfig.MULTILANG_ENABLE = multilang;
    }

    @Nested
    class ReceiverDependent {
        static Stream<Arguments> packets() {
            final Stream<Arguments> packets = Stream.of(
                    Arguments.of("NpcInfo", (Supplier<ServerPacket>) () -> new NpcInfo(npc(), mock(Player.class))),
                    Arguments.of("NpcSay", (Supplier<ServerPacket>)
                            () -> new NpcSay(OBJECT_ID, ChatType.NPC_GENERAL, NPC_ID, "hi")),
                    Arguments.of(
                            "ExShowScreenMessage", (Supplier<ServerPacket>) () -> new ExShowScreenMessage("hi", 1000)),
                    Arguments.of(
                            "SystemMessage", (Supplier<ServerPacket>) () -> new SystemMessage(SystemMessageId.S1_2)));
            return packets.flatMap(packet ->
                    Stream.of(true, false).map(enabled -> Arguments.of(packet.get()[0], packet.get()[1], enabled)));
        }

        @ParameterizedTest(name = "{0} with multilang {2}")
        @MethodSource("packets")
        void receiverDependent_shouldFollowMultilang(String name, Supplier<ServerPacket> packet, boolean enabled) {
            MultilingualSupportConfig.MULTILANG_ENABLE = enabled;

            assertThat(packet.get().receiverDependent()).isEqualTo(enabled);
        }
    }

    @Nested
    class NpcInfoWriteImpl {
        @Test
        void writeImpl_shouldGiveEachRecipientItsOwnName_whenTheFirstRecipientIsLocalised() {
            MultilingualSupportConfig.MULTILANG_ENABLE = true;
            final NpcNameLocalisationData data = mock(NpcNameLocalisationData.class);
            when(data.getLocalisation("ru", NPC_ID)).thenReturn(new String[] {LOCALISED, "Титул"});
            try (MockedStatic<NpcNameLocalisationData> instance = mockStatic(NpcNameLocalisationData.class)) {
                instance.when(NpcNameLocalisationData::getInstance).thenReturn(data);
                final NpcInfo packet = new NpcInfo(npc(), mock(Player.class));
                packet.sendInBroadcast();

                final String russian = hex(packet, client("ru"));
                final String english = hex(packet, client("en"));
                packet.releaseBroadcast();

                assertThat(russian).contains(utf16(LOCALISED));
                assertThat(english).doesNotContain(utf16(LOCALISED));
            }
        }
    }

    private static Npc npc() {
        final NpcTemplate template = mock(NpcTemplate.class);
        when(template.getDisplayId()).thenReturn(NPC_ID);
        final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
        when(npc.getObjectId()).thenReturn(OBJECT_ID);
        when(npc.getId()).thenReturn(NPC_ID);
        when(npc.getTemplate()).thenReturn(template);
        when(npc.getTeam()).thenReturn(Team.NONE);
        when(npc.isAutoAttackable(any(Creature.class))).thenReturn(false);
        return npc;
    }

    private static GameClient client(String lang) {
        final Player player = mock(Player.class);
        when(player.getLang()).thenReturn(lang);
        final GameClient client = mock(GameClient.class);
        when(client.getPlayer()).thenReturn(player);
        return client;
    }

    private static String hex(ServerPacket packet, GameClient client) {
        final ByteBuf frame = packet.serialize(client, UnpooledByteBufAllocator.DEFAULT);
        try {
            return HexFormat.of().formatHex(ByteBufUtil.getBytes(frame));
        } finally {
            frame.release();
        }
    }

    private static String utf16(String text) {
        return HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_16LE));
    }
}
