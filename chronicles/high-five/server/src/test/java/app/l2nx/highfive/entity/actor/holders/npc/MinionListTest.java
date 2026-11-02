/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.npc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.instance.Monster;
import app.l2nx.highfive.entity.actor.templates.NpcTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
class MinionListTest {
    private static final int MASTER_ID = 20_545;
    private static final int MINION_ID = 20_546;

    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    private static Monster monster(int npcId) {
        final NpcTemplate template = new NpcTemplate(new StatSet(Map.of("id", npcId, "baseHpMax", 1_000)));
        template.setSkills(Map.of());
        template.setParameters(new StatSet());
        return new Monster(template);
    }

    private static MinionList minionList() {
        return WorldLoopExtension.callInPass(() -> new MinionList(monster(MASTER_ID)));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<MinionList> call) {
            final MinionList minions = minionList();

            assertThatThrownBy(() -> call.accept(minions))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<MinionList>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getSpawnedMinions", MinionList::getSpawnedMinions),
                    Named.of("getSpawnedMinionCount", MinionList::getSpawnedMinionCount),
                    Named.of("spawnMinions", minions -> minions.spawnMinions(List.of())),
                    Named.of("onMinionSpawn", minions -> minions.onMinionSpawn(null)),
                    Named.of("onMasterDie", minions -> minions.onMasterDie(true)),
                    Named.of("onMinionDie", minions -> minions.onMinionDie(null, 0)),
                    Named.of("onAssist", minions -> minions.onAssist(null, null)),
                    Named.of("onMasterTeleported", MinionList::onMasterTeleported),
                    Named.of("spawnMinion (static)", _ -> MinionList.spawnMinion(null, MINION_ID)));
        }
    }

    @Nested
    class SpawnedMinions {
        @Test
        void getSpawnedMinions_shouldReturnAReadOnlySnapshotInSpawnOrder() {
            final MinionList minions = minionList();
            final Monster first = WorldLoopExtension.callInPass(() -> monster(MINION_ID));
            final Monster second = WorldLoopExtension.callInPass(() -> monster(MINION_ID));

            final Collection<Monster> actual = WorldLoopExtension.callInPass(() -> {
                minions.onMinionSpawn(second);
                minions.onMinionSpawn(first);
                minions.onMinionSpawn(second);
                final Collection<Monster> snapshot = minions.getSpawnedMinions();
                minions.onMinionSpawn(monster(MINION_ID));
                return snapshot;
            });

            assertThat(actual).containsExactly(second, first);
            assertThat(WorldLoopExtension.callInPass(minions::getSpawnedMinionCount))
                    .isEqualTo(3);
            assertThatThrownBy(() -> actual.add(first)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void onMinionDie_shouldDropTheMinion_whenItDoesNotRespawn() {
            final MinionList minions = minionList();

            final List<Integer> actual = WorldLoopExtension.callInPass(() -> {
                final Monster minion = monster(MINION_ID);
                minions.onMinionSpawn(minion);
                final int before = minions.getSpawnedMinionCount();
                minions.onMinionDie(minion, 0);
                return List.of(before, minions.getSpawnedMinionCount());
            });

            assertThat(actual).containsExactly(1, 0);
        }
    }
}
