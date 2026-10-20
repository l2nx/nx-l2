/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "app.l2nx.highfive", importOptions = ImportOption.DoNotIncludeTests.class)
class ScheduledWorkArchitectureTest {
    @ArchTest
    static final ArchRule scheduledWorkGoesThroughThreadPool =
            ScheduledWorkRules.SCHEDULED_WORK_GOES_THROUGH_THREAD_POOL;
}
