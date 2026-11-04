/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.managers;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.entity.zone.AbstractZoneSettings;
import app.l2nx.highfive.entity.zone.ZoneType;
import app.l2nx.highfive.entity.zone.type.OlympiadStadiumZone;
import app.l2nx.highfive.entity.zone.type.SiegeZone;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class ZoneManagerTest {
    private static OlympiadStadiumZone stadium(int id, String name) {
        final OlympiadStadiumZone zone = new OlympiadStadiumZone(id);
        zone.setName(name);
        return zone;
    }

    private static SiegeZone siege(int id, String name) {
        final SiegeZone zone = new SiegeZone(id);
        zone.setName(name);
        return zone;
    }

    private static Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced(ZoneType... zones) {
        final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced = new HashMap<>();
        for (ZoneType zone : zones) {
            ZoneManager.rememberReplaced(zone, replaced);
        }

        return replaced;
    }

    @Nested
    class RestoreSettings {
        @Test
        void restoreSettings_shouldGiveEachZoneItsOwnSettings_whenNamesRepeat() {
            final List<AbstractZoneSettings> actual = WorldLoopExtension.callInPass(() -> {
                final OlympiadStadiumZone firstBefore = stadium(300_001, "Orbis Arena");
                final OlympiadStadiumZone secondBefore = stadium(300_002, "Orbis Arena");
                final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced =
                        replaced(firstBefore, secondBefore);
                final OlympiadStadiumZone first = stadium(300_001, "Orbis Arena");
                final OlympiadStadiumZone second = stadium(300_002, "Orbis Arena");

                ZoneManager.restoreSettings(first, replaced);
                ZoneManager.restoreSettings(second, replaced);

                return List.of(
                        firstBefore.getSettings(),
                        secondBefore.getSettings(),
                        first.getSettings(),
                        second.getSettings());
            });

            assertThat(actual.get(2)).isSameAs(actual.get(0));
            assertThat(actual.get(3)).isSameAs(actual.get(1)).isNotSameAs(actual.get(0));
        }

        @Test
        void restoreSettings_shouldGiveEachTypeItsOwnSettings_whenTwoTypesShareAnId() {
            final List<AbstractZoneSettings> actual = WorldLoopExtension.callInPass(() -> {
                final OlympiadStadiumZone stadiumBefore = stadium(300_001, "Arena");
                final SiegeZone siegeBefore = siege(300_001, "Arena");
                final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced =
                        replaced(stadiumBefore, siegeBefore);
                final OlympiadStadiumZone stadium = stadium(300_001, "Arena");
                final SiegeZone siege = siege(300_001, "Arena");

                ZoneManager.restoreSettings(stadium, replaced);
                ZoneManager.restoreSettings(siege, replaced);

                return List.of(
                        stadiumBefore.getSettings(),
                        siegeBefore.getSettings(),
                        stadium.getSettings(),
                        siege.getSettings());
            });

            assertThat(actual.get(2)).isSameAs(actual.get(0));
            assertThat(actual.get(3)).isSameAs(actual.get(1));
        }

        @Test
        void restoreSettings_shouldKeepFreshSettings_whenTheIdNowHoldsAnotherZone() {
            final List<Boolean> actual = WorldLoopExtension.callInPass(() -> {
                final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced =
                        replaced(stadium(300_001, "Orbis Arena"), stadium(300_002, "Orbis Arena"));
                final OlympiadStadiumZone renamed = stadium(300_001, "Grassy Arena");
                final SiegeZone otherType = siege(300_002, "Orbis Arena");
                final AbstractZoneSettings renamedFresh = renamed.getSettings();
                final AbstractZoneSettings otherTypeFresh = otherType.getSettings();

                ZoneManager.restoreSettings(renamed, replaced);
                ZoneManager.restoreSettings(otherType, replaced);

                return List.of(renamed.getSettings() == renamedFresh, otherType.getSettings() == otherTypeFresh);
            });

            assertThat(actual).containsExactly(true, true);
        }

        @Test
        void restoreSettings_shouldKeepTheFreshSiegeObjectId_whenTheDatapackChangedTheId() {
            final int actual = WorldLoopExtension.callInPass(() -> {
                final SiegeZone before = siege(300_001, "Giran Castle");
                before.setParameter("castleId", "3");
                final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced = replaced(before);
                final SiegeZone reloaded = siege(300_001, "Giran Castle");
                reloaded.setParameter("castleId", "5");

                ZoneManager.restoreSettings(reloaded, replaced);

                return reloaded.getSiegeObjectId();
            });

            assertThat(actual).isEqualTo(5);
        }

        @Test
        void restoreSettings_shouldKeepFreshSettings_whenTheDatapackGaveTheZoneToAnotherResidence() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final SiegeZone before = siege(300_001, "Giran Castle");
                before.setParameter("castleId", "3");
                final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced = replaced(before);
                final SiegeZone reloaded = siege(300_001, "Giran Castle");
                reloaded.setParameter("castleId", "5");

                ZoneManager.restoreSettings(reloaded, replaced);

                return reloaded.getSettings() == before.getSettings();
            });

            assertThat(actual).isFalse();
        }

        @Test
        void restoreSettings_shouldCarryTheSettingsOver_whenTheResidenceStays() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final SiegeZone before = siege(300_001, "Giran Castle");
                before.setParameter("castleId", "3");
                final Map<Class<? extends ZoneType>, Map<Integer, ZoneType>> replaced = replaced(before);
                final SiegeZone reloaded = siege(300_001, "Giran Castle");
                reloaded.setParameter("castleId", "3");

                ZoneManager.restoreSettings(reloaded, replaced);

                return reloaded.getSettings() == before.getSettings();
            });

            assertThat(actual).isTrue();
        }

        @Test
        void restoreSettings_shouldKeepFreshSettings_whenNoZoneWasReplaced() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                final OlympiadStadiumZone zone = stadium(300_001, "Orbis Arena");
                final AbstractZoneSettings fresh = zone.getSettings();

                ZoneManager.restoreSettings(zone, Map.of());

                return zone.getSettings() == fresh;
            });

            assertThat(actual).isTrue();
        }
    }
}
