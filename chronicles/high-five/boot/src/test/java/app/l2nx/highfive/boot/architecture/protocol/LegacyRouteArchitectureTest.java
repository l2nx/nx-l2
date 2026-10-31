/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.protocol;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static org.assertj.core.api.Assertions.assertThat;

import app.l2nx.highfive.network.protocol.p267.P267Adapter;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Packets not yet moved to ports are legacy routes. Both measures may only fall: porting a packet lowers the pin and
 * the store.
 */
@AnalyzeClasses(
        packages = {"app.l2nx.highfive", "app.l2nx.engine"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class LegacyRouteArchitectureTest {
    private static final int LEGACY_INBOUND_OPCODES = 274;

    @ArchTest
    static final ArchRule serverPacketReferences = freeze(LegacyRouteRules.CHRONICLE.serverPacketReferences());

    @ArchTest
    static final ArchRule wireRecordReferences = LegacyRouteRules.CHRONICLE.wireRecordReferences();

    @ArchTest
    static void legacyInboundOpcodes_shouldMatchThePin(JavaClasses classes) {
        final int actual = new P267Adapter().legacyInboundCount();

        assertThat(actual)
                .as("inbound opcodes of the p267 table served by legacy packets, now %d", actual)
                .isEqualTo(LEGACY_INBOUND_OPCODES);
    }
}
