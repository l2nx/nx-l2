/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.instancezone;

import static app.l2nx.highfive.entity.WorldTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Npc;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.network.protocol.p267.serverpackets.ServerPacket;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
class InstanceWorldTest {
    private static final int INSTANCE_ID = 300_002;

    private final Instance instance = new Instance(INSTANCE_ID);
    private final InstanceWorld world = new InstanceWorld();

    @BeforeEach
    void bindInstance() {
        WorldLoopExtension.runInPass(() -> world.setInstance(instance));
    }

    private static Npc npc(int npcId, double hp) {
        final Npc npc = mock(Npc.class);
        when(npc.getId()).thenReturn(npcId);
        when(npc.getCurrentHp()).thenReturn(hp);
        return npc;
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<InstanceWorld> call) {
            assertThatThrownBy(() -> call.accept(world))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<InstanceWorld>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("setInstance (accessor)", w -> w.setInstance(null)),
                    Named.of("getParameters", InstanceWorld::getParameters),
                    Named.of("getInstanceId", InstanceWorld::getInstanceId),
                    Named.of("getTemplateId", InstanceWorld::getTemplateId),
                    Named.of("getAllowed", InstanceWorld::getAllowed),
                    Named.of("addAllowed (former concurrent set)", w -> w.addAllowed(null)),
                    Named.of("removeAllowed", w -> w.removeAllowed(null)),
                    Named.of("isAllowed", w -> w.isAllowed(null)),
                    Named.of("ejectPlayer", w -> w.ejectPlayer(null)),
                    Named.of("setParameter", w -> w.setParameter("key", null)),
                    Named.of("getStatus", InstanceWorld::getStatus),
                    Named.of("isStatus", w -> w.isStatus(1)),
                    Named.of("setStatus", w -> w.setStatus(1)),
                    Named.of("incStatus", InstanceWorld::incStatus),
                    Named.of("getNpcs", InstanceWorld::getNpcs),
                    Named.of("getNpcs (ids)", w -> w.getNpcs(1)),
                    Named.of("getNpcs (type)", w -> w.getNpcs(Npc.class)),
                    Named.of("getNpcs (type and ids)", w -> w.getNpcs(Npc.class, 1)),
                    Named.of("getAliveNpcs", InstanceWorld::getAliveNpcs),
                    Named.of("getAliveNpcs (ids)", w -> w.getAliveNpcs(1)),
                    Named.of("getAliveNpcs (type)", w -> w.getAliveNpcs(Npc.class)),
                    Named.of("getAliveNpcs (type and ids)", w -> w.getAliveNpcs(Npc.class, 1)),
                    Named.of("getNpc", w -> w.getNpc(1)),
                    Named.of("spawnGroup", w -> w.spawnGroup("general")),
                    Named.of("openDoor", w -> w.openDoor(1)),
                    Named.of("closeDoor", w -> w.closeDoor(1)),
                    Named.of("getDoors", InstanceWorld::getDoors),
                    Named.of("getDoor", w -> w.getDoor(1)),
                    Named.of("getPlayersCount", InstanceWorld::getPlayersCount),
                    Named.of("destroy", InstanceWorld::destroy),
                    Named.of("broadcastPacket", w -> w.broadcastPacket()));
        }
    }

    @Nested
    class Allowed {
        @Test
        void getAllowed_shouldListOnlinePlayersInAllowOrder() {
            final Player first = player(0x7ffe_2001);
            final Player second = player(0x7ffe_2002);
            final Player offline = player(0x7ffe_2003);

            final List<Player> actual = WorldLoopExtension.callInPass(() -> {
                World.addObject(first);
                World.addObject(second);
                try {
                    world.addAllowed(second);
                    world.addAllowed(offline);
                    world.addAllowed(first);
                    world.addAllowed(second);
                    return world.getAllowed();
                } finally {
                    World.removeObject(first);
                    World.removeObject(second);
                }
            });

            assertThat(actual).containsExactly(second, first);
        }

        @Test
        void removeAllowed_shouldForbidTheEntry() {
            final Player allowed = player(0x7ffe_2011);
            final Player removed = player(0x7ffe_2012);

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                world.addAllowed(allowed);
                world.addAllowed(removed);
                world.removeAllowed(removed);
                return List.of(world.isAllowed(allowed), world.isAllowed(removed));
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    @Nested
    class Npcs {
        @Test
        void getNpcs_shouldFilterTheInstanceNpcsInSpawnOrder() {
            final Npc alive = npc(7, 100);
            final Npc dead = npc(7, 0);
            final Npc other = npc(8, 100);
            final Monster monster = mock(Monster.class);
            when(monster.getId()).thenReturn(8);
            when(monster.getCurrentHp()).thenReturn(100.0);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                List.of(alive, dead, other, monster).forEach(instance::addNpc);
                return List.of(
                        world.getNpcs(7),
                        world.getAliveNpcs(7),
                        world.getAliveNpcs(),
                        world.getNpcs(Monster.class),
                        world.getAliveNpcs(Monster.class, 7),
                        world.getNpc(8));
            });

            assertThat(actual)
                    .containsExactly(
                            List.of(alive, dead),
                            List.of(alive),
                            List.of(alive, other, monster),
                            List.of(monster),
                            List.of(),
                            other);
        }
    }

    @Nested
    class EmptyIds {
        @Test
        void getNpcs_shouldMatchNothing_whenTheIdArrayIsEmpty() {
            final Npc npc = npc(7, 100);

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                instance.addNpc(npc);
                return List.of(
                        world.getNpcs(new int[0]),
                        world.getAliveNpcs(new int[0]),
                        world.getNpcs(Npc.class, new int[0]),
                        world.getAliveNpcs(Npc.class, new int[0]),
                        world.getNpcs(Npc.class),
                        world.getAliveNpcs(Npc.class),
                        world.getAliveNpcs());
            });

            assertThat(actual)
                    .containsExactly(
                            List.of(), List.of(), List.of(), List.of(), List.of(npc), List.of(npc), List.of(npc));
        }
    }

    @Nested
    class Status {
        @Test
        void incStatus_shouldAdvanceTheStatusFromZero() {
            final List<Object> actual = WorldLoopExtension.callInPass(
                    () -> List.of(world.incStatus(), world.incStatus(), world.isStatus(2), world.getStatus()));

            assertThat(actual).containsExactly(1, 2, true, 2);
        }
    }

    @Nested
    class BroadcastPacket {
        @Test
        void broadcastPacket_shouldReachOnlyThePlayersStillInside() {
            final Player inside = player(0x7ffe_2021);
            when(inside.getInstanceId()).thenReturn(INSTANCE_ID);
            final Player left = player(0x7ffe_2022);
            final ServerPacket packet = mock(ServerPacket.class);

            final int actual = WorldLoopExtension.callInPass(() -> {
                World.addObject(inside);
                World.addObject(left);
                try {
                    instance.addPlayer(inside.getObjectId());
                    instance.addPlayer(left.getObjectId());
                    world.broadcastPacket(packet);
                    return world.getPlayersCount();
                } finally {
                    World.removeObject(inside);
                    World.removeObject(left);
                }
            });

            assertThat(actual).isEqualTo(2);
            verify(inside).sendPacket(packet);
            verify(left, never()).sendPacket(packet);
        }
    }
}
