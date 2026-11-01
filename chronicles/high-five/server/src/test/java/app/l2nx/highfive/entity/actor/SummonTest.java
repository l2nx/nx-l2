/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.clock.ClockBridgeExtension;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.WorldObject;
import app.l2nx.highfive.geoengine.GeoEngine;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
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
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class SummonTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Summon> call) {
            final Summon summon = summonOnTheLoop(mock(Player.class));

            assertThatThrownBy(() -> call.accept(summon)).isInstanceOf(IllegalStateException.class);
        }

        static Stream<Named<Consumer<Summon>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getOwner (accessor)", Summon::getOwner),
                    Named.of("getAttackRange (accessor)", Summon::getAttackRange),
                    Named.of("getPvpFlag (implements Playable)", Summon::getPvpFlag),
                    Named.of("switchMode (empty body)", Summon::switchMode),
                    Named.of("Playable.setLockedTarget (accessor)", summon -> summon.setLockedTarget(null)),
                    Named.of("Playable.isLockedTarget", Summon::isLockedTarget),
                    Named.of("Playable.checkIfPvP", summon -> summon.checkIfPvP(null)));
        }

        @Test
        void getOwner_shouldAnswer_whenCalledOnTheLoop() {
            final Player expected = mock(Player.class);
            final Summon summon = summonOnTheLoop(expected);
            final AtomicReference<Player> actual = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> actual.set(summon.getOwner()));

            assertThat(actual.get()).isSameAs(expected);
        }
    }

    @Nested
    class LockedTarget {
        @Test
        void setLockedTarget_shouldBeReadBack() {
            final AtomicReference<Creature> actual = new AtomicReference<>();
            final AtomicReference<Creature> expected = new AtomicReference<>();

            WorldLoopExtension.runInPass(() -> {
                final Summon summon = newSummon(mock(Player.class));
                expected.set(new TestAttackable());
                summon.setLockedTarget(expected.get());
                actual.set(summon.getLockedTarget());
            });

            assertThat(actual.get()).isSameAs(expected.get());
        }
    }

    @Nested
    class ToString {
        @Test
        void toString_shouldNameTheSummonAndItsOwner_whenCalledOffTheLoop() {
            final Player owner = mock(Player.class, "owner");
            final Summon summon = summonOnTheLoop(owner);

            final String actual = summon.toString();

            assertThat(actual).startsWith("TestSummon:").contains("(" + TestAttackable.NPC_ID + ") Owner: owner");
        }
    }

    private static Summon summonOnTheLoop(Player owner) {
        final AtomicReference<Summon> summon = new AtomicReference<>();
        WorldLoopExtension.runInPass(() -> summon.set(newSummon(owner)));
        assertThat(summon.get()).as("summon built on the loop").isNotNull();
        return summon.get();
    }

    // Static mocks are thread-local, so the geodata stub is opened on the loop thread that builds the summon.
    private static Summon newSummon(Player owner) {
        final GeoEngine geoEngine = mock(GeoEngine.class);
        when(geoEngine.getValidLocation(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new Location(0, 0, 0));
        try (MockedStatic<GeoEngine> geo = mockStatic(GeoEngine.class)) {
            geo.when(GeoEngine::getInstance).thenReturn(geoEngine);
            return new TestSummon(owner);
        }
    }

    private static final class TestSummon extends Summon {
        private TestSummon(Player owner) {
            super(TestAttackable.template(), owner);
        }

        @Override
        public int getSummonType() {
            return 1;
        }

        @Override
        public int getLevel() {
            return 1;
        }

        @Override
        public void doPickupItem(WorldObject object) {}

        @Override
        public void storeMe() {}

        @Override
        public void storeEffect(boolean storeEffects) {}

        @Override
        public void restoreEffects() {}
    }
}
