/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol.p267.serverpackets;

import static app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessageTest.d;
import static app.l2nx.network.test.GoldenBytes.written;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.data.holders.AccessLevel;
import app.l2nx.highfive.data.xml.AdminData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.clan.Clan;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.managers.CHSiegeManager;
import app.l2nx.highfive.managers.CastleManager;
import app.l2nx.highfive.managers.FortManager;
import app.l2nx.highfive.managers.TerritoryWarManager;
import app.l2nx.highfive.mechanics.olympiad.OlympiadManager;
import app.l2nx.network.codec.PacketWriter;
import app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.Attribute;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class WirePacketsTest {
    private static final int ID = 5;
    private static final int TARGET_ID = 7;

    private final Creature attacker = creature(ID, 1, -2, 3);
    private final Creature target = creature(TARGET_ID, 4, -5, 6);

    @Nested
    class AttackPacket {
        @Test
        void writeImpl_shouldWriteASingleHit() {
            final Attack attack = new Attack(attacker, target, true, 3);
            attack.addHit(target, 120, false, true, (byte) 1);

            assertThat(hex(attack))
                    .isEqualTo("33" + d(ID) + d(TARGET_ID) + d(120) + "73" + d(1) + d(-2) + d(3) + "0000" + d(4) + d(-5)
                            + d(6));
        }

        @Test
        void writeImpl_shouldWriteTheExtraHitsBehindTheAttacker() {
            final Attack attack = new Attack(attacker, target, false, 0);
            attack.addHit(target, 120, false, false, (byte) 0);
            attack.addHit(target, 0, true, false, (byte) 0);

            assertThat(hex(attack))
                    .isEqualTo("33" + d(ID) + d(TARGET_ID) + d(120) + "00" + d(1) + d(-2) + d(3) + "0100" + d(TARGET_ID)
                            + d(0) + "80" + d(4) + d(-5) + d(6));
        }

        @Test
        void hasHits_shouldTurnTrue_whenTheFirstHitIsAdded() {
            final Attack attack = new Attack(attacker, target, false, 0);

            assertThat(attack.hasHits()).isFalse();
            attack.addHit(target, 120, false, false, (byte) 0);
            assertThat(attack.hasHits()).isTrue();
        }
    }

    @Nested
    class MagicSkillUsePacket {
        @Test
        void writeImpl_shouldKeepThePositionsOfTheStartOfTheCast() {
            final MagicSkillUse packet = new MagicSkillUse(attacker, target, 1177, 3, 1500, 6000);
            when(attacker.getX()).thenReturn(9);

            assertThat(hex(packet))
                    .isEqualTo("48" + d(ID) + d(TARGET_ID) + d(1177) + d(3) + d(1500) + d(6000) + d(1) + d(-2) + d(3)
                            + "0000" + "0000" + d(4) + d(-5) + d(6));
        }

        @Test
        void writeImpl_shouldWriteTheGroundLocation_whenThePlayerHasOne() {
            final Player player = mock(Player.class);
            when(player.isPlayer()).thenReturn(true);
            when(player.asPlayer()).thenReturn(player);
            when(player.getObjectId()).thenReturn(ID);
            when(player.getCurrentSkillWorldPosition()).thenReturn(new Location(10, -11, 12));

            assertThat(hex(new MagicSkillUse(player, target, 1177, 3, 1500, 6000)))
                    .isEqualTo("48" + d(ID) + d(TARGET_ID) + d(1177) + d(3) + d(1500) + d(6000) + d(0) + d(0) + d(0)
                            + "0000" + "0100" + d(10) + d(-11) + d(12) + d(4) + d(-5) + d(6));
        }
    }

    @Nested
    class DiePacket {
        private final AdminData admin = mock(AdminData.class);
        private MockedStatic<AdminData> adminInstance;

        @BeforeEach
        void bindAdminData() {
            adminInstance = mockStatic(AdminData.class);
            adminInstance.when(AdminData::getInstance).thenReturn(admin);
        }

        @AfterEach
        void releaseAdminData() {
            adminInstance.close();
        }

        @Test
        void writeImpl_shouldOfferNoPoint_whenTheDeadIsNoPlayer() {
            when(attacker.canRevive()).thenReturn(true);
            when(attacker.isSweepActive()).thenReturn(true);

            assertThat(hex(new Die(attacker))).isEqualTo("00" + d(ID) + d(1) + d(0) + d(0) + d(0) + d(1) + d(0) + d(0));
        }

        @Test
        void writeImpl_shouldOfferTheClanPoints_whenThePlayerHasAClan() {
            final Clan clan = mock(Clan.class);
            when(clan.getHideoutId()).thenReturn(1);
            when(clan.getFortId()).thenReturn(1);
            final PlayerInventory inventory = mock(PlayerInventory.class);
            when(inventory.haveItemForSelfResurrection()).thenReturn(true);
            final Player player = mock(Player.class);
            when(player.isPlayer()).thenReturn(true);
            when(player.asPlayer()).thenReturn(player);
            when(player.getObjectId()).thenReturn(ID);
            when(player.getAccessLevel()).thenReturn(mock(AccessLevel.class));
            when(player.getClan()).thenReturn(clan);
            when(player.getInventory()).thenReturn(inventory);
            when(player.canRevive()).thenReturn(true);

            try (MockedStatic<OlympiadManager> olympiad = mockStatic(OlympiadManager.class);
                    MockedStatic<CastleManager> castles = mockStatic(CastleManager.class);
                    MockedStatic<FortManager> forts = mockStatic(FortManager.class);
                    MockedStatic<CHSiegeManager> halls = mockStatic(CHSiegeManager.class);
                    MockedStatic<TerritoryWarManager> territories = mockStatic(TerritoryWarManager.class)) {
                olympiad.when(OlympiadManager::getInstance).thenReturn(mock(OlympiadManager.class));
                castles.when(CastleManager::getInstance).thenReturn(mock(CastleManager.class));
                forts.when(FortManager::getInstance).thenReturn(mock(FortManager.class));
                halls.when(CHSiegeManager::getInstance).thenReturn(mock(CHSiegeManager.class));
                territories.when(TerritoryWarManager::getInstance).thenReturn(mock(TerritoryWarManager.class));

                assertThat(hex(new Die(player)))
                        .isEqualTo("00" + d(ID) + d(1) + d(1) + d(0) + d(0) + d(0) + d(1) + d(1));
            }
        }
    }

    @Nested
    class StatusUpdatePacket {
        @Test
        void writeImpl_shouldEqualTheWireRecord() {
            final StatusUpdate legacy = new StatusUpdate(ID);
            legacy.addAttribute(StatusUpdate.MAX_HP, 1000);
            legacy.addAttribute(StatusUpdate.CUR_HP, 750);
            final app.l2nx.protocol.game.p267.serverpackets.StatusUpdate wire =
                    app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.builder()
                            .objectId(ID)
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

            assertThat(hex(legacy)).isEqualTo(written(wire::write));
        }

        @Test
        void writeImpl_shouldEqualTheWireRecord_whenThereAreNoAttributes() {
            final app.l2nx.protocol.game.p267.serverpackets.StatusUpdate wire =
                    app.l2nx.protocol.game.p267.serverpackets.StatusUpdate.builder()
                            .objectId(ID)
                            .attributes(List.of())
                            .build();

            assertThat(hex(new StatusUpdate(ID))).isEqualTo(written(wire::write));
        }
    }

    @Nested
    class PositionPackets {
        @Test
        void writeImpl_shouldWriteMoveToLocationTargetBeforeOrigin() {
            when(attacker.getXdestination()).thenReturn(7);
            when(attacker.getYdestination()).thenReturn(-8);
            when(attacker.getZdestination()).thenReturn(9);

            assertThat(hex(new MoveToLocation(attacker)))
                    .isEqualTo("2f" + d(ID) + d(7) + d(-8) + d(9) + d(1) + d(-2) + d(3));
        }

        @Test
        void writeImpl_shouldWriteStopMovePositionAndHeading() {
            when(attacker.getHeading()).thenReturn(4);

            assertThat(hex(new StopMove(attacker))).isEqualTo("47" + d(ID) + d(1) + d(-2) + d(3) + d(4));
        }

        @Test
        void writeImpl_shouldWriteTeleportToLocationWithAFade() {
            assertThat(hex(new TeleportToLocation(attacker, 7, -8, 9, 4)))
                    .isEqualTo("22" + d(ID) + d(7) + d(-8) + d(9) + d(0) + d(4));
        }
    }

    @Nested
    class ObjectPackets {
        @Test
        void writeImpl_shouldWriteDeleteObjectAsDisappearing() {
            assertThat(hex(new DeleteObject(attacker))).isEqualTo("08" + d(ID) + d(1));
        }

        @Test
        void writeImpl_shouldWriteRevive() {
            assertThat(hex(new Revive(attacker))).isEqualTo("01" + d(ID));
        }

        @Test
        void writeImpl_shouldWriteMyTargetSelectedWithTheLevelGap_whenTheTargetIsAttackable() {
            final Player player = mock(Player.class);
            when(player.getLevel()).thenReturn(20);
            when(target.getLevel()).thenReturn(29);
            when(target.isAutoAttackable(player)).thenReturn(true);

            assertThat(hex(new MyTargetSelected(player, target))).isEqualTo("b9" + d(TARGET_ID) + "f7ff" + d(0));
        }
    }

    private static Creature creature(int objectId, int x, int y, int z) {
        final Creature creature = mock(Creature.class);
        when(creature.getObjectId()).thenReturn(objectId);
        when(creature.getX()).thenReturn(x);
        when(creature.getY()).thenReturn(y);
        when(creature.getZ()).thenReturn(z);
        return creature;
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
}
