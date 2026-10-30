/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.reporting.Report;
import net.sourceforge.pmd.reporting.RuleViolation;
import org.junit.jupiter.api.Test;

class SingleWriterPmdRulesetTest {
    private static final String RULESET = Objects.requireNonNull(
            System.getProperty("l2nx.pmd.single-writer-ruleset"), "l2nx.pmd.single-writer-ruleset is not set");

    @Test
    void ruleset_shouldReportMethodsAndBlock_whenTypeIsSingleWriter() {
        assertThat(violations("AnnotatedSynchronized.java"))
                .extracting(violation -> violation.getRule().getName())
                .containsExactlyInAnyOrder(
                        "AvoidSynchronizedAtMethodLevel",
                        "AvoidSynchronizedStatement",
                        "AvoidSynchronizedAtMethodLevel",
                        "AvoidSynchronizedAtMethodLevel",
                        "AvoidSynchronizedStatement");
    }

    @Test
    void ruleset_shouldStaySilent_whenTypeIsNotSingleWriter() {
        assertThat(violations("PlainSynchronized.java")).isEmpty();
    }

    private static List<RuleViolation> violations(String fixture) {
        final PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(LanguageRegistry.PMD.getLanguageVersionById("java", "25"));
        config.addRuleSet(RULESET);
        config.addInputPath(fixturePath(fixture));
        config.setIgnoreIncrementalAnalysis(true);
        // hasAnnotation resolves the annotation type through the auxiliary classpath, as the Gradle plugin passes it.
        config.prependAuxClasspath(System.getProperty("java.class.path"));
        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            final Report report = pmd.performAnalysisAndCollectReport();
            // A broken ruleset or XPath reports errors instead of violations, which would pass the silent case.
            assertThat(report.getConfigurationErrors()).isEmpty();
            assertThat(report.getProcessingErrors()).isEmpty();
            assertThat(pmd.getRulesets())
                    .flatExtracting(ruleSet -> ruleSet.getRules())
                    .hasSize(2);
            return report.getViolations();
        }
    }

    private static Path fixturePath(String fixture) {
        try {
            return Path.of(Objects.requireNonNull(
                            SingleWriterPmdRulesetTest.class.getResource("/pmd/single-writer/" + fixture), fixture)
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
