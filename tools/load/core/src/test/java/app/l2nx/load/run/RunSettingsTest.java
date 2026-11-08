/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import app.l2nx.load.gate.model.GateThresholds;
import app.l2nx.load.injection.InjectionProfile;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RunSettingsTest {
    @Nested
    class LifecycleTimeout {
        @Test
        void build_shouldRefuse_whenTheLifecycleTimeoutIsBelowTheIntentTimeout() {
            final RunSettings.RunSettingsBuilder settings =
                    settings().intentTimeout(Duration.ofSeconds(5)).lifecycleTimeout(Duration.ofSeconds(4));

            assertThatIllegalArgumentException()
                    .isThrownBy(settings::build)
                    .withMessageContaining("lifecycle timeout PT4S")
                    .withMessageContaining("intent timeout PT5S");
        }

        @Test
        void build_shouldAccept_whenTheLifecycleTimeoutEqualsTheIntentTimeout() {
            final RunSettings actual = settings()
                    .intentTimeout(Duration.ofSeconds(5))
                    .lifecycleTimeout(Duration.ofSeconds(5))
                    .build();

            assertThat(actual.lifecycleTimeout()).isEqualTo(Duration.ofSeconds(5));
        }
    }

    @Nested
    class Gate {
        @Test
        void build_shouldRefuse_whenTheGateThresholdsAreMissing() {
            final RunSettings.RunSettingsBuilder settings = settings().gate(null);

            assertThatIllegalArgumentException().isThrownBy(settings::build).withMessageContaining("gate thresholds");
        }

        @Test
        void withDefaults_shouldTakeTheGateDefaults() {
            final RunSettings actual = settings().build();

            assertThat(actual.gate()).isEqualTo(GateThresholds.defaults());
        }
    }

    private static RunSettings.RunSettingsBuilder settings() {
        return RunSettings.withDefaults()
                .scenario("load-fake")
                .protocol("fake")
                .injection(InjectionProfile.builder()
                        .bots(1)
                        .ramp(Duration.ZERO)
                        .warmUp(Duration.ZERO)
                        .steady(Duration.ofSeconds(1))
                        .rampDown(Duration.ZERO)
                        .build());
    }
}
