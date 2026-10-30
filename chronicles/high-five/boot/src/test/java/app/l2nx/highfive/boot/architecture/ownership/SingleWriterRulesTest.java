/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.boot.architecture.ownership.fixture.AllowedSingleWriter;
import app.l2nx.highfive.boot.architecture.ownership.fixture.ForbiddenSingleWriter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SingleWriterRulesTest {
    private static final List<ArchRule> RULES = List.of(
            SingleWriterRules.NO_CONCURRENT_STATE,
            SingleWriterRules.NO_LOCK_CALLS,
            SingleWriterRules.NO_PUBLIC_MUTABLE_FIELDS,
            SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER);

    private static final Map<String, ArchRule> RULE_BY_FIXTURE = Map.ofEntries(
            Map.entry("ConcurrentMapField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("ConcurrentKeySetField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("AtomicField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("VolatileField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("LockField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("ExecutorField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("CompletableFutureField", SingleWriterRules.NO_CONCURRENT_STATE),
            Map.entry("LockCall", SingleWriterRules.NO_LOCK_CALLS),
            Map.entry("SynchronizedWrapper", SingleWriterRules.NO_LOCK_CALLS),
            Map.entry("PublicMutableField", SingleWriterRules.NO_PUBLIC_MUTABLE_FIELDS),
            Map.entry("UncheckedPublicMethod", SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER),
            Map.entry("CheckedAfterCall", SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER),
            Map.entry("CheckedAfterFieldWrite", SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER),
            Map.entry("UncheckedPublicStaticMethod", SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER));

    static Stream<Class<?>> forbidden() {
        return Arrays.stream(ForbiddenSingleWriter.class.getDeclaredClasses());
    }

    static Stream<Class<?>> allowed() {
        return Arrays.stream(AllowedSingleWriter.class.getDeclaredClasses());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("forbidden")
    void rules_shouldReportOnlyTheBrokenClause_whenSingleWriterBreaksIt(Class<?> fixture) {
        final JavaClasses classes = new ClassFileImporter().importClasses(fixture);

        assertThat(RULES)
                .filteredOn(rule -> hasViolation(rule, classes))
                .containsExactly(RULE_BY_FIXTURE.get(fixture.getSimpleName()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allowed")
    void rules_shouldPass_whenSingleWriterIsCheckedOrClassIsNotAnnotated(Class<?> fixture) {
        final JavaClasses classes = new ClassFileImporter().importClasses(fixture);

        assertThat(RULES).filteredOn(rule -> hasViolation(rule, classes)).isEmpty();
    }

    @Test
    void forbidden_shouldCoverEveryFixture() {
        assertThat(forbidden().map(Class::getSimpleName)).containsExactlyInAnyOrderElementsOf(RULE_BY_FIXTURE.keySet());
    }

    private static boolean hasViolation(ArchRule rule, JavaClasses classes) {
        return rule.evaluate(classes).hasViolation();
    }
}
