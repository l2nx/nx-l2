/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "app.l2nx.engine.core", importOptions = ImportOption.DoNotIncludeTests.class)
class EngineDependencyArchitectureTest {
    // Sessions are the network's input queue ; loops, clocks, identities and partitions know no transport.
    @ArchTest
    static final ArchRule onlySessionsUseTheNetworkLibrary = noClasses()
            .that()
            .resideInAPackage("app.l2nx.engine.core..")
            .and()
            .resideOutsideOfPackage("app.l2nx.engine.core.session..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("app.l2nx.network..")
            .as("only engine sessions depend on libs/network");
}
