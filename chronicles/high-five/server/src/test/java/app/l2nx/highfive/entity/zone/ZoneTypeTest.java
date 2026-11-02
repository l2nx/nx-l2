/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone;

import static app.l2nx.highfive.entity.zone.ZoneTestObjects.creature;
import static app.l2nx.highfive.entity.zone.ZoneTestObjects.moveTo;
import static app.l2nx.highfive.entity.zone.ZoneTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.zone.ZoneTestObjects.RecordingZone;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ZoneTypeTest {
    private final Player first = player(268_300_009);
    private final Player second = player(268_300_001);
    private final Creature monster = creature(268_300_005);

    private static RecordingZone zone() {
        return WorldLoopExtension.callInPass(() -> new RecordingZone(70_001));
    }

    private RecordingZone zoneWithThree() {
        return WorldLoopExtension.callInPass(() -> {
            final RecordingZone zone = new RecordingZone(70_001);
            zone.revalidateInZone(first);
            zone.revalidateInZone(monster);
            zone.revalidateInZone(second);
            return zone;
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ZoneType> call) {
            final ZoneType zone = zone();

            assertThatThrownBy(() -> call.accept(zone))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ZoneType>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId (accessor)", ZoneType::getId),
                    Named.of("getName (accessor)", ZoneType::getName),
                    Named.of("setName", z -> z.setName("Zone")),
                    Named.of("isEnabled (accessor)", ZoneType::isEnabled),
                    Named.of("setEnabled", z -> z.setEnabled(false)),
                    Named.of("getZone (accessor)", ZoneType::getZone),
                    Named.of("setParameter", z -> z.setParameter("affectedLvlMin", "10")),
                    Named.of("getSettings (accessor)", ZoneType::getSettings),
                    Named.of("setSettings", z -> z.setSettings(null)),
                    Named.of("isInsideZone(x, y, z)", z -> z.isInsideZone(0, 0, 0)),
                    Named.of("isInsideZone(x, y)", z -> z.isInsideZone(0, 0)),
                    Named.of("revalidateInZone", z -> z.revalidateInZone(creature(1))),
                    Named.of("removeCharacter", z -> z.removeCharacter(creature(1))),
                    Named.of("isCharacterInZone", z -> z.isCharacterInZone(creature(1))),
                    Named.of("getCharactersInside", ZoneType::getCharactersInside),
                    Named.of("getPlayersInside", ZoneType::getPlayersInside),
                    Named.of("collectCharactersInside", z -> z.collectCharactersInside(new ArrayList<>())),
                    Named.of("collectPlayersInside", z -> z.collectPlayersInside(new ArrayList<>())),
                    Named.of("hasCharactersInside", ZoneType::hasCharactersInside),
                    Named.of("hasPlayersInside", ZoneType::hasPlayersInside),
                    Named.of("getCharacterCount", ZoneType::getCharacterCount),
                    Named.of("getPlayerCount", ZoneType::getPlayerCount),
                    Named.of("broadcastPacket", z -> z.broadcastPacket(mock(ServerPacket.class))),
                    Named.of("oustAllPlayers", ZoneType::oustAllPlayers),
                    Named.of("onDieInside", z -> z.onDieInside(creature(1))));
        }
    }

    @Nested
    class RevalidateInZone {
        @Test
        void revalidateInZone_shouldEnterOnce_whenRevalidatedAgainInside() {
            final RecordingZone zone = zone();

            WorldLoopExtension.runInPass(() -> {
                zone.revalidateInZone(first);
                zone.revalidateInZone(first);
            });

            assertThat(zone.events).containsExactly("enter 268300009");
        }

        @Test
        void revalidateInZone_shouldExit_whenTheCreatureLeftTheShape() {
            final RecordingZone zone = zoneWithThree();
            moveTo(monster, 500, 0, 0);

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                zone.revalidateInZone(monster);
                return zone.isCharacterInZone(monster);
            });

            assertThat(actual).isFalse();
            assertThat(zone.events).endsWith("exit 268300005");
        }

        @Test
        void revalidateInZone_shouldNotEnter_whenTheCreatureIsNotAffected() {
            final RecordingZone zone = zone();

            WorldLoopExtension.runInPass(() -> {
                zone.setParameter("affectedLvlMin", "10");
                zone.revalidateInZone(first);
            });

            assertThat(zone.events).isEmpty();
        }
    }

    @Nested
    class RemoveCharacter {
        @Test
        void removeCharacter_shouldExitOnlyACreatureInside() {
            final RecordingZone zone = zoneWithThree();

            WorldLoopExtension.runInPass(() -> {
                zone.removeCharacter(second);
                zone.removeCharacter(second);
            });

            assertThat(zone.events)
                    .containsExactly("enter 268300009", "enter 268300005", "enter 268300001", "exit 268300001");
        }
    }

    @Nested
    class GetCharactersInside {
        @Test
        void getCharactersInside_shouldBeAReadOnlySnapshotInEntryOrder() {
            final RecordingZone zone = zoneWithThree();

            final List<Creature> actual = WorldLoopExtension.callInPass(() -> {
                final List<Creature> snapshot = zone.getCharactersInside();
                zone.removeCharacter(first);
                return snapshot;
            });

            assertThat(actual).containsExactly(first, monster, second);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getPlayersInside_shouldListOnlyPlayersInEntryOrder() {
            final RecordingZone zone = zoneWithThree();

            final List<Player> actual = WorldLoopExtension.callInPass(zone::getPlayersInside);

            assertThat(actual).containsExactly(first, second);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void collectPlayersInside_shouldAddOnlyPlayersInEntryOrder() {
            final RecordingZone zone = zoneWithThree();

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                final List<Player> into = new ArrayList<>();
                zone.collectPlayersInside(into);
                return into;
            });

            assertThat(actual).containsExactly(first, second);
        }
    }

    @Nested
    class Counts {
        @Test
        void getPlayerCount_shouldCountOnlyPlayers() {
            final RecordingZone zone = zoneWithThree();

            final List<Integer> actual =
                    WorldLoopExtension.callInPass(() -> List.of(zone.getCharacterCount(), zone.getPlayerCount()));

            assertThat(actual).containsExactly(3, 2);
        }

        @Test
        void hasPlayersInside_shouldBeFalse_whenOnlyNonPlayersAreInside() {
            final RecordingZone zone = zoneWithThree();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final boolean withPlayers = zone.hasPlayersInside();
                zone.removeCharacter(first);
                zone.removeCharacter(second);
                return List.of(withPlayers, zone.hasPlayersInside(), zone.hasCharactersInside());
            });

            assertThat(actual).containsExactly(true, false, true);
        }

        @Test
        void hasCharactersInside_shouldBeFalse_whenTheLastCreatureLeft() {
            final RecordingZone zone = zone();

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                zone.revalidateInZone(monster);
                final boolean before = zone.hasCharactersInside();
                zone.removeCharacter(monster);
                return List.of(before, zone.hasCharactersInside());
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class BroadcastPacket {
        @Test
        void broadcastPacket_shouldSendToEveryPlayerInside() {
            final RecordingZone zone = zoneWithThree();
            final ServerPacket packet = mock(ServerPacket.class);

            WorldLoopExtension.runInPass(() -> zone.broadcastPacket(packet));

            verify(first).sendPacket(packet);
            verify(second).sendPacket(packet);
            verify(monster, never()).sendPacket(any(ServerPacket.class));
        }
    }

    @Nested
    class OustAllPlayers {
        @Test
        void oustAllPlayers_shouldTeleportEveryPlayer_whenATeleportTakesThePlayerOut() {
            final RecordingZone zone = zoneWithThree();
            for (Player player : List.of(first, second)) {
                doAnswer(invocation -> {
                            zone.removeCharacter(player);
                            return null;
                        })
                        .when(player)
                        .teleToLocation(TeleportWhereType.TOWN);
            }

            final List<Creature> actual = WorldLoopExtension.callInPass(() -> {
                zone.oustAllPlayers();
                return zone.getCharactersInside();
            });

            verify(first).teleToLocation(TeleportWhereType.TOWN);
            verify(second).teleToLocation(TeleportWhereType.TOWN);
            assertThat(actual).containsExactly(monster);
        }
    }
}
