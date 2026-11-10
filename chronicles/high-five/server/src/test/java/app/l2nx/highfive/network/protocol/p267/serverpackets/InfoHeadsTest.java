/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.creature.Race;
import app.l2nx.highfive.entity.actor.enums.creature.Team;
import app.l2nx.highfive.entity.actor.enums.player.MountType;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.enums.player.PrivateStoreType;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.network.protocol.p267.serverpackets.AbstractNpcInfo.NpcInfo;
import app.l2nx.network.codec.PacketWriter;
import app.l2nx.protocol.game.p267.serverpackets.GameServerOpcode;
import app.l2nx.protocol.game.p267.serverpackets.head.CharInfoHead;
import app.l2nx.protocol.game.p267.serverpackets.head.NpcInfoHead;
import app.l2nx.protocol.game.p267.serverpackets.head.UserInfoHead;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.function.Function;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class InfoHeadsTest {
    private static final int OBJECT_ID = 0x10000001;
    private static final int X = -71338;
    private static final int Y = 258271;
    private static final int Z = -3104;
    private static final String NAME = "Bot";

    @Nested
    class CharInfoPacket {
        @Test
        void writeImpl_shouldGiveTheHeadItsFields() {
            final Player player = player();
            when(player.isAlikeDead()).thenReturn(true);

            final CharInfoHead actual =
                    head(new CharInfo(player, false), GameServerOpcode.CHAR_INFO, CharInfoHead::read);

            assertThat(actual)
                    .isEqualTo(CharInfoHead.builder()
                            .x(X)
                            .y(Y)
                            .z(Z)
                            .vehicleId(0)
                            .objectId(OBJECT_ID)
                            .name(NAME)
                            .dead(true)
                            .build());
        }
    }

    @Nested
    class UserInfoPacket {
        @Test
        void writeImpl_shouldGiveTheHeadItsFields() {
            final Player player = player();
            when(player.getRunSpeed()).thenReturn(115.0);
            final TerritoryWarManager territories = mock(TerritoryWarManager.class);
            try (MockedStatic<TerritoryWarManager> instance = mockStatic(TerritoryWarManager.class)) {
                instance.when(TerritoryWarManager::getInstance).thenReturn(territories);

                final UserInfoHead actual = head(new UserInfo(player), GameServerOpcode.USER_INFO, UserInfoHead::read);

                assertThat(actual)
                        .isEqualTo(UserInfoHead.builder()
                                .x(X)
                                .y(Y)
                                .z(Z)
                                .vehicleId(0)
                                .objectId(OBJECT_ID)
                                .name(NAME)
                                .baseRunSpeed(115)
                                .moveMultiplier(1.0)
                                .build());
            }
        }

        @Test
        void writeImpl_shouldGiveTheHeadTheRunSpeedTheClientMovesAt_whenAMultiplierApplies() {
            final Player player = player();
            when(player.getRunSpeed()).thenReturn(132.0);
            when(player.getMovementSpeedMultiplier()).thenReturn(1.1);
            final TerritoryWarManager territories = mock(TerritoryWarManager.class);
            try (MockedStatic<TerritoryWarManager> instance = mockStatic(TerritoryWarManager.class)) {
                instance.when(TerritoryWarManager::getInstance).thenReturn(territories);

                final UserInfoHead actual = head(new UserInfo(player), GameServerOpcode.USER_INFO, UserInfoHead::read);

                assertThat(actual.baseRunSpeed()).isEqualTo(120);
                assertThat(actual.runSpeed()).isCloseTo(132.0, within(1e-9));
            }
        }

        @Test
        void writeImpl_shouldWriteTheBytesOfAnEmptyTitle_whenThePlayerHasNoTitle() {
            final Player player = player();
            final TerritoryWarManager territories = mock(TerritoryWarManager.class);
            try (MockedStatic<TerritoryWarManager> instance = mockStatic(TerritoryWarManager.class)) {
                instance.when(TerritoryWarManager::getInstance).thenReturn(territories);
                final UserInfo packet = new UserInfo(player);
                when(player.getTitle()).thenReturn("");
                final String expected = hex(packet);
                when(player.getTitle()).thenReturn(null);

                assertThat(hex(packet)).isEqualTo(expected);
            }
        }
    }

    @Nested
    class NpcInfoPacket {
        @Test
        void writeImpl_shouldGiveTheHeadItsFields() {
            final NpcTemplate template = mock(NpcTemplate.class);
            when(template.getDisplayId()).thenReturn(20120);
            final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
            when(npc.getObjectId()).thenReturn(OBJECT_ID);
            when(npc.getX()).thenReturn(X);
            when(npc.getY()).thenReturn(Y);
            when(npc.getZ()).thenReturn(Z);
            when(npc.getTemplate()).thenReturn(template);
            when(npc.getTeam()).thenReturn(Team.NONE);
            when(npc.isAutoAttackable(any(Creature.class))).thenReturn(true);
            when(npc.isAlikeDead()).thenReturn(true);

            final NpcInfoHead actual =
                    head(new NpcInfo(npc, mock(Player.class)), GameServerOpcode.NPC_INFO, NpcInfoHead::read);

            assertThat(actual)
                    .isEqualTo(NpcInfoHead.builder()
                            .objectId(OBJECT_ID)
                            .npcId(20120)
                            .attackable(true)
                            .x(X)
                            .y(Y)
                            .z(Z)
                            .dead(true)
                            .build());
        }

        @Test
        void writeImpl_shouldWriteTheBytesOfAnEmptyTitle_whenTheNpcHasNoTitle() {
            final Npc npc = mock(Npc.class, RETURNS_DEEP_STUBS);
            when(npc.getTemplate()).thenReturn(mock(NpcTemplate.class));
            when(npc.getTeam()).thenReturn(Team.NONE);
            final NpcInfo packet = new NpcInfo(npc, mock(Player.class));
            when(npc.getTitle()).thenReturn("");
            final String expected = hex(packet);
            when(npc.getTitle()).thenReturn(null);

            assertThat(hex(packet)).isEqualTo(expected);
        }
    }

    private static Player player() {
        final Player player = mock(Player.class, RETURNS_DEEP_STUBS);
        when(player.getObjectId()).thenReturn(OBJECT_ID);
        when(player.getX()).thenReturn(X);
        when(player.getY()).thenReturn(Y);
        when(player.getZ()).thenReturn(Z);
        when(player.getVehicle()).thenReturn(null);
        when(player.getClan()).thenReturn(null);
        when(player.getAppearance().getVisibleName()).thenReturn(NAME);
        when(player.getRace()).thenReturn(Race.HUMAN);
        when(player.getTeam()).thenReturn(Team.NONE);
        when(player.getMountType()).thenReturn(MountType.NONE);
        when(player.getPrivateStoreType()).thenReturn(PrivateStoreType.NONE);
        when(player.getPlayerClass()).thenReturn(PlayerClass.FIGHTER);
        when(player.getCubicIds()).thenReturn(new int[0]);
        when(player.getMovementSpeedMultiplier()).thenReturn(1.0);
        return player;
    }

    private static String hex(ServerPacket packet) {
        return written(out -> {
            try {
                packet.writeImpl(null, new PacketWriter(out));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    private static <T> T head(ServerPacket packet, GameServerOpcode opcode, Function<ByteBuf, T> reader) {
        final ByteBuf frame = Unpooled.buffer();
        try {
            packet.writeImpl(null, new PacketWriter(frame));
            assertThat(frame.readUnsignedByte()).isEqualTo((short) opcode.opcode());
            return reader.apply(frame);
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            frame.release();
        }
    }
}
