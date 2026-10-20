/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.boot.architecture.fixture.AllowedTimeAndRandomness;
import app.l2nx.highfive.boot.architecture.fixture.ForbiddenTimeAndRandomness;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TimeAndRandomnessRulesTest {
    static Stream<Class<?>> forbidden() {
        return Arrays.stream(ForbiddenTimeAndRandomness.class.getDeclaredClasses());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forbidden")
    void rule_shouldReport_whenForbiddenApiIsUsed(Class<?> fixture) {
        final JavaClasses classes = new ClassFileImporter().importClasses(fixture);

        assertThat(TimeAndRandomnessRules.NO_SYSTEM_TIME_OR_GLOBAL_RANDOM
                        .evaluate(classes)
                        .hasViolation())
                .isTrue();
    }

    @Test
    void rule_shouldPass_whenClockAndGeneratorArePassedIn() {
        final JavaClasses classes = new ClassFileImporter().importClasses(AllowedTimeAndRandomness.class);

        assertThat(TimeAndRandomnessRules.NO_SYSTEM_TIME_OR_GLOBAL_RANDOM
                        .evaluate(classes)
                        .hasViolation())
                .isFalse();
    }

    @Test
    void forbidden_shouldCoverEveryFixture() {
        assertThat(forbidden()).hasSize(15);
    }
}
