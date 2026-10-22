/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.partition;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Explicit constructor: Lombok would not copy the qualifier, and the scheduler is a {@link TaskExecutor} too.
 */
@Component
class PartitionBridgeBinding {
    PartitionBridgeBinding(
            TaskScheduler taskScheduler, @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        BackgroundTasks.bind(taskScheduler, taskExecutor);
    }
}
