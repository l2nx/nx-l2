/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdentityRegistryTest {
    private final IdentityRegistry registry =
            new IdentityRegistry(new WireHandleAllocator(Clock.systemUTC(), Duration.ofMinutes(5)));

    @Nested
    class HandleOf {
        @Test
        void handleOf_shouldReturnSameHandle_forSameId() {
            final UUID id = EntityIds.next();

            assertThat(registry.handleOf(id)).isEqualTo(registry.handleOf(id));
        }

        @Test
        void handleOf_shouldReturnDifferentHandles_forDifferentIds() {
            assertThat(registry.handleOf(EntityIds.next())).isNotEqualTo(registry.handleOf(EntityIds.next()));
        }
    }

    @Nested
    class IdOf {
        @Test
        void idOf_shouldReturnLoadedId() {
            final UUID id = EntityIds.next();
            final int handle = registry.handleOf(id);

            assertThat(registry.idOf(handle)).isEqualTo(id);
        }

        @Test
        void idOf_shouldAssignStableId_toNewEntity() {
            final int handle = registry.allocateTransient();

            final UUID id = registry.idOf(handle);

            assertThat(registry.idOf(handle)).isEqualTo(id);
            assertThat(registry.handleOf(id)).isEqualTo(handle);
        }
    }

    @Nested
    class FindHandle {
        @Test
        void findHandle_shouldReturnHandle_whenEntityLoaded() {
            final UUID id = EntityIds.next();
            final int handle = registry.handleOf(id);

            assertThat(registry.findHandle(id)).hasValue(handle);
        }

        @Test
        void findHandle_shouldBeEmpty_whenEntityNeverLoaded() {
            assertThat(registry.findHandle(EntityIds.next())).isEmpty();
        }
    }

    @Nested
    class Release {
        @Test
        void release_shouldForgetMapping() {
            final UUID id = EntityIds.next();
            final int handle = registry.handleOf(id);

            registry.release(handle);

            assertThat(registry.findId(handle)).isEmpty();
            assertThat(registry.findHandle(id)).isEmpty();
            assertThat(registry.handleOf(id)).isNotEqualTo(handle);
        }
    }
}
