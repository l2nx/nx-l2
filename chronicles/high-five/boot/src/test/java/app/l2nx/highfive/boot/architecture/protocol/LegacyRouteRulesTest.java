/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

class LegacyRouteRulesTest {
    // Outside app.l2nx.highfive: HighFiveApplication scans it, and the fixture beans would join its context.
    private static final String FIXTURE = "app.l2nx.architecture.fixture.";
    private static final String WELLFORMED = FIXTURE + "wellformed";
    private static final String BROKEN = FIXTURE + "broken";

    @Test
    void serverPacketReferences_shouldPass_whenOnlyTheAdapterReferencesServerPackets() {
        final JavaClasses classes = new ClassFileImporter().importPackages(WELLFORMED);

        final EvaluationResult actual = new LegacyRouteRules(WELLFORMED, WELLFORMED + ".wire")
                .serverPacketReferences()
                .evaluate(classes);

        assertThat(actual.hasViolation()).isFalse();
    }

    @Test
    void serverPacketReferences_shouldReportTheTopLevelClassOnce_whenItAndItsNestedClassesReferenceAPacket() {
        final JavaClasses classes = new ClassFileImporter().importPackages(BROKEN);

        final EvaluationResult actual = new LegacyRouteRules(BROKEN, BROKEN + ".wire")
                .serverPacketReferences()
                .evaluate(classes);

        assertThat(actual.getFailureReport().getDetails())
                .containsExactly(
                        BROKEN + ".login.Notice references " + BROKEN + ".network.protocol.p267.serverpackets.Pong");
    }

    @Test
    void wireRecordReferences_shouldPass_whenOnlyTheAdapterReferencesWireRecords() {
        final JavaClasses classes = new ClassFileImporter().importPackages(WELLFORMED);

        final EvaluationResult actual = new LegacyRouteRules(WELLFORMED, WELLFORMED + ".wire")
                .wireRecordReferences()
                .evaluate(classes);

        assertThat(actual.hasViolation()).isFalse();
    }

    @Test
    void wireRecordReferences_shouldReport_whenASliceReferencesAWireRecord() {
        final JavaClasses classes = new ClassFileImporter().importPackages(BROKEN);

        final EvaluationResult actual = new LegacyRouteRules(BROKEN, BROKEN + ".wire")
                .wireRecordReferences()
                .evaluate(classes);

        assertThat(actual.getFailureReport().getDetails())
                .anyMatch(line -> line.contains(BROKEN + ".login.Greeting")
                        && line.contains(BROKEN + ".wire.p267.serverpackets.Ping"));
    }
}
