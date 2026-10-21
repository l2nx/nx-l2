/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.report.model;

/** Why a run is invalid; an invalid run is neither a pass nor a fail. */
public enum InvalidReason {
    GENERATOR_LAG,
    LOAD_BOTS_STOPPED
}
