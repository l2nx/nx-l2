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
class GuardTest {
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
                    call("Guard.isAutoAttackable", GuardTest::guard, guard -> guard.isAutoAttackable(null)),
                    call("Guard.getHtmlPath", GuardTest::guard, guard -> guard.getHtmlPath(TestNpcs.NPC_ID, 0)),
                    call(
                            "QuestGuard.setAutoAttackable (accessor)",
                            GuardTest::questGuard,
                            guard -> guard.setAutoAttackable(false)),
                    call("QuestGuard.isPassive (accessor)", GuardTest::questGuard, QuestGuard::isPassive),
                    call("QuestGuard.setPassive (accessor)", GuardTest::questGuard, guard -> guard.setPassive(true)),
                    call("FriendlyMob.isAutoAttackable", GuardTest::friendlyMob, mob -> mob.isAutoAttackable(null)),
                    call("Defender.hasRandomAnimation", GuardTest::defender, Defender::hasRandomAnimation),
                    call(
                            "FortCommander.hasRandomAnimation",
                            GuardTest::fortCommander,
                            FortCommander::hasRandomAnimation),
                    call(
                            "TerritoryWard.hasRandomAnimation",
                            GuardTest::territoryWard,
                            TerritoryWard::hasRandomAnimation));
        }
    }

    @Nested
    class QuestGuardAutoAttackable {
        @Test
        void setAutoAttackable_shouldDecideIsAutoAttackable_whenTheAttackerIsNoPlayer() {
            final List<Boolean> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final QuestGuard guard = questGuard();
                final Monster attacker = new Monster(TestNpcs.template());
                actual.add(guard.isAutoAttackable(attacker));
                guard.setAutoAttackable(false);
                actual.add(guard.isAutoAttackable(attacker));
            });

            assertThat(actual).containsExactly(true, false);
        }
    }

    private static Guard guard() {
        return new Guard(TestNpcs.template());
    }

    private static QuestGuard questGuard() {
        return new QuestGuard(TestNpcs.template());
    }

    private static FriendlyMob friendlyMob() {
        return new FriendlyMob(TestNpcs.template());
    }

    private static Defender defender() {
        return new Defender(TestNpcs.template());
    }

    private static FortCommander fortCommander() {
        return new FortCommander(TestNpcs.template());
    }

    private static TerritoryWard territoryWard() {
        return new TerritoryWard(TestNpcs.template());
    }
}
