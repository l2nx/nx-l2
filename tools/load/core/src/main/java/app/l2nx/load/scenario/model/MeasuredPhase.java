/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.load.scenario.model;

/**
 * The phase of the injection profile a load scenario measures: client latencies, lifecycle steps, the server meter
 * window and the generator self-check all follow it.
 */
public enum MeasuredPhase {
    STEADY,
    /**
     * The ramp: the whole arrival (steps and intents up to the final state, however late) of every load bot whose
     * intended start lies in it, not its in-game actions or logout. Server meters run until every arrival is done.
     */
    RAMP
}
