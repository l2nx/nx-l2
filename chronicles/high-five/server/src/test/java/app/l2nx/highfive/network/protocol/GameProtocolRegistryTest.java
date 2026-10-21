/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.network.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GameProtocolRegistryTest {
    private static final GameProtocolProperties STRICT = new GameProtocolProperties(false);
    private static final GameProtocolProperties TEST_PROFILE = new GameProtocolProperties(true);

    @Nested
    class Find {
        @Test
        void find_shouldReturnTheAdapterOfTheVersion() {
            final StubAdapter adapter = new StubAdapter("a", 267, 10);
            final GameProtocolRegistry registry = new GameProtocolRegistry(List.of(adapter), STRICT);

            assertThat(registry.find(267)).containsSame(adapter);
            assertThat(registry.find(140)).isEmpty();
        }
    }

    @Nested
    class Constructor {
        @Test
        void constructor_shouldRefuseNoAdapter() {
            assertThatThrownBy(() -> new GameProtocolRegistry(List.of(), STRICT))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void constructor_shouldRefuseASecondAdapter_whileAnyAdapterHasLegacyRoutes() {
            assertThatThrownBy(() -> new GameProtocolRegistry(
                            List.of(new StubAdapter("highfive", 267, 10), new StubAdapter("b", 140, 0)), STRICT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("10 legacy");
        }

        @Test
        void constructor_shouldRefuseASecondAdapter_whenTheLegacyRoutesSitOutsideTheHighFiveAdapter() {
            assertThatThrownBy(() -> new GameProtocolRegistry(
                            List.of(new StubAdapter("a", 267, 3), new StubAdapter("b", 140, 4)), STRICT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("7 legacy");
        }

        @Test
        void constructor_shouldAcceptASecondAdapter_onceNoAdapterHasLegacyRoutes() {
            assertThat(new GameProtocolRegistry(
                                    List.of(new StubAdapter("highfive", 267, 0), new StubAdapter("b", 140, 0)), STRICT)
                            .adapters())
                    .hasSize(2);
        }

        @Test
        void constructor_shouldAcceptASecondAdapter_whenTheTestProfileAllowsIt() {
            assertThat(new GameProtocolRegistry(
                                    List.of(new StubAdapter("highfive", 267, 10), new StubAdapter("b", 140, 0)),
                                    TEST_PROFILE)
                            .adapters())
                    .hasSize(2);
        }

        @Test
        void constructor_shouldRefuseTwoAdaptersOfOneVersion() {
            assertThatThrownBy(() -> new GameProtocolRegistry(
                            List.of(new StubAdapter("a", 267, 0), new StubAdapter("b", 267, 0)), TEST_PROFILE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("267");
        }
    }
}
