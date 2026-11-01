/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.partition.WorldLoopExtension;
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
class AutoPlaySettingsHolderTest {
    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<AutoPlaySettingsHolder> call) {
            final AutoPlaySettingsHolder settings = new AutoPlaySettingsHolder();

            assertThatThrownBy(() -> call.accept(settings))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<AutoPlaySettingsHolder>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("getOptions", AutoPlaySettingsHolder::getOptions),
                    Named.of("setOptions", settings -> settings.setOptions(1)),
                    Named.of("doPickup", AutoPlaySettingsHolder::doPickup),
                    Named.of("setPickup", settings -> settings.setPickup(false)),
                    Named.of("getNextTargetMode", AutoPlaySettingsHolder::getNextTargetMode),
                    Named.of("setNextTargetMode", settings -> settings.setNextTargetMode(2)),
                    Named.of("isShortRange", AutoPlaySettingsHolder::isShortRange),
                    Named.of("setShortRange", settings -> settings.setShortRange(true)),
                    Named.of("isRespectfulHunting", AutoPlaySettingsHolder::isRespectfulHunting),
                    Named.of("setRespectfulHunting", settings -> settings.setRespectfulHunting(false)),
                    Named.of("getAutoPotionPercent", AutoPlaySettingsHolder::getAutoPotionPercent),
                    Named.of("setAutoPotionPercent", settings -> settings.setAutoPotionPercent(10)));
        }
    }

    @Nested
    class Settings {
        @Test
        void setters_shouldReplaceTheDefaults() {
            final AutoPlaySettingsHolder settings = new AutoPlaySettingsHolder();

            final List<Object> actual = WorldLoopExtension.callInPass(() -> {
                settings.setOptions(7);
                settings.setPickup(false);
                settings.setNextTargetMode(3);
                settings.setShortRange(true);
                settings.setRespectfulHunting(false);
                settings.setAutoPotionPercent(30);
                return List.of(
                        settings.getOptions(),
                        settings.doPickup(),
                        settings.getNextTargetMode(),
                        settings.isShortRange(),
                        settings.isRespectfulHunting(),
                        settings.getAutoPotionPercent());
            });

            assertThat(actual).containsExactly(7, false, 3, true, false, 30);
        }
    }
}
