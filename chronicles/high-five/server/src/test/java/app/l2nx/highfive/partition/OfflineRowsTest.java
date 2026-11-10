/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.engine.core.identity.IdentityRegistry;
import app.l2nx.engine.core.identity.WireHandleAllocator;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.IdManager;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
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
class OfflineRowsTest {
    @RegisterExtension
    static final WorldLoopExtension WORLD = WorldLoopExtension.perTest();

    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));
    private final UUID characterId = EntityIds.next();
    private final Player player = mock(Player.class);
    private int handle;

    @BeforeEach
    void setUp() {
        new IdManager(registry);
        handle = registry.handleOf(characterId);
    }

    @Nested
    class WhenSettled {
        @Test
        void whenSettled_shouldDecideAtOnce_whenTheCharacterIsNotBusy() {
            final List<String> ran = new ArrayList<>();

            WorldLoopExtension.runInPass(() -> OfflineRows.whenSettled(handle, () -> ran.add("decided")));

            assertThat(ran).containsExactly("decided");
        }

        @Test
        void whenSettled_shouldDecideAfterWorldEntry_whenTheCharacterIsLoading() {
            final List<String> ran = new ArrayList<>();
            WorldLoopExtension.runInPass(() -> {
                WorldPartition.loadedCharacters().startLoading(characterId);
                OfflineRows.whenSettled(handle, () -> ran.add("decided"));
                WorldPartition.loadedCharacters().loaded(characterId, player);
                WorldPartition.loadedCharacters().loadEnded(characterId);
            });
            assertThat(ran).isEmpty();

            WorldLoopExtension.runInPass(() -> WorldPartition.loadedCharacters().entered(characterId, player));

            assertThat(ran).containsExactly("decided");
        }
    }

    @Nested
    class IsLoaded {
        @Test
        void isLoaded_shouldBeTrue_whileTheCharacterIsLoadingOrBuilt() {
            WorldLoopExtension.runInPass(() -> WorldPartition.loadedCharacters().startLoading(characterId));
            assertThat(WorldLoopExtension.callInPass(() -> OfflineRows.isLoaded(handle)))
                    .as("loading")
                    .isTrue();

            WorldLoopExtension.runInPass(() -> {
                WorldPartition.loadedCharacters().loaded(characterId, player);
                WorldPartition.loadedCharacters().loadEnded(characterId);
                WorldPartition.loadedCharacters().entered(characterId, player);
            });
            assertThat(WorldLoopExtension.callInPass(() -> OfflineRows.isLoaded(handle)))
                    .as("in memory, settled")
                    .isTrue();

            WorldLoopExtension.runInPass(() -> WorldPartition.loadedCharacters().release(characterId, player));
            assertThat(WorldLoopExtension.callInPass(() -> OfflineRows.isLoaded(handle)))
                    .as("released")
                    .isFalse();
        }
    }
}
