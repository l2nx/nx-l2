/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.instancezone;

import static app.l2nx.highfive.entity.WorldTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.engine.core.loop.timer.TimeBase;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.partition.WorldLoopExtension;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.time.Duration;
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

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class InstanceTest {
    private static final int INSTANCE_ID = 300_001;
    private static final int EJECT_TIME = 60_000;

    private final Instance instance = new Instance(INSTANCE_ID);

    private static Npc npc(int npcId, double hp) {
        final Npc npc = mock(Npc.class);
        when(npc.getId()).thenReturn(npcId);
        when(npc.getCurrentHp()).thenReturn(hp);
        return npc;
    }

    private static Player insidePlayer(int objectId) {
        final Player player = player(objectId);
        when(player.getInstanceId()).thenReturn(INSTANCE_ID);
        return player;
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Instance> call) {
            assertThatThrownBy(() -> call.accept(instance))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Instance>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getId (accessor)", Instance::getId),
                    Named.of("getTemplateId", Instance::getTemplateId),
                    Named.of("getEjectTime", Instance::getEjectTime),
                    Named.of("setEjectTime", i -> i.setEjectTime(1)),
                    Named.of("getExitLoc", Instance::getExitLoc),
                    Named.of("setExitLoc", i -> i.setExitLoc(null)),
                    Named.of("isSummonAllowed", Instance::isSummonAllowed),
                    Named.of("setAllowSummon", i -> i.setAllowSummon(false)),
                    Named.of("setEmptyDestroyTime", i -> i.setEmptyDestroyTime(0)),
                    Named.of("getInstanceStartTime", Instance::getInstanceStartTime),
                    Named.of("getInstanceEndTime", Instance::getInstanceEndTime),
                    Named.of("isPvP", Instance::isPvP),
                    Named.of("setPvP", i -> i.setPvP(true)),
                    Named.of("isShowTimer", Instance::isShowTimer),
                    Named.of("isTimerIncrease", Instance::isTimerIncrease),
                    Named.of("getTimerText", Instance::getTimerText),
                    Named.of("getReenterType", Instance::getReenterType),
                    Named.of("setReenterType", i -> i.setReenterType(InstanceReenterType.NONE)),
                    Named.of("getReenterData", Instance::getReenterData),
                    Named.of("getRemoveBuffType", Instance::getRemoveBuffType),
                    Named.of("isRemoveBuffEnabled", Instance::isRemoveBuffEnabled),
                    Named.of("isBuffException", i -> i.isBuffException(1)),
                    Named.of("setDuration", i -> i.setDuration(1_000)),
                    Named.of("cancelTimer", Instance::cancelTimer),
                    Named.of("containsPlayer", i -> i.containsPlayer(1)),
                    Named.of("addPlayer (former concurrent set)", i -> i.addPlayer(1)),
                    Named.of("removePlayer", i -> i.removePlayer(1)),
                    Named.of("getPlayerCount", Instance::getPlayerCount),
                    Named.of("collectPlayers", i -> i.collectPlayers(new IntArrayList())),
                    Named.of("removePlayers", Instance::removePlayers),
                    Named.of("eject", i -> i.eject(null)),
                    Named.of("addNpc", i -> i.addNpc(null)),
                    Named.of("removeNpc", i -> i.removeNpc(null)),
                    Named.of("getNpcs", Instance::getNpcs),
                    Named.of("getNpc", i -> i.getNpc(1)),
                    Named.of("collectNpcs (ids)", i -> i.collectNpcs(Npc.class, new int[0], false, new ArrayList<>())),
                    Named.of("collectNpcs (any id)", i -> i.collectNpcs(Npc.class, false, new ArrayList<>())),
                    Named.of("removeNpcs", Instance::removeNpcs),
                    Named.of("spawnGroup", i -> i.spawnGroup("general")),
                    Named.of("addDoor", i -> i.addDoor(null)),
                    Named.of("spawnDoors", Instance::spawnDoors),
                    Named.of("getDoors", Instance::getDoors),
                    Named.of("getDoor", i -> i.getDoor(1)),
                    Named.of("anyDoor", i -> i.anyDoor(_ -> true)),
                    Named.of("removeDoors", Instance::removeDoors),
                    Named.of("addEnterLoc", i -> i.addEnterLoc(null)),
                    Named.of("getEnterLocs", Instance::getEnterLocs),
                    Named.of("loadInstanceTemplate", i -> i.loadInstanceTemplate(1)),
                    Named.of("notifyDeath", i -> i.notifyDeath(null)),
                    Named.of("cancelEjectDeadPlayer", i -> i.cancelEjectDeadPlayer(null)));
        }
    }

    @Nested
    class CollectPlayers {
        @Test
        void collectPlayers_shouldKeepEntryOrder() {
            final IntArrayList actual = WorldLoopExtension.callInPass(() -> {
                instance.addPlayer(30);
                instance.addPlayer(10);
                instance.addPlayer(20);
                instance.addPlayer(10);
                final IntArrayList inside = new IntArrayList();
                instance.collectPlayers(inside);
                return inside;
            });

            assertThat(actual.toIntArray()).containsExactly(30, 10, 20);
        }

        @Test
        void removePlayer_shouldDropThePlayerFromCountAndLookup() {
            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                instance.addPlayer(10);
                instance.addPlayer(20);
                instance.removePlayer(10);
                return List.of(instance.getPlayerCount(), instance.containsPlayer(10), instance.containsPlayer(20));
            });

            assertThat(actual).containsExactly(1, false, true);
        }
    }

    @Nested
    class RemovePlayers {
        @Test
        void removePlayers_shouldTeleportEveryPlayerOut_whenTheTeleportTakesThemOffTheList() {
            final Player first = insidePlayer(0x7ffe_1001);
            final Player second = insidePlayer(0x7ffe_1002);
            for (Player player : List.of(first, second)) {
                doAnswer(_ -> {
                            instance.removePlayer(player.getObjectId());
                            return null;
                        })
                        .when(player)
                        .setInstanceId(0);
            }

            final int actual = WorldLoopExtension.callInPass(() -> {
                World.addObject(first);
                World.addObject(second);
                try {
                    instance.addPlayer(first.getObjectId());
                    instance.addPlayer(second.getObjectId());
                    instance.removePlayers();
                    return instance.getPlayerCount();
                } finally {
                    World.removeObject(first);
                    World.removeObject(second);
                }
            });

            assertThat(actual).isZero();
            verify(first).teleToLocation(TeleportWhereType.TOWN);
            verify(second).teleToLocation(TeleportWhereType.TOWN);
        }
    }

    @Nested
    class Npcs {
        @Test
        void getNpcs_shouldBeAReadOnlySnapshotInSpawnOrder() {
            final Npc first = npc(1, 100);
            final Npc second = npc(2, 100);

            final List<Npc> actual = WorldLoopExtension.callInPass(() -> {
                instance.addNpc(first);
                instance.addNpc(second);
                final List<Npc> snapshot = instance.getNpcs();
                instance.removeNpc(first);
                return snapshot;
            });

            assertThat(actual).containsExactly(first, second);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void getNpc_shouldFindTheFirstSpawnedWithTheTemplateId() {
            final Npc first = npc(7, 100);
            final Npc second = npc(7, 100);

            final Npc actual = WorldLoopExtension.callInPass(() -> {
                instance.addNpc(npc(5, 100));
                instance.addNpc(first);
                instance.addNpc(second);
                return instance.getNpc(7);
            });

            assertThat(actual).isSameAs(first);
        }

        @Test
        void collectNpcs_shouldFilterByIdLifeAndType() {
            final Npc alive = npc(7, 100);
            final Npc dead = npc(7, 0);
            final Npc other = npc(8, 100);
            final Monster monster = mock(Monster.class);
            when(monster.getId()).thenReturn(7);
            when(monster.getCurrentHp()).thenReturn(100.0);

            final List<List<?>> actual = WorldLoopExtension.callInPass(() -> {
                instance.addNpc(alive);
                instance.addNpc(dead);
                instance.addNpc(other);
                instance.addNpc(monster);
                final List<Npc> byId = new ArrayList<>();
                instance.collectNpcs(Npc.class, new int[] {7}, false, byId);
                final List<Npc> aliveById = new ArrayList<>();
                instance.collectNpcs(Npc.class, new int[] {7}, true, aliveById);
                final List<Monster> monsters = new ArrayList<>();
                instance.collectNpcs(Monster.class, false, monsters);
                return List.of(byId, aliveById, monsters);
            });

            assertThat(actual)
                    .containsExactly(List.of(alive, dead, monster), List.of(alive, monster), List.of(monster));
        }

        @Test
        void removeNpcs_shouldDeleteEveryNpc_whenTheDeleteTakesItOffTheInstance() {
            final List<Npc> npcs = List.of(npc(1, 100), npc(2, 100), npc(3, 100));
            for (Npc npc : npcs) {
                doAnswer(_ -> {
                            instance.removeNpc(npc);
                            return true;
                        })
                        .when(npc)
                        .deleteMe();
            }

            final List<Npc> actual = WorldLoopExtension.callInPass(() -> {
                npcs.forEach(instance::addNpc);
                instance.removeNpcs();
                return instance.getNpcs();
            });

            assertThat(actual).isEmpty();
            npcs.forEach(npc -> verify(npc).deleteMe());
        }
    }

    @Nested
    class Eject {
        @Test
        void eject_shouldTeleportToTheExitLocation_whenTheInstanceHasOne() {
            final Player player = insidePlayer(0x7ffe_1201);

            WorldLoopExtension.runInPass(() -> {
                instance.setExitLoc(new Location(100, 200, 300));
                instance.eject(player);
            });

            verify(player).setInstanceId(0);
            verify(player).teleToLocation(new Location(100, 200, 300), true);
        }

        @Test
        void eject_shouldTeleportToTown_whenTheInstanceHasNoExitLocation() {
            final Player player = insidePlayer(0x7ffe_1202);

            WorldLoopExtension.runInPass(() -> instance.eject(player));

            verify(player).setInstanceId(0);
            verify(player).teleToLocation(TeleportWhereType.TOWN);
        }

        @Test
        void eject_shouldLeaveThePlayer_whenHeIsOutsideTheInstance() {
            final Player player = player(0x7ffe_1203);

            WorldLoopExtension.runInPass(() -> instance.eject(player));

            verify(player, never()).setInstanceId(0);
        }
    }

    @Nested
    class NotifyDeath {
        @Test
        void notifyDeath_shouldKeepOneEjection_whenThePlayerDiesTwice(Loop world) {
            final Player player = insidePlayer(0x7ffe_1101);

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                instance.setEjectTime(EJECT_TIME);
                final int before = world.timers(TimeBase.GAME).size();
                instance.notifyDeath(player);
                instance.notifyDeath(player);
                final int afterDeaths = world.timers(TimeBase.GAME).size() - before;
                instance.cancelEjectDeadPlayer(player);
                return List.of(afterDeaths, world.timers(TimeBase.GAME).size() - before);
            });

            assertThat(actual).containsExactly(1, 0);
        }

        @Test
        void notifyDeath_shouldEjectThePlayer_whenHeIsStillDeadAtTheEjectTime() {
            final Player player = insidePlayer(0x7ffe_1102);
            when(player.isDead()).thenReturn(true);

            WorldLoopExtension.runInPass(() -> {
                instance.setEjectTime(EJECT_TIME);
                instance.notifyDeath(player);
            });
            WorldLoopExtension.passAfter(Duration.ofMillis(EJECT_TIME));

            verify(player).setInstanceId(0);
            verify(player).teleToLocation(TeleportWhereType.TOWN);
        }

        @Test
        void cancelEjectDeadPlayer_shouldKeepThePlayerInside_whenHeIsResurrected() {
            final Player player = insidePlayer(0x7ffe_1103);
            when(player.isDead()).thenReturn(true);

            WorldLoopExtension.runInPass(() -> {
                instance.setEjectTime(EJECT_TIME);
                instance.notifyDeath(player);
                instance.cancelEjectDeadPlayer(player);
            });
            WorldLoopExtension.passAfter(Duration.ofMillis(EJECT_TIME));

            verify(player, never()).setInstanceId(0);
        }
    }

    @Nested
    class Locations {
        @Test
        void getExitLoc_shouldHandOutACopy() {
            final Location exit = new Location(100, 200, 300);

            final List<Location> actual = WorldLoopExtension.callInPass(() -> {
                instance.setExitLoc(exit);
                exit.setXYZ(0, 0, 0);
                instance.getExitLoc().setXYZ(1, 1, 1);
                instance.addEnterLoc(new Location(10, 20, 30));
                instance.getEnterLocs().getFirst().setXYZ(1, 1, 1);
                return List.of(instance.getExitLoc(), instance.getEnterLocs().getFirst());
            });

            assertThat(actual).containsExactly(new Location(100, 200, 300), new Location(10, 20, 30));
        }
    }

    @Nested
    class Template {
        @Test
        void getTimerText_shouldBeNull_whenTheTemplateSetsNone() {
            final String actual = WorldLoopExtension.callInPass(instance::getTimerText);

            assertThat(actual).isNull();
        }
    }
}
