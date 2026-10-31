/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.layout;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SliceLayoutRulesTest {
    // Outside app.l2nx.highfive: HighFiveApplication scans it, and the fixture beans would join its context.
    private static final String FIXTURE = "app.l2nx.architecture.fixture.";
    private static final String WELLFORMED = FIXTURE + "wellformed";
    private static final String BROKEN = FIXTURE + "broken";

    private static final Map<String, Function<SliceLayoutRules, ArchRule>> RULES = Map.ofEntries(
            Map.entry("subpackages", SliceLayoutRules::subpackages),
            Map.entry("handlers", SliceLayoutRules::handlers),
            Map.entry("handlerConstructors", SliceLayoutRules::handlerConstructors),
            Map.entry("commands", SliceLayoutRules::commands),
            Map.entry("commandPackages", SliceLayoutRules::commandPackages),
            Map.entry("viewEvents", SliceLayoutRules::viewEvents),
            Map.entry("domainEvents", SliceLayoutRules::domainEvents),
            Map.entry("ports", SliceLayoutRules::ports),
            Map.entry("repositories", SliceLayoutRules::repositories),
            Map.entry("noImplOrGateway", SliceLayoutRules::noImplOrGateway),
            Map.entry("slicesWithoutNetwork", SliceLayoutRules::slicesWithoutNetwork),
            Map.entry("networkUsesSliceApi", SliceLayoutRules::networkUsesSliceApi),
            Map.entry("legacyUsesSliceApi", SliceLayoutRules::legacyUsesSliceApi),
            Map.entry("chatChannelsWithoutNetwork", SliceLayoutRules::chatChannelsWithoutNetwork));

    static Set<String> rules() {
        return RULES.keySet();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void rule_shouldPass_whenTheSliceFollowsTheLayout(String rule) {
        final JavaClasses classes = new ClassFileImporter().importPackages(WELLFORMED);

        final ArchRule actual = RULES.get(rule).apply(new SliceLayoutRules(WELLFORMED, List.of("login")));

        assertThat(actual.evaluate(classes).hasViolation()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    void rule_shouldReport_whenTheSliceBreaksTheLayout(String rule) {
        final JavaClasses classes = new ClassFileImporter().importPackages(BROKEN);

        final ArchRule actual = RULES.get(rule).apply(new SliceLayoutRules(BROKEN, List.of("login")));

        assertThat(actual.evaluate(classes).hasViolation()).isTrue();
    }
}
