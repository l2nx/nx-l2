/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

/** What ended the count of passing levels of a capacity run. */
public enum CapacityLimit {
    PASS_BUDGET,
    /** The next level was invalid: the generator, not the server, lagged. */
    GENERATOR,
    LOAD_BOTS_FAILED,
    SERVER_METERS,
    /** Every level passed: the capacity is at least the last level. */
    LAST_LEVEL
}
