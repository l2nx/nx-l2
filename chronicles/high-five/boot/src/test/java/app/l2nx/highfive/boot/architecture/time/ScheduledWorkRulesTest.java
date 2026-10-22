/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.boot.architecture.time.fixture.AllowedScheduledWork;
import app.l2nx.highfive.boot.architecture.time.fixture.ForbiddenBlockingWork;
import app.l2nx.highfive.boot.architecture.time.fixture.ForbiddenScheduledWork;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ScheduledWorkRulesTest {
    static Stream<Class<?>> forbidden() {
        return Arrays.stream(ForbiddenScheduledWork.class.getDeclaredClasses());
    }

    static Stream<Class<?>> blocking() {
        return Arrays.stream(ForbiddenBlockingWork.class.getDeclaredClasses());
    }

    @Nested
    class ThreadsAndExecutors {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.boot.architecture.time.ScheduledWorkRulesTest#forbidden")
        void rules_shouldReport_whenGameCodeRunsWorkOffTheLoop(Class<?> fixture) {
            final JavaClasses classes = new ClassFileImporter().importClasses(fixture);

            assertThat(hasViolation(ScheduledWorkRules.NO_THREADS_OR_EXECUTORS, classes)
                            || hasViolation(ScheduledWorkRules.NO_EXECUTOR_FIELDS, classes))
                    .isTrue();
        }

        @Test
        void rules_shouldPass_whenWorkStaysOnTheCallingThread() {
            final JavaClasses classes = new ClassFileImporter().importClasses(AllowedScheduledWork.class);

            assertThat(hasViolation(ScheduledWorkRules.NO_THREADS_OR_EXECUTORS, classes))
                    .isFalse();
            assertThat(hasViolation(ScheduledWorkRules.NO_EXECUTOR_FIELDS, classes))
                    .isFalse();
        }

        @Test
        void forbidden_shouldCoverEveryFixture() {
            assertThat(forbidden()).hasSize(13);
        }
    }

    @Nested
    class BlockingCalls {
        @ParameterizedTest(name = "{0}")
        @MethodSource("app.l2nx.highfive.boot.architecture.time.ScheduledWorkRulesTest#blocking")
        void noBlockingCalls_shouldReport_whenGameCodeBlocksItsThread(Class<?> fixture) {
            final JavaClasses classes = new ClassFileImporter().importClasses(fixture);

            assertThat(hasViolation(ScheduledWorkRules.NO_BLOCKING_CALLS, classes))
                    .isTrue();
        }

        @Test
        void noBlockingCalls_shouldPass_whenFuturesAreOnlyPolled() {
            final JavaClasses classes = new ClassFileImporter().importClasses(AllowedScheduledWork.class);

            assertThat(hasViolation(ScheduledWorkRules.NO_BLOCKING_CALLS, classes))
                    .isFalse();
        }

        @Test
        void blocking_shouldCoverEveryFixture() {
            assertThat(blocking()).hasSize(10);
        }
    }

    private static boolean hasViolation(ArchRule rule, JavaClasses classes) {
        return rule.evaluate(classes).hasViolation();
    }
}
