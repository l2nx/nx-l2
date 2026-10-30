/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.ownership;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "app.l2nx.highfive", importOptions = ImportOption.DoNotIncludeTests.class)
class SingleWriterArchitectureTest {
    @ArchTest
    static final ArchRule noConcurrentState = SingleWriterRules.NO_CONCURRENT_STATE;

    @ArchTest
    static final ArchRule noLockCalls = SingleWriterRules.NO_LOCK_CALLS;

    @ArchTest
    static final ArchRule noPublicMutableFields = SingleWriterRules.NO_PUBLIC_MUTABLE_FIELDS;

    @ArchTest
    static final ArchRule publicMethodsCheckOwner = SingleWriterRules.PUBLIC_METHODS_CHECK_OWNER;
}
