/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture.time;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "app.l2nx.highfive", importOptions = ImportOption.DoNotIncludeTests.class)
class ScheduledWorkArchitectureTest {
    @ArchTest
    static final ArchRule scheduledWorkGoesThroughTheLoop = ScheduledWorkRules.SCHEDULED_WORK_GOES_THROUGH_THE_LOOP;

    @ArchTest
    static final ArchRule noThreadsOrExecutors = ScheduledWorkRules.NO_THREADS_OR_EXECUTORS;

    @ArchTest
    static final ArchRule noExecutorFields = ScheduledWorkRules.NO_EXECUTOR_FIELDS;

    @ArchTest
    static final ArchRule noBlockingCalls = ScheduledWorkRules.NO_BLOCKING_CALLS;
}
