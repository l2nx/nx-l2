/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.boot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import java.util.Timer;
import java.util.TimerTask;

final class ScheduledWorkRules {
    static final ArchRule SCHEDULED_WORK_GOES_THROUGH_THREAD_POOL = noClasses()
            .that()
            .resideInAPackage("app.l2nx.highfive..")
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(Timer.class, TimerTask.class)
            .because("scheduled game work goes through ThreadPool, which meters it and reports task failures;"
                    + " a java.util.Timer thread dies silently on the first exception");

    private ScheduledWorkRules() {}
}
