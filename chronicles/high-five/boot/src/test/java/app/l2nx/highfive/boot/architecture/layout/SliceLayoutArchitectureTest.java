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
    private static final SliceLayoutRules ENGINE = SliceLayoutRules.ENGINE;

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
    static final ArchRule ports = RULES.ports();

    @ArchTest
    static final ArchRule networkUsesSliceApi = RULES.networkUsesSliceApi();

    @ArchTest
    static final ArchRule legacyUsesSliceApi = RULES.legacyUsesSliceApi();

    @ArchTest
    static final ArchRule slicesWithoutNetwork = RULES.slicesWithoutNetwork();

    @ArchTest
    static final ArchRule chatChannelsWithoutNetwork = RULES.chatChannelsWithoutNetwork();

    @ArchTest
    static final ArchRule noImplOrGateway = RULES.noImplOrGateway();

    // Legacy code breaks this; src/test/archunit-store holds its violations and may only shrink.
    @ArchTest
    static final ArchRule repositories = freeze(RULES.repositories());

    @ArchTest
    static void modules(JavaClasses classes) {
        RULES.modules().verify();
    }

    @ArchTest
    static final ArchRule engineSubpackages = ENGINE.subpackages();

    @ArchTest
    static final ArchRule engineHandlers = ENGINE.handlers();

    @ArchTest
    static final ArchRule engineHandlerConstructors = ENGINE.handlerConstructors();

    @ArchTest
    static final ArchRule engineCommands = ENGINE.commands();

    @ArchTest
    static final ArchRule engineCommandPackages = ENGINE.commandPackages();

    @ArchTest
    static final ArchRule engineViewEvents = ENGINE.viewEvents();

    @ArchTest
    static final ArchRule enginePorts = ENGINE.ports();

    @ArchTest
    static final ArchRule engineLegacyUsesSliceApi = ENGINE.legacyUsesSliceApi();

    @ArchTest
    static final ArchRule engineNoImplOrGateway = ENGINE.noImplOrGateway();

    @ArchTest
    static final ArchRule engineRepositories = ENGINE.repositories();

    // A Modulith verify over the one-module engine root compares nothing; this rule holds its chronicle consumers to
    // the API.
    @ArchTest
    static final ArchRule chronicleUsesEngineApi = ENGINE.consumersUseSliceApi(SliceLayoutRules.CHRONICLE_ROOT);
}
