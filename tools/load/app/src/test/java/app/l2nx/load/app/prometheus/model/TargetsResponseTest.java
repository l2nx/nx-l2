/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.app.prometheus.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TargetsResponseTest {
    @Nested
    class ScrapeIntervals {
        @Test
        void scrapeIntervals_shouldSkipATargetWithoutAJob() {
            final TargetsResponse response = success(List.of(
                    TargetsResponse.Target.builder()
                            .labels(Map.of("instance", "a:1"))
                            .scrapeInterval("1s")
                            .build(),
                    TargetsResponse.Target.builder()
                            .labels(Map.of("job", "game"))
                            .scrapeInterval("2s")
                            .build()));

            assertThat(response.scrapeIntervals()).isEqualTo(Map.of("game", Duration.ofSeconds(2)));
        }

        @Test
        void scrapeIntervals_shouldNameTheReason_whenTheEnvelopeIsAnError() {
            final TargetsResponse response = TargetsResponse.builder()
                    .status("error")
                    .errorType("bad_data")
                    .error("unknown state")
                    .build();

            assertThatIllegalStateException()
                    .isThrownBy(response::scrapeIntervals)
                    .withMessageContaining("bad_data")
                    .withMessageContaining("unknown state");
        }

        @Test
        void scrapeIntervals_shouldKeepItsTargets_whenTheSourceChanges() {
            final Map<String, String> labels = new HashMap<>(Map.of("job", "game"));
            final List<TargetsResponse.Target> targets = new ArrayList<>(List.of(TargetsResponse.Target.builder()
                    .labels(labels)
                    .scrapeInterval("1s")
                    .build()));
            final TargetsResponse response = success(targets);

            labels.put("job", "login");
            targets.clear();

            assertThat(response.scrapeIntervals()).isEqualTo(Map.of("game", Duration.ofSeconds(1)));
        }
    }

    @Nested
    class ParseDuration {
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "0s, PT0S",
            "500ms, PT0.5S",
            "1s, PT1S",
            "1m30s, PT1M30S",
            "2h, PT2H",
            "1d12h, PT36H",
            "1w, PT168H",
            "1y, PT8760H",
            "1m1s1ms, PT1M1.001S"
        })
        void duration_shouldReadPrometheusDurations(String text, Duration expected) {
            assertThat(TargetsResponse.duration(text)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"1.5s", "30s1m", "1 s", "-1s", "10"})
        void duration_shouldRefuse_whenPrometheusWouldNotWriteIt(String text) {
            assertThatIllegalStateException()
                    .isThrownBy(() -> TargetsResponse.duration(text))
                    .withMessageContaining(String.valueOf(text));
        }
    }

    private static TargetsResponse success(List<TargetsResponse.Target> targets) {
        return TargetsResponse.builder()
                .status("success")
                .data(TargetsResponse.Data.builder().activeTargets(targets).build())
                .build();
    }
}
