/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.l2nx.highfive.boot.architecture.persistence.fixture.LoopSideJdbcFixtures;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoopSideJdbcRulesTest {
    private static boolean hasViolation(Class<?> loopSide) {
        final JavaClasses classes = new ClassFileImporter().importClasses(loopSide);
        final ArchRule rule = LoopSideJdbcRules.loopSideTouchesNoDatabase(List.of(loopSide.getName()));
        return rule.evaluate(classes).hasViolation();
    }

    @Nested
    class LoopSideTouchesNoDatabase {
        @ParameterizedTest(name = "{0}")
        @ValueSource(
                classes = {
                    LoopSideJdbcFixtures.UsesJdbcClient.class,
                    LoopSideJdbcFixtures.UsesJdbcTemplate.class,
                    LoopSideJdbcFixtures.UsesDataSource.class,
                    LoopSideJdbcFixtures.UsesRepository.class
                })
        void rule_shouldReport_whenALoopSideClassReachesTheDatabase(Class<?> fixture) {
            assertThat(hasViolation(fixture)).isTrue();
        }

        @Test
        void rule_shouldPass_whenTheLoopSideClassTouchesNoDatabase() {
            assertThat(hasViolation(LoopSideJdbcFixtures.Clean.class)).isFalse();
        }

        @Test
        void rule_shouldFail_whenNoLoopSideClassIsFound() {
            final JavaClasses classes = new ClassFileImporter().importClasses(LoopSideJdbcFixtures.Clean.class);

            final ArchRule rule = LoopSideJdbcRules.loopSideTouchesNoDatabase(List.of("app.l2nx.highfive.mail.Gone"));

            assertThatThrownBy(() -> rule.evaluate(classes))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("failed to check any classes");
        }
    }
}
