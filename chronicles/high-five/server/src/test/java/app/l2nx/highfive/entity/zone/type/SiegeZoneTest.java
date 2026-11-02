/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.zone.type;

import static app.l2nx.highfive.entity.zone.ZoneTestObjects.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.enums.player.TeleportWhereType;
import app.l2nx.highfive.entity.zone.ZoneTestObjects;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class SiegeZoneTest {
    private static SiegeZone zone() {
        return WorldLoopExtension.callInPass(() -> {
            final SiegeZone zone = new SiegeZone(70_501);
            zone.setZone(ZoneTestObjects.boxAroundOrigin());
            zone.setParameter("castleId", "3");
            return zone;
        });
    }

    @Nested
    class Settings {
        @Test
        void setParameter_shouldKeepTheSiegeObjectInTheSettings() {
            final SiegeZone zone = zone();

            final int actual = WorldLoopExtension.callInPass(zone::getSiegeObjectId);

            assertThat(actual).isEqualTo(3);
        }

        @Test
        void setParameter_shouldRefuseASecondSiegeObject() {
            final SiegeZone zone = zone();

            assertThatThrownBy(() -> WorldLoopExtension.runInPass(() -> zone.setParameter("fortId", "101")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void settingsClear_shouldResetTheSiegeState() {
            final SiegeZone zone = zone();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                zone.setActive(true);
                zone.getSettings().clear();
                return List.of(zone.isActive(), zone.getSiegeObjectId());
            });

            assertThat(actual).containsExactly(false, -1);
        }

        @Test
        void settingsClear_shouldThrow_whenCalledOffTheLoop() {
            final SiegeZone.Settings settings = WorldLoopExtension.callInPass(zone()::getSettings);

            assertThatThrownBy(settings::clear)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }
    }

    @Nested
    class BanishForeigners {
        @Test
        void banishForeigners_shouldTeleportOnlyPlayersOfOtherClans() {
            final SiegeZone zone = zone();
            final Player owner = player(268_600_001);
            final Player foreigner = player(268_600_002);
            when(owner.getClanId()).thenReturn(5);
            when(foreigner.getClanId()).thenReturn(6);

            WorldLoopExtension.runInPass(() -> {
                zone.revalidateInZone(owner);
                zone.revalidateInZone(foreigner);
                zone.banishForeigners(5);
            });

            verify(foreigner).teleToLocation(TeleportWhereType.TOWN);
            verify(owner, never()).teleToLocation(TeleportWhereType.TOWN);
        }
    }
}
