/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.loop.Loop;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@ExtendWith(WorldLoopExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class OnlinePlayersGaugeTest {
    private static final String NAME = "l2nx.players.online";
    private static final int PLAYER_ID = 0x7ffe_0002;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OnlinePlayersGauge gauge = new OnlinePlayersGauge(registry);
    private final Player player = mock(Player.class);

    @BeforeEach
    void preparePlayer() {
        when(player.getObjectId()).thenReturn(PLAYER_ID);
        when(player.isPlayer()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
    }

    @AfterEach
    void leaveWorld() {
        WorldLoopExtension.runInPass(() -> World.removeObject(player));
    }

    private double actual() {
        return registry.get(NAME).gauge().value();
    }

    @Nested
    class Construct {
        @Test
        void constructor_shouldRegisterAGaugeReadingZero_beforeAnyPass() {
            assertThat(actual()).isZero();
        }
    }

    @Nested
    class Install {
        @Test
        void install_shouldPublishThePlayerCount_inTheFlushOfEveryPass() {
            final AtomicInteger before = new AtomicInteger();
            WorldLoopExtension.runInPass(() -> {
                gauge.install(Loop.current());
                before.set(World.getPlayers().size());
            });
            assertThat(actual()).as("published by the installing pass").isEqualTo(before.get());

            final AtomicReference<Double> beforeFlush = new AtomicReference<>();
            WorldLoopExtension.runInPass(() -> {
                World.addObject(player);
                beforeFlush.set(actual());
            });
            assertThat(beforeFlush.get())
                    .as("within the pass, before its flush")
                    .isEqualTo(before.get());
            assertThat(actual()).as("after the pass").isEqualTo(before.get() + 1);
        }
    }
}
