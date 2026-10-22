/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock(BackgroundTasksTest.LOCK)
class PartitionBridgeBindingTest {
    // The application enables scheduling, which is what makes Boot configure the scheduler, itself a TaskExecutor.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(TaskExecutionAutoConfiguration.class, TaskSchedulingAutoConfiguration.class))
            .withUserConfiguration(Scheduling.class, PartitionBridgeBinding.class);

    private BackgroundTasks.Binding previous;

    @BeforeEach
    void unbind() {
        previous = BackgroundTasks.swap(null);
    }

    @AfterEach
    void restore() {
        BackgroundTasks.swap(previous);
    }

    @Nested
    class Construct {
        @Test
        void construct_shouldBindTheAutoConfiguredSchedulerAndExecutor() {
            runner.run(context -> {
                final BackgroundTasks.Binding actual = BackgroundTasks.swap(null);

                assertThat(actual.scheduler()).isSameAs(context.getBean(TaskScheduler.class));
                assertThat(actual.executor()).isSameAs(context.getBean("applicationTaskExecutor"));
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class Scheduling {}
}
