/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor.holders.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.mechanics.variables.PlayerVariables;
import app.l2nx.highfive.partition.WorldLoopExtension;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
@ResourceLock("PlayerConfig")
class NevitTest {
    private final Player player = mock(Player.class);
    private final PlayerVariables variables = mock(PlayerVariables.class);
    private boolean nevitEnabled;
    private boolean ignoreAdventTime;

    @BeforeEach
    void configure() {
        nevitEnabled = PlayerConfig.NEVIT_ENABLED;
        ignoreAdventTime = PlayerConfig.NEVIT_IGNORE_ADVENT_TIME;
        PlayerConfig.NEVIT_ENABLED = false;
        PlayerConfig.NEVIT_IGNORE_ADVENT_TIME = true;
        when(player.getVariables()).thenReturn(variables);
    }

    @AfterEach
    void restoreConfig() {
        PlayerConfig.NEVIT_ENABLED = nevitEnabled;
        PlayerConfig.NEVIT_IGNORE_ADVENT_TIME = ignoreAdventTime;
    }

    @Nested
    class OwnershipCheck {
        @ParameterizedTest(name = "{0}")
        @MethodSource("ownerCheckedCalls")
        void ownerCheckedCall_shouldThrow_whenCalledOffTheLoop(Consumer<Nevit> call) {
            final Nevit nevit = new Nevit(player);

            assertThatThrownBy(() -> call.accept(nevit))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("touched from thread");
        }

        static Stream<Named<Consumer<Nevit>>> ownerCheckedCalls() {
            return Stream.of(
                    Named.of("addPoints", nevit -> nevit.addPoints(1)),
                    Named.of("startAdventTask", Nevit::startAdventTask),
                    Named.of("stopAdventTask", nevit -> nevit.stopAdventTask(false)),
                    Named.of("startNevitEffect", nevit -> nevit.startNevitEffect(60)),
                    Named.of("stopNevitEffectTask", nevit -> nevit.stopNevitEffectTask(false)),
                    Named.of("getObjectId", Nevit::getObjectId),
                    Named.of("isAdventBlessingActive", Nevit::isAdventBlessingActive),
                    Named.of("setAdventPoints", nevit -> nevit.setAdventPoints(1)),
                    Named.of("setAdventTime", nevit -> nevit.setAdventTime(1)),
                    Named.of("getAdventPoints", Nevit::getAdventPoints),
                    Named.of("getAdventTime", Nevit::getAdventTime));
        }
    }

    @Nested
    class Blessing {
        @Test
        void startNevitEffect_shouldActivateTheBlessing() {
            final Nevit nevit = new Nevit(player);

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                nevit.startNevitEffect(60);
                return nevit.isAdventBlessingActive();
            });

            assertThat(actual).isTrue();
        }

        @Test
        void startNevitEffect_shouldEndTheBlessing_whenItsTimerFires() {
            final Nevit nevit = new Nevit(player);

            WorldLoopExtension.runInPass(() -> nevit.startNevitEffect(60));
            WorldLoopExtension.passAfter(Duration.ofSeconds(61));
            final boolean actual = WorldLoopExtension.callInPass(nevit::isAdventBlessingActive);

            assertThat(actual).isFalse();
            verify(variables).remove("nevit_b");
        }

        @Test
        void stopNevitEffectTask_shouldEndTheBlessing() {
            final Nevit nevit = new Nevit(player);

            final boolean actual = WorldLoopExtension.callInPass(() -> {
                nevit.startNevitEffect(60);
                nevit.stopNevitEffectTask(false);
                return nevit.isAdventBlessingActive();
            });

            assertThat(actual).isFalse();
        }
    }
}
