/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.instance;

import static app.l2nx.highfive.entity.actor.instance.TestNpcs.call;
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
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.geoengine.GeoEngine;
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
import org.mockito.MockedStatic;

@ExtendWith({ClockBridgeExtension.class, WorldLoopExtension.class})
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(ClockBridgeExtension.LOCK)
@ResourceLock(WorldLoopExtension.LOCK)
class ServitorTest {
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
                    call("getExpMultiplier (accessor)", ServitorTest::servitor, Servitor::getExpMultiplier),
                    call("setExpMultiplier (accessor)", ServitorTest::servitor, s -> s.setExpMultiplier(1)),
                    call("getItemConsume (accessor)", ServitorTest::servitor, Servitor::getItemConsume),
                    call("setItemConsume (accessor)", ServitorTest::servitor, s -> s.setItemConsume(null)),
                    call("getLifeTime (accessor)", ServitorTest::servitor, Servitor::getLifeTime),
                    call("getLifeTimeRemaining (accessor)", ServitorTest::servitor, Servitor::getLifeTimeRemaining),
                    call("setLifeTimeRemaining (accessor)", ServitorTest::servitor, s -> s.setLifeTimeRemaining(1)),
                    call("getItemConsumeInterval (accessor)", ServitorTest::servitor, Servitor::getItemConsumeInterval),
                    call("getReferenceSkill (accessor)", ServitorTest::servitor, Servitor::getReferenceSkill),
                    call("setReferenceSkill (accessor)", ServitorTest::servitor, s -> s.setReferenceSkill(1)),
                    call("doPickupItem (empty body)", ServitorTest::servitor, s -> s.doPickupItem(null)),
                    call("run (life task)", ServitorTest::servitor, Servitor::run));
        }
    }

    @Nested
    class LifeTime {
        @Test
        void setLifeTime_shouldResetTheRemainingLifeTime() {
            final List<Integer> actual = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> {
                final Servitor servitor = servitor();
                servitor.setLifeTime(60_000);
                actual.add(servitor.getLifeTime());
                actual.add(servitor.getLifeTimeRemaining());
                servitor.setLifeTimeRemaining(30_000);
                actual.add(servitor.getLifeTimeRemaining());
            });

            assertThat(actual).containsExactly(60_000, 60_000, 30_000);
        }
    }

    // Static mocks are thread-local, so the geodata stub is opened on the loop thread that builds the servitor.
    private static Servitor servitor() {
        final GeoEngine geoEngine = mock(GeoEngine.class);
        when(geoEngine.getValidLocation(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(new Location(0, 0, 0));
        try (MockedStatic<GeoEngine> geo = mockStatic(GeoEngine.class)) {
            geo.when(GeoEngine::getInstance).thenReturn(geoEngine);
            return new Servitor(TestNpcs.template(), mock(Player.class));
        }
    }
}
