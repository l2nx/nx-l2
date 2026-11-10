/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.entity.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.managers.IdManager;
import app.l2nx.highfive.partition.LoadedCharacters;
import app.l2nx.highfive.partition.WorldLoopExtension;
import app.l2nx.highfive.partition.WorldPartition;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(WorldLoopExtension.LOCK)
class PlayerWorldEntryTest {
    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final UUID characterId = EntityIds.next();
    private final Player player = mock(Player.class);

    @BeforeEach
    void setUp() {
        new IdManager(registry);
        when(player.getObjectId()).thenReturn(registry.handleOf(characterId));
        when(player.isPlayer()).thenReturn(true);
        when(player.asPlayer()).thenReturn(player);
        when(player.getClient()).thenThrow(new IllegalStateException("broken entry step"));
        WorldLoopExtension.runInPass(() -> {
            final LoadedCharacters loaded = WorldPartition.loadedCharacters();
            loaded.startLoading(characterId);
            loaded.loaded(characterId, player);
            loaded.loadEnded(characterId);
        });
    }

    @Nested
    class Enter {
        @Test
        void enter_shouldSettleTheCharacterAndDropItsRows_whenAStepFailsWithThePlayerInTheWorld() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                World.addObject(player);
                try {
                    assertThatThrownBy(() -> PlayerWorldEntry.enter(player, List.of()))
                            .hasMessage("broken entry step");
                    return WorldPartition.loadedCharacters().isBusy(characterId);
                } finally {
                    World.removeObject(player);
                }
            });

            assertThat(actual).as("busy").isFalse();
            verify(player).clearStoredWorldEntry();
        }

        @Test
        void enter_shouldKeepTheCharacterBusyAndDropItsRows_whenAStepFailsBeforeTheSpawn() {
            final boolean actual = WorldLoopExtension.callInPass(() -> {
                assertThatThrownBy(() -> PlayerWorldEntry.enter(player, List.of()))
                        .hasMessage("broken entry step");
                return WorldPartition.loadedCharacters().isBusy(characterId);
            });

            assertThat(actual).as("busy until the logout releases it").isTrue();
            verify(player).clearStoredWorldEntry();
        }
    }
}
