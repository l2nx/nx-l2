/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.engine.core.loop.timer;

/** Entries of one sequence and deadline run in this order, so a TICK runs before the END of its effect. */
public enum TimerKind {
    ACTION,
    TICK,
    END
}
