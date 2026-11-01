/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.instance;

import static app.l2nx.highfive.entity.actor.instance.TestNpcs.call;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.actor.enums.npc.RaidBossStatus;
import app.l2nx.highfive.entity.actor.holders.npc.MinionList;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
class MonsterTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(TestNpcs.OffLoopCall call) {
            final Runnable offLoop = call.prepare();

            assertThatThrownBy(offLoop::run)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<TestNpcs.OffLoopCall>> ownerCheckedCalls() {
            return Stream.of(
                    call("Monster.getLeader (accessor)", MonsterTest::monster, Monster::getLeader),
                    call("Monster.setLeader (accessor)", MonsterTest::monster, monster -> monster.setLeader(null)),
                    call("Monster.getMinionList (lazy init)", MonsterTest::monster, Monster::getMinionList),
                    call("Monster.hasMinions", MonsterTest::monster, Monster::hasMinions),
                    call("Monster.enableMinions", MonsterTest::monster, monster -> monster.enableMinions(false)),
                    call("Monster.onTeleported (was synchronized)", MonsterTest::monster, Monster::onTeleported),
                    call("RaidBoss.getRaidStatus (accessor)", MonsterTest::raidBoss, RaidBoss::getRaidStatus),
                    call(
                            "RaidBoss.setRaidStatus (accessor)",
                            MonsterTest::raidBoss,
                            boss -> boss.setRaidStatus(RaidBossStatus.ALIVE)),
                    call(
                            "RaidBoss.setUseRaidCurse (accessor)",
                            MonsterTest::raidBoss,
                            boss -> boss.setUseRaidCurse(false)),
                    call("RaidBoss.giveRaidCurse", MonsterTest::raidBoss, RaidBoss::giveRaidCurse),
                    call(
                            "GrandBoss.setUseRaidCurse (accessor)",
                            MonsterTest::grandBoss,
                            boss -> boss.setUseRaidCurse(false)),
                    call("GrandBoss.giveRaidCurse", MonsterTest::grandBoss, GrandBoss::giveRaidCurse),
                    call(
                            "EventMonster.eventSetDropOnGround",
                            MonsterTest::eventMonster,
                            monster -> monster.eventSetDropOnGround(true)),
                    call("EventMonster.eventDropOnGround", MonsterTest::eventMonster, EventMonster::eventDropOnGround),
                    call(
                            "FestivalMonster.setOfferingBonus",
                            MonsterTest::festivalMonster,
                            monster -> monster.setOfferingBonus(2)));
        }

        @Test
        void getLeader_shouldAnswer_whenCalledOnTheLoop() {
            final Monster monster = TestNpcs.builtOnTheLoop(MonsterTest::monster);

            final Monster actual = WorldLoopExtension.callInPass(monster::getLeader);

            assertThat(actual).isNull();
        }
    }

    @Nested
    class Minions {
        @Test
        void getMinionList_shouldCreateTheListOnce() {
            final List<MinionList> actual = new ArrayList<>();
            final List<Boolean> hasMinions = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final Monster master = monster();
                hasMinions.add(master.hasMinions());
                actual.add(master.getMinionList());
                actual.add(master.getMinionList());
                hasMinions.add(master.hasMinions());
            });

            assertThat(actual.get(0)).isNotNull().isSameAs(actual.get(1));
            assertThat(hasMinions).containsExactly(false, true);
        }
    }

    private static Monster monster() {
        return new Monster(TestNpcs.template());
    }

    private static RaidBoss raidBoss() {
        return new RaidBoss(TestNpcs.template());
    }

    private static GrandBoss grandBoss() {
        return new GrandBoss(TestNpcs.template());
    }

    private static EventMonster eventMonster() {
        return new EventMonster(TestNpcs.template());
    }

    private static FestivalMonster festivalMonster() {
        return new FestivalMonster(TestNpcs.template());
    }
}
