/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.l2nx.engine.core.identity.EntityIds;
import app.l2nx.highfive.entity.actor.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LoadedCharactersTest {
    private final LoadedCharacters loaded = new LoadedCharacters();
    private final UUID characterId = EntityIds.next();
    private final Player player = mock(Player.class);

    @Nested
    class Release {
        @Test
        void release_shouldForgetTheCharacter_whenItIsStillThisPlayer() {
            loaded.loaded(characterId, player);

            loaded.release(characterId, player);

            assertThat(loaded.find(characterId)).isEmpty();
        }

        @Test
        void release_shouldKeepALaterLoad_ofTheSameCharacter() {
            final Player reloaded = mock(Player.class);
            loaded.loaded(characterId, player);
            loaded.loaded(characterId, reloaded);

            loaded.release(characterId, player);

            assertThat(loaded.find(characterId)).containsSame(reloaded);
        }
    }

    @Nested
    class StartLoading {
        @Test
        void startLoading_shouldRefuse_whenTheCharacterIsLoadingAlready() {
            assertThat(loaded.startLoading(characterId)).isTrue();

            assertThat(loaded.startLoading(characterId)).isFalse();
        }
    }

    @Nested
    class WhenLoaded {
        @Test
        void whenLoaded_shouldRunAtOnce_whenNothingLoads() {
            final List<String> ran = new ArrayList<>();

            loaded.whenLoaded(characterId, () -> ran.add("select"));

            assertThat(ran).containsExactly("select");
        }

        @Test
        void whenLoaded_shouldWaitForTheLoadToEnd_whenTheCharacterIsLoading() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);

            loaded.whenLoaded(characterId, () -> ran.add("select"));
            assertThat(ran).isEmpty();
            loaded.loadEnded(characterId);

            assertThat(ran).containsExactly("select");
        }

        @Test
        void whenLoaded_shouldWaitForTheLoadToEnd_whenThePlayerIsBuilt() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);
            loaded.whenLoaded(characterId, () -> ran.add("select"));

            loaded.loaded(characterId, player);
            assertThat(ran)
                    .as("the built player has not reached its session yet")
                    .isEmpty();
            loaded.loadEnded(characterId);

            assertThat(ran).containsExactly("select");
        }

        @Test
        void loadEnded_shouldKeepTheNextSelectionWaiting_whenTheFirstOneStartsALoadAgain() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);
            loaded.whenLoaded(characterId, () -> {
                ran.add("first");
                loaded.startLoading(characterId);
            });
            loaded.whenLoaded(characterId, () -> ran.add("second"));
            loaded.whenLoaded(characterId, () -> ran.add("third"));

            loaded.loadEnded(characterId);
            assertThat(ran).as("the first selection's load runs").containsExactly("first");
            loaded.whenLoaded(characterId, () -> ran.add("later"));
            loaded.loadEnded(characterId);

            assertThat(ran).containsExactly("first", "second", "third", "later");
        }
    }

    @Nested
    class WhenSettled {
        @Test
        void whenSettled_shouldWaitUntilWorldEntry_whenThePlayerIsBuilt() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);

            loaded.whenSettled(characterId, () -> ran.add("write"));
            loaded.loaded(characterId, player);
            loaded.loadEnded(characterId);
            assertThat(ran).as("built, not yet in the world").isEmpty();
            loaded.entered(characterId, player);

            assertThat(ran).containsExactly("write");
        }

        @Test
        void whenSettled_shouldRunAfterTheRelease_whenThePlayerLeavesBeforeEnteringTheWorld() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);
            loaded.loaded(characterId, player);
            loaded.loadEnded(characterId);

            loaded.whenSettled(characterId, () -> ran.add("write"));
            loaded.release(characterId, player);

            assertThat(ran).containsExactly("write");
        }

        @Test
        void whenSettled_shouldRun_whenTheLoadEndsWithoutAPlayer() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);

            loaded.whenSettled(characterId, () -> ran.add("write"));
            loaded.loadEnded(characterId);

            assertThat(ran).containsExactly("write");
        }

        @Test
        void whenSettled_shouldRunTheRest_whenOneWaitingTaskFails() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);
            loaded.whenSettled(characterId, () -> {
                throw new IllegalStateException("broken writer");
            });
            loaded.whenSettled(characterId, () -> ran.add("write"));

            loaded.loadEnded(characterId);

            assertThat(ran).containsExactly("write");
        }

        @Test
        void whenSettled_shouldKeepTheRestWaiting_whenAWaitingTaskMakesTheCharacterBusyAgain() {
            final List<String> ran = new ArrayList<>();
            loaded.startLoading(characterId);
            loaded.whenSettled(characterId, () -> {
                ran.add("select");
                loaded.startLoading(characterId);
                loaded.whenSettled(characterId, () -> ran.add("queued by select"));
            });
            loaded.whenSettled(characterId, () -> ran.add("write"));

            loaded.loadEnded(characterId);
            assertThat(ran).as("busy again after the first task").containsExactly("select");
            loaded.loadEnded(characterId);

            assertThat(ran).containsExactly("select", "write", "queued by select");
        }
    }

    @Nested
    class Busy {
        @Test
        void busy_shouldListLoadingAndBuiltCharactersOutsideTheWorld() {
            final UUID building = EntityIds.next();
            final UUID inWorld = EntityIds.next();
            final Player other = mock(Player.class);
            loaded.startLoading(building);
            loaded.startLoading(characterId);
            loaded.loaded(characterId, player);
            loaded.loadEnded(characterId);
            loaded.startLoading(inWorld);
            loaded.loaded(inWorld, other);
            loaded.loadEnded(inWorld);
            loaded.entered(inWorld, other);

            assertThat(loaded.busy()).containsExactlyInAnyOrder(building, characterId);
        }
    }
}
