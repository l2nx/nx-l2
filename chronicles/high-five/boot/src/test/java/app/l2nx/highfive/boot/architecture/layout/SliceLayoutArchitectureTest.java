/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.layout;

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = {"app.l2nx.highfive", "app.l2nx.engine"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class SliceLayoutArchitectureTest {
    private static final SliceLayoutRules RULES = SliceLayoutRules.CHRONICLE;

    @ArchTest
    static final ArchRule subpackages = RULES.subpackages();

    @ArchTest
    static final ArchRule handlers = RULES.handlers();

    @ArchTest
    static final ArchRule handlerConstructors = RULES.handlerConstructors();

    @ArchTest
    static final ArchRule commands = RULES.commands();

    @ArchTest
    static final ArchRule commandPackages = RULES.commandPackages();

    @ArchTest
    static final ArchRule viewEvents = RULES.viewEvents();

    @ArchTest
    static final ArchRule domainEvents = RULES.domainEvents();

    @ArchTest
    static final ArchRule ports = RULES.ports();

    @ArchTest
    static final ArchRule networkUsesSliceApi = RULES.networkUsesSliceApi();

    @ArchTest
    static final ArchRule legacyUsesSliceApi = RULES.legacyUsesSliceApi();

    @ArchTest
    static final ArchRule slicesWithoutNetwork = RULES.slicesWithoutNetwork();

    // Legacy code breaks these; src/test/archunit-store holds its violations and may only shrink.
    @ArchTest
    static final ArchRule repositories = freeze(RULES.repositories());

    @ArchTest
    static final ArchRule noImplOrGateway = freeze(RULES.noImplOrGateway());

    @ArchTest
    static void modules(JavaClasses classes) {
        RULES.modules().verify();
    }
}
