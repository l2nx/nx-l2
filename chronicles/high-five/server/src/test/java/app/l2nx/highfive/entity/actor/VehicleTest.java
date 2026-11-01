/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.actor.instance.Boat;
import app.l2nx.highfive.entity.actor.templates.CreatureTemplate;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.util.StatSet;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
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

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class VehicleTest {
    @BeforeEach
    void ids() {
        new IdManager(new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5))));
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Vehicle> call) {
            final Vehicle boat = boatOnTheLoop();

            assertThatThrownBy(() -> call.accept(boat))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Vehicle>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getPassengers (accessor)", Vehicle::getPassengers),
                    Named.of("getDockId (accessor)", Vehicle::getDockId),
                    Named.of("addPassenger", boat -> boat.addPassenger(mock(Player.class))),
                    Named.of("oustPlayers", Vehicle::oustPlayers),
                    Named.of("isEmpty", Vehicle::isEmpty));
        }
    }

    @Nested
    class Passengers {
        @Test
        void getPassengers_shouldKeepBoardingOrder() {
            final Player first = mock(Player.class);
            final Player second = mock(Player.class);
            final Player third = mock(Player.class);

            final Collection<Player> actual = WorldLoopExtension.callInPass(() -> {
                final Vehicle boat = newBoat();
                boat.addPassenger(first);
                boat.addPassenger(second);
                boat.addPassenger(third);
                return boat.getPassengers();
            });

            assertThat(actual).containsExactly(first, second, third);
        }

        @Test
        void getPassengers_shouldNotFollowLaterBoarding() {
            final Player first = mock(Player.class);
            final Player second = mock(Player.class);

            final Collection<Player> actual = WorldLoopExtension.callInPass(() -> {
                final Vehicle boat = newBoat();
                boat.addPassenger(first);
                final Collection<Player> passengers = boat.getPassengers();
                boat.addPassenger(second);
                boat.removePassenger(first);
                return passengers;
            });

            assertThat(actual).containsExactly(first);
        }

        @Test
        void getPassengers_shouldRejectWrites() {
            final Collection<Player> actual =
                    WorldLoopExtension.callInPass(() -> newBoat().getPassengers());

            assertThatThrownBy(() -> actual.add(mock(Player.class))).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void addPassenger_shouldRefuse_whenAlreadyAboard() {
            final Player passenger = mock(Player.class);

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final Vehicle boat = newBoat();
                boat.addPassenger(passenger);
                return boat.addPassenger(passenger);
            });

            assertThat(actual).isFalse();
        }
    }

    @Nested
    class OustPlayers {
        // Each oust removes its passenger from the set the loop walks.
        @Test
        void oustPlayers_shouldOustEveryPassenger() {
            final Player first = mock(Player.class);
            final Player second = mock(Player.class);

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final Vehicle boat = newBoat();
                boat.setOustLoc(new Location(10, 20, 30));
                boat.addPassenger(first);
                boat.addPassenger(second);
                boat.oustPlayers();
                return boat.isEmpty();
            });

            assertThat(actual).isTrue();
            verify(first).setVehicle(null);
            verify(second).setVehicle(null);
        }
    }

    private static Vehicle boatOnTheLoop() {
        final Vehicle boat = WorldLoopExtension.callInPass(VehicleTest::newBoat);
        assertThat(boat).as("boat built on the loop").isNotNull();
        return boat;
    }

    private static Vehicle newBoat() {
        return new Boat(new CreatureTemplate(new StatSet()));
    }
}
