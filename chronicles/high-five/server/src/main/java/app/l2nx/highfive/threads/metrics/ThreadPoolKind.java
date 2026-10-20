/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.threads.metrics;

import lombok.Getter;

public enum ThreadPoolKind {
    SCHEDULED("l2nx-scheduled", true),
    HIGH_PRIORITY_SCHEDULED("l2nx-high-priority-scheduled", true),
    INSTANT("l2nx-instant", false);

    @Getter
    private final String tagValue;

    @Getter
    private final boolean scheduled;

    ThreadPoolKind(String tagValue, boolean scheduled) {
        this.tagValue = tagValue;
        this.scheduled = scheduled;
    }
}
