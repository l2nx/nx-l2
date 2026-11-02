/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone;

import static app.l2nx.highfive.entity.zone.ZoneTestObjects.creature;
import static app.l2nx.highfive.entity.zone.ZoneTestObjects.moveTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Creature;
import app.l2nx.highfive.entity.zone.ZoneTestObjects.RecordingZone;
import app.l2nx.highfive.entity.zone.form.ZoneCuboid;
import app.l2nx.highfive.entity.zone.type.PeaceZone;
import app.l2nx.highfive.mechanics.skill.Skill;
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
class ZoneRegionTest {
    private final Creature creature = creature(268_400_001);

    private static ZoneRegion region(ZoneType... zones) {
        return WorldLoopExtension.callInPass(() -> {
            final ZoneRegion region = new ZoneRegion(3, 4);
            for (ZoneType zone : zones) {
                region.addZone(zone);
            }

            return region;
        });
    }

    private static RecordingZone recording(int id) {
        return WorldLoopExtension.callInPass(() -> new RecordingZone(id));
    }

    private static PeaceZone peace(int id, ZoneCuboid shape) {
        return WorldLoopExtension.callInPass(() -> {
            final PeaceZone zone = new PeaceZone(id);
            zone.setZone(shape);
            return zone;
        });
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<ZoneRegion> call) {
            final ZoneRegion region = region(recording(70_101));

            assertThatThrownBy(() -> call.accept(region))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<ZoneRegion>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getRegionX (accessor)", ZoneRegion::getRegionX),
                    Named.of("getRegionY (accessor)", ZoneRegion::getRegionY),
                    Named.of("addZone", r -> r.addZone(null)),
                    Named.of("clearZones", ZoneRegion::clearZones),
                    Named.of("containsZone", r -> r.containsZone(70_101)),
                    Named.of("collectZones(x, y)", r -> r.collectZones(0, 0, new ArrayList<>())),
                    Named.of("collectZones(x, y, z)", r -> r.collectZones(0, 0, 0, new ArrayList<>())),
                    Named.of("getZone", r -> r.getZone(0, 0, 0, ZoneType.class)),
                    Named.of("revalidateZones", r -> r.revalidateZones(creature(1))),
                    Named.of("removeFromZones", r -> r.removeFromZones(creature(1))),
                    Named.of(
                            "checkEffectRangeInsidePeaceZone",
                            r -> r.checkEffectRangeInsidePeaceZone(mock(Skill.class), 0, 0, 0)),
                    Named.of("onDeath", r -> r.onDeath(creature(1))),
                    Named.of("onRevive", r -> r.onRevive(creature(1))));
        }
    }

    @Nested
    class AddZone {
        @Test
        void addZone_shouldReplaceTheZoneOfTheSameIdInItsPlace() {
            final RecordingZone first = recording(70_101);
            final RecordingZone second = recording(70_102);
            final RecordingZone replacement = recording(70_101);
            final ZoneRegion region = region(first, second, replacement);

            final List<ZoneType> actual = WorldLoopExtension.callInPass(() -> {
                final List<ZoneType> into = new ArrayList<>();
                region.collectZones(0, 0, 0, into);
                return into;
            });

            assertThat(actual).containsExactly(replacement, second);
        }

        @Test
        void containsZone_shouldAnswerByZoneId() {
            final ZoneRegion region = region(recording(70_101));

            final List<Boolean> actual = WorldLoopExtension.callInPass(
                    () -> List.of(region.containsZone(70_101), region.containsZone(70_102)));

            assertThat(actual).containsExactly(true, false);
        }

        @Test
        void clearZones_shouldDropEveryZone() {
            final ZoneRegion region = region(recording(70_101));

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                region.clearZones();
                return region.containsZone(70_101);
            });

            assertThat(actual).isFalse();
        }
    }

    @Nested
    class CollectZones {
        @Test
        void collectZones_shouldAddTheZonesHoldingThePointInLoadOrder() {
            final PeaceZone far = peace(70_103, new ZoneCuboid(1000, 2000, 1000, 2000, -100, 100));
            final RecordingZone first = recording(70_102);
            final RecordingZone second = recording(70_101);
            final ZoneRegion region = region(first, far, second);

            final List<ZoneType> actual = WorldLoopExtension.callInPass(() -> {
                final List<ZoneType> into = new ArrayList<>();
                region.collectZones(0, 0, 0, into);
                return into;
            });

            assertThat(actual).containsExactly(first, second);
        }

        @Test
        void collectZones_shouldIgnoreHeight_whenGivenAPointOfThePlane() {
            final RecordingZone zone = recording(70_101);
            final ZoneRegion region = region(zone);

            final List<List<ZoneType>> actual = WorldLoopExtension.callInPass(() -> {
                final List<ZoneType> plane = new ArrayList<>();
                region.collectZones(0, 0, plane);
                final List<ZoneType> above = new ArrayList<>();
                region.collectZones(0, 0, 5000, above);
                return List.of(plane, above);
            });

            assertThat(actual.get(0)).containsExactly(zone);
            assertThat(actual.get(1)).isEmpty();
        }

        @Test
        void getZone_shouldReturnTheFirstZoneOfTheTypeHoldingThePoint() {
            final RecordingZone recording = recording(70_101);
            final PeaceZone peace = peace(70_102, ZoneTestObjects.boxAroundOrigin());
            final ZoneRegion region = region(recording, peace);

            final List<ZoneType> actual = WorldLoopExtension.callInPass(() -> {
                final List<ZoneType> found = new ArrayList<>();
                found.add(region.getZone(0, 0, 0, PeaceZone.class));
                found.add(region.getZone(0, 0, 0, ZoneType.class));
                found.add(region.getZone(5000, 0, 0, PeaceZone.class));
                return found;
            });

            assertThat(actual).containsExactly(peace, recording, null);
        }
    }

    @Nested
    class RevalidateZones {
        @Test
        void revalidateZones_shouldEnterEveryZoneHoldingTheCreature() {
            final RecordingZone first = recording(70_101);
            final RecordingZone second = recording(70_102);
            final ZoneRegion region = region(first, second);

            WorldLoopExtension.runInPass(() -> region.revalidateZones(creature));

            assertThat(first.events).containsExactly("enter 268400001");
            assertThat(second.events).containsExactly("enter 268400001");
        }

        @Test
        void revalidateZones_shouldLeaveZonesAlone_whenTheCreatureIsTeleporting() {
            final RecordingZone zone = recording(70_101);
            final ZoneRegion region = region(zone);
            when(creature.isTeleporting()).thenReturn(true);

            WorldLoopExtension.runInPass(() -> region.revalidateZones(creature));

            assertThat(zone.events).isEmpty();
        }

        @Test
        void removeFromZones_shouldExitEveryZoneTheCreatureIsIn() {
            final RecordingZone first = recording(70_101);
            final RecordingZone second = recording(70_102);
            final ZoneRegion region = region(first, second);

            WorldLoopExtension.runInPass(() -> {
                region.revalidateZones(creature);
                region.removeFromZones(creature);
            });

            assertThat(first.events).containsExactly("enter 268400001", "exit 268400001");
            assertThat(second.events).containsExactly("enter 268400001", "exit 268400001");
        }

        @Test
        void revalidateZones_shouldExit_whenTheCreatureMovedOut() {
            final RecordingZone zone = recording(70_101);
            final ZoneRegion region = region(zone);

            WorldLoopExtension.runInPass(() -> {
                region.revalidateZones(creature);
                moveTo(creature, 500, 0, 0);
                region.revalidateZones(creature);
            });

            assertThat(zone.events).containsExactly("enter 268400001", "exit 268400001");
        }
    }

    @Nested
    class CheckEffectRangeInsidePeaceZone {
        @Test
        void checkEffectRangeInsidePeaceZone_shouldBeFalse_whenTheRangeReachesAPeaceZone() {
            final ZoneRegion region = region(peace(70_102, new ZoneCuboid(150, 300, -100, 100, -100, 100)));
            final Skill skill = mock(Skill.class);
            when(skill.getEffectRange()).thenReturn(200);

            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> List.of(
                    region.checkEffectRangeInsidePeaceZone(skill, 0, 0, 0),
                    region.checkEffectRangeInsidePeaceZone(skill, -1000, 0, 0)));

            assertThat(actual).containsExactly(false, true);
        }
    }
}
